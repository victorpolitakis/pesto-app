"""Profiles: one Markdown file per profile in backend/profiles/.

A file starts with an optional TOML header between `+++` lines (name, the
limits of the output guard, optionally the effort), followed by the system
prompt. A profile with no prompt passes the transcript through unchanged
(raw). `_base.md` is not a profile: its text is put before the prompt of
every profile that has one.
"""

import tomllib
from dataclasses import dataclass
from pathlib import Path

BASE_FILE = "_base.md"


@dataclass(frozen=True)
class Profile:
    id: str
    name: str
    prompt: str
    min_length_ratio: float = 0.3
    max_length_ratio: float = 3.0
    min_word_overlap: float = 0.3
    max_new_share: float = 0.6
    effort: str | None = None  # overrides llm_effort for this profile


def parse_profile(profile_id: str, source: str) -> tuple[dict, str]:
    header: dict = {}
    body = source
    if source.startswith("+++\n"):
        end = source.find("\n+++", 4)
        if end == -1:
            raise ValueError(f"profile {profile_id}: header is not closed with +++")
        header = tomllib.loads(source[4:end])
        body = source[end + 4 :]
    return header, body.strip()


def load_profiles(directory: Path) -> dict[str, Profile]:
    base_path = directory / BASE_FILE
    base = base_path.read_text(encoding="utf-8").strip() if base_path.exists() else ""
    profiles = {}
    for path in sorted(directory.glob("*.md")):
        if path.name.startswith("_"):
            continue
        header, body = parse_profile(path.stem, path.read_text(encoding="utf-8"))
        prompt = f"{base}\n\n{body}" if body and base else body
        profiles[path.stem] = Profile(
            id=path.stem, name=header.pop("name", path.stem), prompt=prompt, **header
        )
    return profiles
