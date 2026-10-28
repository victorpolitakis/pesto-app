from pesto_server import cli


def test_smoke():
    assert callable(cli.main)
