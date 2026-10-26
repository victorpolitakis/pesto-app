"""Messages between the phone (or tools/ws_client.py) and the backend.

This module is the only source of truth for the fields. docs/protocol.md
describes the flow and gives JSON examples.
"""

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, TypeAdapter, field_validator

SAMPLE_RATE = 16000
BYTES_PER_SECOND = SAMPLE_RATE * 2  # pcm_s16le, mono

# Soniox limits the whole context to ~10,000 characters.
MAX_TERMS_CHARS = 10_000


class _ClientMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")


class FieldContext(_ClientMessage):
    package: str | None = None
    input_type: str | None = None
    hint: str | None = None
    single_line: bool | None = None
    is_password: bool | None = None
    is_incognito: bool | None = None


class Start(_ClientMessage):
    type: Literal["start"]
    language_hints: list[str] = ["el", "en"]
    language_hints_strict: bool = False
    terms: list[str] = []
    profile: str = "raw"
    field_context: FieldContext = FieldContext()
    sample_rate: Literal[16000] = SAMPLE_RATE
    encoding: Literal["pcm_s16le"] = "pcm_s16le"

    @field_validator("terms")
    @classmethod
    def _terms_fit_soniox(cls, terms: list[str]) -> list[str]:
        terms = [t.strip() for t in terms if t.strip()]
        size = sum(len(t) + 1 for t in terms)
        if size > MAX_TERMS_CHARS:
            raise ValueError(
                f"terms are {size} characters, the limit is {MAX_TERMS_CHARS}"
            )
        return terms


class Pause(_ClientMessage):
    type: Literal["pause"]


class Resume(_ClientMessage):
    type: Literal["resume"]


class Finalize(_ClientMessage):
    type: Literal["finalize"]


class Cancel(_ClientMessage):
    type: Literal["cancel"]


ClientMessage = Annotated[
    Start | Pause | Resume | Finalize | Cancel, Field(discriminator="type")
]
client_message = TypeAdapter(ClientMessage)


class Ready(BaseModel):
    type: Literal["ready"] = "ready"


class Partial(BaseModel):
    type: Literal["partial"] = "partial"
    final_text: str
    pending_text: str


# "error": the STT failed mid-dictation; what was transcribed is still sent.
EndReason = Literal["user", "silence", "pause_limit", "max_duration", "error"]


class Stats(BaseModel):
    audio_s: float
    words: int
    stt_ms: int
    llm_ms: int | None
    total_ms: int


class Final(BaseModel):
    type: Literal["final"] = "final"
    raw_text: str
    text: str
    profile: str
    fallback_reason: str | None
    end_reason: EndReason
    stats: Stats


class Error(BaseModel):
    type: Literal["error"] = "error"
    code: str
    message: str
    retryable: bool


ServerMessage = Ready | Partial | Final | Error
