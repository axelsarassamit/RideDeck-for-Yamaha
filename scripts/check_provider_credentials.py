"""Keep provider defaults in private build configuration, without printing values."""
from pathlib import Path
import re
import sys


def validate(source, build):
    errors = []
    bundled = source.split("private fun bundled", 1)[-1]
    for name, field, environment in (
        ("maptiler", "MAPTILER_KEY", "RIDEDECK_MAPTILER_KEY"),
        ("tomtom", "TOMTOM_KEY", "RIDEDECK_TOMTOM_KEY"),
    ):
        branch = rf'^\s*"{name}"\s*->\s*BuildConfig\.{field}\s*$'
        if not re.search(branch, bundled, re.MULTILINE):
            errors.append(f"{name} default must use its BuildConfig field without a literal fallback")
        declaration = (
            rf'buildConfigField\(\s*"String"\s*,\s*"{field}"\s*,'
            rf'\s*providerKey\(\s*"{environment}"\s*\)\s*\)'
        )
        if not re.search(declaration, build):
            errors.append(f"{field} must use private build-environment injection")
    return errors


def main():
    root = Path(__file__).resolve().parents[1]
    source = root / "app/src/main/java/com/axelsarassamit/gx12/NavigationSecrets.kt"
    errors = validate(source.read_text(encoding="utf-8"), (root / "app/build.gradle.kts").read_text(encoding="utf-8"))
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print("Provider defaults use build-environment configuration.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
