# FileCollector (Groovy Swing)

Groovy + Swing で `.jar` ファイルをかき集めて 1 つの ZIP にまとめるツールです。

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

- 「検索するフォルダ」  
  `.jar` を探しに行くルートフォルダを指定します（サブフォルダも再帰的に検索）。
- 「パターン (glob)」  
  検索パターンです。デフォルトは `*.jar` です。`*.war` なども指定可能。
- 「出力ZIPファイル」  
  まとめて出力する ZIP ファイルのパスを指定します。
- 下部ログ  
  収集したファイルや処理状況が表示されます。

