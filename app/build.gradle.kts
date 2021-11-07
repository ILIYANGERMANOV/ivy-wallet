import com.ivy.wallet.buildsrc.Libs

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.android")
    id("dagger.hilt.android.plugin")
}

android {
    compileSdk = Libs.Project.compileSdkVersion
    buildToolsVersion = Libs.Project.buildToolsVersion

    defaultConfig {
        applicationId = Libs.Project.applicationId
        minSdk = Libs.Project.minSdk
        targetSdk = Libs.Project.targetSdk
        versionCode = Libs.Project.versionCode
        versionName = Libs.Project.versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.jks")
            storePassword = "IVY7834!DEbug"
            keyAlias = "debug"
            keyPassword = "IVY7834!DEbug"
        }

        create("release") {
//             val tmpFilePath = System.getProperty("user.home") + "/work/_temp/keystore/"
//             val allFilesFromDir = File(tmpFilePath).listFiles()

//             if (allFilesFromDir != null) {
//                 val keystoreFile = allFilesFromDir.first()
//                 keystoreFile.renameTo(File("keystore/release.jks"))
//             }

            storeFile = file("../sign.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            isDefault = false

            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            resValue("string", "app_name", "Ivy Wallet")
        }

        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isDefault = true

            signingConfig = signingConfigs.getByName("debug")

            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Ivy Wallet Debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Libs.AndroidX.Compose.version
    }

    lint {
        isCheckReleaseBuilds = true
        isAbortOnError = false
    }
}

dependencies {
    //Core
    implementation(Libs.Kotlin.stdlib)
    implementation(Libs.Coroutines.android)
    implementation(Libs.AndroidX.coreKtx)
    implementation(Libs.AndroidX.appcompat)
    implementation(Libs.AndroidX.workRuntime)
    implementation(Libs.AndroidX.constraintLayout)

    //Make Life easy
    implementation(Libs.ThirdParty.timber)
    implementation(Libs.ThirdParty.eventBus)
    implementation(Libs.ThirdParty.kval)

    //Coroutines
    implementation(Libs.Coroutines.core)
    implementation(Libs.Coroutines.android)
    implementation(Libs.Coroutines.playServices)

    //Compose
    implementation(Libs.AndroidX.Compose.foundation)
    implementation(Libs.AndroidX.Compose.activity)
    implementation(Libs.AndroidX.Compose.material)
    implementation(Libs.AndroidX.Compose.materialIconsExtended)
    implementation(Libs.AndroidX.Compose.tooling)
    implementation(Libs.AndroidX.Compose.ui)
    implementation(Libs.AndroidX.Compose.animation)
    implementation(Libs.AndroidX.Compose.livedata)
    implementation(Libs.AndroidX.Compose.viewmodel)

    // Architecture Lifecycle Components
    implementation(Libs.AndroidX.Lifecycle.livedata)
    implementation(Libs.AndroidX.Lifecycle.viewmodel)
    implementation(Libs.AndroidX.Lifecycle.savedState)
    implementation(Libs.AndroidX.Lifecycle.lifecycleScope)
    kapt(Libs.AndroidX.Lifecycle.annotationProcessor)

    //Reorder
    implementation(Libs.AndroidX.Reorder.recyclerView)

    //Window Insets + Image Loading
    implementation(Libs.Google.Accompanist.insets)
    implementation(Libs.Google.Accompanist.coil)

    //Hilt DI
    implementation(Libs.Hilt.hiltAndroid)
    kapt(Libs.Hilt.hiltDaggerCompiler)
    kapt(Libs.Hilt.hiltCompiler)
    implementation(Libs.Hilt.hiltViewmodel)
    implementation(Libs.Hilt.hiltWorkManager)

    //Room DB (SQLite local persistence)
    implementation(Libs.Room.roomRuntime)
    kapt(Libs.Room.roomCompiler)
    implementation(Libs.Room.roomKtx)

    //Network
    implementation(Libs.Network.retrofit)
    implementation(Libs.Network.retrofitGsonConverter)
    implementation(Libs.Network.gson)
    implementation(Libs.Network.okhttpLoggingInterceptor)

    //Google Play Services
    implementation(Libs.Google.billing)

    //In-App Reviews
    implementation(Libs.Google.playCore)
    implementation(Libs.Google.playCoreKtx)

    //Firebase & Google
    implementation(Libs.Google.Firebase.analytics)
    implementation(Libs.Google.Firebase.crashlytics)
    implementation(Libs.Google.Firebase.messaging)
    implementation(Libs.Google.googleAuth)

    //Biometrics (Fingerprint + FaceID)
    implementation(Libs.AndroidX.biometrics)

    //UI
    implementation(Libs.ThirdPartyUI.lottie)
    implementation(Libs.ThirdPartyUI.markdownText)

    //Java
    implementation(Libs.Java.openCSV)
    implementation(Libs.Java.escapeCSVString)

    //Testing
    testImplementation(Libs.Testing.junit)
    androidTestImplementation(Libs.Testing.junit)
    androidTestImplementation(Libs.Testing.junitExt)
    androidTestImplementation(Libs.Testing.hilt)
    kaptAndroidTest(Libs.Testing.hiltCompiler)
    androidTestImplementation(Libs.Testing.work)
    testImplementation(Libs.Testing.assertK)
    androidTestImplementation(Libs.Testing.assertK)
//    testImplementation(Libs.Testing.mockk)
//    androidTestImplementation(Libs.Testing.mockkAndroid)
    testImplementation(Libs.Testing.architectureComponents)
    androidTestImplementation(Libs.Testing.espresso)
}
