import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// --- Demo config (kept out of git) ----------------------------------------
// The "Start Demo Session" button mints a session by calling the platform's
// POST /sessions with a tenant API key. Put secrets in local.properties
// (git-ignored — see local.properties.example):
//   DEMO_API_KEY=sk_...
//   DEMO_API_BASE_URL=https://10.10.10.51/api
//   DEMO_HOSTED_FLOW_BASE_URL=https://10.10.10.51
// Anything omitted falls back to the local-LAN defaults below. The committed
// default for DEMO_API_KEY is empty, so the button is inert until you set one.
val demoProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun demoProp(key: String, default: String): String =
    demoProps.getProperty(key) ?: System.getenv(key) ?: default

android {
    namespace = "com.finvasia.sentinel.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.finvasia.sentinel.demo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "DEMO_API_KEY", "\"${demoProp("DEMO_API_KEY", "")}\"")
        buildConfigField(
            "String",
            "DEMO_API_BASE_URL",
            "\"${demoProp("DEMO_API_BASE_URL", "https://10.10.10.51/api")}\"",
        )
        buildConfigField(
            "String",
            "DEMO_HOSTED_FLOW_BASE_URL",
            "\"${demoProp("DEMO_HOSTED_FLOW_BASE_URL", "https://10.10.10.51")}\"",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Local development uses the library as a project dependency. To test the
    // *published* artifact instead, swap this for
    //   implementation("com.finvasia.sentinel:sentinel-android:<version>")
    // and add mavenLocal()/mavenCentral() to settings repositories.
    implementation(project(":sentinel"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
}
