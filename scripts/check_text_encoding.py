"""Reject malformed UTF-8 and common double-decoding damage without stripping languages.

Source reports show paths and line numbers, never source text or bundled credentials.
Use --apk to inspect the compiled DEX strings as well as repository text.
"""
from pathlib import Path
import argparse
import re
import struct
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TEXT_SUFFIXES = {'.java', '.kt', '.kts', '.xml', '.json', '.yml', '.yaml', '.md',
    '.txt', '.properties', '.py', '.ps1', '.bat', '.sh', '.svg', '.tsv', '.csv'}

def chars(*values):
    return ''.join(map(chr, values))

# Windows-1252/Latin-1 renderings of UTF-8 prefixes. Single accented letters are valid.
SUSPECT = re.compile('|'.join([
    chars(0xC3) + '[' + chars(0x80) + '-' + chars(0xBF) + ']',
    chars(0xC2) + '[' + chars(0x80) + '-' + chars(0xBF) + ']',
    re.escape(chars(0xE2, 0x20AC)),
    re.escape(chars(0xF0, 0x178)),
    re.escape(chars(0xEF, 0xBF)),
    re.escape(chars(0xEF, 0xBB, 0xBF)),
    chars(0xE0) + '[' + chars(0xB8, 0xB9) + ']',
    '[' + chars(0x80) + '-' + chars(0x9F) + ']',
    chars(0xFFFD),
]))
ESCAPE = re.compile(r'(?<!\\)\\u([0-9a-fA-F]{4})')

def problems(text, expand_unicode=True):
    # Includes Kotlin/Java/JSON Unicode escapes so escaping broken text cannot hide it.
    expanded = ESCAPE.sub(lambda match: chr(int(match[1], 16)), text) if expand_unicode else text
    return sorted({expanded.count('\n', 0, match.start()) + 1 for match in SUSPECT.finditer(expanded)})

def check_file(path):
    try:
        return problems(path.read_text(encoding='utf-8-sig'))
    except UnicodeDecodeError:
        return ['invalid UTF-8']

def source_files():
    result = subprocess.run(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'],
        cwd=ROOT, capture_output=True, check=True)
    for name in sorted(set(result.stdout.decode('utf-8').split('\0'))):
        if not name:
            continue
        path = ROOT / name
        if path.is_file() and (path.suffix.lower() in TEXT_SUFFIXES or not path.suffix or name == '.gitignore'):
            yield path

def dex_strings(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Invalid DEX header')
    count, table = struct.unpack_from('<II', data, 56)
    for index in range(count):
        pos = struct.unpack_from('<I', data, table + index * 4)[0]
        # DEX prefixes each string with its UTF-16 length as unsigned LEB128.
        while data[pos] & 0x80:
            pos += 1
        pos += 1
        raw = data[pos:data.index(b'\0', pos)].replace(b'\xc0\x80', b'\0')
        # Modified UTF-8 stores supplementary characters as surrogate pairs.
        text = raw.decode('utf-8', 'surrogatepass').encode('utf-16', 'surrogatepass').decode('utf-16')
        yield index, text

def check_apk(path):
    issues, count = [], 0
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                for index, text in dex_strings(archive.read(name)):
                    # DEX also contains binary Kotlin metadata, not displayed text.
                    if any(ord(char) < 32 and char not in '\n\r\t' for char in text):
                        continue
                    count += 1
                    # Escapes here are already compiled. Literal regex escapes are not UI glyphs.
                    if problems(text, expand_unicode=False):
                        issues.append(f'{name}: string #{index}')
    if not count:
        raise ValueError('APK contains no DEX strings')
    return issues, count

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path)
    args = parser.parse_args()
    files = list(source_files())
    issues = [f'{path.relative_to(ROOT)}: {line}' for path in files for line in check_file(path)]
    if args.apk:
        apk_issues, strings = check_apk(args.apk)
        issues.extend(apk_issues)
        print(f'Checked {strings} readable compiled DEX strings (binary metadata excluded)')
    for issue in issues:
        print('Encoding error: ' + issue)
    print(f'Checked {len(files)} UTF-8 source, resource, documentation and build files; {len(issues)} errors')
    return bool(issues)

if __name__ == '__main__':
    sys.exit(main())
