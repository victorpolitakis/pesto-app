"""Checks around the Haiku call, without an LLM.

input_guard runs before the call: text that reads like an instruction to the
model is never sent. output_guard runs after: an output that does not look
like a rewrite of the transcript is thrown away. Both return a reason, or
None when the text is fine. On any reason the user gets the plain transcript.
"""

import re
import unicodedata

from .profiles import Profile

# Matched against lowercase text without accents (see normalize).
_INSTRUCTION_PATTERNS = [
    # Greek
    r"αγνοησε\s+(ολες\s+)?(τις\s+)?(προηγουμενες\s+|παραπανω\s+)?(οδηγιες|εντολες)",
    r"(ξεχνα|ξεχασε)\s+(ολες\s+)?(τις\s+)?(προηγουμενες\s+)?(οδηγιες|εντολες)",
    r"μην\s+(υπακους|υπακουεις|ακολουθεις)\s+(σε\s+|στις\s+|τις\s+)?(οδηγιες|εντολες)",
    r"(δειξε|πες|γραψε|επαναλαβε)\s+(μου\s+)?(το\s+|τις\s+)?"
    r"(system prompt|οδηγιες σου|εντολες σου|αρχικες οδηγιες)",
    r"απο\s+(εδω|τωρα)\s+και\s+(περα|στο εξης)\s+(εισαι|θα εισαι|να εισαι)",
    r"νεες\s+οδηγιες\s*:",
    r"system\s+prompt",
    # English
    r"ignore\s+(all\s+)?(the\s+|your\s+)?(previous\s+|prior\s+|above\s+)?"
    r"(instructions|prompts?|rules)",
    r"disregard\s+(all\s+)?(the\s+|your\s+)?(previous\s+|prior\s+|above\s+)?"
    r"(instructions|prompts?|rules)",
    r"forget\s+(all\s+)?(the\s+|your\s+)?(previous\s+)?(instructions|rules)",
    r"do\s+not\s+follow\s+(your|the)\s+(instructions|rules)",
    r"(reveal|show|print|repeat)\s+(me\s+)?(your\s+|the\s+)?"
    r"(system\s+prompt|instructions|initial\s+prompt)",
    r"you\s+are\s+now\b",
    r"from\s+now\s+on,?\s+you\b",
    r"new\s+instructions\s*:",
    r"developer\s+mode",
    r"\bjailbreak",
]
_INSTRUCTION_RE = re.compile("|".join(f"(?:{p})" for p in _INSTRUCTION_PATTERNS))

# Phrases of a model talking about itself. Only a problem when the
# transcript did not already contain them.
_MODEL_PHRASES = [
    "ως ai",
    "ως μοντελο",
    "ως γλωσσικο μοντελο",
    "ειμαι ενα μοντελο",
    "δεν μπορω να βοηθησω",
    "λυπαμαι, αλλα",
    "as an ai",
    "language model",
    "i can't help",
    "i cannot help",
    "i'm sorry, but",
    "system prompt",
    "<transcript>",
    "</transcript>",
]

_EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
_URL_RE = re.compile(r"(?:https?://|www\.)\S+")
_WORD_RE = re.compile(r"\w+")

# Output may be this many characters longer than the ratio allows, so that
# short dictations can still get a greeting and a closing.
_LENGTH_SLACK = 150
_MAX_SCRIPT_SHIFT = 0.4

# Lines that only show up in code (matched on lowercase text).
_CODE_RE = re.compile(
    r"```"
    r"|^\s*(def|class)\s+\w+.*:\s*$"
    r"|^\s*(import\s+\w+|from\s+[\w.]+\s+import\b)"
    r"|^\s*(function|const|let|var)\s+\w+"
    r"|^\s*(return|print\(|console\.log|#include)"
    r"|\bselect\b.+\bfrom\b"
    r"|[{}]\s*$",
    re.MULTILINE,
)
# An output whose words are mostly new (not dictated) is an answer or a new
# text, not a rewrite. Below this many new words a greeting can still be added.
_MIN_NEW_STEMS = 6


def normalize(text: str) -> str:
    decomposed = unicodedata.normalize("NFD", text.lower())
    return "".join(c for c in decomposed if not unicodedata.combining(c))


def input_guard(transcript: str) -> str | None:
    if _INSTRUCTION_RE.search(normalize(transcript)):
        return "input"
    return None


def _greek_share(text: str) -> float | None:
    greek = latin = 0
    for c in text:
        if not c.isalpha():
            continue
        if "Ͱ" <= c <= "Ͽ" or "ἀ" <= c <= "῿":
            greek += 1
        elif c.isascii():
            latin += 1
    total = greek + latin
    return greek / total if total else None


def _stems(text: str) -> set[str]:
    # First four letters, so that Greek inflections still match.
    return {w[:4] for w in _WORD_RE.findall(normalize(text)) if len(w) >= 4}


def _has_question(text: str) -> bool:
    # Greek uses ";" (or U+037E) as the question mark.
    return any(mark in text for mark in ("?", ";", ";"))


def output_guard(transcript: str, output: str, profile: Profile) -> str | None:
    if not output.strip():
        return "empty"

    n_in, n_out = len(transcript), len(output)
    if n_out > profile.max_length_ratio * n_in + _LENGTH_SLACK:
        return "length"
    if n_out < profile.min_length_ratio * n_in:
        return "length"

    share_in, share_out = _greek_share(transcript), _greek_share(output)
    if share_in is not None and share_out is not None:
        if abs(share_in - share_out) > _MAX_SCRIPT_SHIFT:
            return "language"

    stems_in, stems_out = _stems(transcript), _stems(output)
    if len(stems_in) >= 2:
        overlap = len(stems_in & stems_out) / len(stems_in)
        if overlap < profile.min_word_overlap:
            return "overlap"

    if _CODE_RE.search(output.lower()) and not _CODE_RE.search(transcript.lower()):
        return "code"

    new = stems_out - stems_in
    if len(new) >= _MIN_NEW_STEMS and len(new) / len(stems_out) > profile.max_new_share:
        return "added"

    norm_in, norm_out = normalize(transcript), normalize(output)
    for phrase in _MODEL_PHRASES:
        if phrase in norm_out and phrase not in norm_in:
            return "phrase"

    for pattern in (_EMAIL_RE, _URL_RE):
        if set(pattern.findall(norm_out)) - set(pattern.findall(norm_in)):
            return "leak"

    if _has_question(transcript) and not _has_question(output):
        return "answered"

    return None
