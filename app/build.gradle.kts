import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseSigningProps = Properties().apply {
    val candidateFiles = listOfNotNull(
        System.getenv("TIMEWALLET_KEYSTORE_PROPERTIES")?.takeIf { it.isNotBlank() }?.let(::file),
        rootProject.file("keystore.properties"),
        file("${System.getProperty("user.home")}/.timewallet/keystore.properties")
    )
    val propsFile = candidateFiles.firstOrNull { it.isFile }
    if (propsFile != null) {
        propsFile.inputStream().use(::load)
    }
}

fun resolveReleaseProp(envKey: String, propKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: releaseSigningProps.getProperty(propKey)?.trim()?.takeIf { it.isNotEmpty() }

val resolvedStoreFilePath = resolveReleaseProp("TIMEWALLET_KEYSTORE_PATH", "storeFile")
val resolvedStorePassword = resolveReleaseProp("TIMEWALLET_KEYSTORE_PASSWORD", "storePassword")
val resolvedKeyAlias = resolveReleaseProp("TIMEWALLET_KEY_ALIAS", "keyAlias")
val resolvedKeyPassword = resolveReleaseProp("TIMEWALLET_KEY_PASSWORD", "keyPassword")
val resolvedStoreFile = resolvedStoreFilePath?.let { path ->
    val direct = file(path)
    if (direct.isAbsolute) direct else rootProject.file(path)
}?.takeIf { it.isFile }

val hasCompleteReleaseSigning =
    resolvedStoreFile != null &&
        resolvedStorePassword != null &&
        resolvedKeyAlias != null &&
        resolvedKeyPassword != null

android {
    namespace = "com.timewallet.mvp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.timewallet.mvp"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "2.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                storeFile = resolvedStoreFile
                storePassword = resolvedStorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    // Hỗ trợ override tạm qua CLI (-PqaVersionCode / -PqaVersionName) khi kiểm thử nâng cấp GitHub Auto-Update E2E trên QA emulator.
    defaultConfig {
        (project.findProperty("qaVersionCode") as? String)?.toIntOrNull()?.let { versionCode = it }
        (project.findProperty("qaVersionName") as? String)?.takeIf { it.isNotBlank() }?.let { versionName = it }
    }
}

// v1.1.0: Room xuất schema JSON để test L3 đối chiếu DDL migration với lược đồ Room thật.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Fail closed: không bao giờ xuất bản Release không ký số khi chạy đóng gói release.
tasks.matching {
    it.name in setOf("assembleRelease", "packageRelease", "bundleRelease", "signReleaseBundle")
}.configureEach {
    doFirst {
        check(hasCompleteReleaseSigning) {
            "Thieu cau hinh ky so Release. Hay tao ~/.timewallet/keystore.properties hoac dat TIMEWALLET_KEYSTORE_*."
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Room Local Database (Offline-first Time Ledger)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.2")
    testImplementation("org.json:json:20231013")
    debugImplementation(libs.androidx.ui.tooling)
}

