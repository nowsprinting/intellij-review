import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.grammarkit")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.5")
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here, for example:
        // bundledPlugin("com.intellij.java")
    }

    // IntelliJ Platform 2025.3(253.x)でfastutil/kotlinx-collections-immutableがCoreサブシステムから
    // 分離され、generateReviewParserの実行時クラスパスから欠落しNoClassDefFoundErrorになるため明示追加。
    // 参考: https://youtrack.jetbrains.com/issue/IJPL-217565
    compileOnly("org.jetbrains.intellij.deps.fastutil:intellij-deps-fastutil:8.5.18-jb1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.5.0")
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
}
