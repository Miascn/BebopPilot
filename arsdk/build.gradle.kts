plugins {
    id("com.android.library")
}

android {
    namespace = "com.parrot.arsdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "src/generated/java")
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("javax.jmdns:jmdns:3.4.1")
    implementation("org.apache.sanselan:sanselan:0.97-incubator")
}
