import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

// Read local.properties safely
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}

// Extract values (with fallbacks) into plain variables first
//  — avoids nested-quote issues inside buildConfigField strings
val cloudName: String   = localProps.getProperty("CLOUDINARY_CLOUD_NAME", "dk4zjc9pm")
val cloudApiKey: String  = localProps.getProperty("CLOUDINARY_API_KEY", "")
val cloudPreset: String = localProps.getProperty("CLOUDINARY_UPLOAD_PRESET", "")

android {
    namespace = "com.example.sanzinkstore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sanzinkstore"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject Cloudinary values as BuildConfig constants
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME",  "\"$cloudName\"")
        buildConfigField("String", "CLOUDINARY_API_KEY",     "\"$cloudApiKey\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$cloudPreset\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Image Loading
    implementation(libs.picasso)
    implementation(libs.gson)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)

    // Google Auth
    implementation(libs.play.services.auth)

    // Cloudinary (Media / Product image uploads)
    implementation("com.cloudinary:cloudinary-android:3.0.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}