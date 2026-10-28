plugins { id("com.android.application") }

android {
    namespace = "com.rollgarden.stock"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.rollgarden.stock"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.0"
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
