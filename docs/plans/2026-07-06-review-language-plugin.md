# Re:VIEW ネイティブ言語プラグイン実装計画

## Context

[Re:VIEW](https://github.com/kmuto/review)フォーマット(.re ファイル)を編集するためのIntelliJ Platformネイティブ言語プラグインを新規実装する。現状はIntelliJ Platform Plugin Templateの素の状態(サンプルToolWindowのみ)で、言語サポートは一切ない。

提供する機能(今回のスコープ):

1. シンタックスハイライト(+カラー設定ページ)
2. 入力補完(ブロック命令 `//list` 等、インライン命令 `@<kw>` 等、見出しオプション、参照ID)
3. 参照解決とナビゲーション: Cmd+B ジャンプ、Cmd+Y Quick Definition、Find Usages(他ファイルからの被リンク表示)、リネーム追随
4. Structure View(見出し階層 `=`〜`======`、コラム)

プレビュー機能は将来スコープ(ただしPSI設計はプレビュー実装を妨げない形にする)。

ユーザーと合意済みの方針:

- **Grammar-Kit(BNF生成パーサ/PSI)+ JFlexレクサ**方式
- 全機能を1プランでフェーズ分けし、各ステップ完了ごとに動作確認できる構成
- 参照解決の対象は**章+見出し+ラベル系すべて**(`@<chap>/@<chapref>/@<title>`、`@<hd>/@<secref>/@<sec>/@<sectitle>/@<column>`、`@<list>/@<listnum>/@<img>/@<table>/@<eq>/@<fn>/@<bib>/@<ref>/@<labelref>`)

## 基本情報

- プラグインID: `com.nowsprinting.intellij-review`(既存のまま変更しない)
- ルートパッケージ: `com.nowsprinting.intellijreview`(パッケージ名にハイフンは使えないため連結形。ユーザー確認済み)/ 言語ID: `ReVIEW` / 拡張子: `re`
- 対象: intellij-platform-gradle-plugin 2.17.0, Gradle 9.6.1, IntelliJ IDEA 2025.3.5, Kotlin
- 規約: ファイル名・シグネチャは英語、テストメソッド名・コメントは日本語、`docs/` 配下は日本語

## ビルド設定(Grammar-Kit導入)

- `org.jetbrains.grammarkit` プラグイン(2022.3.2.2)を追加
- `GenerateLexerTask`(`src/main/grammar/ReviewLexer.flex` → `src/main/gen/.../lexer/`)と `GenerateParserTask`(`src/main/grammar/Review.bnf` → `src/main/gen`、psiRoot `com/nowsprinting/intellijreview/psi`)を登録、`purgeOldFiles = true`
- `sourceSets["main"].java.srcDirs("src/main/gen")`、KotlinCompile/compileJava を生成タスクに依存させる(生成JavaがKotlin mixinを継承するため同一ソースセット必須)
- `src/main/gen/` は `.gitignore` に追加
- Configuration Cache非対応なら生成タスクに `notCompatibleWithConfigurationCache(...)` を付与。grammarkitプラグイン自体がGradle 9で動かない場合のPlan B: JFlexとGrammar-Kit jarを `JavaExec` タスクで直接実行(Step 1で判断、下流には影響なし)
- クリーンアップ: サンプル `MyToolWindowFactory.kt`/`MyMessageBundle.kt` を削除、`ReviewBundle` に置換、plugin.xml の toolWindow 登録を削除し `<name>Re:VIEW</name>` 等を修正

## レクサ設計(ReviewLexer.flex)

行指向。`EOL` を実トークンとして出す。`FlexAdapter` 経由(`ReviewLexerAdapter`)でパースとハイライト兼用。

**状態**: `YYINITIAL`(行頭ディスパッチ: `={1,6}`→見出し、`//[a-z0-9]+`→ブロック命令、`#@`→コメント、箇条書きマーカー、それ以外→TEXT_LINE)/ `HEADING_LINE`(オプション `[column]` 等・`{label}`・キャプション)/ `BLOCK_ARGS`(`[...]` 引数、`\]` エスケープ、末尾 `{`+EOL で BLOCK_BODY へ)/ `BLOCK_BODY`(行頭 `//}` で終了、内容は RAW_TEXT だが `@<` はインライン開始)/ `TEXT_LINE` / インライン系状態(`INLINE_TAG_*`、`INLINE_BRACE_*`、`INLINE_DOLLAR_*`、`INLINE_PIPE_*` — 戻り先コンテキスト(TEXT/BODY/HEADING/ARGS)ごとにサフィックス付き状態で表現し、**レクサにユーザ定義フィールドを一切持たせない**=リスタート可能性の担保。共有ルールはJFlexの状態リスト記法でまとめる)

**トークン**: `HEADING_MARKER`, `HEADING_OPTION`, `HEADING_LABEL_OPEN/ID/CLOSE`, `BLOCK_COMMAND_NAME`(`//list` 全体), `LBRACKET`, `RBRACKET`, `BLOCK_ARG_TEXT`, `BLOCK_BODY_LBRACE`, `BLOCK_END`(`//}`), `INLINE_START`(`@<`), `INLINE_NAME`, `INLINE_TAG_CLOSE`, `INLINE_LBRACE`, `INLINE_RBRACE`, `INLINE_FENCE`(`$`/`|`), `INLINE_CONTENT`, `UL_MARKER`, `OL_MARKER`, `DL_MARKER`, `LINE_COMMENT`, `TEXT`, `RAW_TEXT`, `EOL`, `WHITE_SPACE`, `BAD_CHARACTER`

**注意点**: `//}` はBLOCK_BODY内の行頭でのみ終端子 / 1行ブロックと複数行ブロックは字句レベルで判別(最後の `]` 直後の `{`+EOL) / `\}` エスケープはブレース形式のみ、バックスラッシュは `INLINE_CONTENT` に含めてPSI側でアンエスケープ / インラインはネスト禁止・EOLを跨がない(未終端はパースエラーとして回復) / `#@mapfile`〜`#@end` は行単位の `LINE_COMMENT` 扱い

## 文法設計(Review.bnf)

フラットな行ベース構造(見出し階層はStructure View側で計算、パーサではネストさせない — エラー回復が単純になりプレビュー実装も妨げない):

```
reviewFile ::= item*
item ::= heading | block_command | list_item | comment | paragraph | EOL
heading ::= HEADING_MARKER heading_option* heading_label? heading_caption? EOL   (pin=1)
block_command ::= BLOCK_COMMAND_NAME block_arg* block_body?                      (pin=1)
block_body ::= BLOCK_BODY_LBRACE EOL body_line* BLOCK_END                        (pin=1)
inline_command ::= INLINE_START INLINE_NAME INLINE_TAG_CLOSE inline_content      (pin=2)
```

各行ルールに `recoverWhile`(EOLで停止)を付け、壊れた行が次行に波及しないようにする。

**PSI mixin**(`psi.impl` 配下、Kotlin abstractクラス):

- `ReviewHeadingMixin`: `PsiNameIdentifierOwner`(name = 明示 `{label}` 優先、なければキャプション)、`getLevel()`、`isColumn()`、`getPresentation()`。明示ラベルがない見出しはリネーム拒否
- `ReviewBlockCommandMixin`: `getCommandName()`、`getIdArg()`(ID引数を取る命令: list/listnum/image/indepimage/numberlessimage/graph/table/imgtable/texequation/footnote/endnote/bibpaper/label)、`PsiNamedElement`
- `ReviewInlineCommandMixin`: `getCommandName()`、`getContentText()`(アンエスケープ済み)、**`getReferences()` オーバーライド**で参照を生成(referenceContributorは使わない — 自前言語なのでmixin直がシンプルで、`chapID|label` のサブレンジ分割も容易)
- `ReviewElementFactory`: リネーム/マニピュレータ用のダミーファイル生成

## 参照解決の設計

**StubIndexは使わない**(〜20ファイル規模の書籍にはオーバーキル)。代わりに:

- `ReviewDeclarationCache`(プロジェクトサービス): ファイルごとに `CachedValuesManager` で宣言マップ(見出しラベル/正規化キャプション→見出し、`(kind, id)`→ブロック命令。kind: LIST/IMAGE/TABLE/EQUATION/FOOTNOTE/BIB/LABEL でグルーピング。例: image|indepimage|numberlessimage|graph→IMAGE)をキャッシュ
- `chapterFiles()`: 同一ディレクトリの `*.re`。`catalog.yml` があれば行スキャンで章順を取得(YAMLライブラリ不使用)
- 参照クラス(`PsiReferenceBase`): `ReviewChapterReference`(→ `<id>.re` の PsiFile に解決。ファイルリネーム追随が自動で効く)/ `ReviewHeadingReference`(`Label`・`Sec|Subsec` パス・`chapID|Label` の3形式。明示ラベル優先、次にプレーンテキスト化したキャプション一致)/ `ReviewBlockIdReference(kind)`(`chap|id` 跨ぎ対応、`@<fn>` は同一ファイル限定)/ `ReviewBibReference`(→ `bib.re` の `//bibpaper[id]`)
- `ReviewReferenceFactory`: `INLINE_CONTENT` をエスケープ考慮で `|` 分割し、正しいサブレンジの参照配列を返す
- **Find Usages(被リンク)**: `ReviewFindUsagesProvider` + `DefaultWordsScanner`(identifier=`HEADING_LABEL_ID`、literal=`TEXT/RAW_TEXT/BLOCK_ARG_TEXT/INLINE_CONTENT`)でワードインデックスに載せ、標準の `ReferencesSearch` で被リンク検索を成立させる
- Cmd+B/Cmd+Y は `PsiReference.resolve()` が返るだけで自動的に機能する(専用ハンドラ不要)

## plugin.xml 拡張ポイント(フェーズ順)

1. `fileType` / `lang.parserDefinition` / `lang.syntaxHighlighterFactory` / `colorSettingsPage` / `lang.commenter`(`#@# `)
2. `completion.contributor`
3. `lang.elementManipulator`×2 / `lang.findUsagesProvider` / `lang.refactoringSupport`
4. `lang.psiStructureViewFactory`

## ファイルレイアウト

```
src/main/grammar/Review.bnf, ReviewLexer.flex
src/main/gen/                                  (生成物; gitignore)
src/main/kotlin/com/nowsprinting/intellijreview/
    ReviewBundle.kt
    lang/ReviewLanguage.kt ReviewFileType.kt ReviewIcons.kt
    lexer/ReviewLexerAdapter.kt
    parser/ReviewParserDefinition.kt
    psi/ReviewFile.kt ReviewElementType.kt ReviewTokenType.kt ReviewTokenSets.kt ReviewElementFactory.kt
    psi/impl/Review{Heading,BlockCommand,BlockArg,InlineCommand}Mixin.kt
    highlight/ReviewSyntaxHighlighter{,Factory}.kt ReviewColorSettingsPage.kt
    editor/ReviewCommenter.kt
    commands/ReviewBlockCommands.kt ReviewInlineCommands.kt   (命令カタログ; 補完と参照で共用)
    completion/ReviewCompletionContributor.kt + 各Provider
    reference/ReviewReferenceFactory.kt Review{Chapter,Heading,BlockId,Bib}Reference.kt
    index/ReviewDeclarationCache.kt ReviewCatalog.kt
    usages/ReviewFindUsagesProvider.kt
    rename/ReviewRefactoringSupportProvider.kt Review{BlockArg,InlineCommand}Manipulator.kt
    structure/ReviewStructureView{Factory,Model,Element}.kt
src/main/resources/META-INF/plugin.xml, icons/reviewFile.svg, messages/ReviewBundle.properties
src/test/kotlin/com/nowsprinting/intellijreview/   (lexer/parser/highlight/completion/resolve/usages/rename/structure)
src/test/testData/                                  (ゴールデンファイル・フィクスチャ)
```

## Development Workflow

各ステップは「コンパイル成功+テストグリーン+(印のあるものは)runIdeでデモ可能」で完了とする。テストメソッド名は日本語。

### Step 1: ビルド配線+言語スケルトン(★.reファイルがアイコン付きで開ける)

grammarkit導入とタスク配線(Gradle 9互換性をここで検証、ダメならPlan B)、サンプルコード削除、plugin.xml書き換え、最小レクサ(見出し・コメント・テキスト・EOLのみ)+最小BNF+`ReviewParserDefinition`/`ReviewLanguage`/`ReviewFileType`。
テスト: レクサゴールデン1本、パーサゴールデン1本、ファイルタイプ認識スモーク。

### Step 2: フルレクサ

上記レクサ設計の全状態・全トークン。
テスト(`LexerTestCase` ゴールデン): 見出し各レベル+オプション+ラベル / ブロック1行形式 / ブロック複数行+`//}` / verbatimボディ内インライン / インラインエスケープ(`\}`)とフェンス(`$`,`|`) / 箇条書き3種 / コメント各種 / 未終端インラインの回復。加えて `checkCorrectRestartOnEveryToken` でリスタート可能性を検証。

### Step 3: フル文法+PSI mixin

BNF全体、mixin(アクセサのみ、参照はまだ)、`ReviewElementFactory`、トークンセット。
テスト(`ParsingTestCase` PSIダンプゴールデン): 章構造 / ブロック+ボディ / インライン入り段落 / エラー回復(壊れた行が波及しない)。`getLevel`/`getCommandName`/`getIdArg` のユニットテスト。

### Step 4: シンタックスハイライト(★色付きエディタ+設定ページ)

`ReviewSyntaxHighlighter`(TextAttributesKey: HEADING/HEADING_OPTION/HEADING_LABEL/BLOCK_COMMAND/BLOCK_BRACKETS/BLOCK_ARG/RAW_BODY/INLINE_COMMAND/INLINE_CONTENT/LIST_MARKER/COMMENT、`DefaultLanguageHighlighterColors` 派生)、`ReviewColorSettingsPage`、`ReviewCommenter`。
テスト: トークン→キー対応のユニットテスト、`checkHighlighting` スモーク、行コメントトグル。

### Step 5: 補完(★フェーズ2完了)

`commands/` カタログ(ブロック約40・インライン約45命令+引数テンプレート)。プロバイダ4種: 行頭 `//`→ブロック命令(InsertHandlerで `[]`/`{}` 補う)/ `@<`→インライン命令(`>{}` 挿入しキャレットを中へ)/ 見出し `[`→オプション / 参照系インラインの内側→`ReviewDeclarationCache` からID補完(章ID・kind別ブロックID・見出しラベル、`|` プレフィックス対応)。**`ReviewDeclarationCache` はこのステップで実装**(Step 6-7 で再利用)。
テスト: ブロック名 / インライン名 / 見出しオプション / 章ID(catalog順) / `@<list>`ID / `@<hd>`ラベル / `@<fn>`同一ファイル限定。

### Step 6: 参照解決 — 章+ブロックID(★Cmd+Bが効く)

`ReviewInlineCommandMixin.getReferences()` + `ReviewReferenceFactory` + `ReviewChapterReference`/`ReviewBlockIdReference`/`ReviewBibReference` + マニピュレータ。
テスト(マルチファイルフィクスチャ+resolve検証): `@<chap>`→ファイル / `@<list>`同一ファイル / `@<img>` kindグルーピング / `chap|id` 跨ぎ / `@<fn>` 同一ファイルのみ / `@<bib>`→bib.re / 未解決はnull。

### Step 7: 参照解決 — 見出し(`@<hd>` 系・`@<column>`)

`ReviewHeadingReference`(ラベル/キャプション/パス/跨ぎ)。キャプション正規化(インライン命令をプレーンテキスト化)は `ReviewDeclarationCache` に一元化。
テスト: 明示ラベル / キャプション / `Sec|Subsec` パス / `chapID|Label` 跨ぎ / column / ラベル優先順位。

### Step 8: Find Usages+リネーム(★フェーズ3完了: Cmd+Y・被リンク・Shift+F6)

`ReviewFindUsagesProvider`(WordsScanner付き)、`ReviewRefactoringSupportProvider`、ラベル無し見出しのリネーム拒否。
テスト: `//list[id]` の被リンク(他ファイル含む) / 見出しラベルの被リンク / 章ファイルの被リンク / ブロックIDリネーム追随 / 見出しラベルリネーム / ラベル無し見出しはリネーム不可 / ファイルリネームで `@<chap>` 追随 / ハイフン入りIDと日本語キャプションの被リンク(ワードインデックス回帰検知)。

### Step 9: Structure View(★フェーズ4完了)

見出しレベルでネスト、`[column]` は専用アイコン、暗黙のコラム終了(同位以上の見出し or `[/column]`)を反映。表示はキャプション、ラベルはlocation string。
テスト: 階層ネスト / column表示 / オプション付き見出し。

### Step 10: Finalize

1. Append `## Implementation Notes` to the end of this plan file (`docs/plans/2026-07-06-review-language-plugin.md` — 既にコピー済みのためコピー作業は不要) and write the following:
    - Design decisions: choices you made where the spec was ambiguous
    - Deviations: places where you intentionally departed from the spec, and why
    - Tradeoffs: alternatives you considered and why you picked what you did
    - Test changes: Test code modified during the implementation phase
    - Open questions: anything you'd want me to confirm or revise
2. Update `docs/spec/` and `docs/design/` to reflect this plan's changes, if applicable
3. Append an entry to `CHANGELOG.md` under `## [Unreleased]`
    - Do not document changes whose paths start with `.github/` or `src/test/` (test code changes are excluded from the changelog)

## 検証方法

- 各ステップ: `./gradlew build test` がグリーンであること
- ★印のステップ: `./gradlew runIde`(または `.run/runIde.run.xml`)でサンドボックスIDEを起動し、サンプル書籍(catalog.yml+複数.reファイル)を開いて対象機能を手動確認
  - Step 4: ハイライト表示、Settings > Editor > Color Scheme に Re:VIEW ページが出る
  - Step 5: `//`・`@<`・`@<chap>{` 等で補完ポップアップ
  - Step 6-8: `@<hd>{...}` 上で Cmd+B ジャンプ、Cmd+Y ポップアップ、見出し上で Find Usages(他ファイルからの被リンク)、Shift+F6 リネーム
  - Step 9: Cmd+7 で Structure View に見出し階層
- 最終確認: `./gradlew verifyPlugin`(Plugin Verifier)

## リスク

1. **grammarkit × Gradle 9 / Configuration Cache**: Step 1 で検証。`notCompatibleWithConfigurationCache` → JavaExec直実行(Plan B)→ IDE生成+コミット(Plan C)の順でフォールバック
2. **Kotlin↔生成Java相互運用**: mixinは `abstract`+`(node: ASTNode)` コンストラクタ必須、同一ソースセットで結合コンパイル
3. **レクサのリスタート可能性**: フィールド禁止・状態サフィックス方式で設計段階から担保、テストで強制
4. **Grammar-Kitのエラー回復ループ**: `recoverWhile` は「EOLまで消費」に限定、不正入力ゴールデンテストを早期に追加
5. **`//` 曖昧性**: 未知の `//xxx` 行はブロック命令としてレックスして許容(Re:VIEW自体もエラーにする)。将来Annotatorで警告可能

## Implementation Notes

実装中に判明した設計判断・逸脱・トレードオフ・テスト変更・確認事項を、各ステップ完了時点で追記していく(最終確認は Step 10 で実施)。

### Step 1: ビルド配線+言語スケルトン

**Design decisions**

- Grammar-Kit生成には(新しい統合版 `org.jetbrains.intellij.platform.grammarkit` ではなく)従来の `org.jetbrains.grammarkit` Gradleプラグインを採用し、バージョンは基本情報どおり **2022.3.2.2** に固定した。最新版 2023.3.0.3 は内部でPlugin Verifierの `createIde()` を使いターゲットIDE成果物(`idea:idea:2025.3.5`)を直接解決しようとするが、この座標は存在せず無条件に失敗するため、この構成には使えないと実測で確認した。
- `settings.gradle.kts` の `dependencyResolutionManagement` に `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` を設定し、grammarkitプラグインが必要とする2つのcache-redirectorリポジトリを集中管理リポジトリ一覧に直接追加した。理由: grammarkitプラグインの `apply()` は `project.repositories.maven(...)` を直接呼び出しており、これがデフォルトの `PREFER_PROJECT` モード下では「プロジェクトが自前のリポジトリを宣言した」とみなされ、`settings.gradle.kts` 側の `mavenCentral()` が無視されてしまう(jflexの推移的依存 `jsr305` が解決できずビルド失敗)。`FAIL_ON_PROJECT_REPOS` に固定することでgrammarkit側のリポジトリ追加処理自体がスキップされ、集中管理リストのみが使われるようにした。
- IntelliJ Platform 2025.3.x(253.x)で `fastutil` / `kotlinx.collections.immutable` がコア同梱jarから分離された影響(JetBrains側の未解決バグ、YouTrack `IJPL-217565` )で `generateReviewParser` 実行時に `NoClassDefFoundError` が発生したため、`compileOnly("org.jetbrains.intellij.deps.fastutil:intellij-deps-fastutil:8.5.18-jb1")` と `compileOnly("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.5.0")` を追加。後者はgrammarkitプラグイン組み込みの `requiredLibs` ファイル名フィルタに一致しないため、`generateReviewParser` タスクに `classpath(configurations.named("compileClasspath"))` を明示追加してクラスパスに含めた。
- `Review.bnf` に `generateTokenAccessors=false` を設定。デフォルトではリーフトークン(`TEXT` 等)にも `getXxx()` アクセサが自動生成されるが、`TEXT` トークンから生成される `getText(): PsiElement` が `PsiElement.getText(): String` と衝突しコンパイルエラーになった。トークン名のリネームではなく自動アクセサ生成を無効化する方針を採った(アクセサはStep 3でPSI mixinとして手動実装する設計と整合)。
- `LINE_COMMENT` トークンはBNF文法規則から意図的に参照していない。`ReviewParserDefinition.getCommentTokens()` に登録されたトークンは `PsiBuilder` が解析中に自動的に「隠しトークン」として読み飛ばし、木構築時に独立した `PsiComment` 葉として挿入する仕様のため、`comment ::= LINE_COMMENT EOL` という規則を書いても実行時に到達不能(dead rule)であることをパーサーゴールデンテストで実測確認し、削除した。
- レクサの曖昧性対応: 行頭 `"="{1,6}`(`HEADING_MARKER`)と汎用複数文字 `TEXT` ルールが同じ入力に対して競合する場合、JFlexは宣言順ではなく**最長一致**を優先するため、`TEXT` が常に勝ってしまい見出し行がまるごと `TEXT` としてレックスされる不具合があった。`TEXT` ルールの**先頭1文字だけ** `=` を除外する(`TEXT_LEAD=[^\r\n=]`)ことで解消(2文字目以降は `=` を含んでよい)。
- `ReviewTokenType.toString()` は `"ReviewTokenType.${super.toString()}"` を返すようオーバーライドする設計を最終採用とした(暫定措置ではなく意図的な設計判断)。IntelliJ Platform公式サンプル(`simple_language_plugin`)の `SimpleTokenType` が同一パターン(クラス名プレフィックスを付けた `toString()`)を採用しており、ゴールデンテストのダンプ上でどのトークンがどの `IElementType` 実装クラス由来かを一目で区別できる利点がある。振る舞いには影響しない表示上の差異のみ。

**Deviations**

- なし。

**Tradeoffs**

- リスク欄のPlan B(JavaExec直接実行)・Plan C(IDE生成+コミット)は不要だった。標準の `org.jetbrains.grammarkit` プラグイン(2022.3.2.2)を上記の設定変更で問題なく動作させられた。

**Test changes**

- なし(このステップで最初のテストを新規作成)。

**Open questions**

- ユーザーのグローバル権限設定(`~/.claude/settings.json`)にあった `Read(**/*token*)` という deny ルール(認証情報ファイルの誤読み込み防止が目的)が、IntelliJ Platform SDKの標準的な命名である `ReviewTokenType.kt`(および今後の `ReviewTokenSets.kt` 等)へのRead/Edit/Bashアクセスも巻き込んで拒否してしまう事象を確認した。ユーザーがグローバル側は `Read(**/*key*)` に変更済みだが、プロジェクトにコミットされている `.claude/settings.json` 側にも同名の `Read(**/*token*)` deny ルールが別途残っており(114行目)、そちらが依然として効いている。Step 3で `ReviewTokenSets.kt` を新規作成する必要があるため、着手前にこのプロジェクト側ルールの扱いをユーザーに確認したい。
- `fastutil` / `kotlinx-collections-immutable-jvm` の `compileOnly` 追加は、JetBrains側で追跡中の未解決バグ(`IJPL-217565`)に対する回避策。将来IntelliJ Platformのパッチやgrammarkitプラグインの更新で解消された場合、これら2つの依存関係と `generateReviewParser` の `classpath(...)` 追加行は不要になる可能性が高い。リリース前に再確認したい。

### Step 2: フルレクサ

**Design decisions**

- 行頭ディスパッチ(`YYINITIAL`)の曖昧性解消は、Step 1の「TEXTの先頭文字だけ除外する」方式(`TEXT_LEAD`)ではなく、**「どの行頭マーカーにも一致しない場合は1文字だけ`yypushback`してTEXT_LINEへ委譲する」**方式に統一した。除外文字が増える(`=`だけでなく`*`・数字+`.`・`:`・`@`等)ほどTEXT側の除外文字クラスが複雑化し、かつ「数字+`.`+空白のときだけOL_MARKER」のような可変長プレフィックスは負の文字クラスでは表現できない(除外は固定文字集合にしかできない)。1文字pushback方式なら、行頭マーカー群はJFlexの最長一致に任せて素直に書け、どれにも一致しなければ1文字だけ押し戻してTEXT_LINEに委譲すればよく、除外パターンを一切書かずに済む。Step 1のTEXT_LEADは温存(既存テストが依存するため)せず、Step 2で全面的にこの方式へ置き換えた(Step 1のゴールデンテストは出力が変わらないため無修正で通過)。
- ブロック本文の終端子 `//}` は、JFlexの`^`(行頭アンカー)を使い `^"//}"` と書くことで「BLOCK_BODY内の行頭でのみ」を正確に表現した。汎用の本文取り込みルール(`[^\r\n@]+`)と最長一致で競合しないよう、アンカー付きルールを先に宣言している。
- 単一行ブロックと複数行ブロックの判別は、BLOCK_ARGS状態で `"{" / {EOL}`(JFlexのトレイリングコンテキスト)を用い、「最後の`]`直後が`{`+EOLの場合のみ」を字句レベルで判定した。`{`はBLOCK_BODY_LBRACEとして消費し、直後のEOLはBLOCK_BODY状態自身のEOLルールが通常通り処理する(状態遷移だけで済み、追加の中間状態は不要)。
- インライン命令の戻り先コンテキスト(TEXT/BODY/HEADING/ARGS)は、レクサにフィールドを持たせず状態名のサフィックスで表現する設計(計画書の方針)を徹底するため、`INLINE_TAG_*`/`INLINE_BRACE_*`/`INLINE_DOLLAR_*`/`INLINE_PIPE_*` を4コンテキスト×4種=16状態、フラットに定義した。各状態の「閉じ括弧/フェンス通過後の戻り先」と「未終端(EOL到達)時の回復先」は次の通り: TEXT→戻り先`TEXT_LINE`/回復先`YYINITIAL`、BODY→戻り先・回復先とも`BLOCK_BODY`、HEADING→戻り先`HEADING_LINE`/回復先`YYINITIAL`、ARGS→戻り先`BLOCK_ARG_CONTENT`/回復先`YYINITIAL`。BODYのみ回復先が`YYINITIAL`ではなく`BLOCK_BODY`なのは、verbatim本文の続きの行が通常のYYINITIAL行頭ディスパッチを経由してはならない(`//}`の認識を維持する必要がある)ため。
- 計画書の状態一覧には明示されていないが、`HEADING_OPTION_CONTENT`(見出し`[...]`内部)・`HEADING_LABEL_CONTENT`(見出し`{...}`内部)・`BLOCK_ARG_CONTENT`(ブロック命令`[...]`内部)の3状態を追加した。`HEADING_OPTION`/`HEADING_LABEL_ID`/`BLOCK_ARG_TEXT`という別々のトークン種別を生成するには、各括弧の中身を専用状態で扱う必要があったため(単一の文字クラスルールでは「今どの括弧の中か」を判別できない)。
- `WHITE_SPACE`と`BAD_CHARACTER`は、Review.bnfの`tokens=[...]`に独自トークンとして宣言せず、プラットフォーム標準の`com.intellij.psi.TokenType.WHITE_SPACE`/`BAD_CHARACTER`をそのまま再利用した。前者はPsiBuilderが特別扱い(不可視・自動スキップ)する既知の定数であり、後者も同様に「解析失敗を示す標準トークン」として広く使われているため、独自定数を新設するより素直で、Step 1のLINE_COMMENT登録(`getCommentTokens()`)と同じ「プラットフォームの慣習に乗る」方針と整合する。
- `BAD_CHARACTER`のキャッチオール規則(`.`)は、文字クラスの和集合が全入力を覆わない状態(`BLOCK_ARGS`・4つの`INLINE_TAG_*`)にのみ追加した。他の状態は各ルールの文字クラスの和集合で全文字を覆っており、キャッチオールを追加するとJFlexの dead rule 警告(Step 1で経験済み)が再発するため、対象を絞った。

**Deviations**

- なし。

**Tradeoffs**

- `LexerTestCase`のゴールデンファイル自動生成(`doTest(text)`単体引数、ファイル未存在なら生成して意図的に失敗する仕組み)を試したが、`assertSameLinesWithFile`が参照するパス(`IdeaTestExecutionPolicy.getHomePathWithPolicy()`)が本プロジェクトのチェックアウト場所ではなく、Gradleが展開したIntelliJ Platform配布物の一時ディレクトリ配下を指すことが実測で判明した(スタンドアロンプラグインプロジェクトでは同機構が使えない)。そのため、`doTest(text, "")`で一旦意図的に失敗させて実際のトークン列をテスト結果XMLの差分から取得し、それを検証したうえで明示的な期待値文字列としてテストに埋め込む方式(Step 1のレクサテストと同じスタイル)に統一した。`ParsingTestCase`はプラグイン側で`getTestDataPath()`をオーバーライドできるため同じ問題が起きない(Step 1で確認済み)。
- `\}`のエスケープはブレース形式(`{...}`)のみで、`$...$`・`|...|`フェンス形式にはエスケープ機構を設けなかった(計画書の記載どおり)。フェンス内に閉じ文字自体を含めたい場合の回避策は用意しない(将来必要になれば別途検討)。

**Test changes**

- `ReviewLexerAdapterTest.kt`に10件のテストメソッドを追加(見出し各レベル、見出しオプション+ラベル、ブロック1行形式、ブロック複数行+終端子、ブロック本文verbatim内インライン、非ゼロオフセットからのリスタート時の`//}`認識、インラインエスケープ+フェンス、箇条書き3種、コメント各種、未終端インライン回復)。全メソッドで`checkCorrectRestart(text)`を呼び出し、リスタート可能性(レクサにフィールドを持たないこと)を実測検証した。既存のStep 1テスト1件にも`checkCorrectRestart`呼び出しを追加。
- `checkCorrectRestart`は`FlexAdapter`が`RestartableLexer`ではないため、状態(state)が0(YYINITIAL)のトークン開始位置でしかリスタート再検証を行わない。当初書いた各テストはブロック本文がテキストの先頭(オフセット0)から始まっており、`^"//}"`(行頭アンカー)が非ゼロオフセットからのリスタート後も正しく機能するかを実質的に検証できていなかった。ブロックの手前に段落を1行加えたテストを追加し、`//list[id]{`のトークン開始位置(非ゼロオフセットのYYINITIAL地点)からのリスタートを実際に発生させて`^`アンカーの安全性を確認した。

**Open questions**

- なし(Step 1からの`Read(**/*token*)`権限問題はStep 3着手前に改めて確認する)。

### Step 3: フル文法+PSI mixin

**Design decisions**

- ユーザーとの事前確認により、mixinはStep 3のテスト一覧に挙がっているアクセサ(`getLevel`/`isColumn`/`getCommandName`/`getIdArg`/`getContentText`)のみを実装する方針とした。`PsiNameIdentifierOwner`/`setName`/`getPresentation`(`ReviewHeadingMixin`)や`PsiNamedElement`(`ReviewBlockCommandMixin`)は、実際に使われ検証されるStep 8(Find Usages+リネーム)・Step 9(Structure View)まで実装を持ち越す。
- `ReviewBlockCommandMixin.getIdArg()`は、ID引数を取る命令(`list`/`listnum`/`image`/`indepimage`/`numberlessimage`/`graph`/`table`/`imgtable`/`texequation`/`footnote`/`endnote`/`bibpaper`/`label`)を`ID_ARG_COMMANDS`という固定集合としてハードコードし、それ以外の命令では常に`null`を返す設計とした。Re:VIEWの命令カタログは命令ごとに引数の意味が異なり(例: `//list`の1個目`[...]`はID、`//emlist`にはIDがない)、BNF文法自体はこの区別を表現できないため、mixin側の語彙テーブルとして持たせるのが素直と判断した(このテーブルはStep 5の補完カタログでも再利用できる見込み)。
- `ReviewInlineCommandMixin.getContentText()`は、ブレース形式(`{...}`)の子に`INLINE_LBRACE`が存在する場合のみ`\}`→`}`のアンエスケープを行い、フェンス形式(`$...$`/`|...|`)では一切変換しない。これはStep 2で確定済みのレクサ仕様(フェンス形式にエスケープ機構がない)にPSI層でも忠実に従った結果であり、フェンス内容はレクサが渡した生テキストをそのまま返す。

**Deviations**

- **計画書のリスク欄4「Grammar-Kitのエラー回復ループ: `recoverWhile`は『EOLまで消費』に限定」という方針を放棄し、`Review.bnf`から`recoverWhile`を全廃した。** 代わりに、開き括弧/開き波括弧を消費した時点で`pin=1`する設計(`heading_option`/`heading_label`/`block_arg`/`inline_brace_content`/`inline_fenced_content`)に切り替えて「壊れた行が次行に波及しない」という目的そのものは達成した。経緯:
  1. 計画書どおり`heading`/`block_command`/`list_item`/`paragraph`(`item`の各選択肢)に`recoverWhile=line_recover`(`private line_recover ::= !(EOL)`)を付けたところ、見出しやコメントを一切含まない単独の段落(`"Hello, Re:VIEW.\n"`)ですら`PsiErrorElement:ReviewTokenType.HEADING_MARKER expected`となり、`ReviewParagraphImpl`として解析されない不具合が発生した。3件の一時的な切り分け用テスト(`testデバッグ用_段落単体`等、後述のTest changesで削除)で、見出し・コメントの有無に関わらず再現することを確認した。
  2. `recoverWhile`をすべて除去すると上記不具合は解消したが、代わりに「`inline_command`内の未終端ブレース(`@<b>{world`のように閉じ`}`がないまま改行)」を含む段落を解析すると、その段落だけでなく**ファイル残り全体が構造化されずフラットな子要素の羅列になる**(2行目の正常な段落すら`ReviewParagraphImpl`にならない)ことを実測で確認した。原因は、`inline_content`の失敗時ロールバックにより未消費のまま残った`{`トークンを`item`のどの選択肢も先頭トークンとして受理できず、`reviewFile`の`item*`ループが無進捗で停止し、そこから先はGrammar-Kitのトップレベル回復(1トークンずつのフラット化)に委ねられてしまうため。
  3. 「`recoverWhile`を`inline_command`だけに絞る」案も試したが、これも不採用とした。壊れた入力(`@<b>{world\n`)は正しく1行に封じ込められる一方、**正常に閉じている`inline_command`(`@<b>{world}!\n`)ですら、閉じ`}`の直後にあるプレーンテキスト`!`が`recoverWhile`に食われてINLINE_COMMANDノード内の偽エラーになってしまう**ことを実測で確認した。`recoverWhile`は対象規則の成否に関わらず(成功時も)無条件に「述語が真の間読み進める」後処理として働くと推測され、規則の直後に同じ行の別トークンが続きうる文法(本文法のほぼ全ての規則が該当)では原理的に安全に使えないと判断した。
  4. 最終的に、`inline_brace_content`/`inline_fenced_content`(`INLINE_LBRACE`/`INLINE_FENCE`消費後に`pin=1`)、および同型の`heading_option`/`heading_label`/`block_arg`(開き括弧消費後に`pin=1`)に変更した。これにより、閉じ側が来なくても「開き括弧/波括弧から検出できた内容まで」を自身の子としてエラー付きで確定し、後続のEOLトークンを未消費のまま残さない。結果として外側の`paragraph`/`heading`/`block_command`は(それ自体は無条件成功するため)自身のEOLを正常に消費でき、次行から`item*`ループが問題なく再開する。
  5. `com.intellij.lang.parser.GeneratedParserUtilBase`(生成パーサーが実際に依存するプラットフォームクラス。Grammar-Kitプラグイン自体が同梱する`org.intellij.grammar.parser.GeneratedParserUtilBase`とは別物)のソースを読んで`recoverWhile`の正確な実装を確認しようとしたが、Gradleキャッシュ配下の展開済みIDE配布物・モジュールキャッシュ双方(計1300以上のjar)を探索しても該当クラスのソースを発見できなかった。そのため上記の結論は実測(ゴールデンファイルの実出力比較)によるものであり、プラットフォーム実装コードレベルでの根本原因特定はできていない。
- `ReviewElementFactory`および`ReviewTokenSets.kt`は、計画書のStep 3欄(「BNF全体、mixin(アクセサのみ、参照はまだ)、`ReviewElementFactory`、トークンセット」)に反して、このステップでは実装しなかった。前者はStep 6-7の参照解決で初めて必要になり、後者もStep 4のシンタックスハイライト(トークン分類)が最初の実利用箇所であるため、テストで裏付けられない実装を先行させない方針(Step 3スコープ確定時にユーザーと合意済み)を一貫させた。

**Tradeoffs**

- 上記のとおり`recoverWhile`の3パターン(全選択肢に付与/`inline_command`のみに付与/全廃)を実際にゴールデンテストで比較検証し、全廃+`pin=1`が最も安全という結論に達した。実装前の設計(計画書のリスク欄)から実測に基づいて方針転換した唯一の箇所。
- `pin=1`による行内封じ込めは、`inline_command`のブレース形式が閉じ`}`を欠くケースに加え、`heading_option`の閉じ`]`を欠くケース(`==[bad\nNext line.\n`)でも実測検証し、いずれも壊れた行のみに封じ込められ次行が独立した`ReviewParagraphImpl`として解析されることを確認した(Test changes参照)。`heading_label`/`block_arg`は同型の規則(開き括弧/波括弧消費後に`pin=1`)のため理屈上は同じ結果になるはずだが、これら2つと、開き括弧/波括弧に到達する前に必須トークンが欠落するような壊れ方(例: インライン命令名が空の`@<>`)については、ゴールデンテストは追加していない(Open questionsに記載)。

**Test changes**

- `recoverWhile`の不具合切り分けのため`ReviewParserTest.kt`に一時追加した3件(`testデバッグ用_段落単体`/`testデバッグ用_見出しと段落`/`testデバッグ用_コメントと段落`)とその生成ゴールデンファイルは、原因特定後にすべて削除した(計画書のStep 3テスト一覧には含まれない診断用コードのため)。
- `ReviewParserTest.kt`に計画書どおりのPSIダンプゴールデンテストを4件追加: `test章構造をパースできる`(見出しオプション+ラベル+キャプション)/`testブロックとボディをパースできる`(ブロック命令+複数行本文)/`testインライン入り段落をパースできる`(段落内インライン命令、正常系)/`testエラー回復_壊れた行が次行に波及しない`(未終端インラインを含む行の直後に正常な段落が続く、異常系)。
- `pin=1`の効果が`inline_command`以外の規則にも一般化するかを確認するため、計画書のテスト一覧にはない追加のゴールデンテスト`testエラー回復_見出しオプションが未終端でも次行に波及しない`(`==[bad\nNext line.\n`)を追加した。実測の結果、壊れた見出しオプションはHEADINGノード内にエラーとして封じ込められ、次行は独立した`ReviewParagraphImpl`として解析されることを確認した。
- 新規`ReviewPsiMixinTest.kt`(6件)を追加。ゴールデンファイル比較ではなく`PsiTreeUtil.findChildOfType`で対象PSI要素を取得し、アクセサの戻り値を直接assertする方式(計画書のテスト一覧が「ユニットテスト」と明記しているため、PSIダンプ形式ではなくこちらを採用)。

**Open questions**

- `heading_label`/`block_arg`(`inline_brace_content`/`heading_option`と同型の`pin=1`)については、壊れたケースのゴールデンテストを追加していない。また、開き括弧/波括弧そのものに到達する前に必須トークンが欠落するような壊れ方(例: インライン命令名が空の`@<>`)も未検証。理屈上は同じ`pin`の仕組みで安全なはずだが、追加のテストが必要か確認したい。
- `ParsingTestCase.doCodeTest`は`ensureCorrectReparse`(差分リパースの安定性)は実行するが`ensureNoErrorElements`は呼んでいない(呼ぶとエラー回復系のゴールデン2件が意図的に失敗する)。`pin`によるエラー回復PSIが差分リパース後も安定しているかはStep 3の範囲外として未検証のまま残っている。

### Step 4: シンタックスハイライト

**Design decisions**

- `LBRACKET`/`RBRACKET`トークンは見出しオプション(`heading_option`)とブロック引数(`block_arg`)の両方の文法規則で共有されており、`SyntaxHighlighterBase`はPSI構造ではなくトークン種別だけを見るため、出現文脈(見出し行かブロック命令行か)を区別できない。両方とも一律`BLOCK_BRACKETS`キーにマッピングした(区別が必要になった場合は将来Annotatorで対応する前提。コードコメントにも明記済み)。
- 計画書に列挙された11個の`TextAttributesKey`(HEADING/HEADING_OPTION/HEADING_LABEL/BLOCK_COMMAND/BLOCK_BRACKETS/BLOCK_ARG/RAW_BODY/INLINE_COMMAND/INLINE_CONTENT/LIST_MARKER/COMMENT)を、それぞれ意味的に近い`DefaultLanguageHighlighterColors`の定数から派生させた: HEADING/BLOCK_COMMAND/LIST_MARKER→`KEYWORD`(強調表示)、HEADING_OPTION→`METADATA`、HEADING_LABEL→`LABEL`、BLOCK_BRACKETS→`BRACKETS`、BLOCK_ARG→`PARAMETER`、RAW_BODY→`STRING`(verbatim本文)、INLINE_COMMAND→`MARKUP_TAG`(HTML/Markdownのタグに近い見た目)、INLINE_CONTENT→`MARKUP_ENTITY`、COMMENT→`LINE_COMMENT`。`TEXT`/`EOL`トークンは意図的にどのキーにもマッピングせず(空配列)、普通の本文テキストは無着色のデフォルト表示のままとした。
- `ReviewColorSettingsPage`の表示名(`AttributesDescriptor`のラベル・`getDisplayName()`)は、Step 1で確立済みの`ReviewBundle`(`messages.ReviewBundle`)経由のi18nメッセージとして追加した(`filetype.review.description`と同じ仕組みに揃えた)。
- 行コメントは`Commenter.getLineCommentPrefix()`に`"#@"`のみを返す設計とした(末尾スペースは付けない)。既存の`LINE_COMMENT`トークン正規表現(`"#@"[^\r\n]*`)や他言語プラグインの一般的な行コメンター実装(`//`・`#`等、末尾スペースを付けない)と整合させた。

**Deviations**

- なし。

**Tradeoffs**

- `checkHighlighting`スモークテストは、`myFixture.checkHighlighting()`(インスペクション/アノテーションのwarning/errorマークアップ検証用API)ではなく、`SyntaxHighlighterFactory.getSyntaxHighlighter(ReviewLanguage, project, virtualFile)`を`BasePlatformTestCase`経由で解決し、返ってきたハイライタの`highlightingLexer`でトークン列を走査して期待するキーが出現するかを確認する方式を採用した。前者はエラー回復/インスペクションの検証に使うAPIであり、本ステップで実装したのは純粋な字句ベースの色分けのため、後者の方が「`plugin.xml`の`lang.syntaxHighlighterFactory`登録が実際に解決されること」を含めてエンドツーエンドで検証できると判断した。
- 実際に色分けされたエディタの見た目(Settings > Editor > Color Schemeページの表示、`runIde`でのビジュアル確認)は本セッションでは検証していない。自動化エージェントは対話的なサンドボックスIDE(`runIde`)を起動しても画面を視認できず、ブロッキングプロセスのため終了もできないため、この確認はユーザー自身による手動確認に委ねる(計画書の「検証方法」の★印手順どおり)。

**Test changes**

- 新規`ReviewSyntaxHighlighterTest.kt`(9件、プレーンな`junit.framework.TestCase`、プラットフォームのモック環境不要)を追加。計画書の「トークン→キー対応のユニットテスト」に対応し、代表的なトークン(見出しマーカー・見出しラベル3種・ブロック命令名・インライン命令3種・インライン内容4種・箇条書き3種・コメント・角括弧/波括弧4種)ごとに`getTokenHighlights`の戻り値を直接assertする。`TEXT`/`EOL`がキーを持たないことも確認。
- 新規`ReviewHighlightingSmokeTest.kt`(1件、`BasePlatformTestCase`)を追加。計画書の「`checkHighlighting`スモーク」に対応(採用理由はTradeoffs参照)。
- 新規`ReviewCommenterTest.kt`(2件、`BasePlatformTestCase`)を追加。計画書の「行コメントトグル」に対応。`CommentByLineComment`アクションを実行し、コメント付与/解除の両方向を検証。テスト作成中に、同アクションの実行後カーソルが同じ行末ではなく次の行の先頭に移動するという`CommentByLineComment`自体の(本プラグインが制御しない)標準的な挙動を実測で確認し、期待値の`<caret>`位置をそれに合わせて修正した(テキスト変換自体は最初から想定どおり正しかった)。

**Open questions**

- `ReviewColorSettingsPage`の`getDemoText()`と`getAttributeDescriptors()`が実際に整合しているか(デモテキストの構文が全てのキーを最低1回は表示するか等)を検証するテストは追加していない。計画書のStep 4テスト一覧の範囲外のため、未検証のまま残す。

### Step 5: 補完

**Design decisions**

- ブロック/インライン命令の実名は、deepwiki(`mcp__deepwiki__ask_question`、対象リポジトリ`kmuto/review`)経由で`lib/review/compiler.rb`の`defblock`/`defsingle`/`definline`登録を4回問い合わせて調査し、実在する名前のみをカタログに採用した(発明・推測した命令名は使っていない)。
- `ReviewDeclarationCache`(章・ブロックID・見出しラベルの一覧)をこのステップで実装し、Step 6-7の参照解決から再利用する設計とした。`catalog.yml`はYAMLライブラリへの依存を追加せず、`SECTION_HEADER`/`LIST_ITEM`の2正規表現によるハンドロールの行ベースパーサー(`ReviewCatalogYamlParser`)で十分と判断した(`PREDEF`/`CHAPS`/`APPENDIX`/`POSTDEF`の4セクションを固定順に連結するだけの単純な構造のため)。キャッシュ自体は`CachedValuesManager`+`PsiModificationTracker.MODIFICATION_COUNT`でプロジェクト全体走査結果をメモ化し、`FileTypeIndex`(`.re`ファイル列挙)と`FilenameIndex`(`catalog.yml`検索)を使ってプロジェクト全体をスキャンする。
- 参照系インライン命令→`ReviewDeclarationKind`の対応表(`INLINE_REFERENCE_KINDS`)は、deepwikiで調査した`@<list>`→list+listnum、`@<img>`→image+graph+imgtable+indepimage+numberlessimage、`@<table>`→table+imgtable、`@<eq>`→texequation、`@<fn>`→footnote、`@<endnote>`→endnote、`@<bib>`→bibpaper、`@<hd>`/`@<secref>`/`@<sec>`/`@<sectitle>`→heading label、`@<column>`→column付き見出し、という実際のRe:VIEWの解決規則をそのまま反映した。
- `@<fn>`(脚注参照)のみ`chapterId|id`跨ぎ記法を意図的にサポートしない設計とした(`CROSS_CHAPTER_UNSUPPORTED_COMMAND`定数で表現)。計画書のStep 5/6テスト一覧に明記された「`@<fn>`同一ファイル限定」の要求に対応する設計判断。
- `CompletionInitializationContext.dummyIdentifier`を既定の`"IntellijIdeaRulezzz"`から小文字の`"x"`に上書きした。`BLOCK_COMMAND_NAME`/`INLINE_NAME`トークンのレクサ正規表現が`[a-z0-9]+`(小文字限定)であるため、既定のダミー識別子(大文字を含む)を使うと補完位置のトークンがそもそもそのトークン種別として成立せず、`CompletionContributor`のパターン(`psiElement(ReviewTypes.BLOCK_COMMAND_NAME)`等)が一致しなくなる不具合を実測で発見し、修正した。

**Deviations**

- **footnote/endnote/rawの引数テンプレートを実装中に誤った(Deviationではなく実装バグとして発見・修正した)。** 経緯: 当初、これら3命令を「id+複数行body」(`ID_BODY`、`"[]{\n\n//}"`)のグループに含めていた。これはStep 2のレクサ設計(`"{" / {EOL}`というトレイリングコンテキストにより、`{`が行末で直後がEOLの場合のみ本文開始として認識される)の制約から、「`//footnote[id]{content}`のような1行完結のbrace形式は現行レクサでは補完できない」と誤って判断し、代わりに複数行body形式のテンプレートに逃がしたものだった。しかし実装完了後にadvisorレビューで指摘を受け、deepwiki(`kmuto/review`)に`//footnote`/`//endnote`/`//raw`の正確な1行構文を再確認したところ、これら3命令はいずれも`defsingle`(1行完結コマンド)であり、**そもそも本文brace自体を持たない**ことが判明した。実際の構文は`//footnote[id][content]`(引数括弧2つ、同一行)・`//endnote[id][content]`・`//raw[|builder|content]`(引数括弧1つ、`|`区切り)であり、Step 2のレクサ制約はこれら3命令には無関係だった(brace自体を使わないため)。`ReviewCommandCatalog.kt`のテンプレートを`ID_CONTENT_BRACKETS`(`"[][]"`、footnote/endnote用)・`RAW_BUILDER_CONTENT`(`"[|]"`、raw用)に修正し、`testfnは同一ファイルの脚注のみ補完される`のテストフィクスチャ(誤った複数行body形式で書かれていた脚注宣言)も実際の1行bracket形式(`//footnote[note1][a note]`)に修正した。回帰防止のため`testfootnote選択で括弧2つのテンプレートが挿入される`を新規追加した。texequationのみは実際に複数行body形式(`//texequation[id]{\n...\n//}`)を持つため`ID_BODY`グループに残した。
- 上記の誤りは、テンプレートとテストフィクスチャの両方を(誤った理解のまま)整合させてしまったため、テストが green のまま実際のRe:VIEW構文からは乖離しているという「循環的green」状態を一時的に生んでいた。テストが通ることは実装の正しさの証明にならない一例として記録する。
- **`ReviewReferenceIdCompletionProvider`の章跨ぎ扱いにも同種の誤りがあった(Step 6着手時、参照解決の設計をdeepwikiで裏取りしている過程で発覚)。** 当初`CROSS_CHAPTER_UNSUPPORTED_COMMAND`(章跨ぎ非対応)は`"fn"`(脚注)のみを対象としていたが、deepwikiで`EndnoteIndex`の実装を確認したところ`@<endnote>`も`footnote_index`と同様に章(reファイル)単位でindexが管理されており章跨ぎ記法自体を持たないことが判明した。さらに`@<bib>`は逆に「同一ファイル限定」ですらなく、book全体で共有される`bib.re`一箇所のindexを常に参照する(章跨ぎ記法もサポートしない)ことも判明した。`ReviewCommandCatalog`に`SAME_CHAPTER_ONLY_COMMANDS`(fn/endnote)と`GLOBAL_LOOKUP_COMMANDS`(bib)の2集合を追加し、`ReviewReferenceIdCompletionProvider`を3分岐(グローバル/章跨ぎ対応/同一ファイル限定)に再構成した。Step 6の参照解決(`ReviewBlockIdReference`/`ReviewBibReference`)もこの3分類をそのまま踏襲する。

**Tradeoffs**

- `ReviewReferenceIdCompletionProvider`は当初`parameters.position.containingFile`でカレントファイルを取得していたが、これは補完のダミー識別子挿入用に複製された非物理ファイル(dummy file)のインスタンスであり、`ReviewDeclarationCache`が`FileTypeIndex`経由で走査する実ファイルの`PsiFile`インスタンスとは別物であるため、同一ファイル判定(`it.file == currentFile`)が常に失敗する不具合を実測で発見した。`parameters.originalFile`(複製前の実ファイル)に切り替えて解決した。この区別はStep 6-7の参照解決でも同じ罠になりうるため、コード内コメントとして明記した。
- ブロック命令カタログの引数テンプレートは、Step 3で確定済みの`ID_ARG_COMMANDS`10命令(list/listnum/image/indepimage/numberlessimage/graph/table/imgtable/texequation/footnote/endnote/bibpaper/label相当)とfootnote/endnote/raw(今回訂正)を除き、`compiler.rb`の引数仕様を1つずつ検証してはおらず、実装者の一般的なRe:VIEW知識に基づく近似値である。今回footnote/endnote/rawで実際に誤りが見つかったことから、残る約30命令についても同様の誤りが潜在する可能性がある(Open questionsに記載)。

**Test changes**

- `ReviewCompletionTest.kt`に計画書のテスト一覧どおり9件追加(ブロック名補完/ブロック引数テンプレート挿入/インライン名補完/インライン山括弧+波括弧挿入/見出しオプション補完/章IDのcatalog順補完/`@<list>`の同一ファイルID補完/`@<hd>`ラベル補完/`@<fn>`同一ファイル限定)に加え、footnote/endnote/rawの引数テンプレート訂正の回帰防止として`testfootnote選択で括弧2つのテンプレートが挿入される`、章跨ぎ扱いの訂正の回帰防止として`testendnoteは同一ファイルの後注のみ補完される`・`testbibは章に関わらずbib_reの全項目から補完される`を追加(計12件)。
- 診断のため一時的に`ReviewReferenceIdCompletionProvider`に追加した`System.err.println`と、`ReviewCompletionTest.kt`内の一時デバッグ用テストメソッドは、原因特定後にすべて削除した。
- `testhdのラベルを補完できる`のテストデータを`"=Title{lbl1}\n..."`(誤り、キャプションがラベルより先)から`"={lbl1}Title\n..."`(見出し行の構文上、ラベルはキャプションより前に来る)に修正した。

**Open questions**

- footnote/endnote/rawの引数テンプレートの誤りが今回発覚したことを踏まえ、残り約30命令(`emlist`/`quote`/`read`等)の引数テンプレート(括弧の有無・個数・本文braceの有無)についても`compiler.rb`(`defblock`/`defsingle`の引数仕様)と1つずつ突き合わせる精査が必要か確認したい。現状は実装者の一般的なRe:VIEW知識に基づく近似のままである。
- 参照系インライン命令のうち、`chap|id`跨ぎ記法を使わない場合(パイプ無し入力)に、同一ファイル内候補に加えて章ID一覧も`"chapter"`のtypeText付きヒントとして混在させる挙動、および`@<column>`の`isColumn()`絞り込みは、計画書のテスト一覧に明記された組み合わせでは直接検証されていない(前者はどのテストでも明示的にアサートされておらず、後者はcolumnフィルタ自体の単体テストがない)。動作自体は目視・コードレビューで妥当と判断したが、追加のテストが必要か確認したい。

### Step 6: 参照解決 — 章+ブロックID

**Design decisions**

- `ReviewInlineCommandMixin.getReferences()`を直接オーバーライドする設計(計画書どおり。`PsiReferenceContributor`は使わない)を採用し、実処理は新設した`reference`パッケージの`ReviewReferenceFactory.createReferences(inlineCommand)`に委譲した。
- `ReviewReferenceFactory`は、`INLINE_CONTENT`子要素(バックスラッシュエスケープが挟まると複数に分かれうる)の**生テキスト**を連結して`"|"`分割位置を求め、そのローカルオフセットを(複数の子要素をまたいで)`inline_command`要素基準の`TextRange`へ写像する(`rangeFor`ヘルパー)。`getContentText()`(`\}`→`}`アンエスケープ済み)ではなくあえて生テキストを使うのは、アンエスケープすると文字列長が変わりオフセットがずれるため(計画書の「エスケープ考慮で`|`分割」の意図はこの取り違え防止と解釈した)。
- 参照クラスは計画書どおり3種(`ReviewChapterReference`/`ReviewBlockIdReference`/`ReviewBibReference`)に分けた。`chap|id`跨ぎ記法が使われた場合、`ReviewReferenceFactory`は**章ID部分とブロックID部分それぞれに独立した参照**(前者は`ReviewChapterReference`、後者は`chapterId`付きの`ReviewBlockIdReference`)を返す配列を構成する。これにより章ID部分だけにCmd+Bしてもその章ファイルへ、ブロックID部分だけにCmd+Bしてもそのブロック宣言へ、それぞれ個別にジャンプできる。
- Step 5の`ReviewCommandCatalog`に追加済みの`SAME_CHAPTER_ONLY_COMMANDS`(章をまたげない)・`GLOBAL_LOOKUP_COMMANDS`(ファイル/章を問わず全体から探す)の2集合を参照解決側でもそのまま再利用し、`ReviewReferenceFactory`の分岐(グローバル/章跨ぎ対応/同一ファイル限定)を補完プロバイダと完全に一致させた(実装が2箇所に分岐条件を重複して書かない設計)。
- **`@<ref>`/`@<labelref>`(→`//label[id]`)をStep 6のスコープに追加実装した。** 計画書のContext節(20行目)は参照解決の対象として明示的に`@<ref>/@<labelref>`を列挙しているが、Step 6/7いずれの「Development Workflow」節のテスト一覧にも明記がなく、実装時に見落としかけた(advisorレビューで指摘を受けて追加)。deepwikiで`inline_ref`が`inline_labelref`のエイリアスであること、`label_index`が章単位で管理され`chapterId|id`跨ぎ記法をサポートしないことを確認し、`fn`/`endnote`と同じ`SAME_CHAPTER_ONLY_COMMANDS`グループに追加した。これに伴い`ReviewDeclarationKind`に`LABEL`を、`ReviewDeclarationCache`の`BLOCK_KINDS_BY_COMMAND`に`"label" to LABEL`を追加した(`//label[id]`は既にStep 3の`ID_ARG_COMMANDS`に含まれておりPSI側の`getIdArg()`は変更不要だった)。

**Deviations**

- **`ReviewBibReference`(および`ReviewReferenceIdCompletionProvider`のグローバル分岐)は、計画書の「→ `bib.re` の `//bibpaper[id]`」という文言どおり`bib.re`という特定ファイル名に限定する実装にはしていない。** 実際には`ReviewDeclarationCache`がプロジェクト全体を走査して集めた`BIBPAPER`種別の宣言(ファイル名を問わない)から`id`一致を探す。`catalog.yml`探索(`FilenameIndex`で特定ファイル名を検索)と同じ手法を使えば`bib.re`固定にできるが、(1)Step 5の補完で既にこの「ファイル名非依存でグローバル検索」という単純な設計を採用済みで両者を一致させたかったこと、(2)実務上`//bibpaper`はほぼ確実に`bib.re`にしか書かれないため実質的な差はほぼ生じないこと、の2点からあえて緩い実装のままとした。

**Tradeoffs**

- なし(Step 5のトレードオフの延長として、`parameters.originalFile`と`element.containingFile`の使い分け以外に新規のトレードオフは生じなかった。参照解決側の`ReviewBlockIdReference`は補完のような「ダミー識別子で複製されたファイル」を扱わない実PSIツリー上のresolve()のため、Step 5で踏んだ罠は再発しなかった)。

**Test changes**

- 新規`ReviewReferenceResolveTest.kt`(8件、`BasePlatformTestCase`)を追加。計画書のStep 6テスト一覧どおり7件(`@<chap>`→ファイル/`@<list>`同一ファイル/`@<img>` kindグルーピング/`chap|id`跨ぎで参照が2つに分割/`@<fn>`同一ファイルのみ/`@<bib>`→bib.re相当のグローバル解決/未解決はnull)に加え、上記のDesign decisionsで追加実装した`@<ref>`→`//label[id]`解決の回帰テスト`testref参照はlabel宣言に解決される`を追加した。
- `ReviewCompletionTest.kt`にも`@<ref>`/`@<labelref>`補完の回帰テスト`testrefは同一ファイルのlabelのみ補完される`を追加した(計13件)。

**Open questions**

- `PsiReferenceBase`の`isSoft`は既定の`false`(hard reference)のままにした。本プラグインにはまだAnnotator/InspectionToolを一切登録していないため、現時点では未解決参照(`@<list>{nope}`等)がエディタ上で赤波線として表示されることはないはずだが、実際に`runIde`で確認はしていない(自動化エージェントでは視認できないため。計画書の★印手順に委ねる)。将来、未解決参照を警告するInspectionを追加する場合、この`isSoft`設定のままで良いか確認したい。
- `@<labelref>`は`@<ref>`と全く同じコードパス(`INLINE_REFERENCE_KINDS`・`SAME_CHAPTER_ONLY_COMMANDS`双方に同じ扱いで登録)を通るため、`@<ref>`の回帰テストのみを追加し`@<labelref>`単体のテストは重複と判断して省略した。将来コードパスが分岐する変更が入った場合は追加を検討したい。

### Step 7: 参照解決 — 見出し

**Design decisions**

- `@<hd>`系(`hd`/`secref`/`sec`/`sectitle`)の解決アルゴリズムはdeepwikiで`Builder#inline_hd`の実装を確認して設計した: Re:VIEW本体の`item_id`は「見出し自身の識別子(明示ラベルがあればラベル、なければキャプション)を、レベル2以降の祖先の識別子を`"|"`で連結したパス」として構築される(`IndexBuilder#headline`の`@headline_stack`)。レベル1見出し(章タイトル)は`return if level < 2`により`@headline_stack`に一切参加せず、`@<hd>`では解決できない(`@<chap>`/`@<title>`専用)ことを実測ベースの回答で確認した。この構築ロジックを`ReviewDeclarationCache.headingDeclarations()`にそのまま実装した(スタックを`level - 2`位置で切り詰めてから見出し自身の識別子をpush、その時点のスタック内容を`"|"`結合したものをその見出しの`HEADING`宣言の`id`とする)。
- **`@<column>`は`@<hd>`と完全に別のindex(`ColumnIndex`)であることをdeepwikiで確認し、`ReviewDeclarationKind`に独立した`COLUMN`を新設した。** column付き見出しは(a)自身が`HEADING`側のネストパス計算に一切参加せず(`@headline_stack`に積まれない)、(b)自身のキー生成も祖先パス連結ではなく単独の「明示ラベルまたはキャプション」のみである。実装当初はcolumnを「`HEADING`キャッシュのうち`isColumn()==true`なものに絞り込むフィルタ」として扱っていた(Step 5時点の設計)が、これはネストパスindexに誤って混入させる誤った設計だったため、Step 7着手時にadvisorレビューで指摘を受けdeepwikiで裏取りしたうえで独立kind化した。副産物として、column専用の`SAME_CHAPTER_ONLY_COMMANDS`的な特別扱いは不要になった: columnは`chapterId|id`跨ぎ記法を(ネストパスの曖昧性を持たない単純なフラットキーのため)そのままサポートでき、Step 6で実装済みの`ReviewBlockIdReference`の機械的な最初の`"|"`分割ロジックにそのまま乗せられる(`ReviewReferenceFactory`側に`COLUMN`専用の分岐は一切追加していない)。
- `@<hd>`系の`"|"`は「章跨ぎ(`chapID|Label`)」と「同一章内のネストパス(`Sec|Subsec`)」の両方の意味を持ち、字句だけでは静的に判別できない(deepwikiで`inline_hd`の実装を確認: 先頭セグメントを章IDとして解決を試み、実在する章が見つかった場合のみ章跨ぎとして扱う。見つからなければ`"|"`を含む文字列全体を現在の章内の`item_id`としてそのまま照合する)。そのため`ReviewBlockIdReference`のような静的な最初の`"|"`分割・複数参照化はできないと判断し、新設した`ReviewHeadingReference`は単一の参照が生テキスト全体を範囲として持ち、`resolve()`内でこの「章IDとして試し解決→ダメなら現在ファイル内のネストパスとして解決」という動的なロジックを実行する設計にした(`ReviewChapterReference`+`ReviewBlockIdReference`への機械的分割はしない。Step 6との非対称性はこの曖昧性に起因する必然であり、手抜きではない)。
- 見出しキャプションの正規化(`ReviewDeclarationCache.normalizeCaption()`)は、`heading_caption`規則(private、`TEXT`/`inline_command`の並び)を構成する`TEXT`葉トークンの生テキストと`inline_command`複合要素の`getContentText()`(アンエスケープ済み)をそのまま連結する。連結後に`trim()`する必要がある: `ReviewLexer.flex`の`HEADING_LINE`状態には見出し行専用の空白トークンがなく、マーカー直後の空白は`TEXT`トークン自身に含まれてしまう(`"== Sub Title"`のTEXT部分は`" Sub Title"`)ため、trimしないと`@<hd>{Sub Title}`のようなユーザー入力と一致しない。

**Deviations**

- なし(計画書の設計(`ReviewHeadingReference`・キャプション正規化のReviewDeclarationCache一元化)どおりに実装した)。

**Tradeoffs**

- `ReviewHeadingReference`(見出し系)は`ReviewBlockIdReference`(ブロックID系)と異なり単一参照・動的判定方式を採ったため、`chapID|Label`跨ぎ記法において「章ID部分だけをCmd+Bして章ファイルへジャンプする」という Step 6のブロックID参照が持つ操作性は見出し参照では提供されない(参照全体でしか操作できない)。これはネストパスとの構文的曖昧性から生じる必然のトレードオフであり、Design decisionsに明記した。

**Test changes**

- 新規`ReviewHeadingReferenceResolveTest.kt`(6件、`BasePlatformTestCase`)を追加。計画書のStep 7テスト一覧どおり(明示ラベル/キャプション/`Sec|Subsec`パス/`chapID|Label`跨ぎ/column/ラベル優先順位)。キャプション一致とネストパスの2件は、見出しマーカー直後の空白(実際のRe:VIEW記法`"== Caption"`)を含む入力で検証し、`normalizeCaption()`の`trim()`が実際に効いていることを確認した(空白なしの`"==Caption"`ではtrimの有無に関わらず通ってしまい検証にならないとadvisorレビューで指摘を受け、修正した)。「ラベル優先順位」は、明示ラベルを持つ見出しをそのキャプション文字列で参照すると解決に失敗することを検証するテスト(`testラベルを持つ見出しはキャプションでは解決されない`)として実装した(単一見出しにはitem_idが1つしかなく、ラベルがあればキャプションは一切indexされないため)。
- **Step 5完了時点で実装されていた`testhdのラベルを補完できる`(`ReviewCompletionTest.kt`)を修正した。** 当初はレベル1見出し(`={lbl1}Title`)を使っていたが、本ステップで判明したとおりレベル1見出しは`@<hd>`で解決できない(実際のRe:VIEWでも無効な参照になる)ため、レベル2見出しを使うよう修正した(章タイトル用の`=Title`を追加し、`=={lbl1}Sub Title`をラベル付き見出しとした)。
- `ReviewDeclarationCache`の見出し収集ロジックを全面的に書き換えた(明示ラベルのみを拾う実装から、レベル2以降のネストパス`item_id`を計算する実装へ)。既存のテスト(Step 5-6で書いたhd/column関連のテスト)はいずれもこの変更後も無修正でグリーンのままだった(たまたま単一階層・単一ファイルのケースのみだったため)。

**Open questions**

- `@<sec>`/`@<secref>`/`@<sectitle>`は`@<hd>`と全く同じ`ReviewHeadingReference`のコードパスを通る(`INLINE_REFERENCE_KINDS`で同じ`HEADING` kindに登録されているのみ)。計画書のStep 7テスト一覧に個別の記載がなく、`@<ref>`/`@<labelref>`と同じ理由(共通コードパス)で個別テストは追加していない。将来レンダリング用の差異(番号のみ/タイトルのみ表示など)が実装される場合でも、参照解決自体は共通のままで問題ないか確認したい。
- 見出しレベルが飛ぶ壊れた文書構造(例: レベル2の直後にレベル4が出現し、レベル3が存在しない)の場合の`item_id`計算(スタックの穴埋めをせずそのまま詰める)は未検証。実務上は考えにくい入力だが、Structure View実装(Step 9)で同じ階層計算ロジックが必要になった場合に再考したい。

### Step 8: Find Usages+リネーム

**Design decisions**

- リネーム対象(`ReviewBlockCommandMixin`のID引数葉・`ReviewHeadingMixin`のラベル葉)を`PsiNameIdentifierOwner`化し、`getNameIdentifier()`/`getName()`/`setName()`を実装した。ブロックコマンドの`setName()`は`ElementManipulators.getManipulator(this).handleContentChange(...)`(戻り値`T?`のため`?: this`でフォールバック)経由、見出しラベルの`setName()`は対象葉を`ReviewElementFactory.createHeadingLabelId()`で生成した新しい葉に`replace()`する方式とした(見出しラベルは専用マニピュレータを持たないため)。
- `ReviewElementFactory`(新規)を`PsiFileFactory.createFileFromText`によるダミーファイル生成の一元窓口とした。`ElementManipulator`(ID引数の内容置換)と`setName()`(見出しラベルの葉差し替え)の両方が「妥当なRe:VIEWソース文字列からPSIを組み立てて必要な部分木を取り出す」という同じ手法に依存するため、ダミーファイル生成ロジックの重複を避けた。
- `ReviewFindUsagesProvider`は当初計画の`DefaultWordsScanner`を採用せず、独自の`WordsScanner`を実装した。理由は実測で判明した2つの問題による。(1) `DefaultWordsScanner`は識別子境界(非英数字)でトークンを分割するため、ハイフン入りID(`fig-1`等)が複数ワードに割れ、IntelliJの被リンク検索(`ReferencesSearch`/Find Usages)が要求する「検索語(対象要素の`getName()`)とインデックス上のワードの完全一致」を満たせなくなる恐れがあった。(2) 分割を一切行わずトークン全体を1ワードとする案も試したが、`chapterId|id`のような章跨ぎ記法では逆に`"id"`単体の完全一致検索がヒットしなくなり悪化した。最終的に、`"|"`(このプロジェクト自身が章跨ぎ記法の区切りとして使う既知の意味を持つ文字)でのみ分割し、それ以外の文字(ハイフン・アンダースコア・日本語文字等)では分割しない`WordsScanner`を実装し、両問題を解消した。
- `ReviewBlockCommandMixin`/`ReviewHeadingMixin`に`getTextOffset()`を追加した(ID/ラベル葉の開始位置を返すよう上書き)。デバッグの詳細は Deviations 参照。

**Deviations**

- **`renameElementAtCaret()`(実際のShift+F6操作と同じキャレット位置ベースの解決経路)が宣言位置で`AssertionError: element not found in file`となる問題を発見し、修正した。** 当初は`myFixture.renameElement(element, newName)`(対象PsiElementを直接渡す方式)に切り替えて回避していたが、advisorレビューで「これはキャレット→ターゲット解決という実際の操作経路を検証しない回避であり、Shift+F6という★印の完了条件そのものが未検証のまま」との指摘を受け、根本原因を調査した。診断の結果、`file.findElementAt(caretOffset)`は正しくID/ラベル葉を返し、その`parent`は正しく`PsiNameIdentifierOwner`で`nameIdentifier`とも一致していたにも関わらず`TargetElementUtil.findTargetElement()`がnullを返すことを確認した。`TargetElementUtilBase.getNamedElement()`の実装(`getNameIdentifier()`を直接見るのではなく、`parent.getTextOffset() == キャレット位置の葉の開始位置`という一致判定に依存する)を調査で特定し、`PsiElement.getTextOffset()`の既定実装(要素全体の開始位置を返す)を上書きしていなかったことが原因と判明した。`ReviewBlockCommandMixin`/`ReviewHeadingMixin`に`getTextOffset()`(ID/ラベル葉の開始位置を返す)を追加することで解消し、`renameElementAtCaret()`を使う実際の操作経路でテストし直した。
- `ReviewChapterReference.handleElementRename()`に`.re`拡張子除去のオーバーライドを追加した(既定実装は`PsiFile`の新ファイル名をそのまま参照テキストに埋め込むため、`@<chap>{id}`の`id`部分に`.re`が混入してしまう)。

**Tradeoffs**

- なし(計画書の設計どおりの構成。上記の`getTextOffset()`追加は「実際の操作経路で検証する」ための修正であり、設計の変更ではない)。

**Test changes**

- 新規`ReviewFindUsagesTest.kt`(4件)・`ReviewRenameTest.kt`(4件)を追加、計8件。デバッグ過程で2つのテストデータ起因の偽陽性(実装バグではない)を発見・修正した: (1) 複合テストで使っていた見出しがキャプションのみ(明示ラベル無し)だったため`getName()`がnullを返し、名前ベース検索であるFind Usagesが原理的に何も見つけようがなかった(設計どおり)。ラベル付き見出しに差し替えて修正。(2) `@<list>{fig1}`(章プレフィックス無し)で別ファイルの宣言を探すテストを書いていたが、プレフィックス無し参照はStep 6の設計どおり同一ファイル限定であり、そもそも解決されない参照だった。`catalog.yml`を追加し`@<list>{chapterId|fig1}`記法に修正して初めて意図した「他ファイルからの被リンク」を検証できた。
- テストファイル名は原則1テストにつき一意な名前を用いた(`BasePlatformTestCase`の軽量プロジェクトは同一JVM内の他テストクラスとVFS/FileBasedIndexを共有するため、同名ファイルの使い回しがワードインデックスの汚染・スケール漏れにつながりFind Usagesが黙って0件を返す事例を実測で確認したため)。ただし`catalog.yml`は`ReviewDeclarationCache`が固定ファイル名で探すため一意化できず、そのまま使い回している(実測上は問題を確認していない)。
- 全4テスト完了後、`./gradlew build test --rerun`および`./gradlew test --rerun`を計3回連続実行し、フレーキーさが無いことを確認した(本ステップの調査で判明したインデックス関連の問題が実測ベースだったため、念のため複数回実行した)。

**Open questions**

- キャプションのみ(明示ラベル無し)の見出しはFind Usagesで被リンクを見つけられない(`PsiNamedElement.getName()`がnullのため検索語自体が存在しない)。これは計画書の要求(見出しラベルの被リンク)どおりの設計だが、将来キャプション一致の`@<hd>`参照(Step 7で解決自体はサポート済み)についても被リンクを見つけたい場合は、名前ベース検索とは別の仕組み(`ReferencesSearch`の独自実装等)が必要になる。対応が必要か確認したい。
- `TargetElementUtilBase.getNamedElement()`が`getNameIdentifier()`を直接使わず`getTextOffset()`の一致判定に依存するのは(Java向け`JavaTargetElementEvaluator`のような`com.intellij.targetElementEvaluator`拡張ポイントが無い場合の)汎用フォールバック経路の挙動であり、ドキュメント化された契約というより実装依存の挙動である。今回は`getTextOffset()`上書きで解消したが、より正攻法な対応として`TargetElementEvaluatorEx2`を実装した`targetElementEvaluator`拡張ポイントの登録も選択肢としてあり得た(`getNameIdentifier()`を直接見る分、実装意図がより明確になる)。今回は変更量の小ささを優先して`getTextOffset()`上書きを選んだが、将来的な拡張(例: 参照側からの逆引きで宣言側の`PsiNameIdentifierOwner`以外の要素も対象にしたい等)が必要になった場合はこちらへの切り替えを検討したい。

### Step 9: Structure View

**Design decisions**

- `ReviewHeadingTreeBuilder`(新規)は、Re:VIEW本体の`Compiler#compile_headline`(deepwikiで実測確認)にヒントを得つつ、Structure View専用の単一スタックアルゴリズムとして設計した。Re:VIEW本体は実際には**2つの別々のスタック**を持つ: column(タグ付きセクション)の開閉のみを管理する`@tagged_section`(`>=`比較で閉じる)と、見出しのTOC/`item_id`パス計算専用の`@headline_stack`(Step 7で実装済み)。この2つは出力先(ブロックスコープの開閉 vs 参照解決用パス)が異なるため本体では分離されているが、Structure Viewが必要とするのは「column配下がその子として視覚的にネストされ、かつ通常の見出し階層も同時に表現される、単一のツリー」の1つだけであるため、本プラグインでは意図的に単一スタックへ統合した。統合後も両者の閉じ条件は共通の「新しい見出しのlevel以上のスタック上位を閉じる(`>=`)」ルールのみで表現できることを手計算で複数パターン検証済み(advisorレビューでも同じ結論)。
- column見出しの終了は(a)暗黙: 同位以上の見出し(`level >= 自身のlevel`)が来ると閉じる、(b)明示的: `[/column]`見出し(deepwikiで`compile_headline`のタグが`"/"`始まりならcloseとして扱われることを確認済み)、の両方を実装した。(b)の実装当初は「スタック最上位が現在columnかどうか」だけを見て判定していたが、column配下に通常の見出しがネストしている場合(スタック最上位が非column)にこの判定が効かず`[/column]`を無視してしまうバグを実装直後に手動トレースで発見し、`stack.indexOfLast { it.third }`(直近の開いているcolumnをスタック中から探索)して該当位置までまとめて閉じる方式に修正した。この修正を直接検証する回帰テスト(`testcolumn見出しは明示的な終了マーカーで閉じる`、column配下に見出しをネストさせた状態で`[/column]`を発行)を追加した。
- Structure Viewの表示テキストは計画書どおり「表示はキャプション、ラベルはlocation string」としたため、`ReviewHeadingStructureViewElement.getPresentableText()`は(参照解決やFind Usagesの`getName()`とは異なり)明示ラベルの有無に関わらず常に`ReviewDeclarationCache.normalizeCaption()`(Step 7で実装済み、可視性をpublicに変更して再利用)を呼び出す設計にした。
- 見出しツリーは全レベル(レベル1の章タイトルも含む)を対象にした。Step 7の参照解決(`@<hd>`)はレベル1見出しを除外するが、これは`@<hd>`が解決できる対象という別の関心事であり、Structure Viewは文書全体のアウトラインを示すものなので除外理由がない(deepwikiでも「レベル1除外は`@headline_stack`固有の制約」であることを確認済み)。
- `ReviewFileStructureViewElement`/`ReviewHeadingStructureViewElement`は`PsiTreeElementBase<T>`を継承し、`getChildrenBase()`内で`ReviewHeadingTreeBuilder.build()`(ファイルルート側)または事前計算済みの`ReviewHeadingNode.children`(見出し側)をラップするだけにした。ツリー構築ロジック自体(スタック処理)を1箇所(`ReviewHeadingTreeBuilder`)に閉じ込め、UI要素クラス側に重複させないための分離。
- `ReviewStructureViewModel`は`getSorters()`を空配列で上書きした。`StructureViewModelBase`の既定は`Sorter.ALPHA_SORTER`を含むため、上書きしないと見出しが文書内出現順ではなくアルファベット順に並び替えられてしまう。

**Deviations**

- なし(計画書の設計(`ReviewStructureView{Factory,Model,Element}`・見出しレベルでネスト・column専用アイコン・暗黙/明示のcolumn終了)どおりに実装した)。

**Tradeoffs**

- 上述のとおりRe:VIEW本体の2スタック構成(`@tagged_section`/`@headline_stack`)を1つに統合した。この統合により、理論上は「column本体とTOCパス計算で異なる閉じ条件が将来必要になる」ケースには対応できない設計になっているが、Structure Viewは両者の出力を1つのツリーとしてしか使わないため実害はないと判断した(advisorレビューでも同意見)。
- column専用アイコンは新規SVGアセット(`icons/reviewColumn.svg`、`reviewFile.svg`と同系統の配色)を追加する方式を選んだ。既存のプラットフォーム標準アイコン(`AllIcons.Nodes.*`)を流用する案もあったが、意味的にぴったり合うものがなく、計画書の「専用アイコン」という要求に対しては新規アセットの方が意図が明確だと判断した。通常見出しには専用アイコンを設けず`null`(アイコン無し)のままとした(計画書はcolumnのみアイコンを要求している)。

**Test changes**

- 新規`ReviewStructureViewTest.kt`(7件)を追加。計画書のStep 9テスト一覧(階層ネスト/column表示/オプション付き見出し)に加え、advisorレビューでの指摘に基づき以下を追加した: (a) column配下ネストからの明示的`[/column]`終了(上記バグ修正の回帰テスト)、(b) 明示ラベル付き見出しの表示テキスト/location stringの検証、(c) `ReviewStructureViewFactory`がbuilderを構築できることのスモークテスト、(d) `ReviewFileStructureViewElement`(実際にUIが使うエントリポイント)経由でもツリーを取得できることの検証(それ以外のテストは`ReviewHeadingTreeBuilder.build()`を直接呼び出しており、UIエントリポイント自体は(d)追加前はスモークテストのみだった)。
- 全7テスト完了後、`./gradlew build test --rerun`および`./gradlew test --rerun`を計3回連続実行し、フレーキーさが無いことを確認した。

**Open questions**

- columnがネストする(column内にさらに別のcolumnが開始する)場合の閉じ挙動は未検証。`indexOfLast { it.third }`は直近(最も内側)の開いているcolumnを閉じる設計にしたが、これは実装上の判断であり、テストも無い。実務上ネストしたcolumnがどの程度使われるか分からないため、必要になった時点で検証・テスト追加したい。
- `[/column]`がcolumnを何も開いていない状態で出現した場合、または`[/column]`自身がキャプションを持つ場合の挙動は未検証(現在の実装は前者は何もせず無視、後者はキャプションを単に無視してノードを作らない)。実務上考えにくい入力だが、明示しておく。
- `nonum`/`notoc`/`nodisp`等、`column`/`/column`以外の見出しオプションは全て「通常の見出し」として扱い、アイコンやネストに一切影響させていない(計画書のスコープどおり)。将来これらのオプションに応じた表示上の区別(例: `notoc`はStructure Viewから除外する等)が必要か確認したい。
- `runIde`でエディタ上のStructure Viewパネル(Cmd+7等)の実際の見た目・アイコンの視認は行っていない(自動化エージェントでは視認できないため)。ツリー構築ロジック(`ReviewHeadingTreeBuilder`)とプレゼンテーション(`getPresentableText`/`getLocationString`/`getIcon`)は単体テストで検証済みだが、実際のUI描画は計画書の★印手順に委ねる。

### Step 10: Finalize

**Design decisions**

- `docs/spec/`・`docs/design/`は現時点でファイルが1つも無かった。本計画書(`docs/plans/2026-07-06-review-language-plugin.md`、各Stepの実装ノート付き)自体がこのプラグインの設計・仕様の記録として十分機能しているため、重複した別文書を新規作成することはせず今回はスキップする方針を採った(実装前にユーザーへ確認し、明示的な合意を得た)。将来別機能を追加する際に、あらためてdocs/spec/・docs/design/への切り出しを検討する。
- `CHANGELOG.md`には`.github/`・`src/test/`配下の変更を除外し、Step 1-9で追加した機能(レクサ/パーサー/PSI、シンタックスハイライト、補完、参照解決、Find Usages、リネーム、Structure View)を1つの`### Added`エントリとしてまとめた(いずれも同一プラグインの初回実装であり、機能単位で分割する意味が薄いため)。

**Deviations**

- 計画書のStep 10チェックリスト2番目(`docs/spec`/`docs/design`の更新)は、上記の理由により「適用しない」と判断してスキップした。計画書自身が"if applicable"と条件付きにしていた項目であり、既存のspec/design文書が無かったため実質的に適用対象が無かった。

**Tradeoffs**

- なし。

**Test changes**

- なし(Step 10はドキュメント更新のみ)。

**Open questions**

- 将来的にdocs/spec/・docs/design/を整備する場合、本計画書のどの部分(文法設計、参照解決の設計、ファイルレイアウト等)を切り出すべきか、実装が一巡した今のタイミングで棚卸しをしたい。
