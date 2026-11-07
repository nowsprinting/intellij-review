package com.nowsprinting.intellijreview.declaration

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

// Re:VIEWのbookのベースディレクトリ(config.yml・catalog.yml・review-ext.rbを置く場所)と、
// config.ymlのcontentdir・imagedirが指すディレクトリを解決する。
object ReviewBookLayout {
    const val CATALOG_YAML = "catalog.yml"
    private const val CONFIG_YAML = "config.yml"

    // contentdirで原稿をベースディレクトリより下に置く構成があるため、.reファイルと同じディレクトリでは決め打ちできない。
    // 遡るのはコンテンツルートまでで、プロジェクト外にたまたまあるconfig.ymlをベースと誤認しないようにする。
    // config.yml・catalog.ymlのどちらも見つからなければ、.reファイルと同じディレクトリをベースとみなす。
    fun baseDir(project: Project, file: VirtualFile): VirtualFile? {
        val start = file.parent ?: return null
        val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file)
        return generateSequence(start) { if (it == contentRoot || contentRoot == null) null else it.parent }
            .firstOrNull { it.findChild(CONFIG_YAML) != null || it.findChild(CATALOG_YAML) != null } ?: start
    }

    fun catalogFile(project: Project): VirtualFile? =
        FilenameIndex.getVirtualFilesByName(CATALOG_YAML, GlobalSearchScope.projectScope(project)).firstOrNull()

    // catalog.ymlに書く章ファイル名はcontentdirからの相対パス。ディレクトリが無ければnull。
    fun chapterDir(project: Project, catalogFile: VirtualFile): VirtualFile? =
        catalogFile.parent?.let { configuredDir(project, it, "contentdir", ".") }

    fun imageDir(project: Project, file: VirtualFile): VirtualFile? =
        baseDir(project, file)?.let { configuredDir(project, it, "imagedir", "images") }

    // VfsUtilCore.loadTextでなくPSI経由で読むのは、エディタで編集中の未保存の設定も拾うため。
    fun configText(project: Project, baseDir: VirtualFile): String? =
        baseDir.findChild(CONFIG_YAML)?.let { PsiManager.getInstance(project).findFile(it)?.text }

    private fun configuredDir(project: Project, baseDir: VirtualFile, key: String, default: String): VirtualFile? {
        val path = configText(project, baseDir)?.let { ReviewConfigYamlParser.parseValue(it, key) } ?: default
        return baseDir.findFileByRelativePath(path)?.takeIf { it.isDirectory }
    }
}
