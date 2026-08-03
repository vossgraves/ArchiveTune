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
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
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
