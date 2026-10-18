pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
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
        mavenCentral()
        // sherpa-onnx is not on Maven Central: its release AAR, kept in app/libs (see
        // app/build.gradle.kts), is served as a module of its own. A plain files()
        // dependency on an AAR is not unpacked for lint ("Null extracted folder").
        exclusiveContent {
            forRepository {
                ivy {
                    name = "appLibs"
                    url = uri("app/libs")
                    patternLayout { artifact("[artifact]-[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.k2fsa.sherpa.onnx", "sherpa-onnx") }
        }
        // The FFmpeg runtime, FFmpegKitNext, is published as source only: the AAR that
        // scripts/build-ffmpeg-kit-next.sh builds from pinned sources into media/libs is
        // served as a module of its own (see media/build.gradle.kts). Only from there:
        // a module of that name on any other repository is never used.
        exclusiveContent {
            forRepository {
                ivy {
                    name = "mediaLibs"
                    url = uri("media/libs")
                    patternLayout { artifact("[artifact]-[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.arthenica", "ffmpeg-kit-next") }
        }
    }
}

rootProject.name = "FFmpeg GUI"

include(":app")    // UI, view model, dependency wiring
include(":core")   // pure Kotlin: models, command generation, conversion workflow
include(":media")  // Android adapters: FFmpeg runtime, media probing, file staging
