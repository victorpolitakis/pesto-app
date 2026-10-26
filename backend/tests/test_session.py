import asyncio
import json

from fakes import FakeClock, FakeLlm, FakeSttFactory

from pesto_server.pipeline import TextPipeline
from pesto_server.protocol import (
    Cancel,
    Error,
    Final,
    Finalize,
    Partial,
    Pause,
    Ready,
    Resume,
    Start,
)
from pesto_server.session import DictationSession, SessionLimits

CHUNK = b"\x00\x01" * 1600  # 0.1 s of audio
WORDS = ["γεια ", "σου ", "κόσμε"]


class Harness:
    def __init__(
        self,
        profiles,
        *,
        start=None,
        words=WORDS,
        llm=None,
        limits=None,
        metrics_path=None,
        **stt,
    ):
        self.clock = FakeClock()
        self.factory = FakeSttFactory(words, **stt)
        self.sent = []
        self.inbox = asyncio.Queue()
        self.session = DictationSession(
            start or Start(type="start"),
            stt_factory=self.factory,
            pipeline=TextPipeline(profiles, llm or FakeLlm()),
            emit=self._emit,
            limits=limits or SessionLimits(tick_s=0.005),
            metrics_path=metrics_path,
            clock=self.clock,
        )

    async def _emit(self, message):
        self.sent.append(message)

    def of(self, kind):
        return [m for m in self.sent if isinstance(m, kind)]

    @property
    def stt(self):
        return self.factory.clients[0]


async def settle():
    for _ in range(20):
        await asyncio.sleep(0.005)


def test_dictation_flow(profiles, tmp_path):
    metrics = tmp_path / "m.jsonl"
    h = Harness(profiles, metrics_path=metrics)

    async def scenario():
        task = asyncio.create_task(h.session.run(h.inbox))
        for _ in WORDS:
            h.inbox.put_nowait(CHUNK)
        await settle()
        h.inbox.put_nowait(Finalize(type="finalize"))
        await task

    asyncio.run(scenario())
    assert isinstance(h.sent[0], Ready)
    partials = h.of(Partial)
    assert any(p.pending_text for p in partials)
    assert partials[-1].final_text == "γεια σου κόσμε"
    final = h.of(Final)[0]
    assert final.raw_text == "γεια σου κόσμε"
    assert final.text == "γεια σου κόσμε"
    assert final.end_reason == "user"
    assert final.stats.words == 3
    assert final.stats.audio_s == 0.3
    assert h.stt.finalized and h.stt.closed
    record = json.loads(metrics.read_text())
    assert record["end_reason"] == "user" and record["words"] == 3
    assert "γεια" not in metrics.read_text()  # numbers only, never text


def test_terms_and_languages_reach_the_stt(profiles):
    start = Start(
        type="start",
        terms=["Πολιτάκης"],
        language_hints=["el"],
        language_hints_strict=True,
    )
    h = Harness(profiles, start=start)

    async def scenario():
        task = asyncio.create_task(h.session.run(h.inbox))
        h.inbox.put_nowait(Cancel(type="cancel"))
        await task

    asyncio.run(scenario())
    config = h.stt.config
    assert config.terms == ["Πολιτάκης"]
    assert config.language_hints == ["el"]
    assert config.language_hints_strict is True


def test_cancel_leaves_no_trace(profiles, tmp_path):
    metrics = tmp_path / "m.jsonl"
    h = Harness(profiles, metrics_path=metrics)

    async def scenario():
        task = asyncio.create_task(h.session.run(h.inbox))
        h.inbox.put_nowait(CHUNK)
        await settle()
        h.inbox.put_nowait(Cancel(type="cancel"))
        await task

    asyncio.run(scenario())
    assert h.of(Final) == []
    assert h.stt.closed
    assert not metrics.exists()


def test_disconnect_is_a_cancel(profiles):
    h = Harness(profiles)

    async def scenario():
        task = asyncio.create_task(h.session.run(h.inbox))
        h.inbox.put_nowait(CHUNK)
        h.inbox.put_nowait(None)
        await task

    asyncio.run(scenario())
    assert h.of(Final) == []


def test_silence_ends_the_dictation(profiles):
    h = Harness(profiles)

    async def scenario():
        task = asyncio.create_task(h.session.run(h.inbox))
        h.inbox.put_nowait(CHUNK)
        await settle()
        h.clock.advance(29)
        await settle()
        assert not task.done()
        h.clock.advance(2)
        await task

    asyncio.run(scenario())
    assert h.of(Final)[0].end_reason == "silence"


def test_pause_sends_keepalives_and_has_a_limit(profiles):
    h = Harness(profiles)

    async def scenario():
        task = asyncio.create_task(h.session.run(h.inbox))
        h.inbox.put_nowait(CHUNK)
        h.inbox.put_nowait(Pause(type="pause"))
        await settle()
        for _ in range(11):  # 110 s of pause, silence does not count
            h.clock.advance(10)
            await settle()
        assert not task.done()
        h.inbox.put_nowait(CHUNK)  # audio during a pause is dropped
        await settle()
        h.clock.advance(10)
        await task

    asyncio.run(scenario())
    assert h.stt.keepalives == 11
    assert len(h.stt.chunks) == 1
    assert h.of(Final)[0].end_reason == "pause_limit"


def test_resume_restarts_the_silence_timer(profiles):
    h = Harness(profiles)

    async def scenario():
        task = asyncio.create_task(h.session.run(h.inbox))
        h.inbox.put_nowait(Pause(type="pause"))
        await settle()
        h.clock.advance(60)
        await settle()
        h.inbox.put_nowait(Resume(type="resume"))
        await settle()
        h.clock.advance(20)
        await settle()
        assert not task.done()
        h.inbox.put_nowait(Finalize(type="finalize"))
        await task

    asyncio.run(scenario())
    assert h.of(Final)[0].end_reason == "user"


def test_max_duration(profiles):
    h = Harness(profiles, limits=SessionLimits(tick_s=0.005, max_audio_s=0.25))

    async def scenario():
        task = asyncio.create_task(h.session.run(h.inbox))
        for _ in range(5):
            h.inbox.put_nowait(CHUNK)
        await task

    asyncio.run(scenario())
    assert h.of(Final)[0].end_reason == "max_duration"
    assert len(h.stt.chunks) == 3


def test_stt_error_keeps_what_was_transcribed(profiles):
    h = Harness(profiles, fail_after=2)

    async def scenario():
        task = asyncio.create_task(h.session.run(h.inbox))
        for _ in range(3):
            h.inbox.put_nowait(CHUNK)
        await task

    asyncio.run(scenario())
    assert h.of(Error)[0].code == "stt"
    final = h.of(Final)[0]
    assert final.end_reason == "error"
    assert final.raw_text == "γεια σου"


def test_stt_unavailable(profiles):
    h = Harness(profiles, connect_error=True)
    asyncio.run(h.session.run(h.inbox))
    assert [m.code for m in h.of(Error)] == ["stt_unavailable"]


def test_profile_is_applied_and_warmed(profiles):
    llm = FakeLlm("Γεια σου, κόσμε!")
    h = Harness(profiles, start=Start(type="start", profile="chatter"), llm=llm)

    async def scenario():
        task = asyncio.create_task(h.session.run(h.inbox))
        for _ in WORDS:
            h.inbox.put_nowait(CHUNK)
        await settle()
        h.inbox.put_nowait(Finalize(type="finalize"))
        await task

    asyncio.run(scenario())
    final = h.of(Final)[0]
    assert final.raw_text == "γεια σου κόσμε"
    assert final.text == "Γεια σου, κόσμε!"
    assert final.profile == "chatter"
    assert llm.warmed == ["chatter"]
