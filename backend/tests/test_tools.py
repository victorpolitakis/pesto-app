"""The tools in backend/tools/, without internet: a real server with FakeStt."""

import asyncio
import json
import socket
import threading
import time

import pytest
import uvicorn
from conftest import TOKEN
from fakes import FakeLlm, FakeSttFactory
from stats import percentile, summarize
from ws_client import run_dictation

from pesto_server.app import create_app
from pesto_server.session import SessionLimits

CHUNK = b"\x00\x01" * 1600


@pytest.fixture
def server(settings, request):
    # Indirect parametrization can pass other SessionLimits.
    limits = getattr(request, "param", None) or SessionLimits(tick_s=0.005)
    app = create_app(
        settings,
        stt_factory=FakeSttFactory(["γεια ", "σου ", "κόσμε"]),
        provider=FakeLlm("Γεια σου, κόσμε!"),
        limits=limits,
    )
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        port = s.getsockname()[1]
    server = uvicorn.Server(uvicorn.Config(app, port=port, log_level="warning"))
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    while not server.started:
        time.sleep(0.01)
    yield f"ws://127.0.0.1:{port}/v1/dictate"
    server.should_exit = True
    thread.join(timeout=5)


async def source(items):
    for item in items:
        yield item
        await asyncio.sleep(0.01)


def test_run_dictation(server):
    seen = []
    items = [CHUNK, "pause", "resume", CHUNK, CHUNK]
    result = asyncio.run(
        run_dictation(server, TOKEN, {"profile": "chatter"}, source(items), seen.append)
    )
    final = result["final"]
    assert final["raw_text"] == "γεια σου κόσμε"
    assert final["text"] == "Γεια σου, κόσμε!"
    assert result["client_ms"] is not None
    assert result["errors"] == []
    assert seen[0]["type"] == "ready"
    assert any(m["type"] == "partial" for m in seen)


async def endless_audio():
    while True:
        yield CHUNK
        await asyncio.sleep(0.01)


@pytest.mark.parametrize(
    "server", [SessionLimits(tick_s=0.005, max_audio_s=0.25)], indirect=True
)
def test_backend_ends_the_dictation(server):
    """The backend stops on its own (here: max duration) while audio still flows."""
    result = asyncio.run(run_dictation(server, TOKEN, {}, endless_audio()))
    assert result["final"]["end_reason"] == "max_duration"
    assert result["client_ms"] is None  # no finalize was sent


def test_cancel_closes_cleanly(server):
    """Ctrl+C: cancel is sent, nothing is left unhandled, no final."""
    errors = []

    async def scenario():
        asyncio.get_running_loop().set_exception_handler(
            lambda loop, context: errors.append(context)
        )
        task = asyncio.create_task(run_dictation(server, TOKEN, {}, endless_audio()))
        await asyncio.sleep(0.2)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        await asyncio.sleep(0.1)

    asyncio.run(scenario())
    assert errors == []


def test_run_dictation_unknown_profile(server):
    result = asyncio.run(run_dictation(server, TOKEN, {"profile": "x"}, source([])))
    assert result["final"] is None
    assert result["errors"][0]["code"] == "unknown_profile"


def test_percentile():
    assert percentile([], 50) is None
    assert percentile([3, 1, 2], 50) == 2
    assert percentile(list(range(1, 21)), 90) == 18


def test_summarize():
    records = [
        {
            "profile": "raw",
            "total_ms": 150,
            "end_reason": "user",
            "fallback_reason": None,
            "audio_s": 30,
        },
        {
            "profile": "mail",
            "total_ms": 1700,
            "end_reason": "silence",
            "fallback_reason": "guard:input",
            "audio_s": 30,
        },
    ]
    summary = summarize(records)
    assert summary["dictations"] == 2
    assert summary["latency_ms"]["raw"]["p50"] == 150
    assert summary["latency_ms"]["profile"]["p50"] == 1700
    assert summary["cuts"] == 1
    assert summary["fallbacks"] == {"guard:input": 1}
    assert summary["audio_minutes"] == 1.0
    assert summary["soniox_usd"] == 0.002
    json.dumps(summary)
