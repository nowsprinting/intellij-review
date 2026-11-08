import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.grammarkit")
    id("org.jetbrains.kotlinx.kover")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugin("org.jetbrains.plugins.yaml")
        testFramework(TestFrameworkType.Platform)
        testBundledPlugin("intellij.libraries.misc.plugin")
    }

    // IntelliJ Platform 2025.3(253.x)でfastutil/kotlinx-collections-immutableがCoreサブシステムから
    // 分離され、generateReviewParserの実行時クラスパスから欠落しNoClassDefFoundErrorになるため明示追加。
    // 参考: https://youtrack.jetbrains.com/issue/IJPL-217565
    compileOnly("org.jetbrains.intellij.deps.fastutil:intellij-deps-fastutil:8.5.18-jb1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.5.0")
}

// IntelliJ Platform Gradle Plugin Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        // ラムダ内で拡張アクセサ changelog を直接参照するとビルドスクリプト(Project)ごと捕捉され、
        // configuration cache にシリアライズできないためローカル変数に退避する
        val changelog = changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    org.jetbrains.changelog.Changelog.OutputType.HTML,
                )
            }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            val channel = pluginVersion.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }
            val platformVersion = providers.gradleProperty("platformVersion").get()
            if (channel == "default" && Regex("(?i)(EAP|SNAPSHOT|-RC\\d*$)").containsMatchIn(platformVersion)) {
                throw GradleException(
                    "platformVersion ('$platformVersion') looks like an EAP/pre-release build, but " +
                        "pluginVersion ('$pluginVersion') has no channel suffix, which would publish to the " +
                        "default (stable) channel. Tag the release with a channel suffix, e.g. 'v2.0.0-eap.1'."
                )
            }
            listOf(channel)
        }
    }
}

changelog {
    version = providers.gradleProperty("pluginVersion")
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

val reviewPsiRoot = "com/nowsprinting/intellijreview/psi"
val reviewGenRoot = "src/main/gen"

grammarKit {
    jflexRelease.set("1.9.2")
    grammarKitRelease.set("2022.3.2")
}

sourceSets {
    main {
        java.srcDirs(reviewGenRoot)
    }
}

val generateReviewLexer = tasks.register<GenerateLexerTask>("generateReviewLexer") {
    sourceFile.set(file("src/main/grammar/ReviewLexer.flex"))
    targetOutputDir.set(file("$reviewGenRoot/com/nowsprinting/intellijreview/lexer"))
    purgeOldFiles.set(true)
}

val generateReviewParser = tasks.register<GenerateParserTask>("generateReviewParser") {
    sourceFile.set(file("src/main/grammar/Review.bnf"))
    targetRootOutputDir.set(file(reviewGenRoot))
    pathToParser.set("com/nowsprinting/intellijreview/parser/ReviewParser.java")
    pathToPsiRoot.set(reviewPsiRoot)
    purgeOldFiles.set(true)

    // grammarkitプラグイン(2022.3.2.2)のrequiredLibsフィルタが古く、IntelliJ 253.x向けに
    // 追加したkotlinx-collections-immutable-jvmを拾えないため、compileClasspath全体を追加で載せる
    classpath(configurations.named("compileClasspath"))
}

tasks {
    compileKotlin {
        dependsOn(generateReviewLexer, generateReviewParser)
    }
    compileJava {
        dependsOn(generateReviewLexer, generateReviewParser)
    }
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
    publishPlugin {
        dependsOn(patchChangelog)
    }
}

// 生成ディレクトリのファイル一覧から除外リストを作る案は、clean直後の構成時点では生成前で空になるため採らない
kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.nowsprinting.intellijreview.lexer._ReviewLexer*",
                    "com.nowsprinting.intellijreview.parser.ReviewParser",
                    "com.nowsprinting.intellijreview.parser.ReviewParser\$*",
                    "com.nowsprinting.intellijreview.psi.ReviewTypes*",
                    "com.nowsprinting.intellijreview.psi.ReviewVisitor",
                    "com.nowsprinting.intellijreview.psi.impl.*Impl",
                )
            }
        }
    }
}
