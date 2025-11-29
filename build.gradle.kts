plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    id("maven-publish")
    id("signing")
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
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

// Maven Publishing Configuration
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "securestore"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("SecureStore")
                description.set(
                    "A secure storage library for Android using Google Tink encryption with Android Keystore backing",
                )
                url.set("https://github.com/Kosikowski/secure-store-kt")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("Kosikowski")
                        name.set("Mateusz Kosikowski")
                        email.set("mateusz.kosikowski@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/Kosikowski/secure-store-kt.git")
                    developerConnection.set("scm:git:ssh://github.com:Kosikowski/secure-store-kt.git")
                    url.set("https://github.com/Kosikowski/secure-store-kt")
                }
            }
        }
    }

    repositories {
        maven {
            name = "sonatype"
            val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)

            credentials {
                username = project.findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME")
                password = project.findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    sign(publishing.publications["release"])
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
