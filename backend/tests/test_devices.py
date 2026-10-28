"""Pairing (Δ24): one-time codes, a token per device, only hashes on disk."""

import hashlib
import json

import pytest
from fakes import FakeClock

from pesto_server.devices import CODE_TTL_S, MAX_ATTEMPTS, DeviceRegistry


@pytest.fixture
def clock():
    return FakeClock()


@pytest.fixture
def path(tmp_path):
    return tmp_path / "data" / "devices.json"


@pytest.fixture
def registry(path, clock):
    return DeviceRegistry(path, clock)


def wrong_code(code: str) -> str:
    return "00000000" if code != "00000000" else "11111111"


def test_a_code_is_eight_digits(registry):
    code = registry.new_code()
    assert len(code) == 8
    assert code.isdigit()


def test_the_right_code_gives_a_token(registry):
    token = registry.pair(registry.new_code(), "POCO X7 Pro")
    assert token
    assert len(token) >= 40
    assert registry.is_valid(token)
    assert [d.name for d in registry.devices()] == ["POCO X7 Pro"]


def test_a_code_works_once(registry):
    code = registry.new_code()
    assert registry.pair(code, "POCO X7 Pro")
    assert registry.pair(code, "Pixel") is None
    assert len(registry.devices()) == 1


def test_an_expired_code_is_wrong(registry, clock):
    code = registry.new_code()
    clock.advance(CODE_TTL_S + 1)
    assert registry.pair(code, "POCO X7 Pro") is None
    assert registry.devices() == []


def test_five_wrong_attempts_drop_the_code(registry):
    code = registry.new_code()
    for _ in range(MAX_ATTEMPTS):
        assert registry.pair(wrong_code(code), "x") is None
    assert registry.pair(code, "POCO X7 Pro") is None


def test_four_wrong_attempts_keep_the_code(registry):
    code = registry.new_code()
    for _ in range(MAX_ATTEMPTS - 1):
        assert registry.pair(wrong_code(code), "x") is None
    assert registry.pair(code, "POCO X7 Pro")


def test_without_a_code_every_attempt_is_wrong(registry):
    assert registry.pair("12345678", "POCO X7 Pro") is None


def test_a_new_code_replaces_the_old_one(registry):
    old = registry.new_code()
    new = registry.new_code()
    if old != new:
        assert registry.pair(old, "POCO X7 Pro") is None
    assert registry.pair(new, "POCO X7 Pro")


def test_the_file_keeps_only_hashes(registry, path):
    code = registry.new_code()
    assert code not in path.read_text()
    token = registry.pair(code, "POCO X7 Pro")
    text = path.read_text()
    assert token not in text
    assert code not in text
    assert hashlib.sha256(token.encode()).hexdigest() in text
    assert json.loads(text)["code"] is None


def test_revoke_works_without_a_restart(path):
    server = DeviceRegistry(path)
    token = server.pair(server.new_code(), "POCO X7 Pro")
    assert server.is_valid(token)
    # The command line is another process, with its own registry on the same file.
    assert DeviceRegistry(path).revoke("POCO X7 Pro")
    assert not server.is_valid(token)


def test_pairing_again_with_the_same_name_replaces_the_device(registry):
    old = registry.pair(registry.new_code(), "POCO X7 Pro")
    new = registry.pair(registry.new_code(), "POCO X7 Pro")
    assert not registry.is_valid(old)
    assert registry.is_valid(new)
    assert len(registry.devices()) == 1


def test_two_devices_have_their_own_tokens(registry):
    poco = registry.pair(registry.new_code(), "POCO X7 Pro")
    pixel = registry.pair(registry.new_code(), "Pixel 9")
    assert registry.revoke("Pixel 9")
    assert registry.is_valid(poco)
    assert not registry.is_valid(pixel)


def test_wrong_or_empty_tokens(registry):
    registry.pair(registry.new_code(), "POCO X7 Pro")
    assert not registry.is_valid("")
    assert not registry.is_valid("nope")


def test_revoke_an_unknown_device(registry):
    assert not registry.revoke("nobody")


def test_a_long_or_empty_name(registry):
    registry.pair(registry.new_code(), "x" * 500)
    registry.pair(registry.new_code(), "   ")
    assert sorted(len(d.name) for d in registry.devices()) == [6, 60]
