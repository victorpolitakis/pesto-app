"""Messages between the phone (or tools/ws_client.py) and the backend.

This module is the only source of truth for the fields. docs/protocol.md
describes the flow and gives JSON examples.
"""

from typing import Annotated, Literal

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    TypeAdapter,
    field_validator,
    model_validator,
)

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
    # TODO(X13): optional custom_profile {name, prompt} and style, with character
    # limits, and bad_request when they come with a ready-made profile.
    # TODO(later): neutral field names (Δ25): language_hints is Soniox's word. Here,
    # in docs/protocol.md and in Protocol.kt together, with the tests of both sides.
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


class PairRequest(_ClientMessage):
    """POST /v1/pair: the code of `pesto-server pair` and a name for the device."""

    code: str = Field(max_length=32)
    device_name: str = Field(max_length=100)


class PairResponse(BaseModel):
    """The token of the device. The backend keeps only its sha256."""

    token: str


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


# --- POST /v1/assistant (X3) ---

AssistantLocale = Literal["el", "en"]
ProfileId = Literal["raw", "note", "chatter", "mail", "professional"]
Destination = Literal[
    "HOME",
    "PROFILES",
    "DICTIONARY",
    "HISTORY",
    "SEARCH",
    "ANALYTICS",
    "APPS",
    "SETTINGS",
    "SETTINGS_LANGUAGES",
    "SETTINGS_PROFILE",
    "SETTINGS_OFFLINE",
    "SETTINGS_PERMISSIONS",
    "SETTINGS_OVERLAY",
    "SETTINGS_XIAOMI",
    "SETTINGS_BACKEND",
    "SETTINGS_PRIVACY",
    "SETTINGS_ABOUT",
    "PROFILE_DETAIL",
]
ToolName = Literal[
    "get_stats",
    "search_history",
    "recent_history",
    "get_dictionary",
    "get_profiles",
]


class NavigationTarget(_ClientMessage):
    destination: Destination
    profile_id: ProfileId | None = None

    @model_validator(mode="after")
    def _check_profile_id(self) -> NavigationTarget:
        if self.destination == "PROFILE_DETAIL" and self.profile_id is None:
            raise ValueError("profile_id is required for PROFILE_DETAIL")
        if self.destination != "PROFILE_DETAIL" and self.profile_id is not None:
            raise ValueError("profile_id is only allowed for PROFILE_DETAIL")
        return self


class ToolCall(_ClientMessage):
    tool: ToolName
    query: str | None = Field(default=None, max_length=100)
    limit: int | None = Field(default=None, ge=1, le=5)


class AssistantTurn(_ClientMessage):
    role: Literal["user", "assistant"]
    text: str = Field(min_length=1, max_length=1500)


class DaySummary(_ClientMessage):
    date: str = Field(max_length=16)
    dictations: int = Field(ge=0)
    words: int = Field(ge=0)


class AppCountSummary(_ClientMessage):
    app: str = Field(max_length=80)
    dictations: int = Field(ge=0)


class StatsSummary(_ClientMessage):
    dictations: int = Field(default=0, ge=0)
    dictations_today: int = Field(default=0, ge=0)
    words: int = Field(default=0, ge=0)
    words_today: int = Field(default=0, ge=0)
    words_this_week: int = Field(default=0, ge=0)
    speech_minutes: float = Field(default=0.0, ge=0.0)
    minutes_saved: float = Field(default=0.0)
    wpm: float | None = Field(default=None, ge=0.0)
    days: list[DaySummary] = Field(default_factory=list, max_length=7)
    top_apps: list[AppCountSummary] = Field(default_factory=list, max_length=5)
    by_profile: dict[str, int] = Field(default_factory=dict)

    @field_validator("by_profile")
    @classmethod
    def _bound_by_profile(cls, v: dict[str, int]) -> dict[str, int]:
        if len(v) > 10 or any(len(k) > 32 or n < 0 for k, n in v.items()):
            raise ValueError("invalid by_profile map")
        return v


class HistoryEntrySummary(_ClientMessage):
    created_at: str = Field(max_length=40)
    app: str = Field(max_length=80)
    profile: str = Field(max_length=32)
    words: int = Field(default=0, ge=0)
    text: str = Field(max_length=500)


class DictionarySummary(_ClientMessage):
    total: int = Field(default=0, ge=0)
    remaining_chars: int = Field(default=0)
    terms: list[str] = Field(default_factory=list, max_length=50)

    @field_validator("terms")
    @classmethod
    def _bound_terms(cls, terms: list[str]) -> list[str]:
        if any(len(t) > 100 for t in terms):
            raise ValueError("dictionary term exceeds 100 characters")
        return terms


class ProfilesSummary(_ClientMessage):
    available: list[str] = Field(default_factory=list, max_length=10)
    default_profile: str = Field(max_length=32)
    app_profiles: dict[str, str] = Field(default_factory=dict)

    @field_validator("available")
    @classmethod
    def _bound_available(cls, ids: list[str]) -> list[str]:
        if any(len(i) > 32 for i in ids):
            raise ValueError("profile id exceeds 32 characters")
        return ids

    @field_validator("app_profiles")
    @classmethod
    def _bound_app_profiles(cls, v: dict[str, str]) -> dict[str, str]:
        if len(v) > 30 or any(len(k) > 80 or len(val) > 32 for k, val in v.items()):
            raise ValueError("invalid app_profiles map")
        return v


class ToolResults(_ClientMessage):
    stats: StatsSummary | None = None
    history_matches: list[HistoryEntrySummary] | None = Field(
        default=None, max_length=5
    )
    recent_history: list[HistoryEntrySummary] | None = Field(default=None, max_length=5)
    dictionary: DictionarySummary | None = None
    profiles: ProfilesSummary | None = None


class AssistantPlanRequest(_ClientMessage):
    type: Literal["plan"]
    locale: AssistantLocale = "el"
    query: str = Field(min_length=1, max_length=1000)
    context: list[AssistantTurn] = Field(default_factory=list, max_length=6)


class AssistantAnswerRequest(_ClientMessage):
    type: Literal["answer"]
    locale: AssistantLocale = "el"
    query: str = Field(min_length=1, max_length=1000)
    tool_results: ToolResults


AssistantRequest = Annotated[
    AssistantPlanRequest | AssistantAnswerRequest, Field(discriminator="type")
]
assistant_request = TypeAdapter(AssistantRequest)


class AssistantReply(BaseModel):
    type: Literal["reply"] = "reply"
    reply: str
    navigate: NavigationTarget | None = None
    fallback_reason: str | None = None


class AssistantToolCalls(BaseModel):
    type: Literal["tool_calls"] = "tool_calls"
    tools: list[ToolCall] = Field(min_length=1, max_length=3)


AssistantResponse = AssistantReply | AssistantToolCalls
