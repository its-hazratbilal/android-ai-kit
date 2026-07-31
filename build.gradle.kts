plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
}

allprojects {
    group = "com.github.its-hazratbilal"
    version = "1.0.0"
}