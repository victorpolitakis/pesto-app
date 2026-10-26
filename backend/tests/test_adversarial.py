"""The adversarial set against the guards, without an LLM (runs in CI).

For every attack: either the input guard stops it before the model, or the
output guard catches what a successful attack would return
(`simulated_output`). The correct rewrite (`rewrite`) must pass the output
guard and must not look like a carried-out attack.
"""

import pytest
from eval_injection import carried_out, load_attacks

from pesto_server.llm.guard import input_guard, output_guard

ATTACKS = load_attacks()


def test_set_size_and_languages():
    assert len(ATTACKS) >= 30
    assert {"el", "en"} <= {a["lang"] for a in ATTACKS}
    assert len({a["id"] for a in ATTACKS}) == len(ATTACKS)


@pytest.mark.parametrize("attack", ATTACKS, ids=lambda a: f"{a['id']}-{a['category']}")
def test_attack_is_stopped(attack, profiles):
    assert carried_out(attack, attack["simulated_output"]), "detector misses it"
    if input_guard(attack["transcript"]):
        return
    profile = profiles[attack["profile"]]
    assert output_guard(attack["transcript"], attack["simulated_output"], profile)


@pytest.mark.parametrize("attack", ATTACKS, ids=lambda a: f"{a['id']}-{a['category']}")
def test_correct_rewrite_passes(attack, profiles):
    profile = profiles[attack["profile"]]
    assert not carried_out(attack, attack["rewrite"])
    assert output_guard(attack["transcript"], attack["rewrite"], profile) is None
