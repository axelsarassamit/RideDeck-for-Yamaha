"""Parse Android SDK aapt xmltree output for APK identity checks."""
from dataclasses import dataclass, field
import json
import os
from pathlib import Path
import re
import subprocess


@dataclass
class Element:
    name: str
    attributes: dict = field(default_factory=dict)
    children: list = field(default_factory=list)


def android_tool(build_tools, name, platform=None):
    platform = os.name if platform is None else platform
    suffix = (".bat" if name == "apksigner" else ".exe") if platform == "nt" else ""
    return str(Path(build_tools) / (name + suffix))


def attribute_value(raw):
    if raw.startswith('"'):
        value, end = json.JSONDecoder().raw_decode(raw)
        if not re.fullmatch(r"\s*(?:\(Raw: .*\))?\s*", raw[end:]):
            raise ValueError("Unexpected manifest string representation")
        return value
    typed = re.fullmatch(r"\(type 0x([0-9a-fA-F]+)\)0x([0-9a-fA-F]+)", raw)
    if typed:
        kind, value = (int(part, 16) for part in typed.groups())
        return bool(value) if kind == 0x12 else value
    if re.fullmatch(r"@0x[0-9a-fA-F]+", raw):
        return raw
    raise ValueError("Unsupported manifest attribute representation")


def parse_xmltree(text):
    root = None
    stack = []
    for line in text.splitlines():
        content = line.strip()
        if not content or content.startswith("N: "):
            continue
        indent = len(line) - len(line.lstrip())
        element = re.fullmatch(r"E: ([^\s]+) \(line=[0-9]+\)", content)
        if element:
            node = Element(element.group(1))
            while stack and indent <= stack[-1][0]:
                stack.pop()
            if stack:
                stack[-1][1].children.append(node)
            elif root is None:
                root = node
            else:
                raise ValueError("Multiple manifest roots")
            stack.append((indent, node))
            continue
        attribute = re.fullmatch(r"A: ([^\s(=]+)(?:\(0x[0-9a-fA-F]+\))?=(.*)", content)
        if not attribute or not stack or indent <= stack[-1][0]:
            raise ValueError("Malformed manifest tree")
        name, raw = attribute.groups()
        node = stack[-1][1]
        if name in node.attributes:
            raise ValueError("Duplicate manifest attribute")
        node.attributes[name] = attribute_value(raw)
    if root is None or root.name != "manifest":
        raise ValueError("APK manifest root missing")
    return root


def single_child(parent, name):
    matches = [node for node in parent.children if node.name == name]
    if len(matches) != 1:
        raise ValueError("Expected exactly one manifest " + name)
    return matches[0]


def verify_instrumentation(manifest, runner, target):
    node = single_child(manifest, "instrumentation")
    if node.attributes.get("android:name") != runner or node.attributes.get("android:targetPackage") != target:
        raise ValueError("Unexpected instrumentation runner or target")
    return node


def verify_signer(certificates, expected):
    signers = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)$", certificates, re.M)
    if [signer.lower() for signer in signers] != [expected]:
        raise ValueError("APK does not use the required original signer")


def read_apk(apk, build_tools):
    aapt = android_tool(build_tools, "aapt")
    signer = android_tool(build_tools, "apksigner")
    def run(command):
        return subprocess.check_output(command, text=True, encoding="utf-8")
    return (
        run([aapt, "dump", "badging", str(apk)]),
        run([signer, "verify", "--print-certs", str(apk)]),
        run([aapt, "dump", "xmltree", str(apk), "AndroidManifest.xml"]),
    )
