plugins { id("com.android.application") }
android {
 namespace = "com.galactic.eightball"
 compileSdk = 35
 defaultConfig { applicationId = "com.galactic.eightball"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1" }
}

dependencies { implementation("org.jbox2d:jbox2d-library:2.2.1.1") }
