"""Plays the phone: dictate into the backend and print what comes back.

Start the backend first (uv run pesto-server), then:

  uv run --group tools tools/ws_client.py --mic
  uv run --group tools tools/ws_client.py --mic --profile mail --terms Πολιτάκης
  uv run --group tools tools/ws_client.py --mic --all-profiles
  uv run tools/ws_client.py --text "στείλε μου το αρχείο αύριο" --all-profiles

--mic: talk to the Mac microphone. Enter = done (finalize), p + Enter =
pause/resume, Ctrl+C = cancel. Nothing is recorded or saved.
--text: skip speech-to-text and run the text through the profiles here,
without the backend and without Soniox (the profile playground).
"""

import argparse
import asyncio
import contextlib
import json
import shutil
import sys
import threading
import time
from collections.abc import AsyncIterator, Callable

import websockets

from pesto_server.app import default_provider
from pesto_server.config import Settings
from pesto_server.llm.profiles import load_profiles
from pesto_server.pipeline import TextPipeline
from pesto_server.protocol import SAMPLE_RATE

CHUNK_FRAMES = SAMPLE_RATE // 10  # 100 ms

Source = AsyncIterator[bytes | str]


async def run_dictation(
    url: str,
    token: str,
    start: dict,
    source: Source,
    on_message: Callable[[dict], None] | None = None,
) -> dict:
    """One dictation over the WebSocket.

    `source` yields audio chunks (bytes) or the controls "pause" / "resume".
    When it ends, finalize is sent. Returns the final message (or None), the
    errors, and the time from finalize to final as seen by the client.
    """
    headers = {"Authorization": f"Bearer {token}"}
    async with websockets.connect(url, additional_headers=headers) as ws:
        await ws.send(json.dumps({"type": "start", **start}))
        first = json.loads(await ws.recv())
        if on_message:
            on_message(first)
        if first["type"] != "ready":
            return {"final": None, "errors": [first], "client_ms": None}

        result: dict = {"final": None, "errors": [], "client_ms": None}

        async def receive() -> None:
            async for raw in ws:
                message = json.loads(raw)
                if on_message:
                    on_message(message)
                if message["type"] == "error":
                    result["errors"].append(message)
                elif message["type"] == "final":
                    result["final"] = message
                    return

        receiver = asyncio.create_task(receive())
        try:
            async with contextlib.aclosing(source):
                async for item in source:
                    if receiver.done():
                        break  # the backend ended the dictation (e.g. silence)
                    if isinstance(item, bytes):
                        await ws.send(item)
                    else:
                        await ws.send(json.dumps({"type": item}))
        except asyncio.CancelledError:
            # Ctrl+C: tell the backend, close the connection normally.
            receiver.cancel()
            with contextlib.suppress(Exception):
                await ws.send(json.dumps({"type": "cancel"}))
                await ws.close()
            await asyncio.gather(receiver, return_exceptions=True)
            raise
        finalized_at = None
        if not receiver.done():
            finalized_at = time.perf_counter()
            await ws.send(json.dumps({"type": "finalize"}))
        await receiver
        if result["final"] and finalized_at is not None:
            result["client_ms"] = int((time.perf_counter() - finalized_at) * 1000)
        return result


async def mic_source() -> Source:
    """Audio from the Mac microphone until Enter. p + Enter toggles pause."""
    import sounddevice as sd  # only in the tools dependency group

    loop = asyncio.get_running_loop()
    audio: asyncio.Queue[bytes] = asyncio.Queue()
    keys: asyncio.Queue[str] = asyncio.Queue()

    def on_audio(indata, frames, time_info, status) -> None:
        loop.call_soon_threadsafe(audio.put_nowait, bytes(indata))

    def read_keys() -> None:
        # A daemon thread, so that a dictation the backend ends on its own
        # (silence, limits) does not wait for an Enter before exiting.
        for line in sys.stdin:
            key = line.strip().lower()
            loop.call_soon_threadsafe(keys.put_nowait, key)
            if key != "p":
                return

    threading.Thread(target=read_keys, daemon=True).start()
    paused = False
    with sd.RawInputStream(
        samplerate=SAMPLE_RATE,
        channels=1,
        dtype="int16",
        blocksize=CHUNK_FRAMES,
        callback=on_audio,
    ):
        while True:
            while not keys.empty():
                key = keys.get_nowait()
                if key != "p":
                    return
                paused = not paused
                print("\n⏸  paused (p + Enter to resume)" if paused else "▶  resumed")
                yield "pause" if paused else "resume"
            try:
                chunk = await asyncio.wait_for(audio.get(), timeout=0.2)
            except TimeoutError:
                continue
            if not paused:
                yield chunk


def show(message: dict) -> None:
    kind = message["type"]
    if kind == "ready":
        print("🎙  Listening. Enter = done, p + Enter = pause, Ctrl+C = cancel")
    elif kind == "partial":
        line = (message["final_text"] + message["pending_text"]).replace("\n", " ")
        # Only what fits in one terminal row: a wrapped line cannot be redrawn.
        width = shutil.get_terminal_size().columns - 1
        print("\r\033[K" + line[-width:], end="", flush=True)
    elif kind == "error":
        print(f"\n⚠️  {message['code']}: {message['message']}")


def print_block(title: str, text: str, note: str = "") -> None:
    print(f"\n── {title} {note}".rstrip() + " " + "─" * 20)
    print(text)


async def run_profiles(text: str, profile_ids: list[str], settings: Settings) -> None:
    profiles = load_profiles(settings.profiles_dir)
    provider = default_provider(settings)
    pipeline = TextPipeline(profiles, provider)

    async def one(profile_id: str):
        started = time.perf_counter()
        result = await pipeline.process(text, profile_id)
        return profile_id, result, time.perf_counter() - started

    try:
        results = await asyncio.gather(*(one(p) for p in profile_ids))
    finally:
        await provider.aclose()
    for profile_id, result, seconds in results:
        note = f"({seconds:.1f} s)"
        if result.fallback_reason:
            note += f" fallback: {result.fallback_reason}"
        print_block(profile_id, result.text, note)


async def main() -> int:
    settings = Settings()
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    what = parser.add_mutually_exclusive_group(required=True)
    what.add_argument("--mic", action="store_true", help="dictate from the microphone")
    what.add_argument("--text", help="run this text through the profiles, no Soniox")
    parser.add_argument("--profile", default="raw")
    parser.add_argument("--all-profiles", action="store_true")
    parser.add_argument("--terms", action="append", default=[], help="repeatable")
    parser.add_argument("--languages", default="el,en")
    parser.add_argument("--strict-languages", action="store_true")
    parser.add_argument(
        "--url", default=f"ws://{settings.host}:{settings.port}/v1/dictate"
    )
    args = parser.parse_args()

    profile_ids = sorted(load_profiles(settings.profiles_dir))
    if args.text is not None:
        await run_profiles(
            args.text, profile_ids if args.all_profiles else [args.profile], settings
        )
        return 0

    start = {
        "profile": "raw" if args.all_profiles else args.profile,
        "terms": args.terms,
        "language_hints": args.languages.split(","),
        "language_hints_strict": args.strict_languages,
    }
    result = await run_dictation(
        args.url, settings.pesto_app_token, start, mic_source(), on_message=show
    )
    final = result["final"]
    if not final:
        return 1
    stats = final["stats"]
    note = (
        f"(end: {final['end_reason']}, {stats['audio_s']} s audio, "
        f"{stats['words']} words, "
    )
    if result["client_ms"] is not None:
        note += f"finalize → text {result['client_ms']} ms)"
    else:  # ended by the backend: its own time from the end to the text
        note += f"end → text {stats['total_ms']} ms)"
    if final["fallback_reason"]:
        note += f" fallback: {final['fallback_reason']}"
    if args.all_profiles:
        print_block("raw (Soniox)", final["raw_text"], note)
        others = [p for p in profile_ids if p != "raw"]
        await run_profiles(final["raw_text"], others, settings)
    else:
        if final["profile"] != "raw":
            print_block("raw (Soniox)", final["raw_text"])
        print_block(final["profile"], final["text"], note)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(asyncio.run(main()))
    except KeyboardInterrupt:
        print("\n✕ cancelled")
