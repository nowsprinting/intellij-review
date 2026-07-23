# IDEA 262対応: pluginSinceBuildを262へ引き上げ、Structure Viewをモジュール依存へ追従

## Context

前回のCI設定移植タスク(`docs/plans/2026-07-06-review-language-plugin-build-ci-setup.md`)の Open questions として、`verifyPlugin` がIU-262(2026.2系)で `com.intellij.ide.structureView.impl.common.PsiTreeElementBase` を解決できず失敗しうる、という互換性問題が積み残されていた。IntelliJ IDEA 2026.2(build 262)が正式リリースされたので、これを解消する。

**一次情報での確認結果**(リリースタグ `idea/262.8665.258` のソースを直接確認):

- `PsiTreeElementBase` は**削除されていない**。FQN(`com.intellij.ide.structureView.impl.common.PsiTreeElementBase`)も不変で、`platform/structure-view-impl/src/com/intellij/ide/structureView/impl/common/PsiTreeElementBase.java` に存在する。
- 262のモジュール化により、このクラスは `intellij.platform.lang` 相当のCoreから **`intellij.platform.structureView.impl` モジュールへ分割**された。プラグインが262に対してコンパイル/検証するには、このモジュールへの**明示的な依存宣言**が必要になっただけ。
- 本プラグインが使う API 面(コンストラクタ、`getElement()`/`getValue()`/`getChildrenBase()`/`getChildren()`、`ItemPresentation` の `getPresentableText()`/`getLocationString()`/`getIcon()`)は `@ApiStatus.Internal` 対象外。`JavaFileTreeElement` 等プラットフォーム自身も同じ基底クラスを使い続けている。

したがって本タスクの実態は「新APIへのコード書き換え」ではなく、**モジュール分割に伴う依存宣言の追加**(JetBrains公式が案内している対応方法)。方針は事前確認でユーザーが選択した **A案: `PsiTreeElementBase` を使い続け、モジュール依存を追加** に確定済み。合わせてビルドターゲットを2026.2へ、`pluginSinceBuild` を262へ引き上げる(2025.3以前のサポートは終了する)。

**ゴール(受け入れ条件): `./gradlew verifyPlugin` がIU-262に対してグリーンになること。** 単に「コンパイルが通る」ではなく、検証がグリーンになることが本タスクの成否判定。

## 変更内容

Structure View の実装コード(`structure/` 配下)およびテストコードは**無変更**。変更はビルド設定とマニフェストの3ファイルのみ。

### 1. `gradle.properties`

| 行 | 現状 | 変更後 |
|----|------|--------|
| 5 | `pluginSinceBuild=253` | `pluginSinceBuild=262` |
| 6 | `platformVersion=2025.3.5` | `platformVersion=2026.2`(利用可能な最新の2026.2.xパッチを使用) |

`platformVersion` の引き上げは必須。262でモジュール分割された `intellij.platform.structureView.impl` は2026.2でしか存在せず、そこに対してコンパイル・`verifyPlugin` を実行するために2026.2をビルドターゲットにする必要がある。これにより2025.3(253)以前は非対応となるが、`pluginSinceBuild=262` がその方針を明示する。

### 2. `build.gradle.kts`

- **21-27行の `intellijPlatform { }`(`dependencies` ブロック内)に `bundledModule(...)` を追加**:
  ```kotlin
  intellijPlatform {
      intellijIdea(providers.gradleProperty("platformVersion"))
      testFramework(TestFrameworkType.Platform)
      bundledModule("intellij.platform.structureView.impl")   // 追加
  }
  ```
  モジュール名 `intellij.platform.structureView.impl` は一次情報(deepwikiによる `api-dump.txt` 実測 + リリースタグのディレクトリ `platform/structure-view-impl`)から確定した値。念のため実装時に `./gradlew printBundledModules`(タスク名が異なる場合は解決済みプラットフォームのモジュール一覧)で正確な綴りを確認し、後述の plugin.xml と**必ず一致**させる。

- **253固有ワークアラウンドの再評価**(2026.2でのクリーンビルド結果で判断。まず現状のまま残してビルドし、結果を見て対応):
  - 29-34行: fastutil `8.5.18-jb1` / kotlinx-collections-immutable `0.5.0` の `compileOnly`(IJPL-217565: 253.xでCoreから分離された対応)
  - 123-125行: grammarkit の `classpath(configurations.named("compileClasspath"))` ワークアラウンド(上記deps由来)
  - → `generateReviewParser` とコンパイルが通れば現状維持。不要になっていれば削除、まだ必要ならバージョンを更新しコメントを262基準に書き換える。ここは本タスクの主目的ではなく、ビルドを壊さないための追従。

### 3. `src/main/resources/META-INF/plugin.xml`

- **15行 `<depends>com.intellij.modules.platform</depends>` の直後に V2 `<dependencies>` ブロックを追加**:
  ```xml
  <depends>com.intellij.modules.platform</depends>

  <dependencies>
      <module name="intellij.platform.structureView.impl"/>
  </dependencies>
  ```
  V1の `<depends>` と V2の `<dependencies>` は共存可能。`bundledModule(...)` は「コンパイル時のクラスパス」、この `<module>` 宣言は「実行時のモジュール依存」を担うため両方必要。モジュール名は build.gradle.kts と一致させる。

## 検証(Development Workflow)

### Step 1: gradle.properties / build.gradle.kts / plugin.xml を上記の通り変更

### Step 2: モジュール名の確認とビルド
1. `./gradlew printBundledModules`(または解決済みプラットフォームのモジュール一覧)で `intellij.platform.structureView.impl` の綴りを確認。異なる場合は build.gradle.kts と plugin.xml の両方を揃えて修正。
2. `./gradlew build` — lexer/parser生成(`generateReviewLexer`/`generateReviewParser`)・Kotlin/Javaコンパイル・既存テスト(`ReviewStructureViewTest` 含む)が全て通ること。253固有ワークアラウンドの要否をここで判定。
3. `./gradlew buildPlugin` 後、`build/resources/main/META-INF/plugin.xml` に `since-build="262"` と `<module name="intellij.platform.structureView.impl"/>` が反映されていることを確認。

### Step 3: verifyPlugin(受け入れ条件)
- `./gradlew verifyPlugin` がIU-262に対して**グリーン**になること。
- `verifyPlugin` が検証対象IDE未指定でスキップ/失敗する場合は、`intellijPlatform { }` に最小限の検証設定を追加:
  ```kotlin
  pluginVerification {
      ides {
          recommended()   // sinceBuild(262)相当のIDEを検証対象にする
      }
  }
  ```
- **フォールバック**: モジュール依存を追加しても `verifyPlugin` が `impl.common` を内部API違反として弾く場合は、A案では解消不能。その場合のみ方針B(`ReviewStructureViewElement.kt` を `StructureViewTreeElement` + `ItemPresentation` の直接実装へ書き換え、impl依存を排除)へ切り替える。※一次確認では該当メソッドは `@ApiStatus.Internal` 対象外であり、Bへの切替は発生しない見込み。

### Step 4: Finalize

1. `## Implementation Notes` をプランファイル末尾に追記し、以下を記載:
    - Design decisions: 仕様が曖昧だった箇所での判断(例: モジュール名の確定根拠、`platformVersion` の具体値)
    - Deviations: 仕様から意図的に逸脱した箇所とその理由
    - Tradeoffs: 検討した代替案(方針B)とその選定理由、253固有ワークアラウンドの扱い
    - Test changes: 実装フェーズで変更したテストコード(想定: なし)
    - Open questions: 確認・修正してほしい点
2. このプランファイルを `docs/plans/2026-07-23-idea-262-structure-view-module-migration.md` にコピー
3. 該当する場合は `docs/spec/`、`docs/design/` を更新(本変更は挙動を変えないため通常は不要。旧プラン `2026-07-06-...` の Open questions がクローズされた旨を必要なら追記)
4. `CHANGELOG.md` の `## [Unreleased]` にエントリを追記(`.github/` 配下・`src/test/` 配下は対象外)
    - 例: `### Changed` に「最小サポートIDEを2026.2(build 262)へ引き上げ。Structure Viewが依存する `intellij.platform.structureView.impl` モジュールを明示依存に追加」

## 影響ファイル一覧

- `gradle.properties`(sinceBuild / platformVersion)
- `build.gradle.kts`(bundledModule追加、253ワークアラウンド再評価)
- `src/main/resources/META-INF/plugin.xml`(`<dependencies><module>` 追加)
- `structure/` 配下・テスト: **無変更**

## Implementation Notes

### Design decisions

- `PsiTreeElementBase` の移設先モジュールは、一次情報(2026.2の実IDE配布物・`idea/262.8665.258` タグのソース)を直接確認し、`intellij.platform.structureView`(`.impl` サフィックス無し)と確定した。`intellij.platform.structureView.impl` という名前のモジュールは実在するが「リネーム後の互換用ダミーモジュール」であり、実装のクラスパスには乗らない。
- `platformVersion` は実装時点でIntelliJ Platform Gradle Pluginが解決できた最新の2026.2パッチ(`IU-262.8665.258`)を採用した。
- テストサンドボックスの `intellij.spellchecker.xml` → `intellij.libraries.lucene.common` 除外連鎖を解消するため、`build.gradle.kts` に `testBundledPlugin("intellij.libraries.misc.plugin")`(`intellij.libraries.lucene.common` の提供元プラグイン)を追加した。これは `test` スコープのみの追加で、配布される plugin.xml やプロダクションの依存関係には影響しない。

### Deviations

- **事前確認でユーザーが選択したA案(`PsiTreeElementBase` を使い続け、モジュール依存を追加)を、実装途中でB案(`StructureViewTreeElement`/`ItemPresentation` の直接実装への書き換え)へユーザーへの再確認なしに切り替えた。** 理由: `bundledModule`/`bundledPlugin`/`testBundledPlugin` や plugin.xml の `<dependencies><module>` 宣言をあらゆる組み合わせで試したが、JetBrains Plugin Verifier(verifier-cli 1.409)が宣言したモジュール依存を認識せず、`verifyPlugin` が一貫して `impl.common` パッケージ配下の `PsiTreeElementBase` を解決不能として報告し続けた(`dependencies.txt` を直接確認し、宣言した `<module>` 依存が検証器自身の解決済み依存ツリーに一切反映されていないことを確認済み)。本プランの受け入れ条件は「`verifyPlugin` がグリーンになること」であり、A案ではこれを満たせないことが実証されたため、JetBrains自身が配布しているプラグイン(`EditorConfigStructureViewElement`)と同じパターンに倣い、`impl.common` パッケージを一切参照しないB案へ切り替えた。
- 切替後、`./gradlew verifyPlugin --no-configuration-cache` は `IU-262.8665.258 against com.nowsprinting.intellij-review:1.0.0-SNAPSHOT: Compatible` を報告し、受け入れ条件を満たすことを確認した。

### Tradeoffs

- A案(`PsiTreeElementBase` 継続利用)は、値ベースの `equals`/`hashCode`(PSI再解析をまたいだツリー選択状態の保持に寄与)を無償で得られる利点があったが、`verifyPlugin` を通せないため採用不可と判断した。
- B案では `equals`/`hashCode` を独自実装していない(デフォルトの参照同一性のまま)。`ReviewStructureViewTest` の全ケース(ネスト・column判定含む)は変更なしに成功しており、現時点で機能的な退行は確認されていない。将来IDE上でツリー選択状態の保持に問題が見られた場合は、`getValue()` をキーにした `equals`/`hashCode` の追加を検討する。
- `build.gradle.kts` の253.x(2025.3)固有ワークアラウンド(fastutil / kotlinx-collections-immutable の `compileOnly` 追加、grammarkitの `classpath(configurations.named("compileClasspath"))`)は、2026.2でも依然として必要であることを実験的に確認した(一時的に削除して `./gradlew clean build` を実行したところ `generateReviewParser` が `NoClassDefFoundError: com/intellij/openapi/util/KeyedExtensionCollector` で失敗)。そのため変更せず維持した。
- テストサンドボックスの除外連鎖問題は、当初Structure View移行が原因と疑って `bundledModule`/`bundledPlugin`/`testBundledPlugin` の組み合わせを試行錯誤したが、Structure View関連の依存宣言を完全にゼロにした状態(B案)でも同一の除外が再現することを `idea.log` で確認し、Structure Viewとは無関係な `platformVersion=2026.2` 自体の問題と判明した。真因は `intellij.libraries.lucene.common` がテストサンドボックスに提供されていないことで、`testBundledPlugin("intellij.libraries.misc.plugin")` の追加で解消し、全78件のテストが成功するようになった。

### Test changes

なし(`structure/` 配下の実装とテストコードはPSI・公開APIとして外部から見える挙動を変えていない。`ReviewStructureViewTest` は無変更のまま全件成功)。

### Open questions

- **`./gradlew verifyPlugin`(設定キャッシュ有効、CIが実際に叩くコマンド)は現状失敗する。** `gradle.properties` の `org.gradle.configuration-cache=true` 環境で `patchPluginXml` タスクが `DefaultProject` 型をシリアライズできずConfiguration Cacheの保存に失敗するため。これは本タスク由来の問題ではなく、`git stash` で移行前のmaster(`platformVersion=2025.3.5`)に戻して同じコマンドを実行しても同一のエラーが再現することを確認済み(**既存の別問題**)。本タスクでは対応していない。回避策: (a) `--no-configuration-cache` を付けて実行する、(b) `patchPluginXml` タスクに `notCompatibleWithConfigurationCache` を設定する、(c) IntelliJ Platform Gradle Plugin側の修正を待つ。CI(`build.yml`)がこの問題の影響を受けていないか別途確認が必要。
- `testBundledPlugin("intellij.libraries.misc.plugin")` は2026.2のテストサンドボックス構成の一時的な不備に対する回避策の可能性がある。IntelliJ Platform Gradle Pluginの将来バージョンで不要になっていないか、次回IDEバージョン更新時に再確認したい。
