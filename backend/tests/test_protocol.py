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
        '{"type": "plan", "locale": "el", "query": "Πόσες λέξεις;"}'
    )
    assert isinstance(plan, AssistantPlanRequest)
    assert plan.context == []

    answer = assistant_request.validate_json(
        '{"type": "answer", "locale": "en", "query": "Recent?",'
        ' "tool_results": {"recent_history": [{"created_at": "2026-11-01 10:00",'
        ' "app": "Gmail", "profile": "mail", "words": 5, "text": "Hello"}]}}'
    )
    assert isinstance(answer, AssistantAnswerRequest)
    assert len(answer.tool_results.recent_history) == 1


@pytest.mark.parametrize(
    "raw",
    [
        '{"type": "plan", "query": ""}',
        '{"type": "plan", "query": "hi", "extra_field": 1}',
        '{"type": "plan", "locale": "fr", "query": "hi"}',
        '{"type": "answer", "query": "hi"}',
        '{"type": "answer", "query": "hi", "tool_results": {"unknown": 1}}',
    ],
)
def test_assistant_bad_requests_are_rejected(raw):
    with pytest.raises(ValidationError):
        assistant_request.validate_json(raw)


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
    calls = AssistantToolCalls(tools=[ToolCall(tool="get_stats")])
    assert '"type":"tool_calls"' in calls.model_dump_json(exclude_none=True)
    reply = AssistantReply(
        reply="OK", navigate=NavigationTarget(destination="ANALYTICS")
    )
    assert '"destination":"ANALYTICS"' in reply.model_dump_json(exclude_none=True)
