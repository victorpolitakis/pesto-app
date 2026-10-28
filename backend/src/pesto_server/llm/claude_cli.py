"""Haiku through Claude Code headless (`claude -p`), with the subscription.

Every dictation gets its own `claude` process, so the context is always
clean. A spare process for the last profile is started ahead of time (warm),
so the ~4 s start-up is not paid after the user stops talking.

Safety of the call:
- the process is started without a shell, from an empty temporary folder,
  with a minimal environment (no ANTHROPIC_API_KEY, so it always uses the
  subscription, and nothing from the parent Claude Code session);
- the transcript never goes on the command line: it is written to stdin as
  one stream-json message built with json.dumps.
"""

import asyncio
import contextlib
import json
import os
import shutil
import signal
import tempfile
import time
from dataclasses import dataclass, field
from typing import Protocol

from .profiles import Profile

OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {"text": {"type": "string"}},
    "required": ["text"],
    "additionalProperties": False,
}

_ENV_KEEP = ("HOME", "PATH", "USER", "LOGNAME", "LANG", "LC_ALL", "LC_CTYPE", "TMPDIR")
_STREAM_LIMIT = 4 * 1024 * 1024


class LlmError(Exception):
    """reason: timeout, wrong_model, failed, bad_output."""

    def __init__(self, reason: str, detail: str = ""):
        super().__init__(f"{reason}: {detail}" if detail else reason)
        self.reason = reason


@dataclass
class LlmResult:
    text: str
    model: str
    latency_ms: int
    usage: dict = field(default_factory=dict)


class LlmProvider(Protocol):
    async def warm(self, profile: Profile) -> None: ...
    async def rewrite(self, profile: Profile, prompt: str) -> LlmResult: ...
    async def aclose(self) -> None: ...


def clean_env() -> dict[str, str]:
    return {k: os.environ[k] for k in _ENV_KEEP if k in os.environ}


def user_message(prompt: str) -> bytes:
    message = {
        "type": "user",
        "message": {"role": "user", "content": [{"type": "text", "text": prompt}]},
    }
    return (json.dumps(message, ensure_ascii=False) + "\n").encode()


def parse_result(event: dict, model: str) -> tuple[str, dict]:
    if event.get("subtype") != "success" or event.get("is_error"):
        raise LlmError("failed", str(event.get("subtype")))
    models = set((event.get("modelUsage") or {}).keys())
    if models != {model}:
        raise LlmError("wrong_model", ",".join(sorted(models)) or "none")
    output = event.get("structured_output")
    if output is None:
        try:
            output = json.loads(event.get("result") or "")
        except json.JSONDecodeError as e:
            raise LlmError("bad_output", "no structured output") from e
    text = output.get("text") if isinstance(output, dict) else None
    if not isinstance(text, str):
        raise LlmError("bad_output", "no text field")
    return text, event.get("usage") or {}


class WarmProcess:
    """One `claude -p` process, started early, used for one dictation."""

    def __init__(self, profile_id: str, proc: asyncio.subprocess.Process, cwd: str):
        self.profile_id = profile_id
        self.started_at = time.monotonic()
        self._proc = proc
        self._cwd = cwd

    @classmethod
    async def start(cls, cmd: list[str], profile: Profile, model: str, effort: str):
        cwd = tempfile.mkdtemp(prefix="pesto-llm-")
        args = [
            *cmd,
            "-p",
            "--model", model,
            "--effort", profile.effort or effort,
            "--input-format", "stream-json",
            "--output-format", "stream-json",
            "--verbose",
            "--system-prompt", profile.prompt,
            "--json-schema", json.dumps(OUTPUT_SCHEMA),
            "--tools", "",
            "--setting-sources", "project",
            "--disable-slash-commands",
            "--permission-mode", "dontAsk",
            "--max-turns", "1",
            "--no-session-persistence",
            "--strict-mcp-config",
        ]  # fmt: skip
        try:
            proc = await asyncio.create_subprocess_exec(
                *args,
                cwd=cwd,
                env=clean_env(),
                stdin=asyncio.subprocess.PIPE,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.DEVNULL,
                start_new_session=True,
                limit=_STREAM_LIMIT,
            )
        except OSError as e:
            shutil.rmtree(cwd, ignore_errors=True)
            raise LlmError("failed", str(e)) from e
        return cls(profile.id, proc, cwd)

    @property
    def alive(self) -> bool:
        return self._proc.returncode is None

    async def run(self, prompt: str, model: str) -> tuple[str, dict]:
        assert self._proc.stdin and self._proc.stdout
        try:
            self._proc.stdin.write(user_message(prompt))
            await self._proc.stdin.drain()
            self._proc.stdin.close()
        except (BrokenPipeError, ConnectionResetError) as e:
            raise LlmError("failed", "claude exited before the input") from e
        while line := await self._proc.stdout.readline():
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            if event.get("type") == "result":
                return parse_result(event, model)
        raise LlmError("failed", "claude exited without a result")

    async def close(self) -> None:
        if self.alive:
            with contextlib.suppress(ProcessLookupError):
                os.killpg(self._proc.pid, signal.SIGKILL)
        with contextlib.suppress(ProcessLookupError):
            await self._proc.wait()
        shutil.rmtree(self._cwd, ignore_errors=True)


class ClaudeCodeCli:
    def __init__(
        self,
        cmd: list[str],
        model: str,
        effort: str = "medium",
        timeout_s: float = 10.0,
        idle_s: float = 15 * 60,
    ):
        self._cmd = cmd
        self._model = model
        self._effort = effort
        self._timeout_s = timeout_s
        self._idle_s = idle_s
        self._spare: WarmProcess | None = None
        self._lock = asyncio.Lock()
        self._idle_task: asyncio.Task | None = None

    async def warm(self, profile: Profile) -> None:
        # TODO(X13): with my own profiles or a style, the key of the spare becomes
        # sha256(final system prompt + effort) instead of the id, here and in
        # rewrite(): else a profile with a new style gets the old prompt.
        async with self._lock:
            spare = self._spare
            if spare and spare.profile_id == profile.id and spare.alive:
                self._restart_idle_timer()
                return
            self._spare = None
            if spare:
                await spare.close()
            self._spare = await WarmProcess.start(
                self._cmd, profile, self._model, self._effort
            )
            self._restart_idle_timer()

    async def rewrite(self, profile: Profile, prompt: str) -> LlmResult:
        async with self._lock:
            proc = self._spare
            self._spare = None
        if not (proc and proc.profile_id == profile.id and proc.alive):
            if proc:
                await proc.close()
            proc = await WarmProcess.start(
                self._cmd, profile, self._model, self._effort
            )
        started = time.perf_counter()
        try:
            async with asyncio.timeout(self._timeout_s):
                text, usage = await proc.run(prompt, self._model)
        except TimeoutError as e:
            raise LlmError("timeout") from e
        finally:
            await proc.close()
            asyncio.get_running_loop().create_task(self._rewarm(profile))
        latency_ms = int((time.perf_counter() - started) * 1000)
        return LlmResult(
            text=text, model=self._model, latency_ms=latency_ms, usage=usage
        )

    async def aclose(self) -> None:
        if self._idle_task:
            self._idle_task.cancel()
        async with self._lock:
            spare, self._spare = self._spare, None
        if spare:
            await spare.close()

    async def _rewarm(self, profile: Profile) -> None:
        with contextlib.suppress(LlmError):
            await self.warm(profile)

    def _restart_idle_timer(self) -> None:
        if self._idle_task:
            self._idle_task.cancel()
        self._idle_task = asyncio.get_running_loop().create_task(
            self._close_when_idle()
        )

    async def _close_when_idle(self) -> None:
        await asyncio.sleep(self._idle_s)
        async with self._lock:
            spare, self._spare = self._spare, None
        if spare:
            await spare.close()
