import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.medicationreminder"
    compileSdkVersion("android-37.0")

    defaultConfig {
        applicationId = "com.example.medicationreminder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val keystoreProps = Properties().apply {
            val file = rootProject.file("keystore.properties")
            if (file.exists()) file.inputStream().use { load(it) }
        }
        val storePw: String? = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
        val keyPw: String? = System.getenv("KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword")
        val keyName: String? = System.getenv("KEY_ALIAS") ?: keystoreProps.getProperty("keyAlias")
        val storePath = System.getenv("KEYSTORE_FILE") ?: keystoreProps.getProperty("storeFile") ?: "release.keystore"
        val releaseSigning: com.android.build.api.dsl.ApkSigningConfig = create("release")
        releaseSigning.storeFile = rootProject.file(storePath)
        storePw?.let { releaseSigning.storePassword = it }
        keyPw?.let { releaseSigning.keyPassword = it }
        keyName?.let { releaseSigning.keyAlias = it }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
