/**
 * ファイル収集ツール (FileCollector)
 * 対象フォルダ内のファイルを glob パターンで絞り込み、一覧表示・tree 出力・ファイル出力を行う Swing アプリ。
 */
package filecollector

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.security.MessageDigest
import java.util.List

import javax.swing.*
import javax.swing.border.EmptyBorder

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatLightLaf

class FileCollector {

    static void main(String[] args) {
        // UI スレッドでフレームを起動
        SwingUtilities.invokeLater {
            FlatLightLaf.setup()
            new FileCollectorFrame().setVisible(true)
        }
    }
}

/** メインウィンドウ。フォルダ指定・パターン入力・抽出・出力処理を行う */
class FileCollectorFrame extends JFrame {
    // --- UI コンポーネント ---
    private final JComboBox<String> sourceDirCombo = new JComboBox<>()   // 対象フォルダ（履歴付き）
    private final JTextArea patternArea = new JTextArea("", 6, 55)       // 抽出条件（glob、複数行可）
    private final JTextField zipSuffixField = new JTextField(".txt", 35) // ファイル出力時の拡張子追加文字
    private final JTextArea logArea = new JTextArea()
    private final DefaultListModel<String> fileListModel = new DefaultListModel<>()
    private final JList<String> fileList = new JList<>(fileListModel)
    private final JButton searchButton = new JButton("抽出")
    private final JComboBox<String> outputCountCombo = new JComboBox<>(["5", "10", "20", "50", "100"] as String[])
    private final JButton copyFilesButton = new JButton("ファイル出力")
    private final JButton fileListButton = new JButton("tree 出力")
    private final JButton removeSelectedButton = new JButton("選択削除")
    private final JButton removeExceptSelectedButton = new JButton("選択以外削除")
    private final JCheckBox clearBeforeOutputCheckBox = new JCheckBox("既存ファイル削除", true)
    // 抽出結果のファイル一覧（相対パス表示用の元データ）
    private List<Path> lastFoundFiles = new ArrayList<>()
    // 対象フォルダの履歴（CONFIG_DIR/history.txt に保存）
    private final List<String> sourceHistory = new ArrayList<>()
    // 設定・キャッシュ用ディレクトリ（ユーザホーム直下のツール専用フォルダ）
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".filecollector")
    // ファイル一覧キャッシュ・履歴・出力件数などは CONFIG_DIR 配下に配置
    private static final Path FILELIST_CACHE_DIR = CONFIG_DIR
    // ファイル出力（コピー）の出力先（CONFIG_DIR 配下）
    private static final Path FILE_OUTPUT_DIR = CONFIG_DIR.resolve("FileCollector")
    // tree 出力の出力先（CONFIG_DIR 配下）
    private static final Path TREE_OUTPUT_DIR = CONFIG_DIR.resolve("FileCollectorTree")
    // 1回あたりの出力件数設定を保存するファイル（CONFIG_DIR 配下）
    private static final Path OUTPUT_COUNT_FILE = CONFIG_DIR.resolve("output-count.txt")
    // 対象フォルダ履歴を保存するファイル（CONFIG_DIR 配下）
    private static final Path SOURCE_HISTORY_FILE = CONFIG_DIR.resolve("history.txt")

    FileCollectorFrame() {
        super("FileCollector")
        // 実行モード判定 (JAR vs IDE): JAR のときは EXIT_ON_CLOSE、IDE のときは DISPOSE_ON_CLOSE
        def resourceUrl = FileCollectorFrame.class.getResource(FileCollectorFrame.class.simpleName + ".class")
        boolean isJarExecution = resourceUrl != null && resourceUrl.toString().startsWith("jar:")
        setDefaultCloseOperation(isJarExecution ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE)
        setSize(800, 600)
        setLocationRelativeTo(null)

        initLayout()
        loadSourceHistory()
        loadOutputCount()
        initActions()
        addWindowListener(new WindowAdapter() {
            @Override
            void windowClosing(WindowEvent e) {
                addSourceHistory(getSourceDirText())
            }
        })
    }

    /** レイアウト構築。上段フォーム + 中央（結果リスト + ログ） */
    private void initLayout() {
        def content = new JPanel(new BorderLayout(8, 8))
        content.setBorder(new EmptyBorder(8, 8, 8, 8))
        setContentPane(content)

        def form = new JPanel()
        form.setLayout(new GridBagLayout())
        def c = new GridBagConstraints(
                insets: new Insets(4, 4, 4, 4),
                fill: GridBagConstraints.HORIZONTAL,
                weightx: 0.0,
                weighty: 0.0
        )

        int row = 0

        // 対象フォルダ行
        c.gridx = 0; c.gridy = row; c.anchor = GridBagConstraints.EAST; c.fill = GridBagConstraints.NONE
        form.add(new JLabel("対象フォルダ"), c)
        c.gridx = 1; c.weightx = 1.0; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL
        sourceDirCombo.setEditable(true)
        sourceDirCombo.setPreferredSize(new Dimension(500, sourceDirCombo.getPreferredSize().height as int))
        form.add(sourceDirCombo, c)
        c.gridx = 2; c.weightx = 0.0
        form.add(fileListButton, c)

        // 抽出条件（glob パターン、1行1パターン）
        row++
        c.gridx = 0; c.gridy = row; c.anchor = GridBagConstraints.EAST; c.fill = GridBagConstraints.NONE
        def patternLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
        def patternLabel = new JLabel("抽出条件")
        // FlatLaf HelpButton（色は main で UIManager の HelpButton.background / questionMarkColor を設定）
        def helpIconButton = new JButton()
        helpIconButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_HELP)
        helpIconButton.setFocusable(false)
        helpIconButton.setPreferredSize(new Dimension(22, 22))
        helpIconButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        helpIconButton.setToolTipText("抽出条件（glob パターン）の説明を表示")
        helpIconButton.addMouseListener(new MouseAdapter() {
            @Override
            void mouseClicked(MouseEvent e) {
                String msg = """\
抽出条件（glob パターン）の例:

- 特別仕様（このツール固有の仕様）
  入力パターンの先頭に ** が自動付与される

- 拡張子で指定
  *.java ( ***.java ) → サブフォルダも含めた .java 全て
  *Action.java ( ***Action.java ) → サブフォルダも含めた *Action.java 全て

- パスの一部で指定
  src/**/Test*.java ( **src/**/Test*.java ) → パスに src を含む Test で始まる .java

- glob 特殊記号
  *   : 任意の文字列（/ を含まない）
  **  : 任意の階層・任意の文字列
  ?   : 任意の 1 文字

- 「...」は UI 上の簡易記法です
  /.../ や ... は内部的に ** に変換されます。
  例) src/.../Test.java  → src/**/Test.java

複数条件を書く場合は、1 行につき 1 パターン入力してください。
""".stripIndent()
                JOptionPane.showMessageDialog(FileCollectorFrame.this, msg, "抽出条件の説明", JOptionPane.INFORMATION_MESSAGE)
            }
        })
        patternLabelPanel.add(patternLabel)
        patternLabelPanel.add(Box.createHorizontalStrut(4))
        patternLabelPanel.add(helpIconButton)
        form.add(patternLabelPanel, c)
        c.gridx = 1; c.weightx = 1.0; c.gridwidth = 2; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL
        def patternScroll = new JScrollPane(patternArea)
        patternScroll.setMinimumSize(new Dimension(150, 90))
        patternArea.lineWrap = true
        patternArea.wrapStyleWord = true
        form.add(patternScroll, c)
        c.gridwidth = 1

        // 拡張子追加文字 + 抽出ボタン
        row++
        c.gridx = 0; c.gridy = row; c.anchor = GridBagConstraints.EAST; c.fill = GridBagConstraints.NONE
        form.add(new JLabel("末尾追加文字"), c)
        c.gridx = 1; c.weightx = 0.5; c.gridwidth = 1; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL
        form.add(zipSuffixField, c)
        c.gridx = 2; c.weightx = 0.5; c.anchor = GridBagConstraints.CENTER; c.fill = GridBagConstraints.HORIZONTAL
        form.add(searchButton, c)
        c.gridwidth = 1

        content.add(form, BorderLayout.NORTH)

        // 中央エリア：抽出結果リスト + ログ
        logArea.setEditable(false)

        fileList.setVisibleRowCount(8)
        def fileScroll = new JScrollPane(fileList)
        def logScroll = new JScrollPane(logArea)
        fileScroll.setMinimumSize(new Dimension(100, 80))
        logScroll.setMinimumSize(new Dimension(100, 80))

        def split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fileScroll, logScroll)
        split.setResizeWeight(0.35d)
        split.setContinuousLayout(true)

        def center = new JPanel(new BorderLayout(4, 4))
        def resultHeader = new JPanel(new BorderLayout())
        def leftButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0))
        leftButtonsPanel.add(new JLabel("抽出結果"))
        leftButtonsPanel.add(removeSelectedButton)
        leftButtonsPanel.add(removeExceptSelectedButton)
        resultHeader.add(leftButtonsPanel, BorderLayout.WEST)
        def rightButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0))
        rightButtonsPanel.add(new JLabel("1回あたり"))
        outputCountCombo.setEditable(true)
        outputCountCombo.setPreferredSize(new Dimension(55, outputCountCombo.getPreferredSize().height as int))
        rightButtonsPanel.add(outputCountCombo)
        rightButtonsPanel.add(new JLabel("件"))
        rightButtonsPanel.add(copyFilesButton)
        resultHeader.add(rightButtonsPanel, BorderLayout.EAST)
        center.add(resultHeader, BorderLayout.NORTH)
        center.add(split, BorderLayout.CENTER)

        content.add(center, BorderLayout.CENTER)

        copyFilesButton.enabled = false
        removeSelectedButton.enabled = false
        removeExceptSelectedButton.enabled = false
        // リストで選択があるときのみ「選択削除」「選択以外削除」を有効化
        fileList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                def hasSelection = fileList.selectedIndices.length > 0
                removeSelectedButton.enabled = hasSelection
                removeExceptSelectedButton.enabled = hasSelection
            }
        }

        SwingUtilities.invokeLater {
            split.setDividerLocation(0.5d)
        }
    }

    /** 各ボタンのアクションリスナーを登録 */
    private void initActions() {
        outputCountCombo.addActionListener { saveOutputCount() }
        searchButton.addActionListener { doSearch() }
        copyFilesButton.addActionListener { doCopyFiles() }
        fileListButton.addActionListener { doFileListOutput() }
        removeSelectedButton.addActionListener { removeSelectedFromResult() }
        removeExceptSelectedButton.addActionListener { removeExceptSelectedFromResult() }
    }

    /** 抽出結果リストで選択した行を削除 */
    private void removeSelectedFromResult() {
        int[] indices = fileList.selectedIndices
        if (indices == null || indices.length == 0) return
        def toRemove = indices.collect { it }.sort().reverse()
        toRemove.each { int idx ->
            fileListModel.remove(idx)
            if (idx < lastFoundFiles.size()) {
                lastFoundFiles.remove(idx)
            }
        }
        copyFilesButton.enabled = !lastFoundFiles.isEmpty()
        appendLog("選択した ${indices.length} 件を抽出結果から削除しました。")
    }

    /** 抽出結果リストで選択した行以外を削除（選択行のみ残す） */
    private void removeExceptSelectedFromResult() {
        int[] indices = fileList.selectedIndices
        if (indices == null || indices.length == 0) return
        def selectedIndicesList = indices.toList().sort()
        def newModelItems = selectedIndicesList.collect { int idx ->
            idx < fileListModel.size() ? fileListModel.get(idx) : null
        }.findAll { it != null }
        def newPaths = selectedIndicesList.collect { int idx ->
            idx < lastFoundFiles.size() ? lastFoundFiles.get(idx) : null
        }.findAll { it != null }
        fileListModel.clear()
        newModelItems.each { fileListModel.addElement(it) }
        lastFoundFiles.clear()
        lastFoundFiles.addAll(newPaths)
        copyFilesButton.enabled = !lastFoundFiles.isEmpty()
        appendLog("選択以外を削除しました。${newModelItems.size()} 件を残しました。")
    }

    /** フォルダ選択ダイアログを開き、選択したパスをコンボに設定 */
    private void chooseSourceDir() {
        def chooser = new JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "対象フォルダを選択"
        def current = getSourceDirText()
        if (current) {
            chooser.currentDirectory = new File(current)
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            def path = chooser.selectedFile.absolutePath
            sourceDirCombo.setSelectedItem(path)
            addSourceHistory(path)
        }
    }

    /** コンボのエディタから現在入力されているパスを取得 */
    private String getSourceDirText() {
        def editor = sourceDirCombo.getEditor()
        def item = editor?.item
        return item?.toString()
    }

    /** パスが存在するディレクトリかどうか */
    private static boolean isExistingDirectory(String path) {
        if (!path?.trim()) return false
        try {
            return Files.isDirectory(Paths.get(path.trim()))
        } catch (Exception ignored) {
            return false
        }
    }

    /** CONFIG_DIR/history.txt から対象フォルダ履歴を読み込み（存在するフォルダのみ） */
    private void loadSourceHistory() {
        try {
            if (Files.exists(SOURCE_HISTORY_FILE)) {
                Files.readAllLines(SOURCE_HISTORY_FILE, StandardCharsets.UTF_8).each { line ->
                    def v = line.trim()
                    if (v && !sourceHistory.contains(v) && isExistingDirectory(v)) {
                        sourceHistory.add(v)
                        sourceDirCombo.addItem(v)
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** CONFIG_DIR/output-count.txt から1回あたりの出力件数を読み込み */
    private void loadOutputCount() {
        try {
            if (Files.exists(OUTPUT_COUNT_FILE)) {
                def line = Files.readAllLines(OUTPUT_COUNT_FILE, StandardCharsets.UTF_8).find { it != null }?.trim()
                if (line != null && !line.isEmpty()) {
                    outputCountCombo.setSelectedItem(line)
                    return
                }
            }
        } catch (Exception ignored) {
        }
        outputCountCombo.setSelectedItem("10")
    }

    /** 1回あたりの出力件数を CONFIG_DIR/output-count.txt に保存 */
    private void saveOutputCount() {
        try {
            def v = getOutputCountValue()
            if (v != null && !v.isEmpty()) {
                Files.createDirectories(CONFIG_DIR)
                Files.write(OUTPUT_COUNT_FILE, [v], StandardCharsets.UTF_8)
            }
        } catch (Exception ignored) {
        }
    }

    /** コンボから出力件数（文字列）を取得。未入力・不正時は "10" */
    private String getOutputCountValue() {
        def item = outputCountCombo.editable ? outputCountCombo.editor?.item?.toString() : outputCountCombo.selectedItem?.toString()
        return item?.trim() ?: "10"
    }

    /** 1回あたりの出力件数を取得（1以上） */
    private int getOutputCount() {
        try {
            def n = Integer.parseInt(getOutputCountValue())
            return n >= 1 ? n : 10
        } catch (NumberFormatException e) {
            return 10
        }
    }

    /** 対象フォルダ履歴を CONFIG_DIR/history.txt に保存（存在するフォルダのみ） */
    private void saveSourceHistory() {
        try {
            def existing = sourceHistory.findAll { isExistingDirectory(it) }
            if (existing.isEmpty()) return
            Files.createDirectories(CONFIG_DIR)
            Files.write(SOURCE_HISTORY_FILE, existing, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        } catch (Exception ignored) {
        }
    }

    /** 対象フォルダを履歴の先頭に追加し、保存（存在するフォルダのみ追加） */
    private void addSourceHistory(String path) {
        def v = path?.trim()
        if (!v || !isExistingDirectory(v)) return
        if (!sourceHistory.contains(v)) {
            sourceHistory.add(0, v)
            sourceDirCombo.insertItemAt(v, 0)
        }
        saveSourceHistory()
    }

    /** 対象フォルダを再帰走査し、抽出条件（glob）に合うファイルを一覧表示 */
    private void doSearch() {
        def src = getSourceDirText()?.trim()
        def patterns = patternArea.text?.readLines()

        if (!src) {
            showError("対象フォルダを指定してください。")
            return
        }
        addSourceHistory(src)
        def cleaned = patterns.collect { it.trim() }.findAll { it }
        if (cleaned.isEmpty()) {
            showError("抽出条件を1行以上入力してください。")
            return
        }

        def srcDir = Paths.get(src)
        if (!Files.isDirectory(srcDir)) {
            showError("フォルダが存在しません: $src")
            return
        }

        searchButton.enabled = false
        copyFilesButton.enabled = false
        logArea.text = ""

        appendLog("抽出開始: $srcDir")
        appendLog("収集ファイルパターン (glob): ${cleaned.join(', ')}")

        // 重い走査は別スレッドで実行（UI フリーズ防止）
        new Thread({
            try {
                ensureFileListCache(srcDir, false)  // キャッシュが無いときだけファイル一覧を作成
                def jarFiles = findFilesFromFileList(srcDir, cleaned)
                def existingSet = new HashSet<Path>(lastFoundFiles)
                def toAdd = jarFiles.findAll { !existingSet.contains(it) }

                SwingUtilities.invokeLater {
                    toAdd.each { p ->
                        lastFoundFiles.add(p)
                        fileListModel.addElement(srcDir.relativize(p).toString())
                    }
                }

                appendLog("見つかったファイル数: ${jarFiles.size()}、うち新規 ${toAdd.size()} 件を追加（同一ファイルは追加しない）")
                if (jarFiles.isEmpty()) {
                    appendLog("対象ファイルが見つかりませんでした。")
                }
            } catch (Exception e) {
                appendLog("エラー: ${getErrorMessage(e)}")
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(this, "エラー: ${getErrorMessage(e)}", "エラー", JOptionPane.ERROR_MESSAGE)
                }
            } finally {
                SwingUtilities.invokeLater {
                    searchButton.enabled = true
                    copyFilesButton.enabled = !lastFoundFiles.isEmpty()
                }
            }
        }, "FileCollectorWorker").start()
    }

    /** 抽出結果のファイルを FILE_OUTPUT_DIR にコピーし、フォルダを開く */
    private void doCopyFiles() {
        if (lastFoundFiles == null || lastFoundFiles.isEmpty()) {
            showError("まず抽出を行い、ファイル一覧を取得してください。")
            return
        }
        def src = getSourceDirText()?.trim()
        if (!src) {
            showError("対象フォルダを指定してください。")
            return
        }
        Path baseDir = Paths.get(src)
        String baseName = baseDir.getFileName() != null ? baseDir.getFileName().toString() : "filecollector"
        String suffix = zipSuffixField.text?.trim() ?: ""
        Path outDir = FILE_OUTPUT_DIR
        try {
            if (clearBeforeOutputCheckBox.isSelected() && Files.exists(outDir)) {
                // 既存ファイル削除 ON のとき、ファイル出力先フォルダの中身を全削除
                Files.walk(outDir).sorted(Comparator.reverseOrder()).forEach { p -> Files.delete(p) }
            }
            Files.createDirectories(outDir)
            int limit = getOutputCount()
            int toCopy = Math.min(limit, lastFoundFiles.size())
            if (toCopy <= 0) return
            Map<String, Integer> nameCount = new HashMap<>()
            toCopy.times { int i ->
                Path file = lastFoundFiles.get(i)
                String baseFileName = file.fileName.toString()
                String nameWithSuffix = fileNameWithSuffix(baseFileName, suffix)
                String destName = uniqueFlatName(nameWithSuffix, nameCount)
                Path dest = outDir.resolve(destName)
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING)
                appendLog("コピー: $destName")
            }
            appendLog("${toCopy} 件を ${outDir} に出力しました。")
            Desktop.getDesktop().open(outDir.toFile())
            appendLog("出力フォルダをエクスプローラで表示しました。")
            // 出力したファイルをリストから削除
            toCopy.times { fileListModel.remove(0); lastFoundFiles.remove(0) }
            copyFilesButton.enabled = !lastFoundFiles.isEmpty()
        } catch (Exception e) {
            appendLog("各ファイル出力中にエラー: ${getErrorMessage(e)}")
            e.printStackTrace()
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(this, "各ファイル出力中にエラー: ${getErrorMessage(e)}", "エラー", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    /** パス区切り \ と / を同義として正規化 */
    private static String normalizePath(String path) {
        return path == null ? "" : path.replace("\\", "/")
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

    /** フォルダパスからファイル一覧キャッシュ用の安定したハッシュ文字列を生成 */
    private static String pathToFileListHash(Path root) {
        def s = root.toAbsolutePath().normalize().toString().replace("\\", "/")
        def digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))
        return digest[0..7].collect { String.format("%02x", it) }.join("")
    }

    /** 対象フォルダ用のファイル一覧キャッシュファイルの Path を返す（CONFIG_DIR/{hash}.filelist.txt） */
    private static Path getFileListCachePath(Path root) {
        Files.createDirectories(FILELIST_CACHE_DIR)
        return FILELIST_CACHE_DIR.resolve(pathToFileListHash(root) + ".filelist.txt")
    }

    /** root 以下を再帰走査し、全通常ファイルの絶対パスをキャッシュファイルに書き出す */
    private void buildAndWriteFileList(Path root) {
        Path cachePath = getFileListCachePath(root)
        def lines = new ArrayList<String>()
        Files.walk(root).forEach { Path p ->
            if (Files.isRegularFile(p)) {
                lines.add(p.toAbsolutePath().normalize().toString())
            }
        }
        Files.write(cachePath, lines, StandardCharsets.UTF_8)
    }

    /**
     * ファイル一覧キャッシュを用意する。
     * @param forceRebuild true のときは既存キャッシュを削除してから作成。false のときは存在しない場合のみ作成。
     */
    private void ensureFileListCache(Path root, boolean forceRebuild) {
        Path cachePath = getFileListCachePath(root)
        if (forceRebuild && Files.exists(cachePath)) {
            Files.delete(cachePath)
        }
        if (!Files.exists(cachePath)) {
            buildAndWriteFileList(root)
        }
    }

    /** ファイル一覧キャッシュを読み、glob パターンにマッチするファイルのみ返す（findFiles のキャッシュ版） */
    private List<Path> findFilesFromFileList(Path root, List<String> patterns) {
        Path cachePath = getFileListCachePath(root)
        if (!Files.exists(cachePath)) {
            return findFiles(root, patterns)
        }
        def globPatterns = patterns.collect { toGlobPattern(it) }.findAll { it }
        def matchers = globPatterns.collect { pattern ->
            FileSystems.default.getPathMatcher("glob:${pattern}")
        }
        def result = []
        Files.readAllLines(cachePath, StandardCharsets.UTF_8).each { String line ->
            if (!line.trim()) return
            Path p = Paths.get(line)
            if (!Files.isRegularFile(p)) return
            Path rel = root.relativize(p)
            def segs = normalizePath(rel.toString()).split("/").toList()
            Path relNormalized = segs ? rel.getFileSystem().getPath(*segs) : rel
            if (matchers.any { it.matches(relNormalized) || it.matches(p.fileName) }) {
                result << p
                appendLog("追加: ${rel}")
            }
        }
        return result
    }

    /** root 以下を再帰走査し、glob パターン（正規化済み）にマッチする通常ファイルを返す（キャッシュ未使用） */
    private List<Path> findFiles(Path root, List<String> patterns) {
        def globPatterns = patterns.collect { toGlobPattern(it) }.findAll { it }
        def matchers = globPatterns.collect { pattern ->
            FileSystems.default.getPathMatcher("glob:${pattern}")
        }
        def result = []
        Files.walk(root).forEach { Path p ->
            if (!Files.isRegularFile(p)) return
            Path rel = root.relativize(p)
            // Windows でも glob の ** が正しく動くよう、パスを / 区切りに正規化
            def segs = normalizePath(rel.toString()).split("/").toList()
            Path relNormalized = segs ? rel.getFileSystem().getPath(*segs) : rel
            if (matchers.any { it.matches(relNormalized) || it.matches(p.fileName) }) {
                result << p
                appendLog("追加: ${rel}")
            }
        }
        return result
    }

    /** フォルダ構造を tree 形式の行リストに変換（ルート名 + 再帰的に子要素） */
    private List<String> buildTreeLines(Path root) {
        def lines = new ArrayList<String>()
        String rootName = root.getFileName() != null ? root.getFileName().toString() : root.toString()
        lines.add(rootName)
        buildTreeRecursive(root, "", lines)
        return lines
    }

    /** tree の子要素を再帰的に追記。connector（├──/└──）と prefix で階層を表現 */
    private void buildTreeRecursive(Path dir, String prefix, List<String> lines) {
        def children = new ArrayList<Path>()
        Files.newDirectoryStream(dir).withCloseable { stream ->
            stream.each { Path p -> children.add(p) }
        }
        children.sort { a, b -> a.fileName.toString().toLowerCase() <=> b.fileName.toString().toLowerCase() }

        int total = children.size()
        children.eachWithIndex { Path child, int idx ->
            boolean last = (idx == total - 1)
            String connector = last ? "└── " : "├── "
            String childName = child.fileName.toString()
            lines.add(prefix + connector + childName)

            if (Files.isDirectory(child)) {
                String nextPrefix = prefix + (last ? "    " : "│   ")
                buildTreeRecursive(child, nextPrefix, lines)
            }
        }
    }

    /** 対象フォルダの tree を TREE_OUTPUT_DIR/<フォルダ名>.tree.txt に出力し、フォルダを開く */
    private void doFileListOutput() {
        def src = getSourceDirText()?.trim()
        if (!src) {
            showError("対象フォルダを指定してください。")
            return
        }

        Path root = Paths.get(src)
        if (!Files.isDirectory(root)) {
            showError("フォルダが存在しません: $src")
            return
        }

        appendLog("ファイル tree 出力開始: $root")
        try {
            ensureFileListCache(root, true)  // ファイル一覧キャッシュを削除してから再作成

            String baseName = root.getFileName() != null ? root.getFileName().toString() : "filecollector"
            Path outDir = TREE_OUTPUT_DIR
            Path outPath = outDir.resolve(baseName + ".tree.txt")

            if (clearBeforeOutputCheckBox.isSelected() && Files.exists(outDir)) {
                // 既存ファイル削除 ON のとき、tree 出力先フォルダの中身を全削除
                Files.walk(outDir).sorted(Comparator.reverseOrder()).forEach { p -> Files.delete(p) }
            }
            Files.createDirectories(outDir)

            def lines = buildTreeLines(root)
            Files.write(outPath, lines, StandardCharsets.UTF_8)

            appendLog("ファイル tree を ${outPath} に出力しました。")

            Desktop.getDesktop().open(outDir.toFile())
            appendLog("出力フォルダをエクスプローラで表示しました。")
        } catch (Exception e) {
            appendLog("ファイル tree 出力中にエラー: ${getErrorMessage(e)}")
            e.printStackTrace()
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(this, "ファイル tree 出力中にエラー: ${getErrorMessage(e)}", "エラー", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    /** 格納ファイル名の末尾に拡張子追加文字を付与 */
    private static String fileNameWithSuffix(String fileName, String suffix) {
        if (suffix == null || suffix.isEmpty()) return fileName
        return fileName + suffix
    }

    /** 同名ファイル対策：2件目以降に _2, _3, ... を付与してユニークな出力名を返す */
    private static String uniqueFlatName(String baseFileName, Map<String, Integer> nameCount) {
        int count = nameCount.getOrDefault(baseFileName, 0)
        nameCount.put(baseFileName, count + 1)
        if (count == 0) return baseFileName
        int lastDot = baseFileName.lastIndexOf('.')
        String namePart, extPart
        if (lastDot > 0) {
            namePart = baseFileName.substring(0, lastDot)
            extPart = baseFileName.substring(lastDot)
        } else {
            namePart = baseFileName
            extPart = ""
        }
        return namePart + "_" + (count + 1) + extPart
    }

    /** ログエリアに 1 行追記し、末尾にスクロール */
    private void appendLog(String msg) {
        SwingUtilities.invokeLater {
            logArea.append(msg + System.lineSeparator())
            logArea.caretPosition = logArea.document.length
        }
    }

    /** 例外から表示用メッセージを取得（message が null の場合は cause をたどってメッセージを探す） */
    private static String getErrorMessage(Throwable t) {
        if (t == null) return "不明なエラー"
        def current = t
        while (current != null) {
            if (current.message != null && !current.message.isEmpty()) {
                return current.message
            }
            current = current.cause
        }
        return t.getClass()?.simpleName ?: "不明なエラー"
    }

    /** エラーダイアログを表示 */
    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "入力エラー", JOptionPane.WARNING_MESSAGE)
    }
}
