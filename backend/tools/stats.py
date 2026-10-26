"""Numbers from normal use, read from logs/metrics.jsonl.

  uv run tools/stats.py

Latency is the time from finalize to the final text (total_ms), p50 and p90,
for raw and for dictations with a profile. A "cut" is a dictation that ended
on its own (end_reason other than user). The Soniox cost uses the real-time
price of 25/10/2026.
"""

import json
import math
import sys
from collections import Counter
from pathlib import Path

from pesto_server.config import Settings

SONIOX_USD_PER_HOUR = 0.12


def percentile(values: list[float], p: float) -> float | None:
    """Nearest-rank percentile."""
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, math.ceil(p / 100 * len(ordered)))
    return ordered[rank - 1]


def load(path: Path) -> list[dict]:
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    return [json.loads(line) for line in lines if line.strip()]


def summarize(records: list[dict]) -> dict:
    groups = {
        "raw": [r["total_ms"] for r in records if r["profile"] == "raw"],
        "profile": [r["total_ms"] for r in records if r["profile"] != "raw"],
    }
    audio_s = sum(r["audio_s"] for r in records)
    return {
        "dictations": len(records),
        "latency_ms": {
            name: {"n": len(v), "p50": percentile(v, 50), "p90": percentile(v, 90)}
            for name, v in groups.items()
        },
        "cuts": sum(1 for r in records if r["end_reason"] != "user"),
        "end_reasons": dict(Counter(r["end_reason"] for r in records)),
        "fallbacks": dict(
            Counter(r["fallback_reason"] for r in records if r["fallback_reason"])
        ),
        "audio_minutes": round(audio_s / 60, 2),
        "soniox_usd": round(audio_s / 3600 * SONIOX_USD_PER_HOUR, 4),
        "soniox_usd_per_minute": round(SONIOX_USD_PER_HOUR / 60, 4),
    }


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Settings().metrics_path
    records = load(path)
    if not records:
        print(f"No dictations yet in {path}")
        return 1
    print(json.dumps(summarize(records), indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
