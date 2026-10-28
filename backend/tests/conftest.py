import pytest

from pesto_server.config import BACKEND_DIR, Settings
from pesto_server.devices import DeviceRegistry
from pesto_server.llm.profiles import load_profiles

TOKEN = "test-token"
DEVICE = "POCO X7 Pro"


@pytest.fixture
def profiles():
    return load_profiles(BACKEND_DIR / "profiles")


@pytest.fixture
def settings(tmp_path):
    return Settings(
        _env_file=None,
        pesto_app_token=TOKEN,
        soniox_api_key="",
        metrics_path=tmp_path / "metrics.jsonl",
        devices_path=tmp_path / "data" / "devices.json",
    )


@pytest.fixture
def devices(settings):
    """The devices file of `settings`: the server and the CLI read the same one."""
    return DeviceRegistry(settings.devices_path)


@pytest.fixture
def device_token(devices):
    """The token of a paired phone."""
    return devices.pair(devices.new_code(), DEVICE)
