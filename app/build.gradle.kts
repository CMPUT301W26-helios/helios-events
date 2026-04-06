import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.JavadocMemberLevel
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val mapsApiKey = providers.gradleProperty("MAPS_API_KEY")
    .orElse(localProperties.getProperty("MAPS_API_KEY") ?: "")
    .get()

android {
    namespace = "com.example.helios"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.helios"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            enableUnitTestCoverage = true
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.transport.api)
    implementation(libs.contentpager)

    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-storage")

    implementation(libs.material)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.barcode.scanning)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    val navVersion = "2.9.7"
    implementation("androidx.navigation:navigation-fragment:$navVersion")
    implementation("androidx.navigation:navigation-ui:$navVersion")
    implementation("androidx.navigation:navigation-dynamic-features-fragment:$navVersion")
    androidTestImplementation("androidx.navigation:navigation-testing:$navVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("org.mockito:mockito-core:5.14.0")
}

afterEvaluate {
    fun registerAndroidJavadocTask(variantName: String) {
        val variantCap = variantName.replaceFirstChar { it.uppercase() }
        val compileTaskProvider =
            tasks.named("compile${variantCap}JavaWithJavac", JavaCompile::class.java)

        tasks.register("javadoc$variantCap", Javadoc::class.java) {
            group = "documentation"
            description = "Generates Javadoc for the $variantName variant."

            dependsOn(compileTaskProvider)

            val javaCompile = compileTaskProvider.get()

            setSource(
                javaCompile.source.matching {
                    include("**/*.java")
                    exclude(
                        "**/R.java",
                        "**/R2.java",
                        "**/BuildConfig.java",
                        "**/Manifest.java",
                        "**/*Binding.java",
                        "**/*Directions.java",
                        "**/*Args.java"
                    )
                }
            )

            setClasspath(
                files(
                    android.bootClasspath,
                    javaCompile.classpath,
                    javaCompile.destinationDirectory
                )
            )

            setDestinationDir(
                layout.buildDirectory.dir("docs/javadoc/$variantName").get().asFile
            )

            isFailOnError = true

            val docOptions = options as StandardJavadocDocletOptions
            docOptions.encoding = "UTF-8"
            docOptions.charSet = "UTF-8"
            docOptions.memberLevel = JavadocMemberLevel.PUBLIC
            docOptions.source("11")
            docOptions.addStringOption("Xdoclint:none", "-quiet")
        }
    }

    registerAndroidJavadocTask("debug")
    registerAndroidJavadocTask("release")

    tasks.register("javadocAll") {
        group = "documentation"
        description = "Generates Javadoc for all configured Android variants."
        dependsOn("javadocDebug", "javadocRelease")
    }
}
