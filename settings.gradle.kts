import org.gradle.api.initialization.resolve.RepositoriesMode
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "intellij-review"

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.20"
        id("org.jetbrains.changelog") version "2.5.0"
        id("org.jetbrains.grammarkit") version "2022.3.2.2"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.17.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // grammarkitプラグインがproject.repositoriesに独自リポジトリを追加してもmavenCentral()が無視されないようにする
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    // Configure all projects' repositories
    repositories {
        mavenCentral()

        // Grammar-Kit(JFlex/Grammar-Kit本体jar)のダウンロード元
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        maven("https://cache-redirector.jetbrains.com/intellij-repository/releases")

        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
