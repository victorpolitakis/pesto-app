"""pesto-server: the backend, and the devices that may use it (Δ24).

  uv run pesto-server              start the server
  uv run pesto-server pair         a one-time code for a new device (10 minutes)
  uv run pesto-server devices      the paired devices
  uv run pesto-server revoke NAME  remove a device: its token stops working at once

Only the server needs PESTO_APP_TOKEN in backend/.env (for tools/ws_client.py).
pair, devices and revoke work with the server running or not: they change
backend/data/devices.json, which the server reads at every connection.
"""

import argparse

from .config import Settings
from .devices import CODE_TTL_S, DeviceRegistry


def main(argv: list[str] | None = None, settings: Settings | None = None) -> None:
    parser = argparse.ArgumentParser(
        prog="pesto-server", description=__doc__.split("\n\n")[0]
    )
    commands = parser.add_subparsers(dest="command")
    commands.add_parser("pair", help="a one-time code for a new device")
    commands.add_parser("devices", help="the paired devices")
    revoke = commands.add_parser("revoke", help="remove a paired device")
    revoke.add_argument("name")
    args = parser.parse_args(argv)

    settings = settings or Settings()
    registry = DeviceRegistry(settings.devices_path)
    if args.command == "pair":
        code = registry.new_code()
        print(f"Pairing code: {code}")
        print(f"One use, for {CODE_TTL_S // 60} minutes. Type it in the app:")
        print("Settings, «Σύνδεση με τον backend».")
    elif args.command == "devices":
        devices = registry.devices()
        if not devices:
            print("No paired devices.")
        for device in devices:
            print(f"{device.name}  (paired {device.paired_at})")
    elif args.command == "revoke":
        if not registry.revoke(args.name):
            raise SystemExit(f"No device named {args.name!r}")
        print(f"Revoked: {args.name}. Its next connection is refused.")
    else:
        serve(settings)


def serve(settings: Settings) -> None:
    import uvicorn

    from .app import create_app

    if not settings.pesto_app_token:
        raise SystemExit("PESTO_APP_TOKEN is missing from backend/.env")
    # TODO(experimental): bind only to the Tailscale interface (Δ24, option b),
    # together with the pairing and the tunnel of phase 6.
    uvicorn.run(create_app(settings), host=settings.host, port=settings.port)
