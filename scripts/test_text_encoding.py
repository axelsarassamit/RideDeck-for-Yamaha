import tempfile
from pathlib import Path
import unittest
import struct
from check_text_encoding import chars, check_file, problems, dex_strings

class TextEncodingTests(unittest.TestCase):
    def test_corrupted_bullet_ellipsis_dimensions_and_thai_are_detected(self):
        for damaged in [chars(0xE2,0x20AC,0xA2), chars(0xE2,0x20AC,0xA6),
                chars(0xC3,0x97), chars(0xE0,0xB8,0x96), chars(0xC2,0xA0),
                chars(0xF0,0x178,0x98,0x80), chars(0xEF,0xBF,0xBD), chars(0xEF,0xBB,0xBF), chars(0xFFFD)]:
            with self.subTest(codepoints=list(map(ord,damaged))):
                self.assertEqual([2], problems('First line\n'+damaged))

    def test_unicode_escaping_cannot_hide_corruption(self):
        damaged = ''.join('\\u'+format(value,'04x') for value in [0xE2,0x20AC,0xA2])
        self.assertEqual([1], problems(damaged))

    def test_real_thai_multilingual_names_symbols_and_emoji_remain_valid(self):
        valid = 'ถนนสุขุมวิท • 480 × 234 … Café à Lyon, Iñaki, Ã, Â, â, 北京 😀'
        self.assertEqual([], problems(valid))
        self.assertEqual([], problems(r'\u2022 \u00d7 \u2026'))
        self.assertEqual([], problems(r'\\u009F'))
        literal_regex='['+chr(92)+'u007F-'+chr(92)+'u009F]'
        self.assertEqual([], problems(literal_regex, expand_unicode=False))

    def test_dex_modified_utf8_preserves_emoji_and_thai(self):
        sample='ถนนสุขุมวิท 😀'
        raw=sample.encode('utf-16-be')
        units=''.join(chr(int.from_bytes(raw[i:i+2],'big')) for i in range(0,len(raw),2))
        encoded=units.encode('utf-8','surrogatepass')
        data=bytearray(116)
        data[:8]=b'dex\n035\x00'
        struct.pack_into('<II',data,56,1,112)
        struct.pack_into('<I',data,112,116)
        data.extend(bytes([len(units)])+encoded+b'\0')
        self.assertEqual([(0,sample)],list(dex_strings(data)))

    def test_invalid_file_bytes_fail_instead_of_becoming_replacement_characters(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'source.kt'
            path.write_bytes(b'Title '+bytes([0xE9]))
            self.assertEqual(['invalid UTF-8'], check_file(path))
            path.write_text('ถนนสุขุมวิท',encoding='utf-8')
            self.assertEqual([],check_file(path))

if __name__ == '__main__':
    unittest.main()
