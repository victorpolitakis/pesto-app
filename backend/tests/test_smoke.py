import pesto_server


def test_smoke():
    assert hasattr(pesto_server, "main")
