import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

@DisableCachingByDefault(because = "Validation-only task has no outputs.")
abstract class ValidateStartIoReleaseConfigurationTask : DefaultTask() {
    @get:Input
    abstract val appId: Property<String>

    @TaskAction
    fun validate() {
        // Official GMS release builds inject START_IO_APP_ID from a secret. Forks and CI that lack
        // that proprietary secret must still be able to assemble release APKs, so a blank value is a
        // warning rather than a hard failure. The Start.io identifier is not consumed by the app at
        // runtime, so building without it is safe (ads are simply not configured).
        if (appId.get().isBlank()) {
            logger.warn(
                "START_IO_APP_ID is not set; building the GMS release without a Start.io identifier.",
            )
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutlibraries.android)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

// Base version. Bump these manually for a big release (e.g. 13.7.x -> 14.0.x). CI derives the
// per-commit patch/versionCode from the git commit count and injects them via the
// VERSION_NAME_OVERRIDE / VERSION_CODE_OVERRIDE env vars. Keep each on a single line so the
// release/canary workflows can grep the base value reliably.
val baseVersionName = "14.0.0"
val baseVersionCode = 1400

val discordApplicationId =
    (
        localProperties.getProperty("DISCORD_APPLICATION_ID")
            ?: System.getenv("DISCORD_APPLICATION_ID")
            ?: "1165706613961789445"
        ).trim()
val discordApplicationIdLong = discordApplicationId.toLongOrNull() ?: 1165706613961789445L
val discordRedirectScheme = "discord-$discordApplicationId"
val releaseKeystoreFile = file("keystore/release.keystore")
val releaseStorePassword =
    System.getenv("STORE_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val releaseKeyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val releaseKeyPassword = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val hasReleaseSigningConfig =
    releaseKeystoreFile.isFile &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
// Start.io (StartApp) app identifier used to initialize the support-ads SDK in GMS builds.
// The fork ships a default committed ID so ads work out of the box; an override can still be
// supplied via local.properties or the START_IO_APP_ID environment variable (e.g. in CI).
val startIoAppId =
    (
        localProperties.getProperty("START_IO_APP_ID")
            ?: System.getenv("START_IO_APP_ID")
            ?: "206779743"
        ).trim()
tasks.register<ValidateStartIoReleaseConfigurationTask>("validateStartIoReleaseConfiguration") {
    group = "verification"
    description = "Validates the production Start.io identifier for GMS release artifacts."
    appId.set(startIoAppId)
}

tasks.configureEach {
    val isGmsReleaseArtifactTask =
        (name.startsWith("assemble") || name.startsWith("bundle")) &&
            name.contains("Gms") &&
            name.endsWith("Release")
    if (isGmsReleaseArtifactTask) {
        dependsOn("validateStartIoReleaseConfiguration")
    }
}

// Debug builds use the standard AGP debug keystore (auto-generated locally, never committed).
// Release builds require a private keystore at app/keystore/release.keystore plus the
// KEYSTORE / KEY_ALIAS / KEYSTORE_PASSWORD / KEY_PASSWORD secrets. The previously
// committed debug keystore was removed from the tree (same key now lives only in
// GitHub Secrets) and must not be restored.

android {
    namespace = "moe.rukamori.archivetune"
    compileSdk = 37

    defaultConfig {
    applicationId = "moe.rukamori.archivetune"
        minSdk = 26
        targetSdk = 37
        // Version. Locally the committed base values are used. In CI, the release/canary
        // workflows override them via VERSION_CODE_OVERRIDE / VERSION_NAME_OVERRIDE (derived from
        // the git commit count) so every push produces a strictly-newer, installable build
        // without committing a bump back to this file.
        versionCode = System.getenv("VERSION_CODE_OVERRIDE")?.trim()?.toIntOrNull() ?: baseVersionCode
        versionName =
            System.getenv("VERSION_NAME_OVERRIDE")?.trim()?.takeIf { it.isNotEmpty() } ?: baseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        val lastfmApiKey =
            localProperties.getProperty("LASTFM_API_KEY")
                ?: System.getenv("LASTFM_API_KEY")
                ?: ""
        val lastfmSecret =
            localProperties.getProperty("LASTFM_SECRET")
                ?: System.getenv("LASTFM_SECRET")
                ?: ""
        buildConfigField("String", "LASTFM_API_KEY", "\"$lastfmApiKey\"")
        buildConfigField("String", "LASTFM_SECRET", "\"$lastfmSecret\"")

        // Telegram (TDLib) app credentials. Baked in at build time so users sign in with just
        // their phone number + login code — no my.telegram.org api_id/api_hash entry. Override via
        // local.properties or the TELEGRAM_API_ID / TELEGRAM_API_HASH env vars (e.g. in CI) to ship
        // the fork's own registered app. The fallback is the public Telegram Desktop api_id/hash,
        // which every TDLib client can use out of the box.
        val telegramApiId =
            (
                localProperties.getProperty("TELEGRAM_API_ID")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("TELEGRAM_API_ID")?.takeIf { it.isNotBlank() }
                    ?: "2040"
            ).trim()
        val telegramApiHash =
            (
                localProperties.getProperty("TELEGRAM_API_HASH")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("TELEGRAM_API_HASH")?.takeIf { it.isNotBlank() }
                    ?: "b18441a1ff607e10a989891a5462e627"
            ).trim()
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")

        // TDLib's libtdjni.so is 21.7 MB per ABI — 8.7 MB compressed into the APK and the full
        // 21.7 MB extracted on install (useLegacyPackaging below) — paid by every user, for an
        // optional integration most never sign in to. `-PslimTdlib=true` leaves it out and the app
        // fetches it the first time someone opens Telegram; see TdLibNativeLibrary.
        //
        // DEFAULTS TO BUNDLED. Do not flip this until the per-ABI libraries are actually published
        // at TDLIB_NATIVE_BASE_URL, or Telegram breaks for everyone on the slim build. The naming
        // the loader expects is libtdjni-<version>-<abi>.so, and `./gradlew extractTdLibNatives`
        // writes exactly those files for uploading.
        val slimTdlib = (project.findProperty("slimTdlib") as String?)?.toBoolean() ?: false
        buildConfigField("boolean", "TDLIB_BUNDLED", "${!slimTdlib}")
        buildConfigField(
            "String",
            "TDLIB_NATIVE_BASE_URL",
            "\"${project.findProperty("tdlibNativeBaseUrl") as String?
                ?: "https://github.com/vossgraves/ArchiveTune/releases/download/tdlib-1.8.56"}\"",
        )

        // Base URL of the community Source Pool website (Next.js). When set, the app auto-discovers
        // health-checked Tidal/Qobuz instances from it. Precedence: local.properties override, then
        // the SOURCE_PROVIDER_URL env/CI variable, then the baked-in default below. Set to "" in
        // local.properties to disable remote discovery for a build.
        // Use .takeIf { it.isNotBlank() } on each source so an explicitly-empty env var or
        // local.properties entry doesn't shadow the hardcoded fallback (a plain `?: fallback`
        // chain would leave the URL blank when the env var is set to "" rather than unset).
        val sourceProviderUrl =
            (
                localProperties.getProperty("SOURCE_PROVIDER_URL")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("SOURCE_PROVIDER_URL")?.takeIf { it.isNotBlank() }
                    ?: "https://archivepool.vercel.app"
                ).trim().trimEnd('/')
        buildConfigField("String", "SOURCE_PROVIDER_URL", "\"$sourceProviderUrl\"")

        // Per-app read key for the Source Pool. Sent as a Bearer token on discovery requests so the
        // pool can gate access. Optional: blank works fine while the pool runs unenforced.
        val sourceProviderKey =
            (
                localProperties.getProperty("SOURCE_PROVIDER_KEY")
                    ?: System.getenv("SOURCE_PROVIDER_KEY")
                    ?: ""
                ).trim()
        buildConfigField("String", "SOURCE_PROVIDER_KEY", "\"$sourceProviderKey\"")

        // LEGACY end-to-end decryption key for sensitive Source Pool credentials (base64 32-byte
        // AES-256 key, matching the site's POOL_CLIENT_KEY). Current builds request the v2 feed
        // protocol (X-Pool-Client: v2), where the encryption key is derived from the read key
        // above and this value is only a fallback for older pool deployments. Optional: blank is
        // fine when every configured pool speaks v2.
        val poolClientKey =
            (
                localProperties.getProperty("POOL_CLIENT_KEY")
                    ?: System.getenv("POOL_CLIENT_KEY")
                    ?: ""
                ).trim()
        buildConfigField("String", "POOL_CLIENT_KEY", "\"$poolClientKey\"")

        val nightlyBuildHash =
            (
                localProperties.getProperty("NIGHTLY_BUILD_HASH")
                    ?: System.getenv("NIGHTLY_BUILD_HASH")
                    ?: ""
                ).trim()
        buildConfigField("String", "NIGHTLY_BUILD_HASH", "\"$nightlyBuildHash\"")
        // True only for builds produced by the canary/nightly workflow (it sets IS_NIGHTLY_BUILD).
        // Used to default the in-app updater to the CANARY channel and to compare canary builds by
        // their monotonic versionCode rather than the fixed display versionName.
        val isNightlyBuild =
            (System.getenv("IS_NIGHTLY_BUILD") ?: localProperties.getProperty("IS_NIGHTLY_BUILD") ?: "")
                .trim()
                .equals("true", ignoreCase = true)
        buildConfigField("boolean", "IS_NIGHTLY", "$isNightlyBuild")
        buildConfigField("String", "DISTRIBUTION", "\"gms\"")
        buildConfigField("boolean", "UPDATER_AVAILABLE", "true")
    }

    flavorDimensions += listOf("distribution", "device", "abi")
    productFlavors {
        create("gms") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("String", "DISTRIBUTION", "\"gms\"")
            buildConfigField("boolean", "UPDATER_AVAILABLE", "true")
            buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordApplicationId\"")
            buildConfigField("long", "DISCORD_APPLICATION_ID_LONG", "${discordApplicationIdLong}L")
            buildConfigField("String", "DISCORD_REDIRECT_SCHEME", "\"$discordRedirectScheme\"")
            manifestPlaceholders["discordRedirectScheme"] = discordRedirectScheme
        }
        create("foss") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"foss\"")
            buildConfigField("boolean", "UPDATER_AVAILABLE", "true")
            buildConfigField("String", "DISCORD_APPLICATION_ID", "\"$discordApplicationId\"")
            buildConfigField("long", "DISCORD_APPLICATION_ID_LONG", "${discordApplicationIdLong}L")
            buildConfigField("String", "DISCORD_REDIRECT_SCHEME", "\"$discordRedirectScheme\"")
            manifestPlaceholders["discordRedirectScheme"] = discordRedirectScheme
        }
        create("mobile") {
            dimension = "device"
            buildConfigField("String", "DEVICE", "\"mobile\"")
        }
        create("tv") {
            dimension = "device"
            buildConfigField("String", "DEVICE", "\"tv\"")
        }
        create("universal") {
            dimension = "abi"
            // Keep the universal APK lean: TDLib's libtdjni.so dominates per-ABI
            // size, so packaging only the two 64-bit ABIs halves the download.
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
            buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        }
        create("arm64") {
            dimension = "abi"
            ndk { abiFilters += "arm64-v8a" }
            buildConfigField("String", "ARCHITECTURE", "\"arm64\"")
        }
        create("armeabi") {
            dimension = "abi"
            ndk { abiFilters += "armeabi-v7a" }
            buildConfigField("String", "ARCHITECTURE", "\"armeabi\"")
        }
        create("x86") {
            dimension = "abi"
            ndk { abiFilters += "x86" }
            buildConfigField("String", "ARCHITECTURE", "\"x86\"")
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += "x86_64" }
            buildConfigField("String", "ARCHITECTURE", "\"x86_64\"")
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
        // Debug uses AGP's default debug keystore; no committed keystore.
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = false
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        prefab = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            // Compress native libs inside the APK and extract only the device's ABI at install.
            // TDLib ships ~87 MiB of libtdjni.so across four ABIs; stored uncompressed that alone
            // made the universal APK ~142 MiB. Compressed packaging cuts the universal APK by
            // ~51 MiB (and each per-ABI APK by ~12 MiB) at the cost of a slightly slower install.
            useLegacyPackaging = true
            // Slim build: the loader fetches it on first Telegram use instead. Excluded here
            // rather than by swapping the dependency, so TdApi and the Client class — which the
            // app compiles against and which survives a missing native library, catching the
            // UnsatisfiedLinkError in its static initialiser — still ship.
            if ((project.findProperty("slimTdlib") as String?)?.toBoolean() == true) {
                excludes += "**/libtdjni.so"
            }
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            // Installed on demand from Lyrics settings; saves roughly 13 MiB per APK.
            excludes += "com/atilika/kuromoji/ipadic/*.bin"
            // Additional safe META-INF / metadata excludes — none of these are read at runtime by
            // the app or any of its libraries (verified by checking for ServiceLoader / reflection
            // usage on each). They are pure build-time / IDE metadata and just bloat every APK.
            // - META-INF/DEPENDENCIES: Maven dependency manifest, only used by build tooling.
            // - META-INF/INDEX.LIST: JAR index used by desktop ClassLoaders, never by Android.
            // - META-INF/io.netty.versions.properties: Netty version manifest, runtime-irrelevant.
            // - META-INF/*.version: per-library version files (e.g. kotlin-stdlib.version).
            // - DebugProbesKt.bin: kotlinx.coroutines debug binary, only consulted by debugger.
            // - kotlin-tooling-metadata.json: Kotlin tooling manifest, build-time only.
            // - META-INF/buildinfo.properties / build.archives: Gradle/AGP build metadata.
            // - META-INF/com.android.tools/**: AGP build-metadata, not consumed at runtime.
            // - META-INF/proguard/**: bundled proguard configs, only needed at minify time.
            // DO NOT add: META-INF/services/** (ServiceLoader), META-INF/MANIFEST.MF (signing +
            // attributes), META-INF/*.kotlin_module (Kotlin reflection) — those are load-bearing.
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/*.version"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
            excludes += "META-INF/buildinfo.properties"
            excludes += "META-INF/build.archives"
            excludes += "META-INF/com.android.tools/**"
            excludes += "META-INF/proguard/**"
        }
    }

}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)
    implementation(libs.work.runtime)
    implementation("androidx.browser:browser:1.10.0")

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    compileOnly("androidx.compose.ui:ui-tooling-preview:${libs.versions.compose.get()}")
    debugImplementation("androidx.compose.ui:ui-tooling-preview:${libs.versions.compose.get()}")
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.reorderable)
    implementation(libs.haze)
    implementation(libs.haze.materials)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.material3)
    implementation(libs.androidx.graphics.shapes)
    
    implementation(libs.palette)
    implementation(libs.androidsvg)
    implementation(libs.aboutlibraries.core)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.html)
    implementation(libs.markwon.image)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.simple.ext)

    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    implementation(libs.shimmer)

    // Glance Widget support
    implementation("androidx.glance:glance:1.1.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    implementation(libs.media3)
    implementation("androidx.media3:media3-exoplayer-hls:${libs.versions.media3.get()}")
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation("androidx.media3:media3-ui:${libs.versions.media3.get()}")
    implementation("androidx.media3:media3-ui-compose:${libs.versions.media3.get()}")
    add("gmsImplementation", libs.media3.cast)
    add("gmsImplementation", libs.mediarouter)
    implementation(libs.squigglyslider)

    // Prebuilt TDLib (Telegram MTProto client) with bundled JNI natives for all ABIs.
    // Powers the Telegram channel lossless-streaming integration (telegram/ package).
    implementation("com.github.tdlibx:td:1.8.56")


    implementation(libs.room.runtime)
    implementation(libs.kuromoji.ipadic)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    // Liquid glass / backdrop blur effect for the SimpMusic-style floating
    // header pills on album / artist / playlist screens.
    implementation(libs.liquid.glass)

    implementation(libs.hilt)
    implementation(libs.re2j)
    annotationProcessor(libs.kotlin.metadata.jvm)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)

    implementation(project(":core"))
    implementation(project(":lyrics:kugou"))
    implementation(project(":lyrics:lrclib"))
    implementation(project(":lyrics:simpmusic"))
    implementation(project(":lyrics:paxsenix"))
    implementation(project(":lyrics:betterlyrics"))
    implementation(project(":lyrics:unison"))
    implementation(project(":lyrics:youlyplus"))
    implementation(project(":musixmatch"))
    implementation(project(":lastfm"))
    implementation(project(":canvas"))
    implementation(project(":shazamkit"))
    implementation(project(":spotifycore"))
    implementation(project(":morideobfuscator"))
    implementation(project(":jiosaavn"))
    implementation("com.materialkolor:material-kolor:5.0.0-alpha07")

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)
    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation("org.mockito:mockito-core:5.20.0")
    implementation(libs.translator)
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0-rc01")
    implementation(libs.accompanist.lyrics.ui)
    implementation(libs.accompanist.lyrics.core)

    implementation("org.json:json:20240303")

    // PRDownloader — lightweight (~45 KB) file download library with
    // pause/resume, retry, and progress callbacks. Used as the HTTP
    // fetcher inside PRDownloaderDataSource (a Media3 DataSource wrapper).
    // Replaces Ketch — Ketch's WorkManager + Flow observation added
    // overhead without improving throughput, and its temp-file lifecycle
    // occasionally left partial files that corrupted subsequent exports.
    // PRDownloader is simpler (single OkHttp call per download, callback
    // API instead of Flow), which makes the temp-file lifecycle easier
    // to reason about.
    implementation(libs.prdownloader)

    // jaudiotagger — pure-Java audio metadata tagger (ID3v2 / Vorbis Comments
    // / MP4 / FLAC). Used by AudioTagger to write title / artist / album /
    // year / track-number / artwork tags onto exported downloaded songs so
    // they show up correctly in external music players. Pinned to 1.4.x
    // (Java 21 bytecode) — the 2.x line targets Java 25 which Android cannot
    // consume.
    implementation("com.github.RouHim:jaudiotagger:1.4.31")
    // SLF4J binding required by jaudiotagger at runtime — jaudiotagger
    // depends on slf4j-api but does not bundle a binding. slf4j-jdk14
    // routes log calls through java.util.logging (which Android forwards
    // to logcat). Without this, jaudiotagger logs a single "no SLF4J
    // providers found" warning at startup and silently no-ops logging.
    implementation("org.slf4j:slf4j-jdk14:2.0.17")

    // QuickJS + BouncyCastle for yt-dlp stream resolution (signature
    // verification of yt-dlp releases + JS challenge evaluation).
    implementation(libs.quickjs.kt)
    implementation(libs.bcpg)
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val capitalizedVariantName =
            variant.name.replaceFirstChar { character ->
                if (character.isLowerCase()) character.titlecase() else character.toString()
            }
        val generateIconPack =
            tasks.register<GenerateIconPackTask>("generate${capitalizedVariantName}IconPack") {
                metadataFile.set(rootProject.layout.projectDirectory.file("IconPack/metadata.json"))
                svgDirectory.set(rootProject.layout.projectDirectory.dir("IconPack/svg"))
                applicationId.set(variant.applicationId)
                targetActivityClassName.set("moe.rukamori.archivetune.MainActivity")
                resourceOutputDirectory.set(
                    layout.buildDirectory.dir("generated/iconPack/${variant.name}/res"),
                )
                assetOutputDirectory.set(
                    layout.buildDirectory.dir("generated/iconPack/${variant.name}/assets"),
                )
                manifestOutputFile.set(
                    layout.buildDirectory.file(
                        "generated/iconPack/${variant.name}/AndroidManifest.xml",
                    ),
                )
            }

        variant.sources.res?.addGeneratedSourceDirectory(
            generateIconPack,
            GenerateIconPackTask::resourceOutputDirectory,
        )
        variant.sources.assets?.addGeneratedSourceDirectory(
            generateIconPack,
            GenerateIconPackTask::assetOutputDirectory,
        )
        variant.sources.manifests.addGeneratedManifestFile(
            generateIconPack,
            GenerateIconPackTask::manifestOutputFile,
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=OptimizeNonSkippingGroups",
        )
        suppressWarnings.set(true)
    }
}

configurations.configureEach {
    resolutionStrategy.force(
        "androidx.compose.runtime:runtime:${libs.versions.compose.get()}",
        "androidx.compose.foundation:foundation:${libs.versions.compose.get()}",
        "androidx.compose.ui:ui:${libs.versions.compose.get()}",
        "androidx.compose.ui:ui-util:${libs.versions.compose.get()}",
        "androidx.compose.ui:ui-tooling:${libs.versions.compose.get()}",
        "androidx.compose.animation:animation-graphics:${libs.versions.compose.get()}",
        "org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlinMetadata.get()}",
    )
}

/**
 * Extracts TDLib's per-ABI native libraries from the resolved `td` AAR, named the way
 * `TdLibNativeLibrary` asks for them, ready to attach to a GitHub release.
 *
 * Only needed to publish the assets a `-PslimTdlib=true` build downloads. Run it once per TDLib
 * version bump, upload the four files to the tag named in TDLIB_NATIVE_BASE_URL, and update the
 * digests in TdLibNativeLibrary — the task prints them.
 */
tasks.register("extractTdLibNatives") {
    group = "distribution"
    description = "Extract libtdjni.so per ABI from the td AAR for publishing as release assets."

    val outputDir = layout.buildDirectory.dir("tdlib-natives")
    val aars =
        configurations
            .detachedConfiguration(dependencies.create("com.github.tdlibx:td:1.8.56@aar"))
            .also { it.isTransitive = false }

    outputs.dir(outputDir)
    doLast {
        val aar = aars.singleFile
        val destination = outputDir.get().asFile
        destination.deleteRecursively()
        destination.mkdirs()
        val version = "1.8.56"
        ZipFile(aar).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("jni/") && it.name.endsWith("/libtdjni.so") }
                .forEach { entry ->
                    val abi = entry.name.removePrefix("jni/").substringBefore('/')
                    val target = File(destination, "libtdjni-$version-$abi.so")
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    val digest =
                        MessageDigest
                            .getInstance("SHA-256")
                            .digest(target.readBytes())
                            .joinToString("") { "%02x".format(it) }
                    logger.lifecycle("$abi  $digest  ${target.name}")
                }
        }
        logger.lifecycle("Wrote to $destination")
    }
}
