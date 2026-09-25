plugins { id("com.android.application") }
android {
 namespace = "com.galactic.eightball"
 compileSdk = 35

 signingConfigs {
   create("stableDebug") {
     storeFile = file("galactic-stable-debug.keystore")
     storePassword = "galactic8ball"
     keyAlias = "galacticdebug"
     keyPassword = "galactic8ball"
   }
 }

 defaultConfig {
   applicationId = "com.galactic.eightball"
   minSdk = 26
   targetSdk = 35
   versionCode = 5
   versionName = "0.5"
 }

 buildTypes {
   getByName("debug") {
     signingConfig = signingConfigs.getByName("stableDebug")
   }
 }
}

dependencies {
 implementation("org.jbox2d:jbox2d-library:2.2.1.1")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
