@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        // Google Cloud Storage mirror of Maven Central.
        //
        // Declared BEFORE mavenCentral()/gradlePluginPortal() because Gradle's
        // HTTP retry on 429 (Too Many Requests) does NOT fall back to the next
        // declared repository — it exhausts retries on the first repo and then
        // fails the build. Maven Central (repo.maven.apache.org) rate-limits
        // GitHub Actions runner IPs aggressively, causing transient 429 build
        // failures (e.g. when resolving transitive deps like org.jsoup:jsoup
        // pulled in by MetrolistExtractor).
        //
        // The GCS mirror (maven-central.storage-download.googleapis.com) is
        // operated by Google Cloud, mirrors all of Maven Central, is NOT
        // rate-limited like Maven Central, AND — critically — returns proper
        // HTTP 404 for missing artifacts (unlike Aliyun's
        // maven.aliyun.com/repository/public which returns HTTP 502 Bad
        // Gateway for artifacts it hasn't mirrored, which Gradle treats as a
        // fatal error and stops the resolution chain). GCS was verified to
        // serve both jsoup 1.15.3 and KSP 2.3.10 plugin marker + actual
        // artifacts.
        //
        // mavenCentral()/gradlePluginPortal() remain as fallbacks for any
        // artifact GCS hasn't mirrored yet (GCS mirrors Maven Central only,
        // not Google Maven or Gradle Plugin Portal — those still need their
        // own `google()` / `gradlePluginPortal()` entries, which are kept).
        maven {
            name = "GcsCentral"
            setUrl("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        // GCS mirror of Maven Central — declared BEFORE mavenCentral() for
        // the same reason as in pluginManagement (see comment above). Maven
        // Central's 429 rate-limiting on GitHub Actions IPs was causing
        // dependency-resolution failures for transitive deps (jsoup,
        // rhino, ...) pulled in by MetrolistExtractor.
        maven {
            name = "GcsCentral"
            setUrl("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral {
            mavenContent {
                releasesOnly()
            }
        }
        exclusiveContent {
            forRepository {
                maven {
                    name = "JitPack"
                    setUrl("https://jitpack.io")
                }
            }
            filter {
                includeGroup("com.github.therealbush")
                includeGroup("com.github.TeamNewPipe")
                // Prebuilt TDLib (Telegram Database Library) AAR with bundled JNI natives,
                // used by the Telegram channel streaming integration.
                includeGroup("com.github.tdlibx")
                // PRDownloader — lightweight (~45 KB) file download library with
                // pause/resume, retry, and progress callbacks. Used as the HTTP
                // fetcher inside PRDownloaderDataSource (Media3 DataSource wrapper).
                includeGroup("com.github.amitshekhariitbhu")
                // jaudiotagger — pure-Java audio metadata tagger (ID3v2/Vorbis/MP4/FLAC).
                // Used by AudioTagger to write title/artist/album/year/artwork tags onto
                // exported downloaded songs.
                includeGroup("com.github.RouHim")
                // MetrolistExtractor — maintained fork of NewPipeExtractor used by :core
                // for YouTube stream resolution (incl. captions + signature decryption).
                // Same `org.schabi.newpipe.extractor` package namespace as upstream.
                // Note: MetrolistExtractor is a multi-module Gradle project on JitPack,
                // so its sub-modules (extractor, timeago-parser, ...) are published under
                // the `com.github.MetrolistGroup.MetrolistExtractor` group — that group
                // must also be allow-listed here, otherwise the parent POM resolves but
                // every sub-module artifact fails to download.
                includeGroup("com.github.MetrolistGroup")
                includeGroup("com.github.MetrolistGroup.MetrolistExtractor")
                // kyant-backdrop — liquid glass / backdrop blur effect library
                // (io.github.kyant0:backdrop) used by the SimpMusic-style
                // floating header pills on album / artist / playlist screens.
                // Published to JitPack under the io.github.kyant0 group.
                includeGroup("io.github.kyant0")
            }
        }
    }
}

// F-Droid doesn't support foojay-resolver plugin
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

rootProject.name = "ArchiveTune"
include(":app")
include(":core")
include(":lyrics:kugou")
include(":lyrics:lrclib")
include(":lyrics:simpmusic")
include(":lyrics:paxsenix")
include(":lyrics:betterlyrics")
include(":lyrics:unison")
include(":lyrics:youlyplus")
include(":musixmatch")
include(":lastfm")
include(":canvas")
include(":shazamkit")
include(":spotifycore")
include(":moriextractor")
include(":morideobfuscator")

// Use a local copy of MetrolistExtractor by uncommenting the lines below.
// We assume, that ArchiveTune and MetrolistExtractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in core/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.metrolist.extractor)
// To:
//      implementation("com.github.MetrolistGroup:MetrolistExtractor")
// includeBuild("../MetrolistExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.MetrolistGroup:MetrolistExtractor")).using(project(":extractor"))
//    }
// }
