import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "hr.smartbudget.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "hr.smartbudget.app"
        minSdk = 26
        targetSdk = 37
        // Verzija: +0.1 za novu funkcionalnost ili veći ispravak (ažuriranje je tada obavezno),
        // +0.001 za manju korekciju (ažuriranje se može odbiti). Vidi data/Azuriranja.kt.
        versionName = "3.400"
        // Iz naziva, tako da svaka nova verzija raste i za Android (1.000 → 1000, 1.101 → 1101).
        versionCode = versionName!!.split('.').let { (a, b) -> a.toInt() * 1000 + b.toInt() }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release ključ: putanja i lozinke u keystore.properties (u korijenu projekta, nije na gitu).
    // Svako izdanje mora biti potpisano istim ključem, inače se ažuriranje ne može instalirati.
    val kljuc = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (kljuc != null) create("release") {
            storeFile = file(kljuc.getProperty("storeFile"))
            storePassword = kljuc.getProperty("storePassword")
            keyAlias = kljuc.getProperty("keyAlias")
            keyPassword = kljuc.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            if (kljuc != null) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    // org.json za testove parsiranja tečajeva (na uređaju je dio Androida)
    testImplementation("org.json:json:20240303")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
