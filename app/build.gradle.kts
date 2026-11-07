import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/** Reads a value from -P / gradle.properties, then from the environment (CI secrets). */
fun config(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.shoto.socialsave"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shoto.socialsave"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        val admobAppId = config("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"
        val admobNativeId = config("ADMOB_NATIVE_ID") ?: "ca-app-pub-3940256099942544/2247696110"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_NATIVE_ID", "\"$admobNativeId\"")
        buildConfigField("String", "WEBSITE_URL", "\"https://socialsave.cc\"")

        // Accounts and sync. The Google id is the web/server OAuth client the API accepts as `aud`;
        // Android also needs its own Android OAuth client (package + SHA-1) in the same Cloud project.
        val apiBaseUrl = config("API_BASE_URL") ?: "https://socialsave.cc/api/v1"
        val googleServerClientId = config("GOOGLE_SERVER_CLIENT_ID")
            ?: "81667312331-mrh5eoha3rdq3d1kv4ecahovn8g89n7j.apps.googleusercontent.com"
        // Empty until the owner creates the Facebook app; the Facebook button is hidden while either is empty.
        val facebookAppId = config("FACEBOOK_APP_ID").orEmpty()
        val facebookClientToken = config("FACEBOOK_CLIENT_TOKEN").orEmpty()
        val facebookEnabled = facebookAppId.isNotEmpty() && facebookClientToken.isNotEmpty()
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl.trimEnd('/')}\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        buildConfigField("String", "FACEBOOK_APP_ID", "\"$facebookAppId\"")
        buildConfigField("boolean", "FACEBOOK_ENABLED", facebookEnabled.toString())
        manifestPlaceholders["facebookAppId"] = facebookAppId
        manifestPlaceholders["facebookClientToken"] = facebookClientToken
        manifestPlaceholders["facebookEnabled"] = facebookEnabled.toString()
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Always Google's test ads while developing, whatever ids are configured.
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)

    implementation(libs.play.services.ads)
    implementation(libs.ump)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.facebook.login)
    implementation(libs.androidx.browser)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    // The Android jar only has stubs for org.json; JVM tests need the real implementation.
    testImplementation(libs.org.json)
}
