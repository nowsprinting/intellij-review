package com.nowsprinting.intellijreview.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.nowsprinting.intellijreview.lang.ReviewLanguage
import com.nowsprinting.intellijreview.lexer.ReviewLexerAdapter
import com.nowsprinting.intellijreview.psi.ReviewFile
import com.nowsprinting.intellijreview.psi.ReviewTypes

class ReviewParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = ReviewLexerAdapter()
    override fun createParser(project: Project?): PsiParser = ReviewParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = COMMENTS
    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
    override fun createElement(node: ASTNode): PsiElement = ReviewTypes.Factory.createElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = ReviewFile(viewProvider)

    companion object {
        val FILE = IFileElementType(ReviewLanguage)
        val COMMENTS = TokenSet.create(ReviewTypes.LINE_COMMENT)
    }
}
