# FileCollector

Java + Swing で作ったファイル収集ツールです。対象フォルダ内のファイルを glob パターンで絞り込み、一覧表示・tree 出力・ファイル出力・クリップボード出力を行います。

## 処理概要

### 起動時

1. レイアウト構築後、次を読み込んで初期表示する。
   - **対象フォルダ履歴**: `~/.filecollector/history.txt` から有効なディレクトリのみ読み、コンボの候補とする。
   - **1回あたりの件数**: `~/.filecollector/output-count.txt` の値をコンボに反映（無ければ "10"）。
   - **フォーム入力の復元**: 抽出条件・除外条件・先頭付加・末尾付加・拡張子追加文字を、それぞれ `pattern.txt` / `exclude-pattern.txt` / `clipboard-prefix.txt` / `clipboard-suffix.txt` / `file-suffix.txt`（いずれも `~/.filecollector/` 下）から読み、該当入力欄に表示する（ファイルが無い項目はデフォルトのまま）。拡張子追加文字の対象外は `file-suffix-exclude-extensions.txt` から読み込み、画面の「拡張子追加 対象外」テキストボックスにカンマ区切りで表示する（ファイルが無い場合は初期値を使用）。

### ファイル抽出（「ファイル抽出」ボタン / Ctrl+Enter）

1. 対象フォルダ・抽出条件（1行以上）を必須チェックする。
2. 対象フォルダ用の**ファイル一覧キャッシュ**を用意する。  
   - キャッシュが無ければ、フォルダを再帰走査して全ファイルの絶対パスを `~/.filecollector/{hash}.filelist.txt` に書き出す。  
   - 以降はこの一覧を読み、glob マッチのみ行う（再走査しない）。
3. **抽出条件（include）**: キャッシュの各行を、入力された glob パターン（先頭に `**` 付与・`...`→`**` 変換済み）でマッチさせ、一致したファイルだけを候補とする。
4. **除外条件**: 除外条件が 1 行以上ある場合、上記候補のうちいずれかの除外パターンにマッチするものを除く。
5. **重複扱い**: 「同名ファイル除外」ON のとき、候補のうち**ファイル名が既にリストにあるもの**は追加しない（ログに「追加（重複）: パス」を出す）。OFF のときは同名もすべてリストに追加する。
6. 既に抽出結果リストにある**同一 Path** は常に追加しない。
7. マッチしたファイルを抽出結果リストに追加し、件数ラベルとログを更新する。処理は別スレッドで実行し、完了後にボタン有効状態を戻す。

### クリップボード出力（「クリップボードに出力」）

1. 抽出結果リストで選択された行に対応するファイルを、対象フォルダ基準で取得する。
2. 「文字付加する」が ON のとき、各ファイルの内容の前後に「先頭付加」「末尾付加」のテキストを付与する（`#{ext}` / `#{filename}` / `#{filepath}` を置換）。OFF のときは内容のみを結合する。
3. 結合した文字列をシステムクリップボードにコピーする。

### ファイルに出力（「ファイルに出力」）

1. 抽出結果リストの先頭から「1回あたり」で指定した件数までを、`~/.filecollector/FileCollector/` にコピーする。
2. 「既存ファイル削除」が ON のとき、出力先フォルダの中身を削除してからコピーする。
3. ファイル名は「拡張子追加文字」を付与したうえで、**同名が 2 件以上ある場合のみ `名前(1).ext` / `名前(2).ext` のように連番を付与して重複を避ける**。同名が 1 件だけの場合はそのままのファイル名で出力する。ただし、「拡張子追加 対象外」に列挙した拡張子のファイルには拡張子追加文字を付与しない。
4. コピーした分だけ抽出結果リストの先頭から削除し、件数・ボタン有効状態を更新する。その後、出力先フォルダをエクスプローラで開く。

### ファイル tree 出力（「ファイル tree 出力」）

1. 対象フォルダのディレクトリ構造を tree 形式（`├──` / `└──`）の行リストに変換する。
2. 「既存ファイル削除」が ON のとき、`~/.filecollector/FileCollectorTree/` の中身を削除してから出力する。
3. `<対象フォルダ名>.tree.txt` を上記フォルダに書き出し、フォルダをエクスプローラで開く。  
   - このときファイル一覧キャッシュを再生成する（tree 用にキャッシュを更新）。

### 終了時（ウィンドウを閉じる）

1. 現在の対象フォルダ（コンボの編集値）を履歴の先頭に追加し、`history.txt` に保存する。
2. **フォーム入力の保存**: 抽出条件・除外条件・先頭付加・末尾付加・拡張子追加文字・拡張子追加対象外を、上記と同じ `~/.filecollector/` 下の各ファイルに上書き保存する。

---

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

## 開発時（Maven で直接実行）

```bash
mvn compile exec:java
```

## 画面の説明

### 上段フォーム

| 項目 | 説明 |
|------|------|
| **対象フォルダ** | 検索対象のルートフォルダ（サブフォルダも再帰的に検索）。履歴は `~/.filecollector/history.txt` に保存されます。右側に履歴プルダウン、ラベルの右に「フィルタ条件（部分一致）」テキストボックスがあり、履歴を部分一致で絞り込めます（入力中はプルダウンが自動的に開きます）。 |
| **ファイル tree 出力** | 対象フォルダのディレクトリ構造を tree 形式で `~/.filecollector/FileCollectorTree/` に出力し、フォルダを開きます。 |
| **抽出条件・除外条件** | 同一行に左右配置。ラベルは各テキストエリアの上。抽出条件は glob パターン（1 行 1 パターン）。先頭に `**` が自動付与され、パス中のどこかにマッチすれば抽出されます。除外条件にマッチしたファイルは抽出結果に含めません。右端に「ファイル抽出」ボタンと「同名ファイル除外」チェックボックス（下揃え）。 |
| **クリップボード出力** | 行の左に「先頭付加」「末尾付加」のテキストエリア。先頭付加エリアはウィンドウ幅に連動して伸縮し、右端に「クリップボードに出力」ボタンと「先頭・末尾文字付加」チェックボックスが配置されます。置換パラメータが使えます。 |
| **拡張子追加 対象外** | クリップボード出力行の下に配置。ファイル出力時に拡張子追加文字を付加**しない**拡張子を、カンマ区切り 1 行で指定（例: `java, md, txt`）。初期値: `pdf,docx,doc,pptx,ppt,rtf,xlsx,xls,csv,json,txt,md,html,htm,xml`。ウィンドウ幅に連動して伸縮。 |
| **ファイル抽出** | 対象フォルダを走査し、条件に合うファイルを一覧表示します。 |

### 抽出結果エリア（中央）

| 項目 | 説明 |
|------|------|
| **抽出結果** | 抽出されたファイルの一覧（相対パス表示）。 |
| **選択削除** | リストで選択した行だけを結果から削除します。1 件以上選択時のみ有効。 |
| **選択以外削除** | 選択した行以外を削除し、選択行のみ残します。1 件以上選択時のみ有効。 |
| **AI用メッセージ** | 「これからファイルを分割して送ります。」＋ 件数 ＋ ファイル一覧（1/N  path）＋「これから順番に送ります。すべて送るまで解析しないでください。」をクリップボードにコピーします。抽出後にのみ有効。 |
| **拡張子追加文字** | ファイル出力時に、各ファイル名の末尾に付加する文字（例: `.txt`）。「拡張子追加 対象外」に列挙した拡張子のファイルには付加しない。 |
| **1回あたり・件** | ファイル出力時に一度にコピーする件数の上限。`~/.filecollector/output-count.txt` に保存されます。 |
| **ファイルに出力** | 抽出結果を `~/.filecollector/FileCollector/` にコピーし、フォルダを開きます。抽出後にのみ有効。 |
| **既存ファイル削除** | ON のとき、ファイル出力・tree 出力の前に出力先フォルダの中身を削除してから出力します。 |

## クリップボード出力の置換パラメータ

先頭付加・末尾付加のテキスト内で、次のプレースホルダがファイルごとに置換されます。

| パラメータ | 内容 |
|------------|------|
| `#{ext}` | 拡張子（ドットなし。例: `java`） |
| `#{filename}` | ファイル名（例: `Main.java`） |
| `#{filepath}` | 対象フォルダからの相対パス（`/` 区切り） |

## 抽出条件（glob パターン）の仕様

- **glob 固定**: 部分一致相当で検索するため、入力パターンの先頭に自動で `**` が付与されます。
- **パターン変換**:
  - `xx/../yy` → `xx**yy`
  - `xx/.../yy` や `xx/ ... /yy`（`...` の前後に半角空白可）→ `xx**yy`
- 例: `target` と入力すると `**target` としてマッチ（パスに `target` を含むファイルが抽出されます）。

## 出力先・設定

- **設定ディレクトリ**: `~/.filecollector/`
- **対象フォルダ履歴**: `~/.filecollector/history.txt`
- **1回あたりの件数**: `~/.filecollector/output-count.txt`
- **フォーム入力の保存**（次回起動時に復元）:  
  `pattern.txt`（抽出条件）, `exclude-pattern.txt`（除外条件）, `clipboard-prefix.txt`（先頭付加）, `clipboard-suffix.txt`（末尾付加）, `file-suffix.txt`（拡張子追加文字）
- **拡張子追加文字の対象外**（付加しない拡張子）:  
  `file-suffix-exclude-extensions.txt`（画面の「拡張子追加 対象外」テキストボックスと同期。カンマ区切り 1 行で保存・読み込み。未作成時は初期値 `pdf,docx,doc,pptx,ppt,rtf,xlsx,xls,csv,json,txt,md,html,htm,xml` を使用し、全ファイルに付加する場合は空にする）
- **ファイル出力**: `~/.filecollector/FileCollector/`
- **tree 出力**: `~/.filecollector/FileCollectorTree/` 内に `<対象フォルダ名>.tree.txt`
- **ファイル一覧キャッシュ**: `~/.filecollector/{対象フォルダパスのハッシュ}.filelist.txt`（抽出・除外のマッチング用。tree 出力時に再生成）

## メソッド一覧・構成

**FileCollector**（エントリ）と **FileCollectorFrame**（メインウィンドウ）の 2 クラスで構成される。メソッドは役割ごとに次のように分かれる。

### FileCollector（エントリポイント）

| メソッド | 説明 |
|----------|------|
| `static void main(String[] args)` | UI スレッドで FlatLaf をセットアップし、フレームを表示する。 |

### FileCollectorFrame — 初期化・UI

| メソッド | 説明 |
|----------|------|
| `initLayout()` | 上段フォーム（対象フォルダ・抽出条件・除外条件・クリップボード用）と中央（抽出結果リスト＋ログ）のレイアウトを構築する。 |
| `initActions()` | 各ボタン・コンボ・チェックボックス・フィルタ欄のリスナーを登録する。 |

### FileCollectorFrame — 抽出結果リスト

| メソッド | 説明 |
|----------|------|
| `updateResultCount()` | 抽出結果件数ラベルを現在のリスト件数で更新する。 |
| `updateResultListButtons()` | 抽出結果の有無に応じて「ファイルに出力」「AI用メッセージ」の有効/無効を更新する。 |
| `removeSelectedFromResult()` | リストで選択した行を抽出結果から削除する。 |
| `removeExceptSelectedFromResult()` | 選択行以外を削除し、選択行のみ残す。 |

### FileCollectorFrame — クリップボード・AI

| メソッド | 説明 |
|----------|------|
| `updateClipboardPrefixSuffixEnabled()` | 「文字付加する」の ON/OFF に応じて先頭付加・末尾付加のテキストエリアの有効/無効を切り替える。 |
| `doClipboardOutput()` | 選択ファイルの内容を結合し（オプションで先頭・末尾付加）、クリップボードにコピーする。 |
| `doAiMessage()` | AI 用の定型メッセージ＋ファイル一覧をクリップボードにコピーする。 |
| `applyClipboardPlaceholders(template, path, baseDir)` | テンプレート内の `#{ext}` / `#{filename}` / `#{filepath}` を置換する。（static） |

### FileCollectorFrame — 対象フォルダ・履歴

| メソッド | 説明 |
|----------|------|
| `getSourceDirText()` | コンボの編集値（対象フォルダパス）を取得する。 |
| `chooseSourceDir()` | フォルダ選択ダイアログを開き、選択パスをコンボに設定する。 |
| `isExistingDirectory(path)` | パスが存在するディレクトリかどうかを返す。（static） |
| `loadSourceHistory()` | `history.txt` から履歴を読み、コンボ候補を構築する。 |
| `saveSourceHistory()` | 現在の履歴を `history.txt` に保存する。 |
| `applySourceFilter(filterText)` | フィルタ文字列で履歴を部分一致絞り込み、コンボの項目を再構築する。 |
| `addSourceHistory(path)` | 指定パスを履歴の先頭に追加し、保存する。 |

### FileCollectorFrame — 設定の読み書き

| メソッド | 説明 |
|----------|------|
| `loadOutputCount()` | `output-count.txt` から 1 回あたりの件数を読み、コンボに反映する。 |
| `saveOutputCount()` | 現在の件数設定を `output-count.txt` に保存する。 |
| `getOutputCountValue()` | コンボから件数（文字列）を取得する。未入力・不正時は `"10"`。 |
| `getOutputCount()` | 件数を int で取得する（1 以上）。 |
| `loadFormSettings()` | 抽出条件・除外条件・先頭付加・末尾付加・拡張子追加文字をファイルから読み、各入力欄に復元する。「拡張子追加 対象外」は `file-suffix-exclude-extensions.txt` から読み、カンマ区切りでテキストボックスに表示する（ファイルが無い場合は初期値のまま）。 |
| `saveFormSettings()` | 上記フォーム入力を各ファイルに保存する。「拡張子追加 対象外」はカンマ区切り 1 行で `file-suffix-exclude-extensions.txt` に保存する。 |
| `updateFileSuffixExcludeExtensionsFromField()` | 「拡張子追加 対象外」テキストボックスの内容をパースし、対象外拡張子の Set を更新する。ファイル出力前に呼ばれる。 |
| `writeTextFile(path, content)` | 指定パスに UTF-8 で文字列を書き込む。（static） |

### FileCollectorFrame — メイン処理

| メソッド | 説明 |
|----------|------|
| `doSearch()` | 対象フォルダをキャッシュまたは再帰走査し、抽出条件・除外条件・同名除外を適用して結果をリストに追加する。 |
| `doCopyFiles()` | 抽出結果の先頭から指定件数を `FileCollector/` にコピーし、リストから削除する。同名時は `(1)` `(2)` 付与。 |
| `doFileListOutput()` | 対象フォルダの tree を `FileCollectorTree/` に出力し、フォルダを開く。 |

### FileCollectorFrame — パス・glob ユーティリティ（static）

| メソッド | 説明 |
|----------|------|
| `normalizePath(path)` | パス文字列の `\` を `/` に統一する。 |
| `parsePatternLines(text)` | 複数行テキストから trim 済みの非空行リストを返す（抽出・除外条件用）。 |
| `relativizeNormalized(root, p)` | root に対する相対パスを `/` 区切りで正規化した Path に変換する。 |
| `buildGlobMatchers(patterns)` | glob パターン文字列のリストから PathMatcher のリストを生成する。 |
| `pathMatchesAny(root, p, matchers)` | パスが matchers のいずれかにマッチするかどうかを返す。 |
| `toGlobPattern(raw)` | ユーザ入力（`...`→`**` 等）を glob 用に正規化し、先頭に `**` を付与する。 |
| `pathToFileListHash(root)` | キャッシュファイル名用のハッシュ文字列を生成する。 |
| `getFileListCachePath(root)` | 対象フォルダ用キャッシュファイルの Path を返す。 |

### FileCollectorFrame — キャッシュ・ファイル一覧

| メソッド | 説明 |
|----------|------|
| `ensureFileListCache(root, forceRebuild)` | キャッシュが無ければ作成、forceRebuild 時は再作成する。 |
| `buildAndWriteFileList(root)` | フォルダを再帰走査し、全ファイルの絶対パスをキャッシュファイルに書き出す。 |
| `findFilesFromFileList(root, patterns)` | キャッシュを読み、glob にマッチするファイルのみ返す。キャッシュが無ければ findFiles に委譲。 |
| `findFiles(root, patterns)` | キャッシュを使わずに root を再帰走査し、glob にマッチするファイルを返す。 |

### FileCollectorFrame — tree 出力

| メソッド | 説明 |
|----------|------|
| `buildTreeLines(root)` | フォルダ構造を tree 形式の行リスト（ルート名＋子要素）に変換する。 |
| `buildTreeRecursive(dir, prefix, lines)` | 指定ディレクトリ以下を再帰的に tree 行として lines に追記する。 |

### FileCollectorFrame — ファイル出力用（static）

| メソッド | 説明 |
|----------|------|
| `fileNameWithSuffix(fileName, suffix)` | ファイル名の末尾に拡張子追加文字を付与する。対象外拡張子リストに含まれる場合は付与しない。 |
| `uniqueFlatName(baseFileName, nameCount, nameTotalCount)` | 同名が 2 件以上ある場合のみ `(1)` `(2)` を付与したユニークな出力名を返す。 |

### FileCollectorFrame — ログ・エラー

| メソッド | 説明 |
|----------|------|
| `appendLog(msg)` | ログエリアに 1 行追記し、末尾にスクロールする。 |
| `getErrorMessage(t)` | 例外から表示用メッセージを取得する（cause チェーンをたどる）。（static） |
| `showError(msg)` | エラーダイアログを表示する。 |

---

## エラー表示

例外発生時は、メッセージが null の場合は cause チェーンをたどって表示するメッセージを探し、それでもなければ例外クラス名を表示します。

## その他

- JAR 実行時はウィンドウを閉じると JVM が終了します（`EXIT_ON_CLOSE`）。IDE などから起動した場合は `DISPOSE_ON_CLOSE` でウィンドウのみ閉じます。
