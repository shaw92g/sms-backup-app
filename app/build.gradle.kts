plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.smsbackup"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.smsbackup"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.10.1")
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation("junit:junit:4.13.2")
}