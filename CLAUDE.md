# Development Guidelines

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

[Re:VIEW](https://github.com/kmuto/review)フォーマットを扱うネイティブ言語プラグイン

## Project Structure

Grammar-Kit/JFlex製のIntelliJ Platformプラグイン。プラグインID `com.nowsprinting.intellij-review`、言語ID `ReVIEW`、拡張子 `re`、ルートパッケージ `com.nowsprinting.intellijreview`。

- `src/main/grammar/` — `Review.bnf`(文法)・`ReviewLexer.flex`(レクサ)。`src/main/gen/`(gitignore対象)にlexer/parser/PSIを生成。
- `src/main/kotlin/com/nowsprinting/intellijreview/`
  - `lang/` — `ReviewLanguage`/`ReviewFileType`/`ReviewIcons`
  - `lexer/` — `ReviewLexerAdapter`(生成レクサのラッパー)
  - `parser/` — `ReviewParserDefinition`
  - `psi/`, `psi/impl/` — PSI要素・ファクトリ・mixin(`Review{Heading,BlockCommand,InlineCommand}Mixin`)
  - `highlighter/` — シンタックスハイライト・配色設定ページ・コメンター
  - `completion/` — 命令名補完・参照ID補完・命令カタログ(`ReviewCommandCatalog`)
  - `declaration/` — プロジェクト全体の宣言(章・ブロックID・見出しラベル)キャッシュ(`ReviewDeclarationCache`)
  - `reference/` — `@<...>`系参照解決(`PsiReference`実装)
  - `rename/` — `ElementManipulator`・`RefactoringSupportProvider`(Shift+F6)
  - `usages/` — `ReviewFindUsagesProvider`(独自`WordsScanner`)
  - `structure/` — Structure View(見出しツリー構築・column表示)
  - `preview/` — 版面プレビュー(config.ymlのtexdocumentclassから字詰め・行数を算出し、行分割・改ページを見積もる分割エディタ)
- `src/main/resources/META-INF/plugin.xml` — 拡張ポイント登録一式
- `src/test/kotlin/com/nowsprinting/intellijreview/` — `src/main/kotlin`と同じパッケージ構成でテストを配置(`BasePlatformTestCase`ベース)
- `src/test/testData/` — パーサーのゴールデンファイル等

設計・実装の詳細な経緯は `docs/plans/2026-07-06-review-language-plugin.md` のImplementation Notes(Step別)を参照。

## Special directories

Special directories outside of plugin projects:

- docs/
  - spec/       # Specification documents
  - design/     # Design documents
  - plans/      # Plan files

## Git Guidelines

- All commits go to the master branch until publication

### Branch Naming

Prefix branch names with the PR category:

- `feature/`: New features
- `fix/`: Bug fixes
- `chore/`: Tests, workflows, documentation, etc.

## Language Guidelines

- **English**:
  - File names and type/member signatures, CHANGELOG.md, and README.md
  - Git commit messages, GitHub Issues, and Pull Requests
- **Japanese**: Code comments, test method names, and all files under `docs/`
