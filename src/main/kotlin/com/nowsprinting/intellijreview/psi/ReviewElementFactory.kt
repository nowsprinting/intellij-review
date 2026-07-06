package com.nowsprinting.intellijreview.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.nowsprinting.intellijreview.lang.ReviewFileType
import com.nowsprinting.intellijreview.psi.impl.ReviewBlockCommandMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewHeadingMixin
import com.nowsprinting.intellijreview.psi.impl.ReviewInlineCommandMixin

// リネーム/マニピュレータ用に、構文的に妥当な断片を含むダミーファイルを生成し、
// そこから目的のPSIノード(置き換え用)を取り出す。生成ファイルは物理ファイルではなく
// リネーム処理の一時的な置換元としてのみ使う。
object ReviewElementFactory {
    private fun createDummyFile(project: Project, text: String) =
        PsiFileFactory.getInstance(project).createFileFromText("dummy.re", ReviewFileType, text)

    fun createBlockCommand(project: Project, text: String): ReviewBlockCommandMixin =
        PsiTreeUtil.findChildOfType(createDummyFile(project, text), ReviewBlockCommandMixin::class.java)!!

    fun createInlineCommand(project: Project, text: String): ReviewInlineCommandMixin =
        PsiTreeUtil.findChildOfType(createDummyFile(project, text), ReviewInlineCommandMixin::class.java)!!

    fun createHeadingLabelId(project: Project, labelText: String) =
        PsiTreeUtil.findChildOfType(createDummyFile(project, "={$labelText}Dummy\n"), ReviewHeadingMixin::class.java)!!
            .let { it.nameIdentifier!! }
}
