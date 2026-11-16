plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.hoddesk.app"
    compileSdk = 35
    useLibrary("android.test.runner")
    useLibrary("android.test.base")

    defaultConfig {
        applicationId = "com.hoddesk.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2022000
        versionName = "2.22.0"
        // Gemma 4 E2B instruction model (Apache 2.0, open download, 2,588,147,712 bytes), pinned to one
        // repository revision with its published LFS SHA-256. Override both properties to use another approved model.
        buildConfigField("String", "INSTRUCTION_MODEL_URL", "\"${providers.gradleProperty("hoddeskInstructionModelUrl").orElse("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/2d74ac41a33d7da82f39f7d5720a04c68514a103/gemma-4-E2B-it.litertlm?download=true").get()}\"")
        buildConfigField("String", "INSTRUCTION_MODEL_SHA256", "\"${providers.gradleProperty("hoddeskInstructionModelSha256").orElse("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c").get()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Published LFS content digest for embeddinggemma-2-740m.litertlm (484622336 bytes).
        buildConfigField("String", "EMBEDDING_MODEL_SHA256", "\"${providers.gradleProperty("hoddeskEmbeddingModelSha256").orElse("e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c").get()}\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // LiteRT-LM's published AAR is built with a newer Kotlin metadata
        // version; its Java-facing MediaPipe API remains binary compatible.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("net.zetetic:android-database-sqlcipher:4.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.rmtheis:tess-two:9.1.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // UniversalEmbedder wraps the official LiteRT-LM EmbeddingEngine and
    // provides the tokenizer/tensor contract for EmbeddingGemma 2.
    implementation("com.google.mediapipe:tasks-retrieval:1.1.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    implementation("com.alphacephei:vosk-android:0.3.75")
    // Multilingual Whisper model for Tamil-first offline transcription.
    implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
