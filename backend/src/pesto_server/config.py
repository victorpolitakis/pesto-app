from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

BACKEND_DIR = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """Backend settings, read from the environment and backend/.env."""

    model_config = SettingsConfigDict(env_file=BACKEND_DIR / ".env", extra="ignore")

    host: str = "127.0.0.1"
    port: int = 8000
    # Only for the tools on the Mac (ws_client.py). The phones have their own
    # tokens, from pairing, in devices_path (Δ24).
    pesto_app_token: str = ""
    devices_path: Path = BACKEND_DIR / "data" / "devices.json"

    soniox_api_key: str = ""
    soniox_url: str = "wss://stt-rt.soniox.com/transcribe-websocket"
    soniox_model: str = "stt-rt-v5"

    claude_cmd: list[str] = ["claude"]
    llm_model: str = "claude-haiku-5-5"
    llm_effort: str = "medium"  # since 26/10, low before (docs/eval.md)
    llm_timeout_s: float = 10.0
    llm_warm_idle_s: float = 15 * 60

    profiles_dir: Path = BACKEND_DIR / "profiles"
    metrics_path: Path = BACKEND_DIR / "logs" / "metrics.jsonl"

    silence_timeout_s: float = 30.0
    pause_limit_s: float = 120.0
    max_audio_s: float = 600.0
    keepalive_interval_s: float = 10.0
    finalize_timeout_s: float = 10.0
