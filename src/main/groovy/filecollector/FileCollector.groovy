/**
 * ファイル収集ツール (FileCollector)
 * 対象フォルダ内のファイルを glob パターンで絞り込み、一覧表示・tree 出力・ファイル出力を行う Swing アプリ。
 */
package filecollector

import javax.swing.*
import javax.swing.border.EmptyBorder
import java.awt.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.List
import java.util.ArrayList
import java.util.HashMap
import java.util.Map
import java.util.Comparator
import java.nio.file.*

class FileCollector {

    static void main(String[] args) {
        // UI スレッドでフレームを起動
        SwingUtilities.invokeLater {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            def defaults = UIManager.getLookAndFeelDefaults()
            setUIFontScaleSize(defaults, "Yu Gothic UI", 1.0f)
            new FileCollectorFrame().setVisible(true)
        }
    }

    /** すべての UI フォントを変更して一括で一回り大きくする（pointDelta: 増やすポイント数） */
    static void setUIFontScaleSize(def defaults, String fontName, float pointDelta) {
        defaults.keySet().findAll { it.toString().endsWith(".font") }.each { key ->
            def value = defaults.get(key)
            if (value instanceof Font) {
                def baseFont = new Font(fontName, value.style as int, value.size as int)
                UIManager.put(key, baseFont.deriveFont((value.size + pointDelta) as float))
            }
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
    private final JButton copyFilesButton = new JButton("ファイル出力")
    private final JButton fileListButton = new JButton("ファイル tree 出力")
    private final JButton removeSelectedButton = new JButton("選択削除")
    private final JButton removeExceptSelectedButton = new JButton("選択以外削除")
    private final JCheckBox clearBeforeOutputCheckBox = new JCheckBox("既存ファイル削除", true)
    // 抽出結果のファイル一覧（相対パス表示用の元データ）
    private List<Path> lastFoundFiles = new ArrayList<>()
    // 対象フォルダの履歴（~/.filecollector-history.txt に保存）
    private final List<String> sourceHistory = new ArrayList<>()

    FileCollectorFrame() {
        super("FileCollector")
        // DISPOSE_ON_CLOSE: ウィンドウを閉じても JVM を終了させない（呼び出し元プロセスが終了しない）
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)
        setSize(800, 600)
        setLocationRelativeTo(null)

        initLayout()
        loadSourceHistory()
        initActions()
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
        form.add(new JLabel("対象フォルダ:"), c)
        c.gridx = 1; c.weightx = 1.0; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL
        sourceDirCombo.setEditable(true)
        sourceDirCombo.setPreferredSize(new Dimension(500, sourceDirCombo.getPreferredSize().height as int))
        form.add(sourceDirCombo, c)
        c.gridx = 2; c.weightx = 0.0
        def browseSrc = new JButton("参照...")
        form.add(browseSrc, c)

        // tree ファイル出力ボタン行
        row++
        c.gridx = 0; c.gridy = row; c.gridwidth = 3
        c.anchor = GridBagConstraints.EAST
        def treePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0))
        treePanel.add(fileListButton)
        form.add(treePanel, c)
        c.gridwidth = 1

        // 抽出条件（glob パターン、1行1パターン）
        row++
        c.gridx = 0; c.gridy = row; c.anchor = GridBagConstraints.EAST; c.fill = GridBagConstraints.NONE
        form.add(new JLabel("抽出条件(glob, 複数可):"), c)
        c.gridx = 1; c.weightx = 1.0; c.gridwidth = 2; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL
        def patternScroll = new JScrollPane(patternArea)
        patternScroll.setMinimumSize(new Dimension(150, 90))
        patternArea.lineWrap = true
        patternArea.wrapStyleWord = true
        patternArea.setFont(sourceDirCombo.getFont())
        form.add(patternScroll, c)
        c.gridwidth = 1

        // 拡張子追加文字 + 抽出ボタン
        row++
        c.gridx = 0; c.gridy = row; c.anchor = GridBagConstraints.EAST; c.fill = GridBagConstraints.NONE
        form.add(new JLabel("拡張子 追加文字:"), c)
        c.gridx = 1; c.weightx = 0.5; c.gridwidth = 1; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL
        form.add(zipSuffixField, c)
        c.gridx = 2; c.weightx = 0.5; c.anchor = GridBagConstraints.EAST; c.fill = GridBagConstraints.NONE
        form.add(searchButton, c)
        c.gridwidth = 1

        content.add(form, BorderLayout.NORTH)

        // 中央エリア：抽出結果リスト + ログ
        logArea.setEditable(false)
        logArea.setFont(sourceDirCombo.getFont())

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
        leftButtonsPanel.add(new JLabel("抽出結果:"))
        leftButtonsPanel.add(removeSelectedButton)
        leftButtonsPanel.add(removeExceptSelectedButton)
        resultHeader.add(leftButtonsPanel, BorderLayout.WEST)
        def rightButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0))
        rightButtonsPanel.add(clearBeforeOutputCheckBox)
        rightButtonsPanel.add(copyFilesButton)
        resultHeader.add(rightButtonsPanel, BorderLayout.EAST)
        center.add(resultHeader, BorderLayout.NORTH)
        center.add(split, BorderLayout.CENTER)

        content.add(center, BorderLayout.CENTER)

        browseSrc.addActionListener { chooseSourceDir() }
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

    /** ~/.filecollector-history.txt から対象フォルダ履歴を読み込み */
    private void loadSourceHistory() {
        try {
            Path histPath = Paths.get(System.getProperty("user.home"), ".filecollector-history.txt")
            if (Files.exists(histPath)) {
                Files.readAllLines(histPath, StandardCharsets.UTF_8).each { line ->
                    def v = line.trim()
                    if (v && !sourceHistory.contains(v)) {
                        sourceHistory.add(v)
                        sourceDirCombo.addItem(v)
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 対象フォルダ履歴を ~/.filecollector-history.txt に保存 */
    private void saveSourceHistory() {
        try {
            Path histPath = Paths.get(System.getProperty("user.home"), ".filecollector-history.txt")
            Files.createDirectories(histPath.parent)
            BufferedWriter w = Files.newBufferedWriter(histPath, StandardCharsets.UTF_8)
            try {
                sourceHistory.each { v ->
                    w.write(v)
                    w.newLine()
                }
            } finally {
                w.close()
            }
        } catch (Exception ignored) {
        }
    }

    /** 対象フォルダを履歴の先頭に追加し、保存 */
    private void addSourceHistory(String path) {
        def v = path?.trim()
        if (!v) return
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
                def jarFiles = findFiles(srcDir, cleaned)
                lastFoundFiles = jarFiles

                SwingUtilities.invokeLater {
                    fileListModel.clear()
                    jarFiles.each { p ->
                        fileListModel.addElement(srcDir.relativize(p).toString())
                    }
                }

                appendLog("見つかったファイル数: ${jarFiles.size()}")

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

    /** 抽出結果のファイルを user.home/FileCollector/ にコピーし、フォルダを開く */
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
        Path outDir = Paths.get(System.getProperty("user.home"), "FileCollector")
        try {
            if (clearBeforeOutputCheckBox.isSelected() && Files.exists(outDir)) {
                // 既存ファイル削除 ON のとき、出力先フォルダの中身を全削除
                Files.walk(outDir).sorted(Comparator.reverseOrder()).forEach { p -> Files.delete(p) }
            }
            Files.createDirectories(outDir)
            Map<String, Integer> nameCount = new HashMap<>()
            lastFoundFiles.each { Path file ->
                String baseFileName = file.fileName.toString()
                String nameWithSuffix = fileNameWithSuffix(baseFileName, suffix)
                String destName = uniqueFlatName(nameWithSuffix, nameCount)
                Path dest = outDir.resolve(destName)
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING)
                appendLog("コピー: $destName")
            }
            appendLog("各ファイルを ${outDir} に出力しました。")
            Desktop.getDesktop().open(outDir.toFile())
            appendLog("出力フォルダをエクスプローラで表示しました。")
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

    /** root 以下を再帰走査し、glob パターン（正規化済み）にマッチする通常ファイルを返す */
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

    /** 対象フォルダの tree を user.home/FileCollector/<フォルダ名>.tree.txt に出力し、フォルダを開く */
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
            String baseName = root.getFileName() != null ? root.getFileName().toString() : "filecollector"
            Path outDir = Paths.get(System.getProperty("user.home"), "FileCollector")
            Path outPath = outDir.resolve(baseName + ".tree.txt")

            if (clearBeforeOutputCheckBox.isSelected() && Files.exists(outDir)) {
                // 既存ファイル削除 ON のとき、出力先フォルダの中身を全削除
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
