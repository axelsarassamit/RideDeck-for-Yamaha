plugins { id("com.android.application") version "9.4.1" }

dependencies { testImplementation("junit:junit:4.13.2") }

val target = providers.gradleProperty("targetPackage").orElse("com.axelsarassamit.gx12.yamaha.preview").get()
val targets = mapOf(
    "com.axelsarassamit.gx12.yamaha" to "yamaha_release",
    "com.axelsarassamit.gx12.yamaha.preview" to "yamaha_preview",
    "com.axelsarassamit.ridedeck.dash" to "dash_release",
    "com.axelsarassamit.ridedeck.dash.debug" to "dash_debug"
)
require(targets.containsKey(target)) { "Unsupported migration target: $target" }
val targetHash = providers.gradleProperty("expectedTargetApkSha256").orElse("0".repeat(64)).get()
require(targetHash.matches(Regex("[0-9a-f]{64}")))

android {
    namespace = "com.axelsarassamit.migrationhelper"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "com.axelsarassamit.migrationhelper.${targets.getValue(target)}"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-test-only"
        manifestPlaceholders["migrationTarget"] = target
        manifestPlaceholders["migrationTargetHash"] = targetHash
        buildConfigField("String", "TARGET_PACKAGE", "\"$target\"")
        buildConfigField("String", "EXPECTED_TARGET_APK_SHA256", "\"$targetHash\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
