package filecollector

import javax.swing.*
import javax.swing.border.EmptyBorder
import java.awt.*
import java.awt.datatransfer.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.List
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.Map
import java.nio.file.*

class FileCollector {

    static void main(String[] args) {
        SwingUtilities.invokeLater {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            setUIFontMSUIGothic()
            scaleFontSize(1)
            new FileCollectorFrame().setVisible(true)
        }
    }

    /** すべての UI フォントを MS UI Gothic に統一する */
    static void setUIFontMSUIGothic() {
        String fontName = "MS UI Gothic"
        def defaults = UIManager.getLookAndFeelDefaults()
        defaults.keySet().findAll { it.toString().endsWith(".font") }.each { key ->
            def value = defaults.get(key)
            if (value instanceof Font) {
                UIManager.put(key, new Font(fontName, value.style, value.size))
            }
        }
    }

    /** UIManager のフォントを一括で一回り大きくする（pointDelta: 増やすポイント数） */
    static void scaleFontSize(int pointDelta) {
        def defaults = UIManager.getLookAndFeelDefaults()
        defaults.keySet().findAll { it.toString().endsWith(".font") }.each { key ->
            def value = defaults.get(key)
            if (value instanceof Font) {
                UIManager.put(key, value.deriveFont((float) (value.size + pointDelta)))
            }
        }
    }
}

class FileCollectorFrame extends JFrame {
    private final JComboBox<String> sourceDirCombo = new JComboBox<>()
    private final JComboBox<String> matchModeCombo = new JComboBox<>(["部分一致", "glob（ワイルドカード）"] as String[])
    private final JTextArea patternArea = new JTextArea("", 6, 55)
    private final JTextField zipSuffixField = new JTextField(".txt", 35)
    private final JTextArea logArea = new JTextArea()
    private final DefaultListModel<String> fileListModel = new DefaultListModel<>()
    private final JList<String> fileList = new JList<>(fileListModel)
    private final JButton searchButton = new JButton("🔍 抽出")
    private final JButton copyFilesButton = new JButton("📄 ファイル出力")
    private final JButton fileListButton = new JButton("🌳 treeファイル出力")
    private final JButton removeSelectedButton = new JButton("🗑️ 選択削除")
    private List<Path> lastFoundFiles = new ArrayList<>()
    private final List<String> sourceHistory = new ArrayList<>()

    FileCollectorFrame() {
        super("📦 ファイル収集ツール")
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
        setSize(800, 600)
        setLocationRelativeTo(null)

        initLayout()
        loadSourceHistory()
        initActions()
    }

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

        // Source directory
        c.gridx = 0; c.gridy = row
        form.add(new JLabel("📁 対象フォルダ:"), c)
        c.gridx = 1; c.weightx = 1.0
        sourceDirCombo.setEditable(true)
        sourceDirCombo.setPreferredSize(new Dimension(500, sourceDirCombo.getPreferredSize().height as int))
        form.add(sourceDirCombo, c)
        c.gridx = 2; c.weightx = 0.0
        def browseSrc = new JButton("📂 参照...")
        form.add(browseSrc, c)

        // File tree button row
        row++
        c.gridx = 0; c.gridy = row; c.gridwidth = 3
        c.anchor = GridBagConstraints.EAST
        def treePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0))
        treePanel.add(fileListButton)
        form.add(treePanel, c)
        c.gridwidth = 1

        // 一致方式
        row++
        c.gridx = 0; c.gridy = row
        form.add(new JLabel("🔀 一致方式:"), c)
        c.gridx = 1; c.weightx = 0.0
        matchModeCombo.setSelectedItem("部分一致")
        form.add(matchModeCombo, c)
        c.gridx = 2; c.weightx = 1.0
        form.add(new JPanel(), c)

        // Pattern
        row++
        c.gridx = 0; c.gridy = row
        form.add(new JLabel("🔍 抽出条件 (複数可):"), c)
        c.gridx = 1; c.weightx = 1.0; c.gridwidth = 2
        def patternScroll = new JScrollPane(patternArea)
        patternArea.lineWrap = true
        patternArea.wrapStyleWord = true
        patternArea.setFont(sourceDirCombo.getFont())
        form.add(patternScroll, c)
        c.gridwidth = 1

        // 拡張子追加文字
        row++
        c.gridx = 0; c.gridy = row
        form.add(new JLabel("✏️ 拡張子追加文字:"), c)
        c.gridx = 1; c.weightx = 1.0; c.gridwidth = 2
        form.add(zipSuffixField, c)
        c.gridwidth = 1

        // Buttons
        row++
        c.gridx = 0; c.gridy = row; c.gridwidth = 3
        c.anchor = GridBagConstraints.EAST
        def buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT))
        buttonsPanel.add(searchButton)
        buttonsPanel.add(copyFilesButton)
        form.add(buttonsPanel, c)

        content.add(form, BorderLayout.NORTH)

        logArea.setEditable(false)
        logArea.setFont(sourceDirCombo.getFont())

        fileList.setVisibleRowCount(8)
        def fileScroll = new JScrollPane(fileList)
        def logScroll = new JScrollPane(logArea)

        def split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fileScroll, logScroll)
        split.setResizeWeight(0.35d)
        split.setContinuousLayout(true)

        def center = new JPanel(new BorderLayout(4, 4))
        def resultHeader = new JPanel(new BorderLayout())
        resultHeader.add(new JLabel("📋 抽出結果:"), BorderLayout.WEST)
        resultHeader.add(removeSelectedButton, BorderLayout.EAST)
        center.add(resultHeader, BorderLayout.NORTH)
        center.add(split, BorderLayout.CENTER)

        content.add(center, BorderLayout.CENTER)

        browseSrc.addActionListener { chooseSourceDir() }
        copyFilesButton.enabled = false
        removeSelectedButton.enabled = false
        fileList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                removeSelectedButton.enabled = fileList.selectedIndices.length > 0
            }
        }

        SwingUtilities.invokeLater {
            split.setDividerLocation(0.5d)
        }
    }

    private void initActions() {
        searchButton.addActionListener { doSearch() }
        copyFilesButton.addActionListener { doCopyFilesToClipboard() }
        fileListButton.addActionListener { doFileListOutput() }
        removeSelectedButton.addActionListener { removeSelectedFromResult() }
    }

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

    private String getSourceDirText() {
        def editor = sourceDirCombo.getEditor()
        def item = editor?.item
        return item?.toString()
    }

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

    private void addSourceHistory(String path) {
        def v = path?.trim()
        if (!v) return
        if (!sourceHistory.contains(v)) {
            sourceHistory.add(0, v)
            sourceDirCombo.insertItemAt(v, 0)
        }
        saveSourceHistory()
    }

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

        String matchMode = matchModeCombo.getSelectedItem()?.toString() ?: "部分一致"
        appendLog("抽出開始: $srcDir")
        appendLog("一致方式: $matchMode")
        appendLog("収集ファイルパターン: ${cleaned.join(', ')}")

        new Thread({
            try {
                def jarFiles = findFiles(srcDir, cleaned, matchMode)
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
                appendLog("エラー: ${e.message}")
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(this, "エラー: ${e.message}", "エラー", JOptionPane.ERROR_MESSAGE)
                }
            } finally {
                SwingUtilities.invokeLater {
                    searchButton.enabled = true
                    copyFilesButton.enabled = !lastFoundFiles.isEmpty()
                }
            }
        }, "FileCollectorWorker").start()
    }

    private void doCopyFilesToClipboard() {
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
        Path outDir = Paths.get(System.getProperty("user.home"), "FileCollector", baseName)
        try {
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
            appendLog("各ファイル出力中にエラー: ${e.message}")
            e.printStackTrace()
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(this, "各ファイル出力中にエラー: ${e.message}", "エラー", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    /** パス区切り \ と / を同義として正規化 */
    private static String normalizePath(String path) {
        return path == null ? "" : path.replace("\\", "/")
    }

    private List<Path> findFiles(Path root, List<String> patterns, String matchMode) {
        boolean partialMatch = "部分一致".equals(matchMode)
        def result = []

        if (partialMatch) {
            def normPatterns = patterns.collect { normalizePath(it.trim()) }.findAll { it }
            Files.walk(root).forEach { Path p ->
                if (!Files.isRegularFile(p)) return
                String relPath = normalizePath(root.relativize(p).toString())
                if (normPatterns.any { relPath.contains(it) }) {
                    result << p
                    appendLog("追加: ${root.relativize(p)}")
                }
            }
        } else {
            def matchers = patterns.collect { pattern ->
                FileSystems.default.getPathMatcher("glob:${pattern.trim()}")
            }
            Files.walk(root).forEach { Path p ->
                if (Files.isRegularFile(p) && matchers.any {
                    it.matches(root.relativize(p)) || it.matches(p.fileName)
                }) {
                    result << p
                    appendLog("追加: ${root.relativize(p)}")
                }
            }
        }
        return result
    }

    private List<String> buildTreeLines(Path root) {
        def lines = new ArrayList<String>()
        String rootName = root.getFileName() != null ? root.getFileName().toString() : root.toString()
        lines.add(rootName)
        buildTreeRecursive(root, "", lines)
        return lines
    }

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
            Files.createDirectories(outDir)
            Path outPath = outDir.resolve(baseName + ".tree.txt")

            def lines = buildTreeLines(root)
            Files.write(outPath, lines, StandardCharsets.UTF_8)

            appendLog("ファイル tree を ${outPath} に出力しました。")

            Desktop.getDesktop().open(outDir.toFile())
            appendLog("出力フォルダをエクスプローラで表示しました。")
        } catch (Exception e) {
            appendLog("ファイル tree 出力中にエラー: ${e.message}")
            e.printStackTrace()
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(this, "ファイル tree 出力中にエラー: ${e.message}", "エラー", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    /** 格納ファイル名の末尾に拡張子追加文字を付与 */
    private static String fileNameWithSuffix(String fileName, String suffix) {
        if (suffix == null || suffix.isEmpty()) return fileName
        return fileName + suffix
    }

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

    private static class ZipFileTransferable implements Transferable {
        private final File file
        private final DataFlavor[] flavors

        ZipFileTransferable(File file) {
            this.file = file
            this.flavors = [DataFlavor.javaFileListFlavor] as DataFlavor[]
        }

        @Override
        DataFlavor[] getTransferDataFlavors() {
            return flavors
        }

        @Override
        boolean isDataFlavorSupported(DataFlavor flavor) {
            flavors.any { it.equals(flavor) }
        }

        @Override
        Object getTransferData(DataFlavor flavor) {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor)
            }
            return [file]
        }
    }

    private static class FileListTransferable implements Transferable {
        private final List<File> files
        private final DataFlavor[] flavors

        FileListTransferable(List<File> files) {
            this.files = files != null ? files : Collections.emptyList()
            this.flavors = [DataFlavor.javaFileListFlavor] as DataFlavor[]
        }

        @Override
        DataFlavor[] getTransferDataFlavors() {
            return flavors
        }

        @Override
        boolean isDataFlavorSupported(DataFlavor flavor) {
            flavors.any { it.equals(flavor) }
        }

        @Override
        Object getTransferData(DataFlavor flavor) {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor)
            }
            return files
        }
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater {
            logArea.append(msg + System.lineSeparator())
            logArea.caretPosition = logArea.document.length
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "入力エラー", JOptionPane.WARNING_MESSAGE)
    }
}
