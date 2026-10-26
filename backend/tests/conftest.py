import pytest

from pesto_server.config import BACKEND_DIR, Settings
from pesto_server.llm.profiles import load_profiles

TOKEN = "test-token"


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
    )
