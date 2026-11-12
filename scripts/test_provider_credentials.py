import unittest

from check_provider_credentials import validate


class ProviderCredentialsTest(unittest.TestCase):
    SOURCE = '''
    private fun bundled(name: String): String = when (name) {
        "maptiler" -> BuildConfig.MAPTILER_KEY
        "tomtom" -> BuildConfig.TOMTOM_KEY
        else -> ""
    }
    '''
    BUILD = '''
    buildConfigField("String", "MAPTILER_KEY", providerKey("RIDEDECK_MAPTILER_KEY"))
    buildConfigField("String", "TOMTOM_KEY", providerKey("RIDEDECK_TOMTOM_KEY"))
    '''

    def test_build_configuration_is_allowed(self):
        self.assertEqual([], validate(self.SOURCE, self.BUILD))

    def test_literal_fallback_is_rejected(self):
        source = self.SOURCE.replace("BuildConfig.MAPTILER_KEY", 'BuildConfig.MAPTILER_KEY.ifBlank { "synthetic-test-only" }')
        self.assertTrue(validate(source, self.BUILD))

    def test_direct_literal_is_rejected(self):
        source = self.SOURCE.replace("BuildConfig.TOMTOM_KEY", '"synthetic-test-only"')
        self.assertTrue(validate(source, self.BUILD))

    def test_literal_build_field_is_rejected(self):
        build = self.BUILD.replace('providerKey("RIDEDECK_TOMTOM_KEY")', '"synthetic-test-only"')
        self.assertTrue(validate(self.SOURCE, build))


if __name__ == "__main__":
    unittest.main()
