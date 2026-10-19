plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.axelsarassamit.gx12"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "com.axelsarassamit.gx12.yamaha"
        buildConfigField("boolean", "YAMAHA", "true")
        buildConfigField("String", "UPDATE_ASSET", "\"ridedeck-yamaha-release.apk\"")
        manifestPlaceholders["appLabel"] = "RideDeck for Yamaha"
        fun providerKey(name: String): String {
            val value = System.getenv(name).orEmpty().trim()
            require(value.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Invalid provider key format" }
            return "\"$value\""
        }
        buildConfigField("String", "MAPTILER_KEY", providerKey("RIDEDECK_MAPTILER_KEY"))
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val releaseVersion = (System.getenv("GX12_VERSION_NAME") ?: "0.12.53").removePrefix("v")
        val parts = releaseVersion.split(".")
        require(parts.size >= 2 && parts.take(3).all { it.all(Char::isDigit) }) {
            "GX12_VERSION_NAME must use numeric semver such as 0.1.0"
        }
        val major = parts[0].toInt()
        val minor = parts[1].toInt()
        val patch = if (parts.size > 2) parts[2].toInt() else 0
        versionCode = major * 10000 + minor * 100 + patch
        versionName = releaseVersion
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("GX12_KEYSTORE_PATH")
            val storePasswordValue = System.getenv("GX12_KEYSTORE_PASSWORD")
            val aliasValue = System.getenv("GX12_KEY_ALIAS")
            val keyPasswordValue = System.getenv("GX12_KEY_PASSWORD")
            if (!storePath.isNullOrBlank() && !storePasswordValue.isNullOrBlank() &&
                !aliasValue.isNullOrBlank() && !keyPasswordValue.isNullOrBlank()
            ) {
                storeFile = rootProject.file(storePath)
                storePassword = storePasswordValue
                keyAlias = aliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".preview"
            manifestPlaceholders["appLabel"] = "RideDeck Yamaha Preview"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    testBuildType = providers.gradleProperty("phoneTestVariant").orElse("debug").get()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.merges += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/LICENSE", "META-INF/NOTICE") }
}

dependencies {
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-video:1.4.2")
    implementation("androidx.core:core:1.16.0")
    implementation("org.maplibre.gl:android-sdk:13.4.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
val checkTextEncoding by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    val python = if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
    commandLine(python, "scripts/check_text_encoding.py")
}
val testTextEncoding by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    val python = if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
    commandLine(python, "-m", "unittest", "discover", "-s", "scripts", "-p", "test_text_encoding.py")
}
tasks.named("preBuild") { dependsOn(checkTextEncoding, testTextEncoding) }
