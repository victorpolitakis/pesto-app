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
    "stat",
    "get_profiles",
    "search_history",
    "recent_history",
    "search_dictionary",
]
StatMetric = Literal[
    "minutes_saved",
    "words",
    "dictations",
    "wpm",
    "top_app",
    "dictionary",
]
TimePeriod = Literal["today", "week", "all"]

_FTS_OPERATORS = frozenset({"and", "or", "not", "near"})


def validate_keyword(raw: str) -> str:
    """Single safe token for FTS/dictionary lookup (2..30 alnum or internal '-')."""
    if not isinstance(raw, str):
        raise ValueError("keyword must be a string")
    kw = raw.strip()
    if len(kw) < 2 or len(kw) > 30:
        raise ValueError("keyword must be 2..30 characters")
    if kw.lower() in _FTS_OPERATORS:
        raise ValueError("FTS boolean operators are not allowed as keywords")
    if not kw[0].isalnum() or not kw[-1].isalnum() or "--" in kw:
        raise ValueError("invalid keyword boundaries")
    if not all(c.isalnum() or c == "-" for c in kw):
        raise ValueError("keyword may contain only letters, digits, and '-'")
    return kw


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
    keywords: list[str] | None = Field(default=None, min_length=1, max_length=3)
    metric: StatMetric | None = None
    period: TimePeriod | None = None
    limit: int | None = Field(default=None, ge=1, le=3)

    @field_validator("keywords")
    @classmethod
    def _validate_keywords(cls, v: list[str] | None) -> list[str] | None:
        if v is None:
            return None
        return [validate_keyword(k) for k in v]

    @model_validator(mode="after")
    def _check_tool_fields(self) -> ToolCall:
        if self.tool == "stat":
            if self.metric is None:
                raise ValueError("metric is required for stat")
            if self.period is None:
                self.period = "all"
            if self.keywords is not None or self.limit is not None:
                raise ValueError("stat does not accept keywords or limit")
        elif self.tool == "get_profiles":
            if any(
                x is not None
                for x in (self.keywords, self.metric, self.period, self.limit)
            ):
                raise ValueError("get_profiles takes no arguments")
        elif self.tool == "search_history":
            if not self.keywords:
                raise ValueError("keywords are required for search_history")
            if self.period is None:
                self.period = "all"
            if self.metric is not None or self.limit is not None:
                raise ValueError("search_history does not accept metric or limit")
        elif self.tool == "search_dictionary":
            if not self.keywords:
                raise ValueError("keywords are required for search_dictionary")
            if any(x is not None for x in (self.metric, self.period, self.limit)):
                raise ValueError("search_dictionary accepts only keywords")
        elif self.tool == "recent_history":
            if self.limit is None:
                self.limit = 3
            if any(x is not None for x in (self.keywords, self.metric, self.period)):
                raise ValueError("recent_history accepts only limit")
        return self


class PreviousPlanState(_ClientMessage):
    tool: ToolName
    keywords: list[str] = Field(default_factory=list, max_length=3)
    metric: StatMetric | None = None
    period: TimePeriod | None = None

    @field_validator("keywords", mode="before")
    @classmethod
    def _default_keywords(cls, v: object) -> object:
        return [] if v is None else v

    @field_validator("keywords")
    @classmethod
    def _validate_prev_keywords(cls, v: list[str]) -> list[str]:
        return [validate_keyword(k) for k in v]


class HistoryEntrySummary(_ClientMessage):
    created_at: str = Field(max_length=40)
    app: str = Field(max_length=80)
    profile: str = Field(max_length=32)
    words: int = Field(default=0, ge=0)
    snippet: str = Field(max_length=160)


class DictionarySummary(_ClientMessage):
    total: int = Field(default=0, ge=0)
    remaining_chars: int = Field(default=0)
    terms: list[str] = Field(default_factory=list, max_length=5)

    @field_validator("terms")
    @classmethod
    def _bound_terms(cls, terms: list[str]) -> list[str]:
        if any(len(t) > 100 for t in terms):
            raise ValueError("dictionary term exceeds 100 characters")
        return terms


class ToolResults(_ClientMessage):
    history_matches: list[HistoryEntrySummary] | None = Field(
        default=None, max_length=3
    )
    recent_history: list[HistoryEntrySummary] | None = Field(
        default=None, max_length=3
    )
    dictionary: DictionarySummary | None = None

    @model_validator(mode="after")
    def _check_combined_snippets(self) -> ToolResults:
        total = len(self.history_matches or []) + len(self.recent_history or [])
        if total > 3:
            raise ValueError("combined history snippets may not exceed 3")
        return self


class AssistantPlanRequest(_ClientMessage):
    type: Literal["plan"]
    locale: AssistantLocale = "el"
    query: str = Field(min_length=1, max_length=1000)
    previous_queries: list[Annotated[str, Field(min_length=1, max_length=300)]] = Field(
        default_factory=list, max_length=2
    )
    previous: PreviousPlanState | None = None


class AssistantAnswerRequest(_ClientMessage):
    type: Literal["answer"]
    plan_id: str = Field(min_length=16, max_length=64)
    locale: AssistantLocale = "el"
    query: str = Field(min_length=1, max_length=1000)
    tool_results: ToolResults


AssistantRequest = Annotated[
    AssistantPlanRequest | AssistantAnswerRequest, Field(discriminator="type")
]
assistant_request = TypeAdapter(AssistantRequest)


class AssistantReply(BaseModel):
    type: Literal["reply"] = "reply"
    reply: str = Field(max_length=300)
    navigate: NavigationTarget | None = None
    relevant: list[Annotated[int, Field(ge=0, le=2)]] = Field(
        default_factory=list, max_length=3
    )
    fallback_reason: str | None = None


class AssistantToolCalls(BaseModel):
    type: Literal["tool_calls"] = "tool_calls"
    plan_id: str | None = None
    tools: list[ToolCall] = Field(min_length=1, max_length=3)


AssistantResponse = AssistantReply | AssistantToolCalls
