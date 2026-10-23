plugins {
    alias(libs.plugins.android.application)
    // AGP 9 has built-in Kotlin support, so org.jetbrains.kotlin.android is not applied.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * v0.10: the app's display name, in ONE place. Everything user-visible that names the app
 * (launcher label, share target "Add to …", widget picker text, backup file text) is generated
 * from it below, so a rename is this one line + a rebuild.
 */
val appName = "Todo"

android {
    namespace = "dev.saikrishna.todo"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.saikrishna.todo"
        minSdk = 33
        targetSdk = 37
        versionCode = 21
        versionName = "0.12.0"

        resValue("string", "app_name", appName)
        resValue("string", "capture_label", "Add to $appName")
        resValue("string", "tile_add_task", "Add to $appName")
        resValue("string", "widget_error", "Couldn\\'t load your tasks. Open $appName, then remove and re-add the widget if this stays.")
        resValue("string", "widget_load_failed", "Couldn\\'t load tasks · tap to open $appName")
        buildConfigField("String", "APP_NAME", "\"$appName\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric needs merged resources/assets (fonts) for the screenshot tests.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            // v0.10: hold the rotating composer hint still in frame-comparison tests.
            it.systemProperty("todo.static.hints", "true")
            it.systemProperty("shots.dir", rootProject.layout.projectDirectory.dir("out/shots").asFile.absolutePath)
            it.maxHeapSize = "1536m"
            // Robolectric on JDK 21 needs these for SDK 36's FileDescriptor interceptor.
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            )
            // Screenshot tests are opt-in: ./gradlew testDebugUnitTest -Pshots
            if (!project.hasProperty("shots")) it.exclude("**/*ScreenshotTest*")
        }
    }
}

composeCompiler {
    // Stability reports: ./gradlew assembleRelease -> app/build/compose_compiler/
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    // Drag to reorder (Apache 2.0): LazyColumn + Column reordering with auto-scroll.
    implementation(libs.reorderable)
    // v0.8 home-screen widget (Apache 2.0).
    implementation(libs.glance.appwidget)
    // v0.8.1: Glance 1.2.0 pulls WorkManager 2.7.1, whose keep rule loses InputMerger
    // constructors under R8 full mode (the widget session never starts). Pin a current release.
    implementation(libs.work.runtime)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    // Screenshot tests (Robolectric native graphics + Roborazzi), run with -Pshots.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.glance.appwidget.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Copy the debug APK to a stable path: out/todo-debug.apk
val copyDebugApk = tasks.register<Copy>("copyDebugApk") {
    from(layout.buildDirectory.dir("outputs/apk/debug")) { include("app-debug.apk") }
    into(rootProject.layout.projectDirectory.dir("out"))
    rename { "todo-debug.apk" }
}
tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(copyDebugApk) }

// Copy the release APK (debug-signed for now) to out/todo-release.apk
val copyReleaseApk = tasks.register<Copy>("copyReleaseApk") {
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("app-release.apk") }
    into(rootProject.layout.projectDirectory.dir("out"))
    rename { "todo-release.apk" }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyReleaseApk) }

// v0.12: out/ is gitignored, and on Oct 23 2026 a workspace restore dropped every gitignored path
// (out/, build/, .gradle/) while keeping tracked files and ../*.apk. No Gradle task deletes
// out/ (the Copy tasks above only add files; `clean` only removes app/build), but mirror it to
// ../todo-artifacts/out after each test run / APK copy so shots and APKs have a second home.
// Copy (not Sync): the mirror never deletes anything.
val mirrorOut = tasks.register<Copy>("mirrorOut") {
    from(rootProject.layout.projectDirectory.dir("out"))
    into(File(rootProject.projectDir.parentFile, "todo-artifacts/out"))
}
tasks.matching { it.name in setOf("testDebugUnitTest", "copyDebugApk", "copyReleaseApk") }.configureEach { finalizedBy(mirrorOut) }
