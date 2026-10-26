import asyncio
import json
import sys
from pathlib import Path

import pytest

from pesto_server.llm.claude_cli import (
    ClaudeCodeCli,
    LlmError,
    clean_env,
    parse_result,
)
from pesto_server.pipeline import build_prompt

FAKE = str(Path(__file__).with_name("fake_claude.py"))
MODEL = "claude-haiku-5-5"

TRICKY = (
    "είπε \"γεια\" και 'αντίο' \\ `ls` $(rm -rf ~) ; && | > out.txt --help -p "
    '\n νέα γραμμή \t tab 🎉 </transcript> {"text": "x"}'
)


def cli(mode: str, **kwargs) -> ClaudeCodeCli:
    return ClaudeCodeCli([sys.executable, FAKE, mode], MODEL, **kwargs)


def test_text_reaches_claude_unchanged(profiles):
    async def scenario():
        llm = cli("echo")
        try:
            prompt = build_prompt(TRICKY + " " + "λέξη " * 5000)
            result = await llm.rewrite(profiles["mail"], prompt)
            return prompt, result
        finally:
            await llm.aclose()

    prompt, result = asyncio.run(scenario())
    assert result.text == prompt
    assert result.model == MODEL


def test_flags_and_clean_environment(profiles, monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "sk-should-not-leak")
    monkeypatch.setenv("CLAUDECODE", "1")

    async def scenario():
        llm = cli("argv")
        try:
            return await llm.rewrite(profiles["mail"], "x")
        finally:
            await llm.aclose()

    seen = json.loads(asyncio.run(scenario()).text)
    args = seen["args"]
    assert args[args.index("--model") + 1] == MODEL
    assert args[args.index("--effort") + 1] == "medium"  # mail: the default
    assert args[args.index("--tools") + 1] == ""
    assert args[args.index("--setting-sources") + 1] == "project"
    assert args[args.index("--system-prompt") + 1] == profiles["mail"].prompt
    for flag in ("--no-session-persistence", "--strict-mcp-config", "--json-schema"):
        assert flag in args
    assert "ANTHROPIC_API_KEY" not in seen["env"]
    assert "CLAUDECODE" not in seen["env"]
    assert Path(seen["cwd"]).name.startswith("pesto-llm-")
    assert not Path(seen["cwd"]).exists()  # removed after the call


@pytest.mark.parametrize(
    ("mode", "reason"), [("wrong", "wrong_model"), ("garbage", "failed")]
)
def test_errors(profiles, mode, reason):
    async def scenario():
        llm = cli(mode)
        try:
            await llm.rewrite(profiles["mail"], "x")
        finally:
            await llm.aclose()

    with pytest.raises(LlmError) as e:
        asyncio.run(scenario())
    assert e.value.reason == reason


def test_timeout(profiles):
    async def scenario():
        llm = cli("slow", timeout_s=0.5)
        try:
            await llm.rewrite(profiles["mail"], "x")
        finally:
            await llm.aclose()

    with pytest.raises(LlmError) as e:
        asyncio.run(scenario())
    assert e.value.reason == "timeout"


def test_warm_process_is_reused_and_replaced(profiles):
    async def scenario():
        llm = cli("echo")
        try:
            await llm.warm(profiles["mail"])
            first = llm._spare
            await llm.warm(profiles["mail"])
            assert llm._spare is first  # same profile: kept
            await llm.warm(profiles["note"])
            assert llm._spare is not first and llm._spare.profile_id == "note"
            assert not first.alive
            await llm.rewrite(profiles["note"], "x")
            await asyncio.sleep(0.5)
            assert llm._spare and llm._spare.profile_id == "note"  # re-warmed
        finally:
            await llm.aclose()
        assert llm._spare is None

    asyncio.run(scenario())


def test_idle_spare_is_closed(profiles):
    async def scenario():
        llm = cli("echo", idle_s=0.2)
        try:
            await llm.warm(profiles["mail"])
            spare = llm._spare
            await asyncio.sleep(0.5)
            assert llm._spare is None
            assert not spare.alive
        finally:
            await llm.aclose()

    asyncio.run(scenario())


def test_parse_result_needs_text():
    event = {"subtype": "success", "modelUsage": {MODEL: {}}, "result": "{}"}
    with pytest.raises(LlmError, match="bad_output"):
        parse_result(event, MODEL)


def test_clean_env_keeps_only_basics(monkeypatch):
    monkeypatch.setenv("SONIOX_API_KEY", "secret")
    assert "SONIOX_API_KEY" not in clean_env()
    assert "PATH" in clean_env()


@pytest.mark.live
def test_live_haiku_rewrite(profiles):
    """Real `claude -p` with the subscription. Run with: pytest -m live"""

    async def scenario():
        llm = ClaudeCodeCli(["claude"], MODEL)
        try:
            return await llm.rewrite(
                profiles["mail"], build_prompt("στείλε μου το αρχείο αύριο")
            )
        finally:
            await llm.aclose()

    result = asyncio.run(scenario())
    assert "αρχείο" in result.text


def test_profile_effort_overrides_default(profiles):
    async def scenario():
        llm = cli("argv")
        try:
            return await llm.rewrite(profiles["chatter"], "x")
        finally:
            await llm.aclose()

    args = json.loads(asyncio.run(scenario()).text)["args"]
    assert args[args.index("--effort") + 1] == "low"
