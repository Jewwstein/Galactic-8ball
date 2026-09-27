plugins { id("com.android.application") }

android {
    namespace = "com.soulslime.nativeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.soulslime.nativeapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1-anime-cave"
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
