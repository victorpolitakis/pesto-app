"""Speech-to-text: the SttClient interface and its Soniox implementation."""

import json
from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Protocol

import websockets

from .protocol import SAMPLE_RATE

# Marker tokens Soniox puts in the text: endpoint detected, finalize done.
END_TOKEN = "<end>"
FIN_TOKEN = "<fin>"


@dataclass
class SttConfig:
    language_hints: list[str]
    language_hints_strict: bool = False
    terms: list[str] = field(default_factory=list)


@dataclass
class SttUpdate:
    """One response from the STT: new final text, current pending text."""

    final_text: str = ""
    pending_text: str = ""
    finalized: bool = False


class SttError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code
        self.message = message


class SttClient(Protocol):
    async def send_audio(self, chunk: bytes) -> None: ...
    async def keepalive(self) -> None: ...
    async def finalize(self) -> None: ...
    async def close(self) -> None: ...
    def updates(self) -> AsyncIterator[SttUpdate]: ...


class SttFactory(Protocol):
    async def __call__(self, config: SttConfig) -> SttClient: ...


def soniox_config(model: str, config: SttConfig) -> dict:
    body: dict = {
        "model": model,
        "audio_format": "pcm_s16le",
        "sample_rate": SAMPLE_RATE,
        "num_channels": 1,
        "language_hints": config.language_hints,
        "language_hints_strict": config.language_hints_strict,
        "enable_endpoint_detection": True,
    }
    if config.terms:
        body["context"] = {"terms": config.terms}
    return body


def parse_soniox(response: dict) -> SttUpdate | None:
    """Turn one Soniox response into an update. None means the session ended."""
    if response.get("error_code") is not None:
        raise SttError(
            str(response.get("error_type") or response["error_code"]),
            str(response.get("error_message", "")),
        )
    if response.get("finished"):
        return None
    update = SttUpdate()
    for token in response.get("tokens", []):
        text = token.get("text", "")
        if text == FIN_TOKEN:
            update.finalized = True
        elif text == END_TOKEN:
            continue
        elif token.get("is_final"):
            update.final_text += text
        else:
            update.pending_text += text
    return update


class SonioxClient:
    def __init__(self, ws):
        self._ws = ws

    @classmethod
    async def connect(
        cls, url: str, api_key: str, model: str, config: SttConfig
    ) -> SonioxClient:
        try:
            ws = await websockets.connect(
                url, additional_headers={"Authorization": f"Bearer {api_key}"}
            )
            await ws.send(json.dumps(soniox_config(model, config)))
        except (OSError, websockets.WebSocketException) as e:
            raise SttError("connect", str(e)) from e
        return cls(ws)

    async def send_audio(self, chunk: bytes) -> None:
        await self._send(chunk)

    async def keepalive(self) -> None:
        await self._send(json.dumps({"type": "keepalive"}))

    async def finalize(self) -> None:
        await self._send(json.dumps({"type": "finalize"}))

    async def close(self) -> None:
        try:
            await self._ws.send("")  # empty text frame = end of audio
        except websockets.WebSocketException:
            pass
        await self._ws.close()

    async def updates(self) -> AsyncIterator[SttUpdate]:
        try:
            async for raw in self._ws:
                try:
                    response = json.loads(raw)
                except json.JSONDecodeError as e:
                    raise SttError("bad_response", str(e)) from e
                update = parse_soniox(response)
                if update is None:
                    return
                yield update
        except websockets.ConnectionClosedOK:
            return
        except websockets.WebSocketException as e:
            raise SttError("connection", str(e)) from e

    async def _send(self, data: bytes | str) -> None:
        try:
            await self._ws.send(data)
        except websockets.WebSocketException as e:
            raise SttError("connection", str(e)) from e
