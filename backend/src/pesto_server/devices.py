"""Devices that may dictate: a one-time pairing code and a token per device (Δ24).

`pesto-server pair` writes a code. The phone sends it to POST /v1/pair and
gets a token of its own, which `pesto-server revoke` can take back without
touching anything else. backend/data/devices.json (not in git) keeps only
sha256 hashes, of the code and of the tokens: nothing in it opens the
backend. The server reads the file at every check, so a `revoke` from the
command line works without a restart.

Never logged: a code or a token (same rule as the phone's PestoLog).
"""

import contextlib
import hashlib
import json
import os
import secrets
import tempfile
import time
from collections.abc import Callable
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path

CODE_DIGITS = 8
CODE_TTL_S = 10 * 60
MAX_ATTEMPTS = 5
MAX_NAME = 60


@dataclass(frozen=True)
class Device:
    name: str
    token_sha256: str
    paired_at: str


def _sha256(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


class DeviceRegistry:
    """The file: the active code, if any, and the paired devices."""

    def __init__(self, path: Path, clock: Callable[[], float] = time.time):
        self._path = path
        self._clock = clock

    def new_code(self) -> str:
        """A one-time code of 8 digits for 10 minutes. It replaces any earlier one."""
        code = f"{secrets.randbelow(10**CODE_DIGITS):0{CODE_DIGITS}d}"
        data = self._read()
        data["code"] = {
            "sha256": _sha256(code),
            "expires_at": self._clock() + CODE_TTL_S,
            "attempts": 0,
        }
        self._write(data)
        return code

    def pair(self, code: str, device_name: str) -> str | None:
        """The token of the new device, or None for a wrong, used or expired code.

        The code is dropped after its first use or after 5 wrong attempts, and
        without an active code every attempt is wrong. Pairing again with the
        name of a paired device replaces it (a reinstall loses its token).
        """
        data = self._read()
        active = data["code"]
        if not active:
            return None
        if active["expires_at"] < self._clock():
            data["code"] = None
            self._write(data)
            return None
        if not secrets.compare_digest(_sha256(code), active["sha256"]):
            active["attempts"] += 1
            if active["attempts"] >= MAX_ATTEMPTS:
                data["code"] = None
            self._write(data)
            return None
        token = secrets.token_urlsafe(32)
        name = device_name.strip()[:MAX_NAME] or "device"
        now = datetime.fromtimestamp(self._clock(), UTC)
        device = Device(name, _sha256(token), now.isoformat(timespec="seconds"))
        data["devices"] = [d for d in data["devices"] if d["name"] != name]
        data["devices"].append(asdict(device))
        data["code"] = None
        self._write(data)
        return token

    def is_valid(self, token: str) -> bool:
        if not token:
            return False
        digest = _sha256(token)
        return any(
            secrets.compare_digest(digest, d["token_sha256"])
            for d in self._read()["devices"]
        )

    def devices(self) -> list[Device]:
        return [Device(**d) for d in self._read()["devices"]]

    def revoke(self, name: str) -> bool:
        """False if no device has this name."""
        data = self._read()
        kept = [d for d in data["devices"] if d["name"] != name]
        if len(kept) == len(data["devices"]):
            return False
        data["devices"] = kept
        self._write(data)
        return True

    def _read(self) -> dict:
        try:
            data = json.loads(self._path.read_text(encoding="utf-8"))
        except FileNotFoundError:
            data = {}
        return {"code": data.get("code"), "devices": data.get("devices", [])}

    def _write(self, data: dict) -> None:
        # A new file renamed over the old one: the server never reads half a
        # file while the command line writes it. mkstemp makes it 0600.
        self._path.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=self._path.parent, prefix=".devices-")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2)
            os.replace(tmp, self._path)
        except BaseException:
            with contextlib.suppress(FileNotFoundError):
                os.unlink(tmp)
            raise
