import pytest
from pydantic import ValidationError

from pesto_server.protocol import (
    MAX_TERMS_CHARS,
    Final,
    Finalize,
    Start,
    Stats,
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
