# Muse Patches — ビルド方法と依存関係

## 先に結論

この .mpp には **Gradle プロジェクトは存在しません**。元のビルド環境（Muse の VM）では
ネットワーク制約で Gradle デーモンが起動できなかったため、Gradle を使わず、
javac / kotlinc / d8 を直接叩く手作業パイプラインで .mpp を生成しました。
この zip は、そのパイプラインをそのまま再現できる形にまとめた「ビルド可能なプロジェクト」です。

- `build.sh` を実行すると `build/muse-patches-<VERSION>.mpp` が生成されます
- 2026-11-13 にこのスクリプトで実際に再ビルドし、Morphe Desktop 2.1.1 の
  `list-patches` で4パッチ（Post header buttons / Copy post text / Save to Muse /
  Copy media URL）が読み込めること、収録ファイル一覧が元の v0.6.2 と一致することを確認済みです

## 必要な依存ライブラリとバージョン

| 依存 | バージョン | 用途・入手先 |
|---|---|---|
| JDK | 17（Temurin 17.0.21 で実施） | コンパイル用。※Morphe jar の実行（list-patches 等）には JDK 21 が必要（jar が Java 21 向け） |
| Morphe Desktop | 2.1.1（fat jar `morphe.jar`） | パッチャー API のコンパイル用クラスパス。Morphe の GitHub リリースから入手 |
| kotlin-compiler-embeddable | 2.0.21 | パッチ側 Kotlin のコンパイル。**Gradle 8.13 配布物の `lib/` に同梱**されているものを流用 |
| kotlin-stdlib | 2.0.21 | 同上（Gradle 8.13 の `lib/`） |
| kotlinx-coroutines-core-jvm | 1.6.4 | kotlinc 起動用クラスパス（Gradle 8.13 の `lib/`） |
| trove4j | 1.0.20200330 | kotlinc 起動用クラスパス（Gradle 8.13 の `lib/`） |
| annotations（JetBrains） | 24.0.1 | kotlinc 起動用クラスパス（Gradle 8.13 の `lib/`） |
| Android SDK build-tools | 36.0.0（`d8`） | Java/Kotlin クラスの dex 化 |
| Android SDK platform | android-36（`android.jar`） | 拡張側 Java のコンパイル用クラスパス |

つまり Gradle 本体は使いませんが、**Gradle 8.13 の配布 zip を展開してその `lib/` を
Kotlin コンパイラ置き場として使う**のが一番簡単です（バージョンが一式揃います）。

### コンパイラフラグ（重要）

パッチ側 kotlinc には以下が必須です（`build.sh` に組み込み済み）:

- `-Xcontext-receivers` — パッチのソースが context receiver を使うため
- `-Xskip-metadata-version-check` / `-Xskip-prerelease-check` — Morphe jar 内の
  Kotlin メタデータがコンパイラ（2.0.21）より新しいため、これが無いと読み込めない
- `-jvm-target 11`

拡張側 javac には `-encoding UTF-8`（日本語文字列リテラルがあるため）。

## ビルド手順

```bash
# 環境に合わせて上書き（デフォルトは元のビルド環境のパス）
export JAVA_HOME=/path/to/jdk17
export GRADLE_LIB=/path/to/gradle-8.13/lib
export MORPHE_JAR=/path/to/morphe.jar        # Morphe Desktop 2.1.1
export ANDROID_JAR=/path/to/android-sdk/platforms/android-36/android.jar
export D8=/path/to/android-sdk/build-tools/36.0.0/d8

./build.sh                # → build/muse-patches-0.6.2.mpp
VERSION=0.6.3 ./build.sh  # 版を上げる場合（MANIFEST の Version に反映される）
```

パイプラインの中身:

1. 拡張（`src-java/**/*.java`）を javac → jar → d8 で `extensions/muse-patches.dex` に
2. パッチ（`src-kt/**/*.kt`）を kotlinc（embeddable）で `patches.jar` に
   （クラスパス = Morphe fat jar）
3. `patches.jar` と `assets/media.jar` を d8 でマージし、バンドル直下の `classes.dex` に
4. パッチの .class 一式＋ `classes.dex` ＋ `extensions/*.dex` ＋
   `META-INF/MANIFEST.MF`（Version / Patcher-Version: 2.0.1 など）＋
   `META-INF/main.kotlin_module` ＋ LICENSE / NOTICE を zip に固めて `.mpp` 完成

## .mpp の構造

.mpp は素の zip です。

```
META-INF/MANIFEST.MF        Name / Description / Version / Author / License / Patcher-Version: 2.0.1
META-INF/main.kotlin_module
classes.dex                 パッチクラスの dex（ルートの .class 一式と同内容）
local/muse/.../*.class      パッチ本体（Kotlin）のクラスファイル
local/xmediaurl/.../*.class Copy media URL パッチのクラスファイル
extensions/muse-patches.dex 拡張（アプリ側で動く Java コード）の dex
extensions/media-url-copy.dex Copy media URL 用の拡張 dex
LICENSE / NOTICE            Piko 由来
```

対象アプリは X（Twitter）12.19.1-release.0 です。

## 注意事項

- **Copy media URL パッチだけソースがありません。** このパッチは、ユーザーが提供した
  サンプル .mpp 由来のものを「まとめて一つに」した経緯があり、Kotlin ソースは
  残っていません。そのためコンパイル済み資産として `assets/media.jar`（パッチ側）と
  `assets/media-url-copy.dex`（拡張側）を同梱し、ビルド時にそのまま取り込みます。
  このパッチが不要なら、`build.sh` の media.jar 参照2箇所と assets の2ファイルを
  外せば、Muse 製3パッチだけのバンドルになります。
- **Piko との同時適用について**: 本バンドルは単体バンドルとして設計されています。
  パッケージを `local.muse.*` に分離し、拡張 dex は各パッチが
  `extendWith(Supplier)` で自バンドルのクラス.ローダから読み込む方式なので、
  Piko（公式 3.11.0）と同じ Morphe セッションで同時適用できます
  （v0.6.2 時点で、単体／Piko 併用の両順序で適用できることを検証済み）。
  なお Morphe はバンドルごとにクラス.ローダが分離されるため、別バンドルの
  パッチへの `dependsOn` や、相手バンドルの拡張クラス参照はできません。
  機能追加の際はこの範囲内で完結させてください。
- Morphe 本体側のユーティリティ（`app.morphe.util.*` の一部）は Morphe Desktop の
  jar には含まれていないため、同等品を `src-kt/local/muse/utils/MorpheCompat.kt` に
  自前実装しています。
