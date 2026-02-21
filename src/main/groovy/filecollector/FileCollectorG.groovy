import java.awt.*
import java.awt.event.*
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.List
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.plaf.FontUIResource

/**
 * ファイル収集ツール エントリーポイント
 */
class FileCollector {
    static void main(String[] args) {
        // UIの初期設定
        SwingUtilities.invokeLater {
            try {
                // システムのルックアンドフィールを適用
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                // フォントサイズを一括調整 (少し大きく)
                setUIFontScaleSize("Yu Gothic UI", 1.2f)
            } catch (Exception e) {
                e.printStackTrace()
            }
            // メインフレームの表示
            new FileCollectorFrame().setVisible(true)
        }
    }

    /**
     * UIのフォントサイズを一括でスケーリングするユーティリティメソッド
     * @param fontName フォント名 (例: "Yu Gothic UI")
     * @param multiplier 倍率 (例: 1.2f)
     */
    static void setUIFontScaleSize(String fontName, float multiplier) {
        def defaults = UIManager.getDefaults()
        defaults.keys().toList().each { key ->
            if (defaults.get(key) instanceof FontUIResource) {
                def font = (FontUIResource) defaults.get(key)
                if (font != null) {
                    defaults.put(key, new FontUIResource(fontName, font.style as int, (int)(font.size as float * multiplier)))
                }
            }
        }
    }
}

/**
 * ファイル収集ツール メイン画面クラス
 */
class FileCollectorFrame extends JFrame {

    // --- 定数 ---
    private static final String APP_TITLE = "File Collector Tool"
    private static final String HISTORY_FILE = System.getProperty("user.home") + "/.filecollector-history.txt"
    private static final String OUTPUT_BASE_DIR = System.getProperty("user.home") + "/FileCollector"

    // --- UIコンポーネント ---
    private JComboBox<String> folderCombo
    private JTextArea patternArea
    private JTextField suffixField
    private JCheckBox deleteExistCheck
    private JList<File> resultList
    private DefaultListModel<File> listModel
    private JTextArea logArea
    private JButton searchButton
    private JButton copyButton
    private JButton treeButton

    // --- 状態管理 ---
    private AtomicBoolean isProcessing = new AtomicBoolean(false)

    FileCollectorFrame() {
        setTitle(APP_TITLE)
        // DISPOSE_ON_CLOSE: ウィンドウを閉じても JVM を終了させない（呼び出し元プロセスが終了しない）
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)
        setSize(900, 700)
        setLocationRelativeTo(null) // 画面中央に配置
        setLayout(new BorderLayout())

        // UI構築
        initTopPanel()
        initCenterPanel()
        
        // 履歴のロード
        loadHistory()
        
        // 終了時の処理（履歴保存）
        addWindowListener(new WindowAdapter() {
            @Override
            void windowClosing(WindowEvent e) {
                saveHistory()
            }
        })
    }

    /**
     * 上部フォームエリアの構築 (GridBagLayout)
     */
    private void initTopPanel() {
        JPanel panel = new JPanel(new GridBagLayout())
        panel.setBorder(new EmptyBorder(10, 10, 10, 10))
        GridBagConstraints gbc = new GridBagConstraints()
        gbc.insets = new Insets(5, 5, 5, 5)
        gbc.fill = GridBagConstraints.HORIZONTAL

        // --- 1行目: 対象フォルダ ---
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        panel.add(new JLabel("対象フォルダ:"), gbc)

        gbc.gridx = 1; gbc.weightx = 1.0
        folderCombo = new JComboBox<>()
        folderCombo.setEditable(true)
        panel.add(folderCombo, gbc)

        gbc.gridx = 2; gbc.weightx = 0.0
        JButton browseBtn = new JButton("参照...")
        browseBtn.addActionListener { chooseFolder() }
        panel.add(browseBtn, gbc)

        gbc.gridx = 3; gbc.weightx = 0.0
        treeButton = new JButton("Tree出力")
        treeButton.addActionListener { doTreeOutput() }
        panel.add(treeButton, gbc)

        // --- 2行目: 抽出条件 (複数行) ---
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(new JLabel("抽出条件(glob):"), gbc)

        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0; gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        patternArea = new JTextArea(5, 40)
        patternArea.setText("")
        JScrollPane patternScroll = new JScrollPane(patternArea)
        panel.add(patternScroll, gbc)

        // --- 3行目: 拡張子追加文字 ---
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.WEST
        panel.add(new JLabel("拡張子追加文字:"), gbc)

        gbc.gridx = 1; gbc.weightx = 1.0
        suffixField = new JTextField(".txt") // デフォルト値 .txt とする
        suffixField.setToolTipText("コピー時のファイル名末尾に付与する文字 (例: .txt)")
        panel.add(suffixField, gbc)

        // --- 4行目: 操作ボタン ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT))
        
        deleteExistCheck = new JCheckBox("既存ファイル削除", true)
        buttonPanel.add(deleteExistCheck)

        searchButton = new JButton("抽出 (Search)")
        searchButton.addActionListener { doSearch() }
        buttonPanel.add(searchButton)

        copyButton = new JButton("ファイル出力 (Copy)")
        copyButton.addActionListener { doCopyFilesToFolder() }
        buttonPanel.add(copyButton)

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 4
        panel.add(buttonPanel, gbc)

        add(panel, BorderLayout.NORTH)
    }

    /**
     * 中央エリアの構築 (JSplitPane)
     */
    private void initCenterPanel() {
        // --- 上部: 結果リスト ---
        listModel = new DefaultListModel<>()
        resultList = new JList<>(listModel)
        
        JPanel listPanel = new JPanel(new BorderLayout())
        JPanel listHeader = new JPanel(new FlowLayout(FlowLayout.LEFT))
        JButton removeBtn = new JButton("選択削除")
        removeBtn.addActionListener {
            List<File> selected = resultList.getSelectedValuesList()
            selected.each { listModel.removeElement(it) }
        }
        listHeader.add(new JLabel("抽出結果一覧:"))
        listHeader.add(removeBtn)
        listPanel.add(listHeader, BorderLayout.NORTH)
        listPanel.add(new JScrollPane(resultList), BorderLayout.CENTER)

        // --- 下部: ログエリア ---
        logArea = new JTextArea()
        logArea.setEditable(false)
        JPanel logPanel = new JPanel(new BorderLayout())
        logPanel.add(new JLabel("ログ:"), BorderLayout.NORTH)
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER)

        // スプリットペイン設定
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listPanel, logPanel)
        splitPane.setDividerLocation(350)
        splitPane.setResizeWeight(0.7)

        add(splitPane, BorderLayout.CENTER)
    }

    // --- 機能ロジック ---

    /**
     * フォルダ選択ダイアログ
     */
    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser()
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
        String current = (String) folderCombo.getEditor().getItem()
        if (current) {
            chooser.setCurrentDirectory(new File(current))
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            folderCombo.getEditor().setItem(chooser.getSelectedFile().getAbsolutePath())
        }
    }

    /**
     * 検索処理
     */
    private void doSearch() {
        if (isProcessing.get()) return
        
        String targetPathStr = (String) folderCombo.getEditor().getItem()
        if (!targetPathStr) {
            log("エラー: 対象フォルダを指定してください。")
            return
        }
        File targetDir = new File(targetPathStr)
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            log("エラー: フォルダが存在しません: $targetPathStr")
            return
        }

        // 履歴に追加
        updateHistory(targetPathStr)

        String[] patterns = patternArea.getText().split("\n").collect { it.trim() }.findAll { it }
        if (!patterns) {
            log("エラー: 抽出条件を入力してください。")
            return
        }

        listModel.clear()
        isProcessing.set(true)
        searchButton.setEnabled(false)
        log("検索開始: $targetPathStr")

        // 別スレッドで実行
        Thread.start {
            try {
                Path startPath = Paths.get(targetPathStr)
                List<PathMatcher> matchers = patterns.collect { pattern ->
                    // 独自仕様: ... または /.../ を ** に置換
                    String normalized = toGlobPattern(pattern)
                    // glob構文でPathMatcherを作成
                    return FileSystems.getDefault().getPathMatcher("glob:" + normalized)
                }

                Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
                    @Override
                    FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path relPath = startPath.relativize(file)
                        // いずれかのパターンにマッチするか確認
                        boolean matched = matchers.any { matcher -> matcher.matches(relPath) }
                        
                        if (matched) {
                            SwingUtilities.invokeLater {
                                listModel.addElement(file.toFile())
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }

                    @Override
                    FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        SwingUtilities.invokeLater { log("アクセスエラー: $file") }
                        return FileVisitResult.CONTINUE
                    }
                })

                SwingUtilities.invokeLater {
                    log("検索完了: ${listModel.size()} 件見つかりました。")
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater {
                    log("検索中にエラーが発生しました: ${e.message}")
                    e.printStackTrace()
                }
            } finally {
                SwingUtilities.invokeLater {
                    isProcessing.set(false)
                    searchButton.setEnabled(true)
                }
            }
        }
    }

    /**
     * ユーザー入力パターンを glob 用に正規化する。
     * - xx/../yy → xx**yy、/.../ および ...（前後に半角空白可）→ ** に置換
     * - 先頭に ** を付与して部分一致相当にする
     */
    private static String toGlobPattern(String raw) {
        if (raw == null || raw.isEmpty()) return ""
        String s = normalizePath(raw.trim())
        s = s.replace("/../", "**")           // xx/../yy → xx**yy
        s = s.replace("/.../", "**")          // /.../ → **
        s = s.replaceAll(/\/\s*\.\.\.\s*\//, "**")   // / ... /（空白あり）→ **
        s = s.replaceAll(/\s*\.\.\.\s*/, "**")      // ...（前後空白可）→ **
        return s.isEmpty() ? "" : "**" + s    // 部分一致相当のため先頭に ** 付与
    }

    /** パス区切り \ と / を同義として正規化 */
    private static String normalizePath(String path) {
        return path == null ? "" : path.replace("\\", "/")
    }    

    /**
     * ファイル出力処理
     */
    private void doCopyFilesToFolder() {
        if (listModel.isEmpty()) {
            log("出力するファイルがありません。先に検索してください。")
            return
        }

        File destDir = new File(OUTPUT_BASE_DIR)
        String suffix = suffixField.getText().trim()
        boolean deleteExist = deleteExistCheck.isSelected()

        log("ファイル出力開始...")

        Thread.start {
            try {
                // 出力先フォルダ作成
                if (!destDir.exists()) {
                    destDir.mkdirs()
                } else if (deleteExist) {
                    // フォルダ内を空にする
                    destDir.eachFile { it.delete() }
                    log("既存ファイルを削除しました。")
                }

                int count = 0
                for (int i = 0; i < listModel.size(); i++) {
                    File srcFile = listModel.get(i)
                    if (!srcFile.exists()) continue

                    // ファイル名の決定（重複回避とサフィックス付与）
                    String baseName = srcFile.getName()
                    String targetName = baseName + suffix
                    File destFile = new File(destDir, targetName)

                    // 重複回避ロジック (_2, _3...)
                    int dupCount = 2
                    while (destFile.exists()) {
                        // 拡張子と名前を分離して連番を振るのが一般的だが、
                        // ここでは要件「末尾に付与」の整合性を取るため、単純に全体の末尾に追加するか、
                        // あるいは suffix の前に連番を入れるかで実装する。
                        // 今回は user.home/FileCollector にフラットに置くため、
                        // 「元のファイル名 + 連番 + サフィックス」とする。
                        targetName = baseName + "_" + dupCount + suffix
                        destFile = new File(destDir, targetName)
                        dupCount++
                    }

                    // コピー実行
                    Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    count++
                    
                    // 進捗ログ (適当な間隔で)
                    if (count % 10 == 0) {
                        int current = count
                        SwingUtilities.invokeLater { log("コピー中: $current / ${listModel.size()}") }
                    }
                }

                SwingUtilities.invokeLater {
                    log("コピー完了: $count 件")
                    try {
                        Desktop.getDesktop().open(destDir)
                    } catch (Exception e) {
                        log("フォルダを開けませんでした: ${e.message}")
                    }
                }

            } catch (Exception e) {
                SwingUtilities.invokeLater {
                    log("コピー処理中にエラーが発生しました: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Tree出力処理
     */
    private void doTreeOutput() {
        String targetPathStr = (String) folderCombo.getEditor().getItem()
        if (!targetPathStr) {
            log("対象フォルダを指定してください。")
            return
        }
        File rootDir = new File(targetPathStr)
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            log("有効なフォルダではありません。")
            return
        }

        log("Tree生成中...")
        
        Thread.start {
            try {
                StringBuilder sb = new StringBuilder()
                sb.append(rootDir.getName()).append("\n")
                
                generateTreeRecursive(rootDir, "", sb)
                
                // ファイル保存
                File outputDir = new File(OUTPUT_BASE_DIR)
                boolean deleteExist = deleteExistCheck.isSelected()
                
                // 出力先フォルダ作成
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                } else if (deleteExist) {
                    // フォルダ内を空にする
                    outputDir.eachFile { it.delete() }
                    log("既存ファイルを削除しました。")
                }
                
                String treeFileName = rootDir.getName() + "_tree.txt"
                File treeFile = new File(outputDir, treeFileName)
                treeFile.setText(sb.toString(), "UTF-8")
                
                SwingUtilities.invokeLater {
                    log("Tree生成完了: ${treeFile.getAbsolutePath()}")
                    try {
                        Desktop.getDesktop().open(outputDir)
                    } catch (Exception e) {
                         // ignore
                    }
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater { log("Tree生成エラー: ${e.message}") }
            }
        }
    }

    /**
     * 再帰的にツリーテキストを生成
     */
    private void generateTreeRecursive(File dir, String prefix, StringBuilder sb) {
        File[] files = dir.listFiles()
        if (files == null) return

        // フォルダ、ファイルの順などでソートすると見やすい
        Arrays.sort(files, { f1, f2 ->
            if (f1.isDirectory() && !f2.isDirectory()) return -1
            if (!f1.isDirectory() && f2.isDirectory()) return 1
            return f1.name.compareToIgnoreCase(f2.name)
        } as Comparator)

        for (int i = 0; i < files.length; i++) {
            File f = files[i]
            boolean isLast = (i == files.length - 1)
            
            sb.append(prefix)
            sb.append(isLast ? "└── " : "├── ")
            sb.append(f.getName()).append("\n")
            
            if (f.isDirectory()) {
                generateTreeRecursive(f, prefix + (isLast ? "    " : "│   "), sb)
            }
        }
    }

    // --- ユーティリティ ---

    private void log(String msg) {
        logArea.append(msg + "\n")
        logArea.setCaretPosition(logArea.getDocument().getLength())
    }

    /**
     * 履歴の読み込み
     */
    private void loadHistory() {
        File file = new File(HISTORY_FILE)
        if (file.exists()) {
            file.eachLine { line ->
                if (line.trim()) {
                    folderCombo.addItem(line.trim())
                }
            }
        }
    }

    /**
     * 履歴の保存
     */
    private void saveHistory() {
        // 現在の入力を履歴に追加（重複排除のためSet経由）
        String current = (String) folderCombo.getEditor().getItem()
        Set<String> history = new LinkedHashSet<>()
        if (current && current.trim()) {
            history.add(current.trim())
        }
        for (int i = 0; i < folderCombo.getItemCount(); i++) {
            history.add(folderCombo.getItemAt(i))
        }

        try {
            File file = new File(HISTORY_FILE)
            file.withWriter('UTF-8') { writer ->
                history.each { writer.writeLine(it) }
            }
        } catch (Exception e) {
            System.err.println("履歴保存エラー: " + e.message)
        }
    }
    
    /**
     * 現在の入力を履歴コンボボックスの先頭に追加
     */
    private void updateHistory(String path) {
        // 既存にあれば削除して先頭に追加するなどの制御が可能だが、
        // ここでは単純にコンボボックスに存在しなければ追加する
        boolean exists = false
        for(int i=0; i<folderCombo.getItemCount(); i++) {
            if (folderCombo.getItemAt(i) == path) {
                exists = true
                break
            }
        }
        if (!exists) {
            folderCombo.addItem(path)
        }
    }
}