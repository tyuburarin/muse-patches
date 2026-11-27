# Muse Patches（X / Piko 向け追加パッチ）

X（com.twitter.android）向け Morphe パッチ集 Piko に対する、個人用の追加パッチ。
Piko 本体とは別バンドルとして Morphe に追加して使う。

- 現在の版: 0.6.4（copy-fix4 ベース。表示中＝翻訳後テキストのコピーに対応）
- 内容: 共有メニューへのアクション追加、投稿ヘッダーのボタン（Grok / コピー / Muse に保存）の個別ON/OFF、通話中の動画再生許可、メディアURLコピー（プリビルトのみ: assets/media.jar + media-url-copy.dex、ソースなし）
- ビルド: Gradle を使わない手組みパイプライン。`build.sh`（詳細は BUILD.md）。JDK 17、Morphe Desktop の fat jar、Android SDK が必要
- 対象の X バージョンは各パッチの compatibleWith を参照
