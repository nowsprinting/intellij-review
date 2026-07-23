# Re:VIEWプラグインへのビルド/CI設定の移植

## Context

`intellij-review` はプラグインの言語機能実装は完了しているが、ビルド設定(gradle.properties/build.gradle.kts)・plugin.xml・GitHub Actions・README のいずれも初期テンプレートのプレースホルダのまま(vendor情報未設定、CI皆無、GitHubリモート未設定、マーケットプレイス未公開)。

一方、同じ作者(Koji Hasegawa / nowsprinting)が持つ姉妹プロジェクト `mcp-extension-unity` は、JetBrains Marketplaceへの公開・CI/CD・changelog自動化まで一通り整備済みの成熟したIntelliJ Platform Gradleプロジェクトである。本タスクは、その姉妹プロジェクトの設定のうち `intellij-review` にそのまま適用できる汎用的な部分(gradle.properties標準プロパティ、pluginConfiguration/changelog/signing/publishing連携、GitHub Actions build/release/dependabot)を移植し、Rider/Unity/.NET固有の部分(useInstaller、bundledModule dotnet、rdgen、compatibility-verification.ymlのRider EAP解決ワークアラウンド)や前提が整っていない部分(gitタグが1つも無いためgit-describeベースのバージョニングは導入不可)を除外することが目的。

ユーザーとの確認により以下が決定済み:
- GitHubリポジトリは `github.com/nowsprinting/intellij-review` を想定(現状originは未設定のまま、URLだけ先に設定する)
- signing/publishing設定と `release.yml` も今のうちに用意する(Secrets未設定でも実行時にのみ影響するため無害)
- vendorは `mcp-extension-unity` と同形式: `<vendor url="https://github.com/nowsprinting">Koji Hasegawa</vendor>`(email属性なし)
- `<description>` は plugin.xml埋め込みをやめ、README.mdの `<!-- Plugin description -->` マーカー間をGradleがMarkdown→HTML変換して注入する方式に変更

**アイコンについて(ユーザーの質問への回答)**: `mcp-extension-unity` は `pluginIcon_dark.svg` を持つが、そのアイコンはモノクロ(前景色描画)なので `fill="#ffffff"` を足すだけで済んでいる。`intellij-review` の `pluginIcon.svg` はグラデーション塗り(`fill="url(#paint0_linear)"`、緑→青→紫)のブランドマークであり、同じ機械的変換を適用すると白一色の潰れた図形になってしまう。グラデーション系ロゴはダークテーマ背景でもそのまま視認できることが多く、ダークバリアントは必須ではない。よって **`pluginIcon_dark.svg` の追加は今回のタスクでは行わない**(必要なら別途デザイン作業として用意する)。既存の `icons/reviewColumn.svg` / `icons/reviewFile.svg` はそのまま流用でよく、追加のアイコンアセットは不要。

## 変更内容

### 1. `gradle.properties`

現状のアドホックな `group=com.nowsprinting` / `version=1.0.0-SNAPSHOT` を、IntelliJ Platform Plugin Templateの標準プロパティに置き換える:

```properties
pluginGroup=com.nowsprinting.intellij-review
pluginName=Re:VIEW
pluginRepositoryUrl=https://github.com/nowsprinting/intellij-review
pluginVersion=1.0.0-SNAPSHOT
pluginSinceBuild=253
platformVersion=2025.3.5
gradleVersion=9.6.1

kotlin.stdlib.default.dependency=false
org.gradle.configuration-cache=true
org.gradle.caching=true
```

`pluginSinceBuild=253` は現行の `platformVersion=2025.3.5`(IntelliJ IDEA 2025.3系)に対応するビルド番号。`gitDescribe` によるバージョニングはタグが1つも存在しないため今回は導入しない(静的な `pluginVersion` のまま)。

### 2. `build.gradle.kts`

- 先頭付近で `group`/`version` をプロパティから設定:
  ```kotlin
  group = providers.gradleProperty("pluginGroup").get()
  version = providers.gradleProperty("pluginVersion").get()
  ```
- `intellijPlatform { intellijIdea("2025.3.5") }` を `intellijIdea(providers.gradleProperty("platformVersion"))` に変更(Provider引数を受け付けない場合は `.get()` を付与)。
- `intellijPlatform { }` ブロックに `pluginConfiguration` / `signing` / `publishing` を追加:
  - `pluginConfiguration.ideaVersion.sinceBuild` を `pluginSinceBuild` プロパティから設定(ビルド後の `plugin.xml` にのみ反映、ソースファイルは変更しない)
  - `pluginConfiguration.description` を `README.md` の `<!-- Plugin description -->`〜`<!-- Plugin description end -->` 間をMarkdown→HTML変換して注入(マーカーが見つからない場合は `GradleException`)
  - `pluginConfiguration.changeNotes` を `org.jetbrains.changelog` プラグインの現在バージョン/Unreleasedエントリから描画
  - `signing`: `CERTIFICATE_CHAIN`/`PRIVATE_KEY`/`PRIVATE_KEY_PASSWORD` 環境変数を参照(Secrets未設定時は該当タスク実行時のみ影響、configuration自体は無害)
  - `publishing`: `PUBLISH_TOKEN` を参照し、`pluginVersion` のプレリリースサフィックスからchannelを算出。`platformVersion` がEAP/SNAPSHOT相当なのにchannelサフィックスが無い場合は `GradleException` で安全側に倒すガードも移植
- `changelog { }` 拡張ブロックを追加(`version`/`groups.empty()`/`repositoryUrl` をプロパティから設定) — プラグイン自体は既に `id("org.jetbrains.changelog")` 宣言済みだが拡張設定が無いため追加が必要
- `tasks { }` に以下を追加:
  - `wrapper { gradleVersion = providers.gradleProperty("gradleVersion").get() }`
  - `publishPlugin { dependsOn(patchChangelog) }`
- 既存のGrammar-Kit/JFlexレクサ・パーサー生成タスク(`generateReviewLexer`/`generateReviewParser`)、`compileKotlin`/`compileJava` の依存設定はそのまま変更しない
- **移植しないもの**: `pluginVerification.ides` のRider EAP向けカスタムロジック(標準の `verifyPlugin` で問題ないため不要)、dotnet/rdgenタスク、`useInstaller = false`

### 3. `src/main/resources/META-INF/plugin.xml`

- `<vendor url="https://www.yourcompany.com">YourCompany</vendor>` → `<vendor url="https://github.com/nowsprinting">Koji Hasegawa</vendor>`
- `<description><![CDATA[...]]></description>` タグを削除(README.md駆動に移行するため)
- その他(`<depends>`、`<extensions>` 一式)は変更なし。`<idea-version since-build="...">` はソースに追記せず、Gradle側の `pluginConfiguration.ideaVersion.sinceBuild` によりビルド後の成果物にのみ反映される

### 4. `README.md`

現状 `<!-- Plugin description -->` / `<!-- Plugin description end -->` マーカーの中身が空なので、`CHANGELOG.md` の `[Unreleased]/### Added` から機能一覧を転記する形で記述:

```markdown
# Re:VIEW IntelliJ plugin

[![Build](https://github.com/nowsprinting/intellij-review/actions/workflows/build.yml/badge.svg)](https://github.com/nowsprinting/intellij-review/actions/workflows/build.yml)

<!-- Plugin description -->
Native language support for [Re:VIEW](https://github.com/kmuto/review) (`.re`) files.

## Features

- **Syntax highlighting** — headings, block/inline commands, and a dedicated color settings page
- **Code completion** — block/inline command names, and block ID/heading label references
- **Reference resolution** — chapters (`@<chap>`), block IDs (`@<list>` etc.), headings (`@<hd>` etc.), footnotes/endnotes/bibliography
- **Find Usages** — block IDs, heading labels, and chapter files
- **Rename refactoring** — block IDs, heading labels, and chapter file names (Shift+F6)
- **Structure View** — nested heading hierarchy with a dedicated icon for columns
<!-- Plugin description end -->
```

マーケットプレイスのバージョン/ダウンロード数/評価バッジ(`img.shields.io/jetbrains/plugin/*`)はマーケットプレイスIDが発行されるまで追加しない(未公開のため)。

### 5. `.github/workflows/build.yml`(新規)

`mcp-extension-unity` の `build.yml` から.NET関連ステップを除いた汎用構成:

- トリガー: `push: branches: [master]`、`pull_request: types: [opened, synchronize, reopened]`、`concurrency` でcancel-in-progress、ワークフロールート `permissions: {}`
- ジョブ:
  - **build**: checkout(タグ取得含む)、`gradle/actions/wrapper-validation`、`actions/setup-java`(Java 21 Zulu)、`gradle/actions/setup-gradle`、`./gradlew properties`/`./gradlew getChangelog --unreleased --no-header` の出力をjob outputsに、`./gradlew buildPlugin`、`build/distributions/*.zip` を `actions/upload-artifact`
  - **test**: `./gradlew test`、失敗時のみ `build/reports/tests/` をアップロード
  - **verify**: `./gradlew verifyPlugin`、常に `build/reports/pluginVerifier/` をアップロード
  - **releaseDraft**: `needs: [build, test, verify]`、`if: push && ref == refs/heads/master`、`permissions: contents: write`、既存のdraft releaseを削除して新規作成(`gh api`/`gh release`使用、changelogは build ジョブのoutputsから)
- Actionsはコミットハッシュ固定+バージョンコメント(`actions/checkout@<sha> # v6.0.2` 等)。実装時に実際の最新安定版タグのSHAを確認して固定する(このプランでは値を仮定しない)

### 6. `.github/workflows/release.yml`(新規、Secrets未設定でも無害)

- トリガー: `release: types: [prereleased, released]`
- リリースタグでcheckout → Java/Gradleセットアップ → `sed` で `gradle.properties` の `pluginVersion` をタグ由来の値に置換 → `./gradlew patchChangelog` → `./gradlew publishPlugin -PbuildVersion=$VERSION`(Secrets: `PUBLISH_TOKEN`/`CERTIFICATE_CHAIN`/`PRIVATE_KEY`/`PRIVATE_KEY_PASSWORD`)→ `gh release upload` で成果物添付 → 安定版タグ(サフィックス無し)の場合のみchangelog更新PRを自動作成

### 7. `.github/dependabot.yml`(新規)

```yaml
version: 2
updates:
  - package-ecosystem: gradle
    directory: /
    schedule:
      interval: weekly
    target-branch: master
    open-pull-requests-limit: 0

  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    target-branch: master
    open-pull-requests-limit: 0
```

セキュリティアップデートのみ通知する低ノイズ設定(`mcp-extension-unity`と同じ方針)。

### 8. `.gitignore`(軽微なクリーンアップ)

`gibo` 生成時に混入した、このプロジェクトには存在しない`.NET`関連の記述(`src/dotnet/*/Model/`、`src/dotnet/*/bin/`、`src/dotnet/*/obj/`、`src/dotnet/SdkProperties.props`)を削除。`### Project-specific ###` セクションの他のエントリ(`src/main/gen/`、`.claude/worktrees/`)は維持。

## Development Workflow

### Step 1: gradle.properties の更新
標準プロパティ(`pluginGroup`/`pluginName`/`pluginRepositoryUrl`/`pluginVersion`/`pluginSinceBuild`/`platformVersion`/`gradleVersion`)を追加。

### Step 2: build.gradle.kts の更新
`group`/`version` のプロパティ化、`intellijIdea(...)` のプロパティ化、`pluginConfiguration`/`signing`/`publishing`/`changelog{}`/`wrapper`/`publishPlugin.dependsOn(patchChangelog)` を追加。

### Step 3: plugin.xml の更新
vendor情報の実値設定、`<description>` タグの削除。

### Step 4: README.md の更新
説明文の記述とビルドバッジの追加。

### Step 5: GitHub Actions / Dependabot の追加
`.github/workflows/build.yml`、`.github/workflows/release.yml`、`.github/dependabot.yml` を新規作成。実際のAction SHAは実装時に最新安定版タグを確認して固定する。

### Step 6: .gitignore のクリーンアップ
不要な `.NET` 関連エントリを削除。

### Step 7: 動作確認
- `./gradlew properties` で `group`/`version` が意図通り解決されることを確認
- `./gradlew patchPluginXml` (または `buildPlugin`)を実行し、`build/resources/main/META-INF/plugin.xml` に `since-build`・`description`・vendorが正しく反映されていることを確認
- `./gradlew build` (既存のlexer/parser生成・コンパイル・テストが壊れていないこと)
- `./gradlew verifyPlugin` が通ることを確認
- `./gradlew wrapper --gradle-version 9.6.1` 相当が `gradleVersion` プロパティと矛盾なく動作することを確認(明示実行はしなくてもタスク定義のみでよい)
- GitHub ActionsワークフローはYAML構文のみをローカルで確認(実際のCI実行はリモートpush後)

### Step 8: Finalize

1. `## Implementation Notes` をプランファイル末尾に追記し、以下を記載:
    - Design decisions: 仕様が曖昧だった箇所での判断
    - Deviations: 仕様から意図的に逸脱した箇所とその理由
    - Tradeoffs: 検討した代替案とその選定理由
    - Test changes: 実装フェーズで変更したテストコード
    - Open questions: 確認・修正してほしい点
2. このプランファイルを `docs/plans/2026-07-06-review-language-plugin-build-ci-setup.md` にコピー
3. 該当する場合は `docs/spec/`、`docs/design/` を本プランの変更内容に合わせて更新
4. `CHANGELOG.md` の `## [Unreleased]` にエントリを追記(`.github/` 配下・`src/test/` 配下のパス変更はchangelog対象外のため、gradle.properties/build.gradle.kts/plugin.xml/README.mdの変更のみを記載)

## Implementation Notes

### Design decisions

- `pluginSinceBuild=253` は現行の `platformVersion=2025.3.5`(IntelliJ IDEA 2025.3系のブランチ番号)から算出。
- `verifyPlugin` 実行時にIU-262(2026.2系)で `com.intellij.ide.structureView.impl.common.PsiTreeElementBase` が見つからないという実際の互換性問題が検出された(Structure View実装がこのバージョンで削除/移動されたAPIを参照している、既存コードの問題でありこのタスクのスコープ外)。一時対応として `pluginUntilBuild=261.*` を追加し検証を通したが、ユーザーの指示により `pluginUntilBuild` は設定しない方針に変更(`mcp-extension-unity` と同様、`until-build` は無指定=無制限のまま)。そのため2026.2に対する非互換は解消されておらず、`verifyPlugin` は当該バージョンに対しては引き続き失敗しうる状態(Open questions参照)。
- vendorは `<vendor url="https://github.com/nowsprinting">Koji Hasegawa</vendor>` とし、email属性は`mcp-extension-unity`に倣い設定しない。
- `intellijIdea(providers.gradleProperty("platformVersion"))` が `Provider<String>` を直接受け付けることを、IntelliJ Platform Gradle Plugin 2.17.0のsourcesジャー(`IntelliJPlatformDependenciesExtension.kt`)を直接確認して検証した(`.get()` は不要)。
- `markdownToHTML` が `org.jetbrains.changelog.markdownToHTML(String, String = "")` というトップレベル関数であること、および `changelog.getOrNull`/`getUnreleased`/`renderItem`/`withHeader`/`withEmptySections` のシグネチャを、実際に導入されている `gradle-changelog-plugin-2.5.0.jar` を `javap` で逆コンパイルして確認した(姉妹プロジェクトは2.2.1を使用しているが、同シグネチャであることを確認済み)。

### Deviations

- `git describe --tags` によるバージョニングは移植しなかった。intellij-reviewにはgitタグが1つも存在せず(`git tag` が空)、導入するとGradleの構成フェーズで即座に失敗するため。静的な `pluginVersion` プロパティのまま。
- Rider/Unity固有の設定(`useInstaller = false`、`pluginVerification.ides` のカスタム `-PverifyIdePaths` ロジック、dotnet/rdgenタスク、`compatibility-verification.yml`)は移植しなかった。これらはRider EAPの依存解決バグ(JetBrains/intellij-platform-gradle-plugin#1852)のワークアラウンドであり、`intellijIdea(...)` でプレーンなIntelliJ IDEAをターゲットにしているintellij-reviewには不要なため。
- `pluginIcon_dark.svg` は追加しなかった。既存の `pluginIcon.svg` は線形グラデーション塗り(`fill="url(#paint0_linear)"`)であり、`mcp-extension-unity`のモノクロアイコンのように `fill="#ffffff"` を機械的に足す変換は使えない(白一色に潰れる)。ダークテーマでも視認性に問題はなく、追加は必須ではないと判断した。
- `.gitignore` のクリーンアップは、プランで列挙した4つの`.NET`関連パス(`src/dotnet/*/Model/`、`src/dotnet/*/bin/`、`src/dotnet/*/obj/`、`src/dotnet/SdkProperties.props`)の削除のみに留めた。同様に不要と思われる `src/main/generated/`(rd-gen用コメント)やgibo生成の`Dotnet.gitignore`テンプレート節は、承認済みプランの対象外のため変更していない。

### Tradeoffs

- signing/publishing設定と `release.yml` はユーザーの明示的な選択により今回追加した。GitHubリモート・マーケットプレイスID・Secretsのいずれも未整備の状態だが、Secrets未設定時はそれらのタスクを実行しない限り無害。
- GitHub Actionsのバージョン・SHA固定値は `mcp-extension-unity` で既に検証済みの値(`actions/checkout` v6.0.2、`gradle/actions` v6.1.0、`actions/setup-java` v5.2.0、`actions/upload-artifact` v7.0.1)を踏襲しつつ、各タグの実際のコミットSHAを `git ls-remote` で個別に再確認してから固定した(値を仮定しなかった)。

### Test changes

なし(テストコードは変更していない。ビルド設定・CI・マニフェスト・ドキュメントのみが対象)。

### Open questions

- `github.com/nowsprinting/intellij-review` のリモート作成・push時期(現状ローカルに `origin` は未設定)。作成されるまで `build.yml` の `releaseDraft` ジョブとREADMEのビルドバッジは実体を指さない。
- 初回タグ付きリリース前に、JetBrains Marketplaceの署名鍵・`PUBLISH_TOKEN` などのSecretsをGitHubリポジトリに登録する必要がある(未登録のままだと `release.yml` の `publishPlugin` ステップで失敗する)。
- IU-262(2026.2系)でのStructure View互換性問題(`PsiTreeElementBase` 参照)の修正方針。`pluginUntilBuild` は設定しない方針のため、`ReviewFileStructureViewElement`/`ReviewHeadingStructureViewElement` の実装を2026.2以降のAPIに追従させる別タスクが必要(未対応のまま `verifyPlugin` はIU-262に対して失敗しうる)。
  - **解決済み**(2026-07-23、`docs/plans/2026-07-23-idea-262-structure-view-module-migration.md`): `pluginSinceBuild=262`/`platformVersion=2026.2` へ引き上げ、Structure View実装を `PsiTreeElementBase` から `StructureViewTreeElement`/`ItemPresentation` の直接実装へ書き換え。`./gradlew verifyPlugin` はIU-262.8665.258に対して `Compatible` を確認済み。
