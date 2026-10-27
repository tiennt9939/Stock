plugins { id("com.android.application") }

android {
    namespace = "com.rollgarden.stock"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.rollgarden.stock"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
