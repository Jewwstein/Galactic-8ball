plugins { id("com.android.application") }

android {
    namespace = "com.soulslime.nativeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.soulslime.nativeapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.7.1-3d-slime-clean"
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
