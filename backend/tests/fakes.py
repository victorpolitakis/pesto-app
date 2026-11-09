"""Fakes for tests without internet and without a Claude login."""

import asyncio
from collections.abc import AsyncIterator

from pesto_server.assistant import AssistantService, PendingPlan, _sha256
from pesto_server.llm.claude_cli import LlmError, LlmJsonResult, LlmResult
from pesto_server.llm.profiles import Profile
from pesto_server.stt import SttConfig, SttError, SttUpdate


class FakeStt:
    """Turns every audio chunk into the next word of `words`.

    Each word first shows up as pending text and becomes final with the next
    chunk, or on finalize.
    """

    def __init__(
        self, config: SttConfig, words: list[str], fail_after: int | None = None
    ):
        self.config = config
        self.words = list(words)
        self.fail_after = fail_after
        self.chunks: list[bytes] = []
        self.keepalives = 0
        self.finalized = False
        self.closed = False
        self._queue: asyncio.Queue[SttUpdate | SttError | None] = asyncio.Queue()
        self._pending = ""

    async def send_audio(self, chunk: bytes) -> None:
        self.chunks.append(chunk)
        if self.fail_after is not None and len(self.chunks) > self.fail_after:
            self._queue.put_nowait(SttError("server_error", "fake failure"))
            return
        final, self._pending = self._pending, ""
        if self.words:
            self._pending = self.words.pop(0)
        self._queue.put_nowait(SttUpdate(final_text=final, pending_text=self._pending))

    async def keepalive(self) -> None:
        self.keepalives += 1

    async def finalize(self) -> None:
        self.finalized = True
        final, self._pending = self._pending, ""
        self._queue.put_nowait(SttUpdate(final_text=final, finalized=True))

    async def close(self) -> None:
        self.closed = True
        self._queue.put_nowait(None)

    async def updates(self) -> AsyncIterator[SttUpdate]:
        while (item := await self._queue.get()) is not None:
            if isinstance(item, SttError):
                raise item
            yield item


class FakeSttFactory:
    def __init__(
        self,
        words: list[str] | None = None,
        fail_after: int | None = None,
        connect_error: bool = False,
    ):
        self.words = words or []
        self.fail_after = fail_after
        self.connect_error = connect_error
        self.clients: list[FakeStt] = []

    async def __call__(self, config: SttConfig) -> FakeStt:
        if self.connect_error:
            raise SttError("connect", "fake connect failure")
        client = FakeStt(config, self.words, self.fail_after)
        self.clients.append(client)
        return client


class FakeLlm:
    """Returns `text` (or the result of `reply(prompt)`), or raises `error`."""

    def __init__(
        self,
        text: str = "",
        reply=None,
        json_reply=None,
        error: str | None = None,
    ):
        self.text = text
        self.reply = reply
        self.json_reply = json_reply
        self.error = error
        self.warmed: list[str] = []
        self.prompts: list[str] = []
        self.json_calls: list[dict] = []
        self.closed = False

    async def warm(self, profile: Profile) -> None:
        self.warmed.append(profile.id)

    async def rewrite(self, profile: Profile, prompt: str) -> LlmResult:
        self.prompts.append(prompt)
        if self.error:
            raise LlmError(self.error)
        text = self.reply(prompt) if self.reply else self.text
        return LlmResult(text=text, model="claude-haiku-5-5", latency_ms=1)

    async def complete_json(
        self,
        system_prompt: str,
        schema: dict,
        prompt: str,
        *,
        effort: str | None = None,
        timeout_s: float | None = None,
    ) -> LlmJsonResult:
        self.json_calls.append(
            {
                "system_prompt": system_prompt,
                "schema": schema,
                "prompt": prompt,
                "effort": effort,
                "timeout_s": timeout_s,
            }
        )
        if self.error:
            raise LlmError(self.error)
        data = (
            self.json_reply(system_prompt, schema, prompt)
            if self.json_reply
            else {"reply": self.text}
        )
        return LlmJsonResult(data=data, model="claude-haiku-5-5", latency_ms=1)

    async def aclose(self) -> None:
        self.closed = True


class FakeClock:
    def __init__(self) -> None:
        self.now = 1000.0

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


def register_plan(
    service: AssistantService,
    *,
    plan_id: str,
    device_key: str,
    query: str,
    locale: str,
    tools: tuple[str, ...],
) -> None:
    """Puts a plan with a known id in the service's store (tests only)."""
    service._plans._plans[plan_id] = PendingPlan(
        device_key=device_key,
        query_sha256=_sha256(query),
        locale=locale,
        tools=tools,
        created_at=service._plans._clock(),
    )
