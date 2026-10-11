"""Standalone Horizon-QA CI tooling; the Minecraft mod does not depend on this package."""
from importlib.resources import files


def version():
    try:
        return files(__package__).joinpath("version.txt").read_text(encoding="utf-8").strip()
    except FileNotFoundError:
        return "development"
