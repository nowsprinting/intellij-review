package com.nowsprinting.intellijreview.declaration

// catalog.ymlはPREDEF/CHAPS/APPENDIX/POSTDEFの4セクションからなる単純なYAMLリストのみを
// 想定しているため、依存追加が必要なYAMLパーサ(snakeyaml等)は使わず行ベースで解析する。
object ReviewCatalogYamlParser {
    val SECTION_ORDER = listOf("PREDEF", "CHAPS", "APPENDIX", "POSTDEF")
    private val SECTION_HEADER = Regex("""^(\w+):\s*$""")
    private val LIST_ITEM = Regex("""^\s*-\s*(\S+\.re)\s*$""")

    // 章IDは、catalog.yml中のセクション出現順ではなく、書籍としての読み順
    // (PREDEF→CHAPS→APPENDIX→POSTDEF)で返す。
    fun parseChapterIds(text: String): List<String> = parseChapters(text).map { it.first }

    // (章ID, セクション名)の組を読み順で返す。章番号(CHAPSは1, 2, …、APPENDIXはA, B, …)の算出に使う。
    fun parseChapters(text: String): List<Pair<String, String>> {
        val filesBySection = mutableMapOf<String, MutableList<String>>()
        var currentSection: String? = null
        for (line in text.lineSequence()) {
            val header = SECTION_HEADER.matchEntire(line)
            if (header != null) {
                currentSection = header.groupValues[1]
                continue
            }
            val section = currentSection ?: continue
            val item = LIST_ITEM.matchEntire(line) ?: continue
            filesBySection.getOrPut(section) { mutableListOf() }.add(item.groupValues[1])
        }
        return SECTION_ORDER.flatMap { section ->
            filesBySection[section].orEmpty().map { it.removeSuffix(".re") to section }
        }
    }
}

// ReviewCatalogYamlParserと同じ理由でYAMLパーサを使わず、トップレベルの単一値キーだけを読む。
object ReviewConfigYamlParser {
    fun parseValue(text: String, key: String): String? {
        val pattern = Regex("""^${Regex.escape(key)}:\s*(["']?)([^"'\s#]+)\1\s*(#.*)?$""")
        return text.lineSequence().firstNotNullOfOrNull { pattern.matchEntire(it)?.groupValues?.get(2) }
    }

    // `key: ["a", "b,c"]`形式(フロースタイル)のリストを読む。texdocumentclassの第2要素は
    // カンマを含むため、カンマ分割ではなく引用符単位で要素を切り出す。
    // ponytail: ブロックスタイル(`- a`の複数行)は未対応。Re:VIEWの雛形がフロースタイルのため
    fun parseList(text: String, key: String): List<String>? {
        val pattern = Regex("""^${Regex.escape(key)}:\s*\[(.*)]\s*(#.*)?$""")
        val body = text.lineSequence().firstNotNullOfOrNull { pattern.matchEntire(it)?.groupValues?.get(1) } ?: return null
        return ITEM.findAll(body).map { m -> m.groups[1]?.value ?: m.groups[2]?.value ?: m.value.trim() }.toList()
    }

    private val ITEM = Regex(""""([^"]*)"|'([^']*)'|([^,\s"'][^,]*)""")
}
