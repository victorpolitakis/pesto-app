import json

import pytest
from conftest import DEVICE, TOKEN
from fakes import FakeLlm, FakeSttFactory
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from pesto_server.app import create_app
from pesto_server.session import SessionLimits

CHUNK = b"\x00\x01" * 1600
AUTH = {"Authorization": f"Bearer {TOKEN}"}


@pytest.fixture
def client(settings):
    app = create_app(
        settings,
        stt_factory=FakeSttFactory(["γεια ", "σου"]),
        provider=FakeLlm("Γεια σου!"),
        limits=SessionLimits(tick_s=0.005),
    )
    with TestClient(app) as client:
        yield client


def receive_until(ws, kind):
    messages = []
    while True:
        message = json.loads(ws.receive_text())
        messages.append(message)
        if message["type"] in (kind, "error"):
            return messages


def test_health(client):
    assert client.get("/health").json() == {"status": "ok"}


@pytest.mark.parametrize("headers", [{}, {"Authorization": "Bearer wrong"}])
def test_token_is_required(client, headers):
    with pytest.raises(WebSocketDisconnect) as e:
        with client.websocket_connect("/v1/dictate", headers=headers) as ws:
            ws.receive_text()
    assert e.value.code == 1008


def test_token_in_query(client):
    with client.websocket_connect(f"/v1/dictate?token={TOKEN}") as ws:
        ws.send_text('{"type": "start"}')
        assert json.loads(ws.receive_text())["type"] == "ready"
        ws.send_text('{"type": "cancel"}')


def test_dictation_over_websocket(client, settings):
    with client.websocket_connect("/v1/dictate", headers=AUTH) as ws:
        ws.send_text(json.dumps({"type": "start", "profile": "chatter"}))
        assert json.loads(ws.receive_text())["type"] == "ready"
        ws.send_bytes(CHUNK)
        ws.send_bytes(CHUNK)
        assert json.loads(ws.receive_text())["type"] == "partial"
        ws.send_text('{"type": "finalize"}')
        final = receive_until(ws, "final")[-1]
    assert final["type"] == "final"
    assert final["raw_text"] == "γεια σου"
    assert final["text"] == "Γεια σου!"
    assert final["end_reason"] == "user"
    assert settings.metrics_path.exists()


@pytest.mark.parametrize(
    ("first", "code"),
    [
        ('{"type": "finalize"}', "bad_request"),
        ('{"type": "start", "profle": "mail"}', "bad_request"),
        ('{"type": "start", "profile": "nope"}', "unknown_profile"),
    ],
)
def test_bad_start(client, first, code):
    with client.websocket_connect("/v1/dictate", headers=AUTH) as ws:
        ws.send_text(first)
        message = json.loads(ws.receive_text())
    assert message["type"] == "error"
    assert message["code"] == code


def test_bad_message_mid_dictation_is_reported(client):
    with client.websocket_connect("/v1/dictate", headers=AUTH) as ws:
        ws.send_text('{"type": "start"}')
        assert json.loads(ws.receive_text())["type"] == "ready"
        ws.send_text('{"type": "jump"}')
        message = json.loads(ws.receive_text())
        assert message["code"] == "bad_request"
        ws.send_text('{"type": "cancel"}')


@pytest.mark.parametrize("path", ["/docs", "/redoc", "/openapi.json"])
def test_no_api_docs(client, path):
    assert client.get(path).status_code == 404


def test_pairing_gives_a_device_token_once(client, devices):
    code = devices.new_code()
    body = {"code": code, "device_name": DEVICE}
    response = client.post("/v1/pair", json=body)
    assert response.status_code == 200
    assert devices.is_valid(response.json()["token"])
    assert client.post("/v1/pair", json=body).status_code == 403


@pytest.mark.parametrize("active", [True, False])
def test_pairing_with_a_wrong_code(client, devices, active):
    if active:
        devices.new_code()
    response = client.post("/v1/pair", json={"code": "nope", "device_name": DEVICE})
    assert response.status_code == 403
    assert response.json() == {"detail": "wrong code"}
    assert devices.devices() == []


@pytest.mark.parametrize(
    "body",
    [
        {"code": "12345678"},
        {"code": "12345678", "device_name": "x", "admin": True},
        {"code": "1" * 33, "device_name": "x"},
    ],
)
def test_pairing_rejects_a_bad_body(client, body):
    assert client.post("/v1/pair", json=body).status_code == 422


def test_a_device_token_dictates(client, device_token):
    headers = {"Authorization": f"Bearer {device_token}"}
    with client.websocket_connect("/v1/dictate", headers=headers) as ws:
        ws.send_text('{"type": "start"}')
        assert json.loads(ws.receive_text())["type"] == "ready"
        ws.send_text('{"type": "cancel"}')


def test_a_revoked_device_token_is_refused(client, devices, device_token):
    devices.revoke(DEVICE)
    headers = {"Authorization": f"Bearer {device_token}"}
    with pytest.raises(WebSocketDisconnect) as e:
        with client.websocket_connect("/v1/dictate", headers=headers) as ws:
            ws.receive_text()
    assert e.value.code == 1008


def test_without_an_app_token_only_devices_dictate(settings, device_token):
    settings.pesto_app_token = ""
    app = create_app(
        settings,
        stt_factory=FakeSttFactory(["γεια"]),
        provider=FakeLlm(),
        limits=SessionLimits(tick_s=0.005),
    )
    with TestClient(app) as client:
        with pytest.raises(WebSocketDisconnect) as e:
            with client.websocket_connect("/v1/dictate?token=") as ws:
                ws.receive_text()
        assert e.value.code == 1008
        headers = {"Authorization": f"Bearer {device_token}"}
        with client.websocket_connect("/v1/dictate", headers=headers) as ws:
            ws.send_text('{"type": "start"}')
            assert json.loads(ws.receive_text())["type"] == "ready"
            ws.send_text('{"type": "cancel"}')
