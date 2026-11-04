import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// "See what's new" reads this copy of the CHANGELOG from the app's assets
val changelogAssets = layout.buildDirectory.dir("generated/changelogAssets")
val copyChangelog = tasks.register<Copy>("copyChangelog") {
    from(rootProject.file("CHANGELOG.md"))
    into(changelogAssets)
}
tasks.named("preBuild") { dependsOn(copyChangelog) }

android {
    sourceSets["main"].assets.srcDir(changelogAssets)
    namespace = "com.bleepp"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.bleepp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release key: keystore.properties next to the project, in ~/.bleepp-signing/, or BLEEPP_KEYSTORE_* env vars.
    // Never committed. Without it, release builds fall back to the debug key.
    val keyProps = Properties().apply {
        listOf(rootProject.file("keystore.properties"), File(System.getProperty("user.home"), ".bleepp-signing/keystore.properties"))
            .firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
        System.getenv("BLEEPP_KEYSTORE_FILE")?.let {
            this["storeFile"] = it
            this["storePassword"] = System.getenv("BLEEPP_KEYSTORE_PASSWORD")
            this["keyAlias"] = System.getenv("BLEEPP_KEY_ALIAS") ?: "bleepp"
            this["keyPassword"] = System.getenv("BLEEPP_KEY_PASSWORD") ?: System.getenv("BLEEPP_KEYSTORE_PASSWORD")
        }
    }
    signingConfigs {
        if (keyProps["storeFile"] != null) {
            create("release") {
                storeFile = file(keyProps["storeFile"] as String)
                storePassword = keyProps["storePassword"] as String
                keyAlias = keyProps["keyAlias"] as String
                keyPassword = keyProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    // Per-app language picker (works back to Android 8 via AppCompat)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    val room = "2.8.5"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    testLogging { events("passed", "failed", "skipped") }
}
