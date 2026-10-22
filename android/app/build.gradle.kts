plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

abstract class BundleExerciseCatalog : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalog: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun bundle() {
        val directory = outputDirectory.get().asFile
        directory.mkdirs()
        catalog.get().asFile.copyTo(directory.resolve("exercise-catalog.json"), overwrite = true)
    }
}

android {
    namespace = "com.hector.workouttracker"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "com.hector.workouttracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.7.0-history"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets["test"].resources.srcDir("../../src/data")
    sourceSets["test"].resources.srcDir("../../tests/fixtures/android")
    sourceSets["test"].resources.srcDir("src/main/assets")
    sourceSets["androidTest"].assets.srcDir("../../tests/fixtures/android")
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    buildTypes { release { isMinifyEnabled = false } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
}

androidComponents.onVariants { variant ->
    val catalogAssets =
        tasks.register<BundleExerciseCatalog>(
            "bundle${variant.name.replaceFirstChar { it.uppercaseChar() }}ExerciseCatalog"
        ) {
            catalog.set(
                rootProject.layout.projectDirectory.file("../src/data/exercise-catalog.json")
            )
        }
    variant.sources.assets?.addGeneratedSourceDirectory(
        catalogAssets,
        BundleExerciseCatalog::outputDirectory,
    )
}
