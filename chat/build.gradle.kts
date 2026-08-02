plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.hazratbilal.aikit.chat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        project.group.toString(),
        "android-ai-kit-chat"
    )

    pom {
        name.set("AiKit Chat")
        description.set("Streaming chat API for AiKit. Provides a coroutine-based interface for token streaming using models loaded with AiKit Core.")
        url.set("https://github.com/its-hazratbilal/android-ai-kit")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("its-hazratbilal")
                name.set("Hazrat Bilal")
                url.set("https://hazratbilal.com")
            }
        }
        scm {
            url.set("https://github.com/its-hazratbilal/android-ai-kit")
            connection.set("scm:git:https://github.com/its-hazratbilal/android-ai-kit.git")
            developerConnection.set("scm:git:ssh://git@github.com/its-hazratbilal/android-ai-kit.git")
        }
    }
}

dependencies {
    api(project(":core"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.kotlinx.coroutines.android)
}
