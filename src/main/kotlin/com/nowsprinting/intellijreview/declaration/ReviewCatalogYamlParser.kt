package com.nowsprinting.intellijreview.declaration

// catalog.ymlはPREDEF/CHAPS/APPENDIX/POSTDEFの4セクションからなる単純なYAMLリストのみを
// 想定しているため、依存追加が必要なYAMLパーサ(snakeyaml等)は使わず行ベースで解析する。
object ReviewCatalogYamlParser {
    private val SECTION_ORDER = listOf("PREDEF", "CHAPS", "APPENDIX", "POSTDEF")
    private val SECTION_HEADER = Regex("""^(\w+):\s*$""")
    private val LIST_ITEM = Regex("""^\s*-\s*(\S+\.re)\s*$""")

    // 章IDは、catalog.yml中のセクション出現順ではなく、書籍としての読み順
    // (PREDEF→CHAPS→APPENDIX→POSTDEF)で返す。
    fun parseChapterIds(text: String): List<String> {
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
        return SECTION_ORDER.flatMap { section -> filesBySection[section].orEmpty() }
            .map { it.removeSuffix(".re") }
    }
}

// ReviewCatalogYamlParserと同じ理由でYAMLパーサを使わず、トップレベルの単一値キーだけを読む。
object ReviewConfigYamlParser {
    fun parseValue(text: String, key: String): String? {
        val pattern = Regex("""^${Regex.escape(key)}:\s*(["']?)([^"'\s#]+)\1\s*(#.*)?$""")
        return text.lineSequence().firstNotNullOfOrNull { pattern.matchEntire(it)?.groupValues?.get(2) }
    }
}
