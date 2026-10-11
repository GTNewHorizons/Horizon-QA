"""One entry point for client supervision and report verification."""
import argparse

from . import version
from . import run_client, verify


def main(argv=None):
    parser = argparse.ArgumentParser(prog="hqa", description=__doc__)
    parser.add_argument("--version", action="version", version="Horizon-QA tools " + version())
    parser.add_argument("command", choices=("run-client", "verify"))
    parser.add_argument("arguments", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    return {"run-client": run_client.main, "verify": verify.main}[args.command](args.arguments)
