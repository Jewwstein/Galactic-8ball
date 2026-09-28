plugins { id("com.android.application") }

android {
    namespace = "com.soulslime.nativeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.soulslime.nativeapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.6-painted-room-test"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
