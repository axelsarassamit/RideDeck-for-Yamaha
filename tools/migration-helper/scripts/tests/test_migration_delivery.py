import sys
from pathlib import Path
import re
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from apk_manifest import android_tool, parse_xmltree
from verify_migration_delivery import POLICIES, validate_helper, validate_target

PACKAGE = POLICIES["dash"]["package"]
HELPER_PACKAGE = POLICIES["dash"]["helper"]
SIGNER = POLICIES["dash"]["signer"]

FIXTURES = Path(__file__).parent / "fixtures"
TARGET_SHA = "eae173e94a66dc19b2a41134458406ddc564f1403ed5367781fa1fdfe2f1d6cd"
CERTIFICATE = "Signer #1 certificate SHA-256 digest: " + SIGNER
TARGET_BADGING = "package: name='com.axelsarassamit.ridedeck.dash' versionCode='101307' versionName='0.13.7'"


def helper_fixture():
    # Actual aapt output; only the two identities are substituted for the Dash policy.
    badging = (FIXTURES / "migration-helper-badging.txt").read_text(encoding="utf-8")
    tree = (FIXTURES / "migration-helper-xmltree.txt").read_text(encoding="utf-8")
    for before, after in [
        ("com.axelsarassamit.migrationhelper.yamaha_preview", HELPER_PACKAGE),
        ("com.axelsarassamit.gx12.yamaha.preview", PACKAGE),
    ]:
        badging, tree = badging.replace(before, after), tree.replace(before, after)
    return badging, tree


class MigrationDeliveryTest(unittest.TestCase):
    def test_actual_xmltree_fixture_and_original_signer(self):
        badging, tree = helper_fixture()
        validate_helper(badging, CERTIFICATE, tree, TARGET_SHA)
        validate_target(TARGET_BADGING, CERTIFICATE)

    def test_unmodified_yamaha_fixture_is_rejected_for_dash(self):
        with self.assertRaises(ValueError):
            validate_helper(
                (FIXTURES / "migration-helper-badging.txt").read_text(encoding="utf-8"),
                CERTIFICATE,
                (FIXTURES / "migration-helper-xmltree.txt").read_text(encoding="utf-8"),
                TARGET_SHA,
            )

    def test_exact_target_hash_and_original_certificate_are_required(self):
        badging, tree = helper_fixture()
        for certificate, expected, changed in [
            ("Signer #1 certificate SHA-256 digest: " + "b" * 64, TARGET_SHA, tree),
            (CERTIFICATE + "\n" + CERTIFICATE, TARGET_SHA, tree),
            (CERTIFICATE, "f" * 64, tree),
            (CERTIFICATE, TARGET_SHA, tree.replace(TARGET_SHA, "f" * 64)),
            (CERTIFICATE, TARGET_SHA, tree.replace(PACKAGE + '"', PACKAGE + '.debug"')),
            (CERTIFICATE, "not-a-hash", tree),
        ]:
            with self.subTest(expected=expected), self.assertRaises(ValueError):
                validate_helper(badging, certificate, changed, expected)

    def test_missing_pin_wrong_runner_and_missing_test_only_fail(self):
        badging, tree = helper_fixture()
        missing_pin = tree[:tree.index("      E: meta-data")]
        for changed in [
            missing_pin,
            tree.replace("MigrationInstrumentation", "OtherRunner"),
            re.sub(r"(android:testOnly[^=]*=).*", r"\g<1>(type 0x12)0x0", tree),
            tree.replace(HELPER_PACKAGE, HELPER_PACKAGE + ".other"),
        ]:
            with self.assertRaises(ValueError):
                validate_helper(badging, CERTIFICATE, changed, TARGET_SHA)

    def test_permissions_components_and_launcher_are_rejected(self):
        badging, tree = helper_fixture()
        for element in ["uses-permission", "uses-permission-sdk-23", "permission", "queries"]:
            changed = tree + f"    E: {element} (line=99)\n"
            with self.subTest(element=element), self.assertRaises(ValueError):
                validate_helper(badging, CERTIFICATE, changed, TARGET_SHA)
        for element in ["activity", "activity-alias", "provider", "service", "receiver", "intent-filter", "meta-data"]:
            changed = tree + f"      E: {element} (line=99)\n"
            with self.subTest(element=element), self.assertRaises(ValueError):
                validate_helper(badging, CERTIFICATE, changed, TARGET_SHA)

    def test_custom_application_and_component_factory_are_rejected(self):
        badging, tree = helper_fixture()
        for attribute in [
            'android:name(0x01010003)="unexpected.Application"',
            'android:appComponentFactory(0x0101057a)="unexpected.Factory"',
            'android:permission(0x01010006)="unexpected.PERMISSION"',
        ]:
            changed = re.sub(r"(    E: application[^\n]*\n)", lambda m: m.group(1) + "      A: " + attribute + "\n", tree)
            with self.subTest(attribute=attribute), self.assertRaises(ValueError):
                validate_helper(badging, CERTIFICATE, changed, TARGET_SHA)

    def test_target_release_guard_rejects_debug_bad_version_and_signer(self):
        for badging, certificate in [
            (TARGET_BADGING + "\napplication-debuggable", CERTIFICATE),
            (TARGET_BADGING.replace("101307", "10307"), CERTIFICATE),
            (TARGET_BADGING.replace("0.13.7", "0.13.7-dev"), CERTIFICATE),
            (TARGET_BADGING.replace(PACKAGE, PACKAGE + ".debug"), CERTIFICATE),
            (TARGET_BADGING, "Signer #1 certificate SHA-256 digest: " + "f" * 64),
        ]:
            with self.assertRaises(ValueError):
                validate_target(badging, certificate)

    def test_yamaha_policy_uses_its_own_identity_and_original_signer(self):
        policy = POLICIES["yamaha"]
        tree = (FIXTURES / "migration-helper-xmltree.txt").read_text(encoding="utf-8")
        badging = (FIXTURES / "migration-helper-badging.txt").read_text(encoding="utf-8")
        for before, after in [
            ("com.axelsarassamit.migrationhelper.yamaha_preview", policy["helper"]),
            ("com.axelsarassamit.gx12.yamaha.preview", policy["package"]),
        ]:
            tree, badging = tree.replace(before, after), badging.replace(before, after)
        certificate = "Signer #1 certificate SHA-256 digest: " + policy["signer"]
        target = f"package: name='{policy['package']}' versionCode='1264' versionName='0.12.64'"
        validate_helper(badging, certificate, tree, TARGET_SHA, "yamaha")
        validate_target(target, certificate, "yamaha")
        for wrong_certificate in [CERTIFICATE, "Signer #1 certificate SHA-256 digest: " + "f" * 64]:
            with self.assertRaises(ValueError):
                validate_helper(badging, wrong_certificate, tree, TARGET_SHA, "yamaha")
            with self.assertRaises(ValueError):
                validate_target(target, wrong_certificate, "yamaha")
        with self.assertRaises(ValueError):
            validate_helper(badging, certificate, tree, TARGET_SHA, "dash")
        with self.assertRaises(ValueError):
            validate_target(target.replace("1264", "1263"), certificate, "yamaha")
        with self.assertRaises(ValueError):
            validate_target(target, certificate, "other")

    def test_windows_and_linux_tool_names(self):
        self.assertTrue(android_tool("/sdk", "apksigner", "nt").endswith("apksigner.bat"))
        self.assertTrue(android_tool("/sdk", "aapt", "nt").endswith("aapt.exe"))
        self.assertTrue(android_tool("/sdk", "apksigner", "posix").endswith("apksigner"))

    def test_duplicate_attributes_and_missing_roots_are_rejected(self):
        _, tree = helper_fixture()
        duplicate = tree.replace(
            '    A: package=',
            '    A: package="duplicate"\n    A: package=',
            1,
        )
        for changed in [duplicate, "", tree + "  E: manifest (line=100)\n"]:
            with self.assertRaises(ValueError):
                parse_xmltree(changed)


if __name__ == "__main__":
    unittest.main()
