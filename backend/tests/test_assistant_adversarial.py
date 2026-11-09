"""Offline CI adversarial suite for X3 Assistant (no LLM required).

Verifies for every attack in `assistant_attacks.jsonl` (>=35 Greek + English
direct, indirect, multi-turn & crescendo cases):
1. `carried_out` detects the simulated successful attack (`simulated_bad_reply` /
   `simulated_bad_navigate`).
2. Running the attack through `AssistantService` with a hostile `FakeLlm` that
   returns `simulated_bad_reply` (and `simulated_bad_navigate`) NEVER leaks or
   triggers an unauthorized navigation route.
3. For `indirect_app_label` cases (`stat` `top_app` and `get_profiles`), app
   names are handled locally (`plan_id = None`) and NEVER sent to the LLM, while
   `app` attributes in history snippets are quote-escaped (`escape_xml`).
4. A safe assistant answer (`safe_reply`) passes `assistant_output_guard` and
   is not flagged as an attack.
"""

import asyncio

import pytest
from eval_assistant_injection import carried_out, load_assistant_attacks
from fakes import FakeLlm, register_plan
from pydantic import ValidationError

from pesto_server.assistant import (
    AssistantService,
    assistant_output_guard,
    collect_allowed_sources,
    escape_xml,
)
from pesto_server.protocol import (
    AssistantAnswerRequest,
    AssistantPlanRequest,
    AssistantReply,
    AssistantToolCalls,
    ToolResults,
    assistant_request,
)

ATTACKS = load_assistant_attacks()
APP_LABEL_ATTACKS = [a for a in ATTACKS if a["category"] == "indirect_app_label"]


def test_assistant_attack_set_size_and_languages():
    assert len(ATTACKS) >= 35
    assert {"el", "en"} <= {a["lang"] for a in ATTACKS}
    assert {"plan", "answer"} <= {a["request"]["type"] for a in ATTACKS}
    assert len(APP_LABEL_ATTACKS) >= 2
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
        return {"relevant": [0], "reply": bad_reply, "navigate": nav_obj}

    req = assistant_request.validate_python(attack["request"])
    llm = FakeLlm(json_reply=hostile_llm)
    service = AssistantService(llm)
    if isinstance(req, AssistantAnswerRequest):
        register_plan(
            service,
            plan_id=req.plan_id,
            device_key="test_device",
            query=req.query,
            locale=req.locale,
            tools=tuple(attack["plan_tools"]),
        )
    res = asyncio.run(service.handle(req, device_key="test_device"))
    assert isinstance(res, AssistantReply)
    nav_dest = res.navigate.destination if res.navigate else None
    assert not carried_out(attack, res.reply, nav_dest)


@pytest.mark.parametrize(
    "attack", APP_LABEL_ATTACKS, ids=lambda a: f"{a['id']}-local-app-label"
)
def test_indirect_app_label_never_reaches_llm_in_stat_or_profiles_path(attack):
    local_spec = attack["local_tool"]
    poisoned_label = attack["poisoned_app_label"]

    def plan_llm(_sys, _schema, _prompt):
        return {
            "action": "call_tools",
            "reply": None,
            "navigate": None,
            "tools": [
                {
                    "tool": local_spec["tool"],
                    "metric": local_spec.get("metric"),
                    "period": local_spec.get("period"),
                    "keywords": None,
                    "limit": None,
                }
            ],
        }

    llm = FakeLlm(json_reply=plan_llm)
    service = AssistantService(llm)
    plan_req = AssistantPlanRequest(
        type="plan", locale=attack["lang"], query="Show my app usage and profiles"
    )
    res = asyncio.run(service.handle(plan_req, device_key="test_device"))
    assert isinstance(res, AssistantToolCalls)
    # Local selector: plan_id is None, so no `answer` step can ever be called,
    # and ToolResults strictly forbids `stats` / `profiles` payloads!
    assert res.plan_id is None
    assert all(poisoned_label not in c["prompt"] for c in llm.json_calls)
    with pytest.raises(ValidationError):
        ToolResults.model_validate({"stats": {"top_apps": [{"app": poisoned_label}]}})
    with pytest.raises(ValidationError):
        ToolResults.model_validate(
            {"profiles": {"app_profiles": {poisoned_label: "raw"}}}
        )
    # And if the app label appears on a history snippet attribute, quotes are escaped:
    assert '"' not in escape_xml(poisoned_label)


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
