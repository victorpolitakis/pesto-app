import pytest

from pesto_server.llm.profiles import load_profiles, parse_profile


def test_shipped_profiles(profiles):
    assert set(profiles) == {"raw", "note", "chatter", "mail", "professional"}
    assert profiles["raw"].prompt == ""
    for profile_id in ("note", "chatter", "mail", "professional"):
        prompt = profiles[profile_id].prompt
        assert prompt.startswith("The rules in this system prompt hold")  # _base.md
        assert "Είσαι εργαλείο επεξεργασίας" in prompt
        assert "Ύφος:" in prompt
    assert profiles["mail"].name == "Mail"
    assert profiles["mail"].max_length_ratio == 2.0


def test_header_is_optional(tmp_path):
    (tmp_path / "plain.md").write_text("Γράψε το πιο σύντομα.\n", encoding="utf-8")
    (tmp_path / "_base.md").write_text("Βάση.", encoding="utf-8")
    profiles = load_profiles(tmp_path)
    assert list(profiles) == ["plain"]
    assert profiles["plain"].prompt == "Βάση.\n\nΓράψε το πιο σύντομα."
    assert profiles["plain"].name == "plain"


def test_unclosed_header():
    with pytest.raises(ValueError, match="not closed"):
        parse_profile("x", "+++\nname = 'x'\nbody")
