plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
    signing
}

android {
    namespace = "com.finvasia.sentinel"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = false
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.webkit)
}

val versionName = providers.gradleProperty("VERSION_NAME").get()

publishing {
    repositories {
        // Sonatype OSSRH (Maven Central). Credentials come from gradle
        // properties or env (set on CI); absent locally, only the
        // publishToMavenLocal task is usable.
        maven {
            name = "sonatype"
            url = uri(
                if (versionName.endsWith("SNAPSHOT")) {
                    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                } else {
                    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                },
            )
            credentials {
                username = providers.gradleProperty("ossrhUsername").orNull
                    ?: System.getenv("OSSRH_USERNAME")
                password = providers.gradleProperty("ossrhPassword").orNull
                    ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
    publications {
        register<MavenPublication>("release") {
            groupId = providers.gradleProperty("GROUP").get()
            artifactId = providers.gradleProperty("POM_ARTIFACT_ID").get()
            version = versionName

            afterEvaluate { from(components["release"]) }

            pom {
                name.set("Sentinel Android SDK")
                description.set("Native Android SDK for the Sentinel identity-verification flow.")
                url.set("https://github.com/prawatstackflow/sentinel-android")
                licenses {
                    license {
                        name.set("Proprietary")
                        url.set("https://github.com/prawatstackflow/sentinel-android")
                    }
                }
                developers {
                    developer {
                        id.set("finvasia")
                        name.set("Finvasia")
                    }
                }
                scm {
                    url.set("https://github.com/prawatstackflow/sentinel-android")
                    connection.set("scm:git:https://github.com/prawatstackflow/sentinel-android.git")
                }
            }
        }
    }
}

// Sign only when signing material is provided (e.g. on CI), so local builds and
// SNAPSHOT publishes don't require a GPG key.
signing {
    val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
    val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
