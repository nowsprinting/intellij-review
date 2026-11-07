package com.nowsprinting.intellijreview.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.psiFile
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.ElementManipulators
import com.intellij.util.ProcessingContext
import com.nowsprinting.intellijreview.declaration.ReviewBookLayout
import com.nowsprinting.intellijreview.declaration.ReviewCatalogYamlParser
import com.nowsprinting.intellijreview.lang.ReviewIcons
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequenceItem

// FileReferenceの補完候補に任せないのは、.re以外のファイルやディレクトリ、登録済みの章まで候補に出るため。
class ReviewCatalogCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            psiElement()
                .withParent(psiElement(YAMLScalar::class.java).withParent(YAMLSequenceItem::class.java))
                .inFile(psiFile().withName(ReviewBookLayout.CATALOG_YAML)),
            ChapterFileProvider,
        )
        // キー名を打ちかけの行は":"が無いためキーではなく、文書直下かトップレベルのマッピング直下のスカラーになる。
        extend(
            CompletionType.BASIC,
            psiElement()
                .withParent(
                    psiElement(YAMLScalar::class.java).withParent(
                        or(psiElement(YAMLDocument::class.java), psiElement(YAMLMapping::class.java).withParent(YAMLDocument::class.java))
                    )
                )
                .inFile(psiFile().withName(ReviewBookLayout.CATALOG_YAML)),
            SectionKeyProvider,
        )
    }

    private object SectionKeyProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val topLevel = (parameters.originalFile as? YAMLFile)?.documents?.firstOrNull()?.topLevelValue as? YAMLMapping
            val existing = topLevel?.keyValues?.map { it.keyText }.orEmpty().toSet()
            result.addAllElements(
                ReviewCatalogYamlParser.SECTION_ORDER.filter { it !in existing }.map { LookupElementBuilder.create("$it:") }
            )
        }
    }

    private object ChapterFileProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet,
        ) {
            val catalogFile = parameters.originalFile
            val chapterDir = catalogFile.virtualFile?.let { ReviewBookLayout.chapterDir(catalogFile.project, it) } ?: return
            val listed = ReviewCatalogYamlParser.parseChapterIds(catalogFile.text).map { "$it.re" }.toSet()
            val candidates = chapterDir.children.filter { !it.isDirectory && it.extension == "re" && it.name !in listed }

            // 既定のプレフィックスは"."で途切れるため、"chapter1.r"まで打つと"chapter1."が残ったまま挿入される。
            val scalar = parameters.position.parent as YAMLScalar
            val valueStart = scalar.textRange.startOffset + ElementManipulators.getValueTextRange(scalar).startOffset
            val typedSoFar = scalar.containingFile.text.substring(valueStart, parameters.offset)
            result.withPrefixMatcher(typedSoFar)
                .addAllElements(candidates.map { LookupElementBuilder.create(it.name).withIcon(ReviewIcons.FILE) })
        }
    }
}
