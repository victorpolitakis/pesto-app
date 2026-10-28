"""The pesto-server command: pair, devices, revoke, and the server itself."""

import re

import pytest
from conftest import DEVICE

from pesto_server import cli
from pesto_server.devices import DeviceRegistry


def test_pair_prints_a_code_without_the_app_token(settings, capsys):
    settings.pesto_app_token = ""
    cli.main(["pair"], settings)
    code = re.search(r"\b\d{8}\b", capsys.readouterr().out).group()
    assert DeviceRegistry(settings.devices_path).pair(code, DEVICE)


def test_devices_lists_names_never_tokens(settings, capsys, device_token):
    cli.main(["devices"], settings)
    out = capsys.readouterr().out
    assert DEVICE in out
    assert device_token not in out


def test_no_devices(settings, capsys):
    cli.main(["devices"], settings)
    assert "No paired devices" in capsys.readouterr().out


def test_revoke(settings, devices, device_token):
    cli.main(["revoke", DEVICE], settings)
    assert not devices.is_valid(device_token)
    with pytest.raises(SystemExit, match="No device"):
        cli.main(["revoke", DEVICE], settings)


def test_the_server_needs_the_app_token(settings):
    settings.pesto_app_token = ""
    with pytest.raises(SystemExit, match="PESTO_APP_TOKEN"):
        cli.main([], settings)


def test_the_server_starts_on_the_host_and_port_of_the_settings(settings, monkeypatch):
    started = []
    monkeypatch.setattr(
        "uvicorn.run", lambda app, host, port: started.append((host, port))
    )
    cli.main([], settings)
    assert started == [(settings.host, settings.port)]
