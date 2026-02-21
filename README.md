# FileCollector (Groovy Swing)

Groovy + Swing で作ったファイル収集ツールです。対象フォルダ内のファイルを glob パターンで絞り込み、一覧表示・tree 出力・ファイル出力を行います。

## ビルド & 実行（Maven 実行可能 JAR）

1. **前提**: Java 17 以上、Maven がインストールされていること（`mvn -v` で確認）。
2. プロジェクトフォルダへ移動:

   ```bash
   cd FileCollector
   ```

3. JAR をビルド:

   ```bash
   mvn -q package
   ```

4. `target/filecollector-1.0.0-shaded.jar` が生成されるので、次のように実行:

   ```bash
   java -jar target/filecollector-1.0.0-shaded.jar
   ```

## 直接 Groovy スクリプトとして起動する場合（おまけ）

```bash
groovy src/main/groovy/filecollector/FileCollector.groovy
```

## 画面の説明

| 項目 | 説明 |
|------|------|
| **対象フォルダ** | 検索対象のルートフォルダ（サブフォルダも再帰的に検索）。履歴は `~/.filecollector-history.txt` に保存されます。 |
| **抽出条件(glob, 複数可)** | glob パターン（1 行 1 パターン）。先頭に `**` が自動付与され、パス中のどこかにマッチすれば抽出されます。 |
| **拡張子 追加文字** | ファイル出力時に、各ファイル名の末尾に付加する文字（例: `.txt`）。 |
| **既存ファイル削除** | ON の場合、出力前に出力先フォルダの中身を削除してから出力します。 |
| **抽出** | 対象フォルダを走査し、条件に合うファイルを一覧表示します。 |
| **ファイル出力** | 抽出結果を `user.home/FileCollector/` にコピーし、フォルダを開きます。 |
| **tree ファイル出力** | 対象フォルダの構造を `user.home/FileCollector/<フォルダ名>.tree.txt` に出力し、フォルダを開きます。 |
| **選択削除** | 抽出結果リストで選択した行を結果から削除します。 |

## 抽出条件（glob パターン）の仕様

- **glob 固定**: 部分一致相当で検索するため、入力パターンの先頭に自動で `**` が付与されます。
- **パターン変換**:
  - `xx/../yy` → `xx**yy`
  - `xx/.../yy` や `xx/ ... /yy`（`...` の前後に半角空白可）→ `xx**yy`
- 例: `target` と入力すると `**target` としてマッチ（パスに `target` を含むファイルが抽出されます）。

## 出力先

- **ファイル出力**: `%USERPROFILE%\FileCollector\`（Windows） / `~/FileCollector/`（他 OS）
- **tree 出力**: 上記フォルダ内に `<対象フォルダ名>.tree.txt`

## その他

- ウィンドウを閉じても JVM を終了させず、呼び出し元プロセスが継続するよう `DISPOSE_ON_CLOSE` を使用しています。
