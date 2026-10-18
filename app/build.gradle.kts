plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.ezsmb.app"
    compileSdk = 36
    defaultConfig { applicationId = "com.ezsmb.app"; minSdk = 29; targetSdk = 36; versionCode = 6; versionName = "0.3.1"; testInstrumentationRunner = "com.ezsmb.app.PreviewInstrumentation" }
    buildFeatures { compose = true }
    val releaseStore = System.getenv("EZSMB_KEYSTORE")
    if (!releaseStore.isNullOrBlank()) signingConfigs.create("release") {
        storeFile = file(releaseStore)
        storePassword = System.getenv("EZSMB_STORE_PASSWORD")
        keyAlias = System.getenv("EZSMB_KEY_ALIAS") ?: "ezsmb"
        keyPassword = System.getenv("EZSMB_KEY_PASSWORD")
    }
    buildTypes.getByName("release") {
        isDebuggable = false
        isMinifyEnabled = false
        if (!releaseStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        resources.merges += setOf("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt")
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.hierynomus:smbj:0.14.0")
    implementation("com.rapid7.client:dcerpc:0.12.13") { exclude(group = "com.google.guava", module = "guava") }
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("com.google.guava:guava:33.4.0-android")
    implementation("org.slf4j:slf4j-nop:2.0.17")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
}
tasks.withType<Test>().configureEach {
    maxHeapSize = "256m"
    minHeapSize = "32m"
    maxParallelForks = 1
    jvmArgs("-XX:+UseSerialGC")
}
