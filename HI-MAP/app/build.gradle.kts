import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// local.properties 에 TMAP_APP_KEY=... 를 적으면 TMAP 경로 API를 쓴다. 없으면 건너뛴다.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.mydrawingmap.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mydrawingmap.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "TMAP_APP_KEY", "\"${localProps.getProperty("TMAP_APP_KEY", "")}\"")
    }

    // demo  : Pleos SDK 없이 어디서나 빌드·실행되는 시연용
    // pleos : Pleos Connect SDK(NaviHelper·FusedLocation·TextToSpeech)를 실제로 부르는 빌드
    flavorDimensions += "sdk"
    productFlavors {
        create("demo") {
            dimension = "sdk"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
        create("pleos") {
            dimension = "sdk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 공모전 제출용: 디버그 키로 서명해 바로 설치되게 한다. 출시 전에는 전용 키로 바꾼다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }

    // 주행 제한 상태(CarUxRestrictions)를 읽는 데 쓰는 차량용 라이브러리
    useLibrary("android.car")

    lint {
        // 제출용 release 빌드가 정적 검사에서 멈추지 않게 한다. 검사는 ./gradlew lint 로 따로 돌린다.
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.maplibre.gl:android-sdk:11.8.2")

    add("pleosImplementation", "ai.pleos.playground:NaviHelper:3.0.5")
    add("pleosImplementation", "ai.pleos.playground:FusedLocation:1.1.3")
    add("pleosImplementation", "ai.pleos.playground:TextToSpeech:3.3.1")

    testImplementation("junit:junit:4.13.2")
}
