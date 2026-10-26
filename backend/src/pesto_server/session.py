"""One dictation: audio in, speech-to-text, the text pipeline, the final message.

The session does not know about the WebSocket. It reads audio and control
messages from a queue and sends its messages through `emit`, so it can be
started by anything (WebSocket today, a file or a tile tomorrow).
"""

import asyncio
import contextlib
import json
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from pydantic import BaseModel

from .config import Settings
from .pipeline import TextPipeline
from .protocol import (
    BYTES_PER_SECOND,
    Cancel,
    Error,
    Final,
    Finalize,
    Partial,
    Pause,
    Ready,
    Resume,
    Start,
    Stats,
)
from .stt import SttClient, SttConfig, SttError, SttFactory

Emit = Callable[[BaseModel], Awaitable[None]]
# None means the client went away, which is handled like a cancel.
InboxItem = bytes | Pause | Resume | Finalize | Cancel | None


@dataclass
class SessionLimits:
    silence_timeout_s: float = 30.0
    pause_limit_s: float = 120.0
    max_audio_s: float = 600.0
    keepalive_interval_s: float = 10.0
    finalize_timeout_s: float = 10.0
    tick_s: float = 0.25

    @classmethod
    def from_settings(cls, settings: Settings) -> SessionLimits:
        return cls(
            silence_timeout_s=settings.silence_timeout_s,
            pause_limit_s=settings.pause_limit_s,
            max_audio_s=settings.max_audio_s,
            keepalive_interval_s=settings.keepalive_interval_s,
            finalize_timeout_s=settings.finalize_timeout_s,
        )


def write_metrics(path: Path, final: Final) -> None:
    """One line of numbers per dictation. Never text, never audio."""
    record = {
        "ts": datetime.now(UTC).isoformat(timespec="seconds"),
        "profile": final.profile,
        "end_reason": final.end_reason,
        "fallback_reason": final.fallback_reason,
        **final.stats.model_dump(),
    }
    with contextlib.suppress(OSError):
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8") as f:
            f.write(json.dumps(record) + "\n")


class DictationSession:
    def __init__(
        self,
        start: Start,
        *,
        stt_factory: SttFactory,
        pipeline: TextPipeline,
        emit: Emit,
        limits: SessionLimits,
        metrics_path: Path | None = None,
        clock: Callable[[], float] = time.monotonic,
    ):
        self._start = start
        self._stt_factory = stt_factory
        self._pipeline = pipeline
        self._emit = emit
        self._limits = limits
        self._metrics_path = metrics_path
        self._clock = clock
        self._final = ""
        self._pending = ""
        self._audio_bytes = 0
        self._last_text_at = clock()
        self._fin = asyncio.Event()
        self._stt_error: SttError | None = None

    async def run(self, inbox: asyncio.Queue[InboxItem]) -> None:
        start = self._start
        asyncio.get_running_loop().create_task(self._pipeline.prepare(start.profile))
        config = SttConfig(
            language_hints=start.language_hints,
            language_hints_strict=start.language_hints_strict,
            terms=start.terms,
        )
        try:
            stt = await self._stt_factory(config)
        except SttError as e:
            await self._emit(
                Error(code="stt_unavailable", message=str(e), retryable=True)
            )
            return
        reader = asyncio.get_running_loop().create_task(self._read(stt))
        try:
            await self._emit(Ready())
            end_reason = await self._loop(stt, inbox)
            if end_reason is not None:
                await self._finish(stt, end_reason)
        finally:
            reader.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await reader
            with contextlib.suppress(SttError):
                await stt.close()

    async def _read(self, stt: SttClient) -> None:
        try:
            async for update in stt.updates():
                changed = False
                if update.final_text:
                    self._final += update.final_text
                    changed = True
                if update.pending_text != self._pending:
                    self._pending = update.pending_text
                    changed = True
                if changed:
                    self._last_text_at = self._clock()
                    await self._emit(
                        Partial(final_text=self._final, pending_text=self._pending)
                    )
                if update.finalized:
                    self._fin.set()
        except SttError as e:
            self._stt_error = e
        finally:
            self._fin.set()

    async def _loop(
        self, stt: SttClient, inbox: asyncio.Queue[InboxItem]
    ) -> str | None:
        """Returns the end reason, or None for a cancel."""
        limits = self._limits
        paused_at: float | None = None
        last_keepalive = 0.0
        self._last_text_at = self._clock()
        try:
            while True:
                if self._stt_error:
                    return await self._report_stt_error()
                try:
                    item = await asyncio.wait_for(inbox.get(), timeout=limits.tick_s)
                except TimeoutError:
                    item = ...
                now = self._clock()

                if isinstance(item, bytes):
                    if paused_at is None:
                        self._audio_bytes += len(item)
                        await stt.send_audio(item)
                elif isinstance(item, Pause):
                    if paused_at is None:
                        paused_at = last_keepalive = now
                elif isinstance(item, Resume):
                    if paused_at is not None:
                        paused_at = None
                        self._last_text_at = now
                elif isinstance(item, Finalize):
                    return "user"
                elif item is None or isinstance(item, Cancel):
                    return None

                if self._audio_bytes / BYTES_PER_SECOND >= limits.max_audio_s:
                    return "max_duration"
                if paused_at is not None:
                    if now - paused_at >= limits.pause_limit_s:
                        return "pause_limit"
                    if now - last_keepalive >= limits.keepalive_interval_s:
                        await stt.keepalive()
                        last_keepalive = now
                elif now - self._last_text_at >= limits.silence_timeout_s:
                    return "silence"
        except SttError as e:
            self._stt_error = e
            return await self._report_stt_error()

    async def _report_stt_error(self) -> str | None:
        assert self._stt_error
        await self._emit(
            Error(code="stt", message=str(self._stt_error), retryable=True)
        )
        # Whatever was transcribed so far is still delivered.
        return "error" if (self._final + self._pending).strip() else None

    async def _finish(self, stt: SttClient, end_reason: str) -> None:
        ended = time.perf_counter()
        if end_reason != "error":
            try:
                await stt.finalize()
                async with asyncio.timeout(self._limits.finalize_timeout_s):
                    await self._fin.wait()
            except SttError, TimeoutError:
                pass
        stt_ms = _ms_since(ended)
        # The STT connection is closed by run() after the final is sent:
        # Soniox takes ~1 s to close, which the user should not wait for.

        raw = (self._final + self._pending).strip()
        start = self._start
        result = await self._pipeline.process(raw, start.profile, start.field_context)
        final = Final(
            raw_text=raw,
            text=result.text,
            profile=start.profile,
            fallback_reason=result.fallback_reason,
            end_reason=end_reason,
            stats=Stats(
                audio_s=round(self._audio_bytes / BYTES_PER_SECOND, 2),
                words=len(result.text.split()),
                stt_ms=stt_ms,
                llm_ms=result.llm_ms,
                total_ms=_ms_since(ended),
            ),
        )
        await self._emit(final)
        if self._metrics_path:
            write_metrics(self._metrics_path, final)


def _ms_since(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)
