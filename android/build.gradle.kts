plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

val kotlinFormatter by configurations.creating

dependencies {
    kotlinFormatter("com.facebook:ktfmt:0.60:with-dependencies") { isTransitive = false }
}

listOf("formatKotlin", "checkKotlinFormat").forEach { taskName ->
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description =
            if (taskName == "formatKotlin") "Format native Kotlin sources"
            else "Check native Kotlin formatting"
        classpath = kotlinFormatter
        mainClass.set("com.facebook.ktfmt.cli.Main")
        args("--kotlinlang-style")
        if (taskName == "checkKotlinFormat") args("--dry-run", "--set-exit-if-changed")
        args(
            fileTree(rootDir) {
                    include("**/*.kt", "**/*.kts")
                    exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
                }
                .files
                .map { it.absolutePath }
                .sorted()
        )
    }
}
