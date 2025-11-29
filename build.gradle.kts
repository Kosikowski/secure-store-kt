import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.kosikowski"
version = "1.0.0"

android {
    namespace = "com.kosikowski.securestore"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
}

dependencies {
    // Kotlin
    implementation(libs.bundles.kotlin)

    // Google Tink for encryption
    implementation(libs.tink.android)

    // AndroidX
    implementation(libs.androidx.core.ktx)

    // Testing
    testImplementation(libs.bundles.test)

    // Android Instrumented Tests
    androidTestImplementation(libs.bundles.android.test)
}

// Maven Publishing Configuration using Vanniktech plugin
mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        ),
    )

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

    // Only sign if signing credentials are available
    // CI sets these via environment variables, locally they can be in ~/.gradle/gradle.properties
    val signingKey =
        providers.gradleProperty("signingInMemoryKey").orNull
            ?: System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey")
    if (signingKey != null) {
        signAllPublications()
    }

    coordinates(
        groupId = "io.github.kosikowski",
        artifactId = "securestore",
        version = project.version.toString(),
    )

    pom {
        name.set("SecureStore")
        description.set(
            "A secure storage library for Android using Google Tink encryption with Android Keystore backing",
        )
        url.set("https://github.com/Kosikowski/secure-store-kt")
        inceptionYear.set("2024")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("Kosikowski")
                name.set("Mateusz Kosikowski")
                email.set("mateusz.kosikowski@gmail.com")
                url.set("https://github.com/Kosikowski")
            }
        }

        scm {
            url.set("https://github.com/Kosikowski/secure-store-kt")
            connection.set("scm:git:git://github.com/Kosikowski/secure-store-kt.git")
            developerConnection.set("scm:git:ssh://git@github.com/Kosikowski/secure-store-kt.git")
        }
    }
}

// ktlint configuration
ktlint {
    version.set(libs.versions.ktlintVersion.get())
    debug.set(false)
    verbose.set(true)
    android.set(true)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    enableExperimentalRules.set(true)
    disabledRules.set(setOf("no-wildcard-imports"))
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        exclude("**/.gradle/**")
        include("**/kotlin/**")
    }
}

// Task to install git hooks
tasks.register("installGitHooks", Copy::class) {
    description = "Install git hooks from .githooks directory"
    group = "setup"
    from(".githooks") {
        include("*")
    }
    into(".git/hooks")
    fileMode = 0b111101101 // 755 in octal - executable permissions
    doLast {
        logger.lifecycle("✅ Git hooks installed successfully!")
        logger.lifecycle("Hooks will run automatically on git operations.")
        logger.lifecycle("To bypass a hook, use: git push --no-verify")
    }
}
