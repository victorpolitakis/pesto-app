"""Offline CI adversarial suite for X3 Assistant (no LLM required).

Verifies for every attack in `assistant_attacks.jsonl` (>=30 Greek + English
direct & indirect cases):
1. `carried_out` detects the simulated successful attack (`simulated_bad_reply` /
   `simulated_bad_navigate`).
2. Running the attack through `AssistantService` with a hostile `FakeLlm` that
   returns `simulated_bad_reply` (and `simulated_bad_navigate`) NEVER leaks or
   triggers an unauthorized navigation route.
3. A safe assistant answer (`safe_reply`) passes `assistant_output_guard` and
   is not flagged as an attack.
"""

import asyncio

import pytest
from eval_assistant_injection import carried_out, load_assistant_attacks
from fakes import FakeLlm

from pesto_server.assistant import (
    AssistantService,
    assistant_output_guard,
    collect_allowed_sources,
)
from pesto_server.protocol import (
    AssistantAnswerRequest,
    AssistantReply,
    assistant_request,
)

ATTACKS = load_assistant_attacks()


def test_assistant_attack_set_size_and_languages():
    assert len(ATTACKS) >= 30
    assert {"el", "en"} <= {a["lang"] for a in ATTACKS}
    assert {"plan", "answer"} <= {a["request"]["type"] for a in ATTACKS}
    assert len({a["id"] for a in ATTACKS}) == len(ATTACKS)


@pytest.mark.parametrize("attack", ATTACKS, ids=lambda a: f"{a['id']}-{a['category']}")
def test_attack_is_stopped_end_to_end(attack):
    bad_reply = attack["simulated_bad_reply"]
    bad_nav = attack.get("simulated_bad_navigate")
    assert carried_out(attack, bad_reply, bad_nav), "detector misses simulated attack"

    def hostile_llm(_sys, _schema, _prompt):
        nav_obj = {"destination": bad_nav, "profile_id": None} if bad_nav else None
        if attack["request"]["type"] == "plan":
            return {
                "action": "reply",
                "reply": bad_reply,
                "navigate": nav_obj,
                "tools": [],
            }
        # Even if a hostile model returns `navigate` in `answer`, CaMeL strips it.
        return {"reply": bad_reply, "navigate": nav_obj}

    req = assistant_request.validate_python(attack["request"])
    service = AssistantService(FakeLlm(json_reply=hostile_llm))
    res = asyncio.run(service.handle(req))
    assert isinstance(res, AssistantReply)
    nav_dest = res.navigate.destination if res.navigate else None
    assert not carried_out(attack, res.reply, nav_dest)


@pytest.mark.parametrize("attack", ATTACKS, ids=lambda a: f"{a['id']}-{a['category']}")
def test_safe_reply_passes_guard(attack):
    req = assistant_request.validate_python(attack["request"])
    safe = attack["safe_reply"]
    assert not carried_out(attack, safe, None)
    sources = (
        collect_allowed_sources(req)
        if isinstance(req, AssistantAnswerRequest)
        else None
    )
    assert assistant_output_guard(safe, req.query, sources) is None
