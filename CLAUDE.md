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

## Language Guidelines

- **English**: File names and type/member signatures, CHANGELOG.md, and README.md
- **Japanese**: Code comments, test method names, and all files under `docs/`

## Skill Guidelines

<important if="Feature implementation planning (writing or modifying a feature implementation plan in plan mode)">
- Add the following final step at the end of the `## Development Workflow` section in the plan file (adjust the step number to follow the last existing step):

  ```markdown
  ### Step N: Finalize

  1. Append `## Implementation Notes` to the end of the plan file and write the following:
      - Design decisions: choices you made where the spec was ambiguous
      - Deviations: places where you intentionally departed from the spec, and why
      - Tradeoffs: alternatives you considered and why you picked what you did
      - Test changes: Test code modified during the implementation phase
      - Open questions: anything you'd want me to confirm or revise
  2. Copy this plan file to `docs/plans/yyyy-MM-dd-<plan-name>.md`
  3. Update `docs/spec/` and `docs/design/` to reflect this plan's changes, if applicable
  4. Append an entry to `CHANGELOG.md` under `## [Unreleased]`
      - Do not document changes whose paths start with `.github/` or `src/test/` (test code changes are excluded from the changelog)
  ```
</important>
