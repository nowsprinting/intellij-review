package com.nowsprinting.intellijreview.completion

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile
import com.nowsprinting.intellijreview.declaration.ReviewBookLayout
import com.nowsprinting.intellijreview.declaration.ReviewDeclarationKind

// コマンド名はRe:VIEW本体(lib/review/compiler.rb と idgxmlbuilder.rb の
// defblock/defsingle/defminicolumn 登録)に従う。引数テンプレートはdoc/format.ja.mdに載っている記法を基準に、
// 必須引数の数だけ括弧を出し、引数がすべて任意の命令(emlistやnoteのキャプション)でも1個は出す。
// compiler.rbの引数仕様を基準にしないのは、それは「パーサーが通す上限」であって書くべき形ではないため。
// たとえばcmdはcompiler.rb上は0..1だがformat.ja.mdにキャプションの記法はなく、LaTeXでは
// キャプション付きのcmdがビルドに失敗する。format.ja.mdに無い命令だけcompiler.rbの引数の数に従う。
object ReviewCommandCatalog {

    private const val ID_CAPTION_BODY = "[][]{\n//}"
    private const val ID_BODY = "[]{\n//}"
    private const val ID_CONTENT_BRACKETS = "[][]"
    private const val RAW_BUILDER_CONTENT = "[|]"
    private const val BODY_ONLY = "{\n//}"
    private const val ID_ONLY = "[]"
    private const val NO_ARGS = ""

    val BLOCK_COMMANDS: List<ReviewCommandSpec> = listOf(
        // 必須引数2個+本文(list/listnum/imageはid+キャプション。graphはファイル名+コマンド名でキャプションは任意)
        "list", "listnum", "image", "bibpaper", "graph",
        // table/imgtableの引数は任意だが、format.ja.mdは"//table[識別子][キャプション]{"を基本形としている
        "table", "imgtable",
    ).map { ReviewCommandSpec(it, ID_CAPTION_BODY) } + listOf(
        // 必須引数1個、または任意引数のみ+本文
        "doorquote", "emlist", "emlistnum", "source", "emtable", "box", "comment",
        "note", "memo", "tip", "info", "warning", "important", "caution", "notice",
        // 以下はidgxmlbuilder.rbで登録される命令
        "insn", "link", "planning", "best", "security", "point", "shoot",
    ).map { ReviewCommandSpec(it, ID_BODY) } + listOf(
        // footnote/endnoteはdefsingle(1行完結)であり、本文braceを一切持たない。
        // 実際のRe:VIEW構文は"//footnote[id][content]"(引数括弧2つ、同一行)。
        ReviewCommandSpec("footnote", ID_CONTENT_BRACKETS),
        ReviewCommandSpec("endnote", ID_CONTENT_BRACKETS),
        // rawもdefsingle。構文は"//raw[|builder|content]"(1個の括弧内を"|"で区切る)
        // という他に類のない形のため、専用テンプレートを用意した。
        ReviewCommandSpec("raw", RAW_BUILDER_CONTENT),
    ) + listOf(
        // 引数無し+本文。cmd/texequation/embedはcompiler.rb上は任意引数を持つが、
        // format.ja.mdの基本形は"//cmd{"・"//texequation{"・"//embed{"
        "read", "lead", "cmd", "texequation", "embed", "quote", "talk",
        "address", "blockquote", "bpo", "flushright", "centering",
        "rawblock", "reference", "term", "practice", "expert",
    ).map { ReviewCommandSpec(it, BODY_ONLY) } + listOf(
        // 必須引数1個・本文無し。indepimage/numberlessimageはformat.ja.mdでは本文を持たず、キャプションは任意
        "indepimage", "numberlessimage",
        "label", "tsize", "olnum", "firstlinenum", "include", "dtp",
    ).map { ReviewCommandSpec(it, ID_ONLY) } + listOf(
        // 引数無し・本文無し
        "printendnotes", "noindent", "blankline", "pagebreak", "hr", "parasep", "beginchild", "endchild",
    ).map { ReviewCommandSpec(it, NO_ARGS) }

    // インライン命令は計画書のとおり、命令名に関わらず一律">{}"を挿入しキャレットを
    // 波括弧内に置く(Re:VIEW本体のdefinline登録名を実名としてすべて採用)。
    val INLINE_COMMANDS: List<String> = listOf(
        "chapref", "chap", "title", "img", "imgref", "icon", "list", "table", "eq", "fn", "endnote",
        "kw", "ruby", "bou", "ami", "b", "dtp", "code", "bib", "hd", "secref", "sec", "sectitle",
        "href", "recipe", "column", "tcy", "balloon", "abbr", "acronym", "cite", "dfn", "em", "kbd",
        "q", "samp", "strong", "var", "big", "small", "del", "ins", "sup", "sub", "tt", "i", "tti",
        "ttb", "u", "raw", "br", "m", "uchar", "idx", "hidx", "comment", "include", "embed", "pageref",
        "w", "wb", "ttbold", "hint", "maru", "keytop", "labelref", "ref",
    )

    // 行頭アンカーで"#"コメント行の定義を除外する。Rubyを構文解析しないのは、
    // review-ext.rbでのdefinline等はモジュール直下に1行で書く慣習が定着しているため。
    private val DEFINLINE = Regex("""^\s*(?:ReVIEW::)?Compiler\.definline\s*\(?\s*:(\w+)""", RegexOption.MULTILINE)
    // 引数の数("2"や"0..1")も読み取り、補完で挿入する括弧の数に使う。
    private val DEFBLOCK = Regex(
        """^\s*(?:ReVIEW::)?Compiler\.(defblock|defsingle|defminicolumn)\s*\(?\s*:(\w+)(?:\s*,\s*(\d+)(?:\.\.(\.?)(\d+))?)?""",
        RegexOption.MULTILINE,
    )

    // 組み込み命令に、review-ext.rbでユーザーが追加した命令を加えたもの。
    fun inlineCommands(file: PsiFile): List<String> =
        (INLINE_COMMANDS + DEFINLINE.findAll(reviewExtText(file)).map { it.groupValues[1] }).distinct()

    fun blockCommands(file: PsiFile): List<ReviewCommandSpec> =
        (BLOCK_COMMANDS + DEFBLOCK.findAll(reviewExtText(file)).map { userBlockSpec(it) }).distinctBy { it.name }

    // 組み込みと同じく、括弧の数は必須引数の数とし、任意引数しか無い場合も1個は出す。
    private fun userBlockSpec(match: MatchResult): ReviewCommandSpec {
        val (method, name, minText, exclusive, maxText) = match.destructured
        val min = minText.toIntOrNull() ?: 0
        val max = maxText.toIntOrNull()?.let { if (exclusive.isEmpty()) it else it - 1 } ?: min
        val brackets = "[]".repeat(if (max == 0) 0 else maxOf(min, 1))
        return ReviewCommandSpec(name, if (method == "defsingle") brackets else brackets + BODY_ONLY)
    }

    // Re:VIEWはreview-ext.rbをbookのベースディレクトリから読むため、contentdir配下の.reファイルと同じディレクトリでは探さない。
    // VfsUtilCore.loadTextでなくDocument経由で読むのは、エディタで編集中の未保存の定義も拾うため。
    // ponytail: 呼び出しごとにreview-ext.rbを走査する。数KBの想定で、重くなればCachedValueに載せる
    private fun reviewExtText(file: PsiFile): String {
        val extFile = file.virtualFile?.let { ReviewBookLayout.baseDir(file.project, it) }?.findChild("review-ext.rb") ?: return ""
        return FileDocumentManager.getInstance().getDocument(extFile)?.text ?: ""
    }

    // 参照系インライン命令から、ReviewDeclarationCacheのどのkindのIDを補完すべきかへの対応表。
    // "column"はHEADINGとは別のCOLUMN kindを持つ(deepwikiでReVIEW本体のColumnIndexが
    // headline_index/headline_stackとは独立した別indexであることを確認。column付き見出しは
    // 自身がHEADING側のネストパスindexに参加せず、単独のlabel/captionのみでキーされる)。
    // @<fn>・@<endnote>・@<ref>・@<labelref>はいずれもRe:VIEW本体のindexが章(reファイル)
    // 単位で管理されており、章をまたいだ参照記法("chapterId|id")自体を持たない(deepwikiで
    // compiler.rb/EndnoteIndex/label_indexの実装を確認。@<ref>は@<labelref>のエイリアス)。
    val SAME_CHAPTER_ONLY_COMMANDS: Set<String> = setOf("fn", "endnote", "ref", "labelref")

    // @<bib>はbib.re一箇所にのみ定義されるbook全体で共有のbibpaper_indexを参照するため、
    // 章をまたぐ記法を持たず、かつ「同一ファイル限定」でもない(常にbib.reを直接見に行く。
    // deepwikiでBook#generate_indexesがbib.reだけを特別扱いすることを確認)。
    val GLOBAL_LOOKUP_COMMANDS: Set<String> = setOf("bib")

    val INLINE_REFERENCE_KINDS: Map<String, ReviewDeclarationKind> = mapOf(
        "chapref" to ReviewDeclarationKind.CHAPTER,
        "chap" to ReviewDeclarationKind.CHAPTER,
        "title" to ReviewDeclarationKind.CHAPTER,
        "img" to ReviewDeclarationKind.IMAGE,
        "imgref" to ReviewDeclarationKind.IMAGE,
        "list" to ReviewDeclarationKind.LIST,
        "table" to ReviewDeclarationKind.TABLE,
        "eq" to ReviewDeclarationKind.EQUATION,
        "fn" to ReviewDeclarationKind.FOOTNOTE,
        "endnote" to ReviewDeclarationKind.ENDNOTE,
        "bib" to ReviewDeclarationKind.BIBPAPER,
        "hd" to ReviewDeclarationKind.HEADING,
        "secref" to ReviewDeclarationKind.HEADING,
        "sec" to ReviewDeclarationKind.HEADING,
        "sectitle" to ReviewDeclarationKind.HEADING,
        "column" to ReviewDeclarationKind.COLUMN,
        "ref" to ReviewDeclarationKind.LABEL,
        "labelref" to ReviewDeclarationKind.LABEL,
    )
}
