# FileCollector

Java + Swing で作ったファイル収集ツールです。  
`フォルダA` / `フォルダB` の2つを対象に、glob 条件で抽出し、一覧表示・tree 出力・ファイル出力・クリップボード出力を行います。

## 主な仕様（現在）

- 検索対象は `フォルダA` / `フォルダB` の2系統
- 抽出結果の先頭ラベルは各タイトル欄（例: `移行前` / `移行後`）
- `同名ファイル除外` は **A内/B内それぞれで判定**（A-B跨ぎ同名は除外しない）
- `tree出力` は1ボタンで A/B を連続処理
- `tree出力` 時は出力先を毎回クリーンしてから生成
- `ファイルに出力` 時はファイル名先頭に `<タイトル>_` を付与
- クリップボード置換で `#{title}` が利用可能

## 処理概要

### 起動時

1. レイアウト構築後、次を読み込んで初期表示します。
   - 共通履歴: `~/.filecollector/history.txt`
   - A前回値: `~/.filecollector/source-last-a.txt`
   - B前回値: `~/.filecollector/source-last-b.txt`
   - 各フォーム設定（抽出条件、除外条件、クリップボードテンプレート、拡張子追加文字、A/Bタイトル など）
2. 履歴候補は A/B 共通、前回選択値は A/B 独立でコンボに反映します。

### ファイル抽出（`ファイル抽出` / `Ctrl+Enter`）

1. `フォルダA` / `フォルダB` のうち入力済みのものを対象にします（両方空はエラー）。
2. 各対象フォルダのファイル一覧キャッシュ（`~/.filecollector/{hash}.filelist.txt`）を利用して include/exclude 判定します。
3. `同名ファイル除外` ON のとき、**同一フォルダ内でのみ**同名を除外します。
4. 抽出結果は `[タイトル] 相対パス` 形式で表示します。

### クリップボード出力（`クリップボード出力`）

選択したファイル内容を連結してコピーします。  
先頭/末尾テンプレートでは以下のプレースホルダが使えます。

- `#{title}`: A/B タイトル
- `#{ext}`: 拡張子（ドットなし）
- `#{filename}`: ファイル名
- `#{filepath}`: 対象フォルダからの相対パス

### ファイルに出力（`ファイルに出力`）

1. 抽出結果の先頭から「1回あたり」の件数を `~/.filecollector/FileCollector/` にコピーします。
2. `既存ファイル削除` ON のとき、出力前に出力先をクリーンします。
3. 出力ファイル名は `<タイトル>_<元ファイル名>`（必要に応じて拡張子追加文字を付加）です。
4. 同名が複数ある場合は `(2)` など連番を付けて重複回避します。

### tree 出力（`tree出力`）

1. 出力先 `~/.filecollector/FileCollectorTree/` をクリーンします。
2. 入力済みの A/B を順に処理し、`<フォルダ名>.tree.txt` を出力します。
3. 出力後にフォルダを開きます。

## 履歴・設定ファイル

設定ディレクトリ: `~/.filecollector/`

- `history.txt` : A/B共通のフォルダ履歴
- `source-last-a.txt` : Aコンボの前回値
- `source-last-b.txt` : Bコンボの前回値
- `source-title-a.txt` : Aタイトルの前回値
- `source-title-b.txt` : Bタイトルの前回値
- `output-count.txt` : ファイル出力の1回あたり件数
- `pattern.txt` : 抽出条件
- `exclude-pattern.txt` : 除外条件
- `clipboard-prefix.txt` : 先頭付加
- `clipboard-suffix.txt` : 末尾付加
- `file-suffix.txt` : 拡張子追加文字
- `file-suffix-exclude-extensions.txt` : 拡張子追加対象外

## ビルド & 実行

### 実行可能 JAR

```bash
mvn -q package
java -jar target/filecollector-1.0.0-shaded.jar
```

### 開発時実行

```bash
mvn compile exec:java
```
