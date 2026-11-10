import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.serialization)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.ksp)
    alias(libs.plugins.secrets.gradle)
}

// Update feed and source link are build switches (Gradle property `-PupdateFeed=...`, `-PsourceUrl=...`, `-PgithubOwner=...`,
// `-PgithubRepo=...`). Order: project property, then build-defaults.internal.properties (only in the internal repository, not in the
// public export), then the public GitHub defaults. `-PupdateFeed=github` forces the GitHub release feed.
val internalBuildDefaults = Properties().apply {
    val file = rootProject.file("build-defaults.internal.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}

fun buildSwitch(name: String): String? =
    (findProperty(name) as String?)?.trim()?.takeIf { it.isNotEmpty() }
        ?: internalBuildDefaults.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val githubOwner = buildSwitch("githubOwner") ?: "dienstag0815"
val githubRepo = buildSwitch("githubRepo") ?: "wow-forever-touch"
val githubRepoUrl = "https://github.com/$githubOwner/$githubRepo"
val updateFeedUrl = buildSwitch("updateFeed").let { if (it == null || it == "github") "$githubRepoUrl/releases/latest/download/latest.json" else it }
val sourceCodeUrl = buildSwitch("sourceUrl") ?: githubRepoUrl

// Release signing: properties file (storeFile, storePassword, keyAlias, keyPassword) kept outside the repository. Order: Gradle property
// `-PsigningProps=<path>`, environment variable `WOWFOREVER_SIGNING`, then app/keystores/keystore.properties. Without any of them the
// build is signed with the debug key as before.
val signingPropsPath = (findProperty("signingProps") as String?)?.trim()?.takeIf { it.isNotEmpty() }
    ?: System.getenv("WOWFOREVER_SIGNING")?.trim()?.takeIf { it.isNotEmpty() }
val keystorePropertiesFile = signingPropsPath?.let { File(it) } ?: rootProject.file("app/keystores/keystore.properties")
val keystoreProperties: Properties? = if (keystorePropertiesFile.isFile) {
    Properties().apply {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
} else {
    if (signingPropsPath != null) logger.warn("signingProps file not found, signing with the debug key: $signingPropsPath")
    null
}

android {
    namespace = "app.gamenative"
    compileSdk = 36

    // https://developer.android.com/ndk/downloads
    ndkVersion = "27.3.13750724"

    signingConfigs {
        create("release") {
            if (keystoreProperties != null) {
                storeFile = file(keystoreProperties["storeFile"].toString())
                storePassword = keystoreProperties["storePassword"].toString()
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
            }
        }
    }

    defaultConfig {
        applicationId = "app.wowforever"

        minSdk = 26

        manifestPlaceholders["screenOrientation"] = "unspecified"
        buildConfigField("boolean", "XR_BUILD", "false")

        versionCode = 289
        versionName = "2.3.3-touch9.2"

        buildConfigField("boolean", "GOLD", "false")
        // Feed of the self-update (latest.json + APK); can be overridden in the app under "Erweitert".
        buildConfigField("String", "UPDATE_FEED_URL", "\"$updateFeedUrl\"")
        // Link to the source code (launcher "About" page and footer).
        buildConfigField("String", "SOURCE_URL", "\"$sourceCodeUrl\"")
        val iconValue = "@mipmap/ic_launcher"
        val iconRoundValue = "@mipmap/ic_launcher_round"
        manifestPlaceholders.putAll(
            mapOf(
                "icon" to iconValue,
                "roundIcon" to iconRoundValue,
            ),
        )

        ndk {
            //abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }

        // Localization support - specify which languages to include
        resourceConfigurations += listOf(
            "en",      // English (default)
            "es",      // Spanish
            "da",      // Danish
            "pt-rBR",  // Portuguese (Brazilian)
            "zh-rTW",  // Traditional Chinese
            "zh-rCN",  // Simplified Chinese
            "fr",      // French
            "de",      // German
            "uk",      // Ukrainian
            "it",      // Italian
            "ro",      // Română
            "pl",      // Polish
            "ru",      // Russian
            "ko",      // Korean
            "ja",      // Japanese
            // TODO: Add more languages here using the ISO 639-1 locale code with regional qualifiers (e.g., "pt-rPT" for European Portuguese)
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        proguardFiles(
            // getDefaultProguardFile("proguard-android-optimize.txt"),
            getDefaultProguardFile("proguard-android.txt"),
            "proguard-rules.pro",
        )
    }

    flavorDimensions += "androidApi"
    productFlavors {
        create("modern") {
            dimension = "androidApi"
            minSdk = 29
            targetSdk = 36
            ndk.abiFilters += listOf("arm64-v8a")
            buildConfigField("boolean", "MODERN_ANDROID", "true")
            buildConfigField("String", "PRELOAD_BIONIC_SO", "\"libredirect-bionic-wx.so\"")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName(if (keystoreProperties != null) "release" else "debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(if (keystoreProperties != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/DebugProbesKt.bin"
            excludes += "/junit/runner/smalllogo.gif"
            excludes += "/junit/runner/logo.gif"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            // 'extractNativeLibs' was not enough to keep the jniLibs and
            // the libs went missing after adding on-demand feature delivery
            useLegacyPackaging = true
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "4g"
                it.testLogging { events("started", "failed") }
            }
        }
    }

    lint {
        // Locale files ship full AndroidX appcompat (abc_*) translations that aren't in the
        // default locale. These extra translations are harmless and pre-existing; without this
        // the release-only lintVital pass fails on 150+ ExtraTranslation errors.
        disable += "ExtraTranslation"
    }

    // Configure Assets to be used in different variants
    sourceSets {
        getByName("modern") {
            assets {
                srcDirs("src/modern/assets", "src/main/assets")
            }
        }
    }

    kotlinter {
        ignoreFormatFailures  = false
    }

}

dependencies {
    implementation(libs.okhttp)

    // Winlator
    implementation(libs.bundles.winlator)
    implementation(libs.zstd.jni) { artifact { type = "aar" } }
    implementation(libs.xz)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Support
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.jetbrains.kotlinx.json)
    implementation(libs.kotlin.coroutines)
    implementation(libs.timber)

    // Hilt
    implementation(libs.bundles.hilt)

    // KSP (Hilt)
    ksp(libs.bundles.ksp)


    // Memory Leak Detection
    // debugImplementation("com.squareup.leakcanary:leakcanary-android:3.0-alpha-8")

    // Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.zstd.jni)
    testImplementation(libs.orgJson)
    testImplementation(libs.mockwebserver)
}
