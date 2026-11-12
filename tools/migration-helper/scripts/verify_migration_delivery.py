"""Verify a minimal original-key migration helper bound to its exact target APK."""
import argparse
import hashlib
from pathlib import Path
import re

from apk_manifest import parse_xmltree, read_apk, single_child, verify_instrumentation, verify_signer
POLICIES = {
    "dash": {
        "package": "com.axelsarassamit.ridedeck.dash",
        "helper": "com.axelsarassamit.migrationhelper.dash_release",
        "signer": "cef073342e133ad4c6650da1a276ae4ad03e3396cecac67143154aeb6e6c731b",
        "version": "0.13.7",
        "code": "101307",
    },
    "yamaha": {
        "package": "com.axelsarassamit.gx12.yamaha",
        "helper": "com.axelsarassamit.migrationhelper.yamaha_release",
        "signer": "79f76edba53e22dcb9e4610dc5fa151e5c52d0895c68b2802444f1ced3a1c06f",
        "version": "0.12.64",
        "code": "1264",
    },
}
RUNNER = "com.axelsarassamit.migrationhelper.MigrationInstrumentation"
PIN_NAME = "com.axelsarassamit.migrationhelper.EXPECTED_TARGET_APK_SHA256"


def policy_for(edition):
    if edition not in POLICIES:
        raise ValueError("Unsupported migration edition")
    return POLICIES[edition]


def validate_helper(badging, certificates, xmltree, target_sha, edition="dash"):
    policy = policy_for(edition)
    if not re.fullmatch(r"[0-9a-f]{64}", target_sha):
        raise ValueError("Target APK hash is required")
    package = re.search(r"^package: name='([^']+)'", badging, re.M)
    if not package or package.group(1) != policy["helper"]:
        raise ValueError("Unexpected migration helper package")
    manifest = parse_xmltree(xmltree)
    if manifest.attributes.get("package") != policy["helper"]:
        raise ValueError("Helper package differs from badging")
    if any(node.name not in {"uses-sdk", "instrumentation", "application"} for node in manifest.children):
        raise ValueError("Migration helper declares permissions or unexpected components")
    if single_child(manifest, "uses-sdk").children:
        raise ValueError("Unexpected SDK configuration")
    runner = verify_instrumentation(manifest, RUNNER, policy["package"])
    runner_attributes = {"android:name", "android:targetPackage", "android:label",
                         "android:handleProfiling", "android:functionalTest"}
    if runner.children or set(runner.attributes) - runner_attributes:
        raise ValueError("Unexpected instrumentation configuration")
    if any(runner.attributes.get(flag, False) is not False
           for flag in ("android:handleProfiling", "android:functionalTest")):
        raise ValueError("Unexpected instrumentation flags")
    app = single_child(manifest, "application")
    app_attributes = {"android:label", "android:name", "android:testOnly", "android:debuggable",
                      "android:allowBackup", "android:extractNativeLibs", "android:usesCleartextTraffic"}
    if set(app.attributes) - app_attributes:
        raise ValueError("Unexpected helper Application attributes")
    if app.attributes.get("android:testOnly") is not True:
        raise ValueError("Migration helper must be testOnly")
    if app.attributes.get("android:name", "android.app.Application") != "android.app.Application":
        raise ValueError("Unexpected helper Application")
    if "android:permission" in app.attributes:
        raise ValueError("Unexpected helper permission")
    if app.attributes.get("android:allowBackup") is not False or app.attributes.get("android:usesCleartextTraffic") is not False:
        raise ValueError("Migration helper must disable backup and cleartext traffic")
    if len(app.children) != 1 or app.children[0].name != "meta-data":
        raise ValueError("Unexpected helper components or metadata")
    pin = app.children[0]
    if pin.children or pin.attributes != {"android:name": PIN_NAME, "android:value": target_sha}:
        raise ValueError("Migration helper does not pin the exact target APK")
    verify_signer(certificates, policy["signer"])


def validate_target(badging, certificates, edition="dash"):
    policy = policy_for(edition)
    identity = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.M)
    if not identity or identity.groups() != (policy["package"], policy["code"], policy["version"]):
        raise ValueError("Unexpected target package or migration release version")
    if re.search(r"^application-debuggable(?:\s|$)", badging, re.M):
        raise ValueError("Migration target must be a non-debuggable release")
    verify_signer(certificates, policy["signer"])


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--helper-apk", required=True)
    parser.add_argument("--target-apk", required=True)
    parser.add_argument("--build-tools", required=True)
    parser.add_argument("--edition", choices=POLICIES, default="dash")
    args = parser.parse_args()
    target_badging, target_certificates, _ = read_apk(args.target_apk, args.build_tools)
    validate_target(target_badging, target_certificates, args.edition)
    validate_helper(*read_apk(args.helper_apk, args.build_tools), sha256(args.target_apk), args.edition)
    print("Verified original-key testOnly migration helper and exact production target APK")


if __name__ == "__main__":
    main()
