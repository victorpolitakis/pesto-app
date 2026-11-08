import pytest
from pydantic import ValidationError

from pesto_server.protocol import (
    MAX_TERMS_CHARS,
    AssistantAnswerRequest,
    AssistantPlanRequest,
    AssistantReply,
    AssistantToolCalls,
    Final,
    Finalize,
    NavigationTarget,
    Start,
    Stats,
    ToolCall,
    assistant_request,
    client_message,
)


def test_start_defaults():
    start = client_message.validate_json('{"type": "start"}')
    assert isinstance(start, Start)
    assert start.language_hints == ["el", "en"]
    assert start.profile == "raw"
    assert start.terms == []
    assert start.field_context.package is None


def test_start_full():
    start = client_message.validate_json(
        '{"type": "start", "language_hints": ["el"], "language_hints_strict": true,'
        ' "terms": [" Πολιτάκης ", ""], "profile": "mail",'
        ' "field_context": {"package": "com.whatsapp", "single_line": false}}'
    )
    assert start.terms == ["Πολιτάκης"]
    assert start.language_hints_strict is True
    assert start.field_context.package == "com.whatsapp"


@pytest.mark.parametrize(
    "raw",
    [
        '{"type": "stop"}',
        '{"type": "start", "profle": "mail"}',  # typo in a field name
        '{"type": "start", "sample_rate": 44100}',
        '{"type": "start", "field_context": {"pakage": "x"}}',
        "not json",
    ],
)
def test_bad_messages_are_rejected(raw):
    with pytest.raises(ValidationError):
        client_message.validate_json(raw)


def test_terms_limit():
    too_many = ["α" * 99] * (MAX_TERMS_CHARS // 100 + 1)
    with pytest.raises(ValidationError, match="limit"):
        Start(type="start", terms=too_many)


def test_control_messages():
    assert isinstance(client_message.validate_json('{"type": "finalize"}'), Finalize)


def test_final_serializes():
    final = Final(
        raw_text="α",
        text="Α.",
        profile="raw",
        fallback_reason=None,
        end_reason="user",
        stats=Stats(audio_s=1.0, words=1, stt_ms=10, llm_ms=None, total_ms=12),
    )
    assert '"type":"final"' in final.model_dump_json()


def test_assistant_plan_and_answer_validate():
    plan = assistant_request.validate_json(
        '{"type": "plan", "locale": "el", "query": "Πόσες λέξεις;",'
        ' "previous_queries": ["Γεια σου"],'
        ' "previous": {"tool": "stat", "metric": "words",'
        ' "period": "today", "keywords": []}}'
    )
    assert isinstance(plan, AssistantPlanRequest)
    assert plan.previous_queries == ["Γεια σου"]
    assert plan.previous is not None and plan.previous.metric == "words"

    answer = assistant_request.validate_json(
        '{"type": "answer", "plan_id": "plan_token_1234567890", "locale": "en",'
        ' "query": "Recent?", "tool_results": {"recent_history": ['
        '{"created_at": "2026-11-01 10:00", "app": "Gmail", "profile": "mail",'
        ' "words": 5, "snippet": "Hello"}]}}'
    )
    assert isinstance(answer, AssistantAnswerRequest)
    assert len(answer.tool_results.recent_history) == 1
    assert answer.tool_results.recent_history[0].snippet == "Hello"


@pytest.mark.parametrize(
    "raw",
    [
        '{"type": "plan", "query": ""}',
        '{"type": "plan", "query": "hi", "extra_field": 1}',
        (
            '{"type": "plan", "query": "hi",'
            ' "context": [{"role": "assistant", "text": "x"}]}'
        ),
        '{"type": "plan", "query": "hi", "previous_queries": ["a", "b", "c"]}',
        '{"type": "plan", "locale": "fr", "query": "hi"}',
        '{"type": "answer", "query": "hi"}',
        (
            '{"type": "answer", "plan_id": "plan_token_1234567890",'
            ' "query": "hi", "tool_results": {"unknown": 1}}'
        ),
        (
            '{"type": "answer", "plan_id": "plan_token_1234567890",'
            ' "query": "hi", "tool_results": {"stats": {}}}'
        ),
        (
            '{"type": "answer", "plan_id": "plan_token_1234567890",'
            ' "query": "hi", "tool_results": {"profiles": {}}}'
        ),
    ],
)
def test_assistant_bad_requests_are_rejected(raw):
    with pytest.raises(ValidationError):
        assistant_request.validate_json(raw)


def test_tool_results_enforces_combined_three_snippet_budget():
    entry = (
        '{"created_at": "2026-11-01", "app": "Gmail", "profile": "mail",'
        ' "words": 3, "snippet": "abc"}'
    )
    ok = (
        '{"type": "answer", "plan_id": "plan_token_1234567890", "query": "q",'
        f' "tool_results": {{"history_matches": [{entry}, {entry}],'
        f' "recent_history": [{entry}]}}}}'
    )
    assert isinstance(assistant_request.validate_json(ok), AssistantAnswerRequest)

    over = (
        '{"type": "answer", "plan_id": "plan_token_1234567890", "query": "q",'
        f' "tool_results": {{"history_matches": [{entry}, {entry}],'
        f' "recent_history": [{entry}, {entry}]}}}}'
    )
    with pytest.raises(ValidationError):
        assistant_request.validate_json(over)


@pytest.mark.parametrize(
    "bad_keywords",
    [
        [],
        ["a"],
        ["α" * 31],
        ['"meeting"'],
        ["meet*"],
        ["NEAR"],
        ["or"],
        ["AND"],
        ["not"],
        ["two words"],
        ["-lead"],
        ["trail-"],
        ["double--dash"],
        ["one", "two", "three", "four"],
    ],
)
def test_keyword_validation_rejects_fts_operators_and_bounds(bad_keywords):
    with pytest.raises(ValidationError):
        ToolCall(tool="search_history", keywords=bad_keywords, period="all")


def test_keyword_validation_accepts_clean_greek_and_english_terms():
    call = ToolCall(
        tool="search_history",
        keywords=["Πέμπτη", "e-mail", "v2"],
        period="week",
    )
    assert call.keywords == ["Πέμπτη", "e-mail", "v2"]


def test_navigation_target_profile_detail_validation():
    assert NavigationTarget(destination="DICTIONARY").profile_id is None
    assert (
        NavigationTarget(destination="PROFILE_DETAIL", profile_id="mail").profile_id
        == "mail"
    )
    with pytest.raises(ValidationError):
        NavigationTarget(destination="PROFILE_DETAIL")
    with pytest.raises(ValidationError):
        NavigationTarget(destination="HOME", profile_id="mail")
    with pytest.raises(ValidationError):
        NavigationTarget(destination="SECRET_ROUTE")  # type: ignore[arg-type]


def test_assistant_responses_serialize():
    calls = AssistantToolCalls(
        plan_id=None,
        tools=[ToolCall(tool="stat", metric="minutes_saved", period="all")],
    )
    assert '"type":"tool_calls"' in calls.model_dump_json()
    reply = AssistantReply(
        reply="OK",
        navigate=NavigationTarget(destination="ANALYTICS"),
        relevant=[0, 2],
    )
    dumped = reply.model_dump_json(exclude_none=True)
    assert '"destination":"ANALYTICS"' in dumped
    assert '"relevant":[0,2]' in dumped
