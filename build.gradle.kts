// AGP 9's built-in Kotlin support has a runtime dependency on KGP 2.2.10.
// Forcing a newer KGP here is the supported way to raise the Kotlin compiler
// version, which is needed because current Hilt releases ship Kotlin 2.3
// metadata that a 2.2.10 compiler cannot read.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Keep in sync with `kotlin` in gradle/libs.versions.toml. The catalog
        // accessor is not available inside `buildscript {}`.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

// Top-level build file. Plugins are declared here and applied per module.
// AGP 9 provides built-in Kotlin support, so `org.jetbrains.kotlin.android`
// is deliberately never applied in this project (it is incompatible with
// AGP 9's new DSL).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
