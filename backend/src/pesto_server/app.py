"""FastAPI app: GET /health and the dictation WebSocket /v1/dictate."""

import asyncio
import contextlib
import secrets
from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, ValidationError

from .config import Settings
from .llm.claude_cli import ClaudeCodeCli, LlmProvider
from .llm.profiles import load_profiles
from .pipeline import TextPipeline
from .protocol import Error, Start, client_message
from .session import DictationSession, InboxItem, SessionLimits
from .stt import SonioxClient, SttConfig, SttFactory

# Close codes: 1008 policy violation (token), 1003 unsupported data.
_CLOSE_POLICY = 1008


def soniox_factory(settings: Settings) -> SttFactory:
    async def connect(config: SttConfig):
        return await SonioxClient.connect(
            settings.soniox_url, settings.soniox_api_key, settings.soniox_model, config
        )

    return connect


def default_provider(settings: Settings) -> LlmProvider:
    return ClaudeCodeCli(
        settings.claude_cmd,
        settings.llm_model,
        effort=settings.llm_effort,
        timeout_s=settings.llm_timeout_s,
        idle_s=settings.llm_warm_idle_s,
    )


def token_ok(websocket: WebSocket, expected: str) -> bool:
    if not expected:
        return False
    header = websocket.headers.get("authorization", "")
    given = header.removeprefix("Bearer ").strip() or websocket.query_params.get(
        "token", ""
    )
    return secrets.compare_digest(given.encode(), expected.encode())


def create_app(
    settings: Settings | None = None,
    *,
    stt_factory: SttFactory | None = None,
    provider: LlmProvider | None = None,
    limits: SessionLimits | None = None,
) -> FastAPI:
    settings = settings or Settings()
    stt_factory = stt_factory or soniox_factory(settings)
    provider = provider or default_provider(settings)
    limits = limits or SessionLimits.from_settings(settings)
    pipeline = TextPipeline(load_profiles(settings.profiles_dir), provider)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        yield
        await provider.aclose()

    app = FastAPI(title="pesto-server", lifespan=lifespan)
    app.state.pipeline = pipeline

    @app.get("/health")
    async def health() -> dict:
        return {"status": "ok"}

    @app.websocket("/v1/dictate")
    async def dictate(websocket: WebSocket) -> None:
        if not token_ok(websocket, settings.pesto_app_token):
            await websocket.close(code=_CLOSE_POLICY)
            return
        await websocket.accept()

        async def emit(message: BaseModel) -> None:
            await websocket.send_text(message.model_dump_json())

        async def reject(code: str, message: str) -> None:
            await emit(Error(code=code, message=message, retryable=False))
            await websocket.close()

        try:
            start = client_message.validate_json(await websocket.receive_text())
        except (ValidationError, KeyError) as e:
            await reject("bad_request", str(e))
            return
        except WebSocketDisconnect:
            return
        if not isinstance(start, Start):
            await reject("bad_request", "the first message must be start")
            return
        if not pipeline.has_profile(start.profile):
            await reject("unknown_profile", f"no profile named {start.profile!r}")
            return

        inbox: asyncio.Queue[InboxItem] = asyncio.Queue()
        session = DictationSession(
            start,
            stt_factory=stt_factory,
            pipeline=pipeline,
            emit=emit,
            limits=limits,
            metrics_path=settings.metrics_path,
        )
        task = asyncio.get_running_loop().create_task(session.run(inbox))
        receiver = asyncio.get_running_loop().create_task(
            _receive(websocket, inbox, emit)
        )
        await asyncio.wait([task, receiver], return_when=asyncio.FIRST_COMPLETED)
        if not task.done():
            await task  # the client left: the session sees None and stops
        receiver.cancel()
        with contextlib.suppress(asyncio.CancelledError, WebSocketDisconnect):
            await receiver
        with contextlib.suppress(RuntimeError):
            await websocket.close()

    return app


async def _receive(websocket: WebSocket, inbox: asyncio.Queue, emit) -> None:
    try:
        while True:
            message = await websocket.receive()
            if message["type"] == "websocket.disconnect":
                break
            if message.get("bytes") is not None:
                inbox.put_nowait(message["bytes"])
            elif message.get("text") is not None:
                try:
                    item = client_message.validate_json(message["text"])
                except ValidationError as e:
                    await emit(
                        Error(code="bad_request", message=str(e), retryable=False)
                    )
                    continue
                if isinstance(item, Start):
                    await emit(
                        Error(
                            code="bad_request",
                            message="already started",
                            retryable=False,
                        )
                    )
                    continue
                inbox.put_nowait(item)
    finally:
        inbox.put_nowait(None)
