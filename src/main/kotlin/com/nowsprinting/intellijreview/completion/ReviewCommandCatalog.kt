package com.nowsprinting.intellijreview.completion

import com.nowsprinting.intellijreview.declaration.ReviewDeclarationKind

// コマンド名はRe:VIEW本体(lib/review/compiler.rb の defblock/defsingle/definline 登録)を
// deepwiki経由で調査し実名を採用した。一方、各ブロック命令の引数テンプレートは
// (id+キャプションを取ることが既にStep 3で確定しているID_ARG_COMMANDSの10命令を除き)
// compiler.rbの引数仕様を1つずつ検証してはいない、実装者の一般的なRe:VIEW知識に基づく
// 近似値である(Open questionsに記載。将来の精査・修正を妨げないようdataとlogicを分離した)。
object ReviewCommandCatalog {

    private const val ID_CAPTION_BODY = "[][]{\n\n//}"
    private const val ID_BODY = "[]{\n\n//}"
    private const val ID_CONTENT_BRACKETS = "[][]"
    private const val RAW_BUILDER_CONTENT = "[|]"
    private const val CAPTION_BODY = "[]{\n\n//}"
    private const val BODY_ONLY = "{\n\n//}"
    private const val ID_ONLY = "[]"
    private const val NO_ARGS = ""

    val BLOCK_COMMANDS: List<ReviewCommandSpec> = listOf(
        // id+キャプション+本文(Step 3のID_ARG_COMMANDSと一致させた9命令)
        "list", "listnum", "image", "indepimage", "numberlessimage", "graph", "table", "imgtable", "bibpaper",
    ).map { ReviewCommandSpec(it, ID_CAPTION_BODY) } + listOf(
        // id+本文(複数行body)。texequationのみがこの形("//texequation[id]{\n...\n//}")。
        "texequation",
    ).map { ReviewCommandSpec(it, ID_BODY) } + listOf(
        // footnote/endnoteはdefsingle(1行完結)であり、本文braceを一切持たない。
        // 実際のRe:VIEW構文は"//footnote[id][content]"(引数括弧2つ、同一行)。
        // 当初"//footnote[id]{content}"という誤った1行brace形式を想定し、
        // レクサの都合で複数行bodyテンプレートに逃がしていたが、deepwikiで
        // compiler.rbのdefsingle定義を確認し誤りと判明したため修正した。
        ReviewCommandSpec("footnote", ID_CONTENT_BRACKETS),
        ReviewCommandSpec("endnote", ID_CONTENT_BRACKETS),
        // rawもdefsingle。構文は"//raw[|builder|content]"(1個の括弧内を"|"で区切る)
        // という他に類のない形のため、専用テンプレートを用意した。
        ReviewCommandSpec("raw", RAW_BUILDER_CONTENT),
    ) + listOf(
        // キャプション+本文
        "emlist", "emlistnum", "cmd", "source", "quote", "emtable", "doorquote", "talk",
        "note", "memo", "tip", "info", "warning", "important", "caution", "notice",
        "planning", "best", "security", "point", "shoot",
        "reference", "term", "practice", "expert",
    ).map { ReviewCommandSpec(it, CAPTION_BODY) } + listOf(
        // 本文のみ(引数無し)
        "read", "lead", "address", "blockquote", "bpo", "flushright", "centering", "box",
        "comment", "embed", "link", "rawblock", "insn",
    ).map { ReviewCommandSpec(it, BODY_ONLY) } + listOf(
        ReviewCommandSpec("label", ID_ONLY),
        ReviewCommandSpec("tsize", ID_ONLY),
        ReviewCommandSpec("olnum", ID_ONLY),
        ReviewCommandSpec("firstlinenum", ID_ONLY),
        ReviewCommandSpec("include", ID_ONLY),
        ReviewCommandSpec("dtp", ID_ONLY),
    ) + listOf(
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
