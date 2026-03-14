/**
 * メインウィンドウ。フォルダ指定・パターン入力・抽出・出力処理を行う
 */
package filecollector;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.ComboBoxEditor;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLightLaf;

public class FileCollectorFrame extends JFrame {

    private final JComboBox<String> sourceDirCombo = new JComboBox<>();
    private final JTextField sourceFilterField = new JTextField(20);
    private final JTextArea patternArea = new JTextArea("", 6, 55);
    private final JTextArea excludePatternArea = new JTextArea("", 2, 55);
    private final JTextField fileSuffixField = new JTextField(".txt", 6);
    private final JTextArea logArea = new JTextArea();
    private final DefaultListModel<String> fileListModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(fileListModel);
    private final JButton searchButton = new JButton("<html>ファイル抽出<<br/>(Ctrl+Enter)</html>");
    private final JCheckBox dedupeByFileNameCheckBox = new JCheckBox("同名ファイル除外", true);
    private final JComboBox<String> outputCountCombo = new JComboBox<>(new String[]{"5", "10", "20", "50", "100"});
    private final JButton clipboardOutputButton = new JButton("クリップボード出力");
    private final JTextArea clipboardPrefixField = new JTextArea("# File: #{filepath}\r\n```#{ext}\r\n", 2, 12);
    private final JTextArea clipboardSuffixField = new JTextArea("```\r\n", 2, 12);
    private final JCheckBox clipboardAddPrefixSuffixCheckBox = new JCheckBox("先頭・末尾文字付加", true);
    private final JButton aiMessageButton = new JButton("AI用メッセージ");
    private final JButton copyFilesButton = new JButton("ファイルに出力");
    private final JButton fileListButton = new JButton("ファイル tree 出力");
    private final JButton removeSelectedButton = new JButton("選択削除");
    private final JButton removeExceptSelectedButton = new JButton("選択以外削除");
    private final JCheckBox clearBeforeOutputCheckBox = new JCheckBox("既存ファイル削除", true);
    private final JLabel resultCountLabel = new JLabel("0 件");

    private List<Path> lastFoundFiles = new ArrayList<>();
    private final List<String> sourceHistory = new ArrayList<>();

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".filecollector");
    private static final Path FILELIST_CACHE_DIR = CONFIG_DIR;
    private static final Path FILE_OUTPUT_DIR = CONFIG_DIR.resolve("FileCollector");
    private static final Path TREE_OUTPUT_DIR = CONFIG_DIR.resolve("FileCollectorTree");
    private static final Path OUTPUT_COUNT_FILE = CONFIG_DIR.resolve("output-count.txt");
    private static final Path SOURCE_HISTORY_FILE = CONFIG_DIR.resolve("history.txt");
    private static final Path PATTERN_FILE = CONFIG_DIR.resolve("pattern.txt");
    private static final Path EXCLUDE_PATTERN_FILE = CONFIG_DIR.resolve("exclude-pattern.txt");
    private static final Path CLIPBOARD_PREFIX_FILE = CONFIG_DIR.resolve("clipboard-prefix.txt");
    private static final Path CLIPBOARD_SUFFIX_FILE = CONFIG_DIR.resolve("clipboard-suffix.txt");
    private static final Path FILE_SUFFIX_FILE = CONFIG_DIR.resolve("file-suffix.txt");

    public FileCollectorFrame() {
        super("FileCollector");
        java.net.URL resourceUrl = FileCollectorFrame.class.getResource(FileCollectorFrame.class.getSimpleName() + ".class");
        boolean isJarExecution = resourceUrl != null && resourceUrl.toString().startsWith("jar:");
        setDefaultCloseOperation(isJarExecution ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        setSize(825, 600);
        setLocationRelativeTo(null);

        initLayout();
        loadSourceHistory();
        loadOutputCount();
        loadFormSettings();
        initActions();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                addSourceHistory(getSourceDirText());
                saveFormSettings();
            }
        });
    }

    private void initLayout() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(content);

        JPanel form = new JPanel();
        form.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.0;
        c.weighty = 0.0;

        int row = 0;

        JPanel sourceRowPanel = new JPanel(new BorderLayout(4, 0));
        JPanel sourceLeftPanel = new JPanel();
        sourceLeftPanel.setLayout(new BoxLayout(sourceLeftPanel, BoxLayout.LINE_AXIS));
        JLabel sourceLabel = new JLabel("対象フォルダ");
        sourceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        sourceLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        sourceFilterField.setColumns(15);
        sourceFilterField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "フィルタ条件（部分一致）");
        sourceFilterField.setAlignmentY(Component.CENTER_ALIGNMENT);
        sourceLeftPanel.add(sourceLabel);
        sourceLeftPanel.add(Box.createHorizontalStrut(6));
        sourceLeftPanel.add(sourceFilterField);
        sourceRowPanel.add(sourceLeftPanel, BorderLayout.WEST);

        JPanel sourceCenterPanel = new JPanel(new BorderLayout(4, 0));
        sourceDirCombo.setEditable(true);
        sourceDirCombo.setPreferredSize(new Dimension(500, sourceDirCombo.getPreferredSize().height));
        sourceCenterPanel.add(sourceDirCombo, BorderLayout.CENTER);
        sourceCenterPanel.add(fileListButton, BorderLayout.EAST);
        sourceRowPanel.add(sourceCenterPanel, BorderLayout.CENTER);

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(sourceRowPanel, c);
        c.gridwidth = 1;

        row++;
        JPanel patternRowPanel = new JPanel(new BorderLayout(4, 0));
        JPanel patternLabelPanel = new JPanel();
        patternLabelPanel.setLayout(new BoxLayout(patternLabelPanel, BoxLayout.LINE_AXIS));
        JLabel patternLabel = new JLabel("<html>抽出条件<br/>(glob パターン)</html>");
        JButton helpIconButton = new JButton();
        helpIconButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_HELP);
        helpIconButton.setFocusable(false);
        helpIconButton.setPreferredSize(new Dimension(22, 22));
        helpIconButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        helpIconButton.setToolTipText("抽出条件（glob パターン）の説明を表示");
        helpIconButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String msg = """
                    抽出条件（glob パターン）の例:

                    - 拡張子で指定
                      *.java → サブフォルダも含めた .java 全て
                      *Action.java → サブフォルダも含めた *Action.java 全て

                    - パスの一部で指定
                      src/**/Test*.java → パスに src を含む Test で始まる .java

                    - 特別仕様（このツール固有の仕様）
                      ・入力した抽出条件の先頭に ** が自動付与されます。
                        例) *.java → ***.java
                        例) *Action.java → ***Action.java
                        例) src/**/Test*.java → **src/**/Test*.java

                      ・/.../ や ... は内部的に ** に変換されます。
                        例) src/.../Test.java  → src/**/Test.java

                    - (参考) glob 特殊記号
                      *   : 任意の文字列（/ を含まない）
                      **  : 任意の階層・任意の文字列
                      ?   : 任意の 1 文字

                    複数条件を書く場合は、1 行につき 1 パターン入力してください。
                    """;
                JOptionPane.showMessageDialog(FileCollectorFrame.this, msg, "抽出条件の説明", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        patternLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        helpIconButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        patternLabelPanel.add(patternLabel);
        patternLabelPanel.add(Box.createHorizontalStrut(4));
        patternLabelPanel.add(helpIconButton);
        patternRowPanel.add(patternLabelPanel, BorderLayout.WEST);

        JScrollPane patternScroll = new JScrollPane(patternArea);
        patternScroll.setMinimumSize(new Dimension(150, 90));
        patternArea.setLineWrap(true);
        patternArea.setWrapStyleWord(true);
        patternRowPanel.add(patternScroll, BorderLayout.CENTER);
        Dimension searchBtnPref = searchButton.getPreferredSize();
        searchButton.setPreferredSize(new Dimension(searchBtnPref.width, 40));
        searchButton.setMaximumSize(new Dimension(searchBtnPref.width, 40));
        JPanel searchBtnWrap = new JPanel(new BorderLayout());
        searchBtnWrap.add(searchButton, BorderLayout.NORTH);
        dedupeByFileNameCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchBtnWrap.add(dedupeByFileNameCheckBox, BorderLayout.SOUTH);
        patternRowPanel.add(searchBtnWrap, BorderLayout.EAST);

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(patternRowPanel, c);
        c.gridwidth = 1;

        row++;
        JPanel excludeRowPanel = new JPanel(new BorderLayout(4, 0));
        JPanel excludeLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        excludeLabelPanel.add(new JLabel("除外条件 (glob)"));
        excludeLabelPanel.setPreferredSize(new Dimension(patternLabelPanel.getPreferredSize().width, excludeLabelPanel.getPreferredSize().height));
        excludeRowPanel.add(excludeLabelPanel, BorderLayout.WEST);
        excludePatternArea.setLineWrap(true);
        excludePatternArea.setWrapStyleWord(true);
        excludePatternArea.setToolTipText("ここにマッチしたファイルは抽出結果に含めません。1行1パターン。空欄なら除外なし。");
        JScrollPane excludeScroll = new JScrollPane(excludePatternArea);
        excludeScroll.setMinimumSize(new Dimension(150, 40));
        excludeRowPanel.add(excludeScroll, BorderLayout.CENTER);
        JPanel excludeRightSpacer = new JPanel();
        excludeRightSpacer.setPreferredSize(new Dimension(searchBtnWrap.getPreferredSize().width, 1));
        excludeRowPanel.add(excludeRightSpacer, BorderLayout.EAST);
        c.gridy = row;
        c.gridwidth = 3;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(excludeRowPanel, c);
        c.gridwidth = 1;

        JPanel optionsRow = new JPanel(new BorderLayout());
        JPanel optionsLeft = new JPanel();
        optionsLeft.setLayout(new BoxLayout(optionsLeft, BoxLayout.LINE_AXIS));
        JLabel prefixLabel2 = new JLabel("クリップボード出力");
        prefixLabel2.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsLeft.add(prefixLabel2);
        optionsLeft.add(Box.createHorizontalStrut(20));
        JLabel prefixLabel = new JLabel("<html>先頭<br/>付加</html>");
        prefixLabel.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsLeft.add(prefixLabel);
        optionsLeft.add(Box.createHorizontalStrut(4));
        clipboardPrefixField.setToolTipText("クリップボード出力時にファイルの先頭に追加。#{ext} #{filename} #{filepath} で置換");
        clipboardPrefixField.setLineWrap(true);
        clipboardPrefixField.setWrapStyleWord(true);
        JScrollPane prefixScroll = new JScrollPane(clipboardPrefixField);
        prefixScroll.setPreferredSize(new Dimension(240, 40));
        prefixScroll.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsLeft.add(prefixScroll);
        optionsLeft.add(Box.createHorizontalStrut(8));
        JLabel suffixLabel = new JLabel("<html>末尾<br/>付加</html>");
        suffixLabel.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsLeft.add(suffixLabel);
        optionsLeft.add(Box.createHorizontalStrut(4));
        clipboardSuffixField.setToolTipText("クリップボード出力時にファイルの末尾に追加。${ext} ${filename} ${filepath} で置換");
        clipboardSuffixField.setLineWrap(true);
        clipboardSuffixField.setWrapStyleWord(true);
        JScrollPane suffixScroll = new JScrollPane(clipboardSuffixField);
        suffixScroll.setPreferredSize(new Dimension(240, 40));
        suffixScroll.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsLeft.add(suffixScroll);
        optionsLeft.add(Box.createHorizontalStrut(8));
        JPanel clipboardButtonPanel = new JPanel();
        clipboardButtonPanel.setLayout(new BoxLayout(clipboardButtonPanel, BoxLayout.Y_AXIS));
        clipboardOutputButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        clipboardButtonPanel.add(clipboardOutputButton);
        clipboardAddPrefixSuffixCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        clipboardButtonPanel.add(clipboardAddPrefixSuffixCheckBox);
        clipboardButtonPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsLeft.add(clipboardButtonPanel);

        updateClipboardPrefixSuffixEnabled();
        optionsRow.add(optionsLeft, BorderLayout.WEST);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(form);
        topPanel.add(optionsRow);
        content.add(topPanel, BorderLayout.NORTH);

        logArea.setEditable(false);
        fileList.setVisibleRowCount(8);
        JScrollPane fileScroll = new JScrollPane(fileList);
        JScrollPane logScroll = new JScrollPane(logArea);
        fileScroll.setMinimumSize(new Dimension(100, 80));
        logScroll.setMinimumSize(new Dimension(100, 80));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fileScroll, logScroll);
        split.setResizeWeight(0.35);
        split.setContinuousLayout(true);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        JPanel resultHeader = new JPanel(new BorderLayout());
        JPanel leftButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftButtonsPanel.add(new JLabel("抽出結果"));
        leftButtonsPanel.add(resultCountLabel);
        leftButtonsPanel.add(removeSelectedButton);
        leftButtonsPanel.add(removeExceptSelectedButton);
        resultHeader.add(leftButtonsPanel, BorderLayout.WEST);
        JPanel rightButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightButtonsPanel.add(aiMessageButton);
        rightButtonsPanel.add(new JLabel("拡張子追加文字"));
        fileSuffixField.setToolTipText("ファイル出力時に拡張子の末尾に追加する文字列");
        rightButtonsPanel.add(fileSuffixField);
        rightButtonsPanel.add(new JLabel("1回あたり"));
        outputCountCombo.setEditable(true);
        outputCountCombo.setPreferredSize(new Dimension(55, outputCountCombo.getPreferredSize().height));
        outputCountCombo.setToolTipText("ファイル出力時に一度に出力するファイル数の上限");
        rightButtonsPanel.add(outputCountCombo);
        rightButtonsPanel.add(new JLabel("件"));
        rightButtonsPanel.add(copyFilesButton);
        resultHeader.add(rightButtonsPanel, BorderLayout.EAST);
        center.add(resultHeader, BorderLayout.NORTH);
        center.add(split, BorderLayout.CENTER);
        content.add(center, BorderLayout.CENTER);

        updateResultListButtons();
        clipboardOutputButton.setEnabled(false);
        removeSelectedButton.setEnabled(false);
        removeExceptSelectedButton.setEnabled(false);
        fileList.addListSelectionListener((ListSelectionEvent evt) -> {
            if (!evt.getValueIsAdjusting()) {
                boolean hasSelection = fileList.getSelectedIndices().length > 0;
                removeSelectedButton.setEnabled(hasSelection);
                removeExceptSelectedButton.setEnabled(hasSelection);
                clipboardOutputButton.setEnabled(hasSelection);
            }
        });
        updateResultCount();

        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.5));
    }

    private void initActions() {
        outputCountCombo.addActionListener(evt -> saveOutputCount());
        searchButton.addActionListener(evt -> doSearch());
        getRootPane().registerKeyboardAction(
                evt -> doSearch(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        searchButton.setToolTipText("対象フォルダを抽出（Ctrl+Enter）");
        copyFilesButton.addActionListener(evt -> doCopyFiles());
        aiMessageButton.addActionListener(evt -> doAiMessage());
        clipboardOutputButton.addActionListener(evt -> doClipboardOutput());
        clipboardAddPrefixSuffixCheckBox.addItemListener(evt -> updateClipboardPrefixSuffixEnabled());
        fileListButton.addActionListener(evt -> doFileListOutput());
        removeSelectedButton.addActionListener(evt -> removeSelectedFromResult());
        removeExceptSelectedButton.addActionListener(evt -> removeExceptSelectedFromResult());

        Runnable onFilterChanged = () -> {
            applySourceFilter(sourceFilterField.getText());
            if (sourceDirCombo.getItemCount() > 0) {
                sourceDirCombo.showPopup();
            } else {
                sourceDirCombo.hidePopup();
            }
        };
        sourceFilterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { onFilterChanged.run(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onFilterChanged.run(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onFilterChanged.run(); }
        });
    }

    private void updateResultCount() {
        int n = fileListModel.getSize();
        resultCountLabel.setText(n + " 件");
    }

    private void removeSelectedFromResult() {
        int[] indices = fileList.getSelectedIndices();
        if (indices == null || indices.length == 0) return;
        int[] toRemove = Arrays.stream(indices).boxed().sorted(Comparator.reverseOrder()).mapToInt(Integer::intValue).toArray();
        for (int idx : toRemove) {
            fileListModel.remove(idx);
            if (idx < lastFoundFiles.size()) {
                lastFoundFiles.remove(idx);
            }
        }
        updateResultListButtons();
        updateResultCount();
        appendLog("選択した " + indices.length + " 件を抽出結果から削除しました。");
    }

    private void removeExceptSelectedFromResult() {
        int[] indices = fileList.getSelectedIndices();
        if (indices == null || indices.length == 0) return;
        List<Integer> selectedIndicesList = Arrays.stream(indices).boxed().sorted().collect(Collectors.toList());
        List<String> newModelItems = new ArrayList<>();
        for (int idx : selectedIndicesList) {
            if (idx < fileListModel.getSize()) {
                newModelItems.add(fileListModel.get(idx));
            }
        }
        List<Path> newPaths = new ArrayList<>();
        for (int idx : selectedIndicesList) {
            if (idx < lastFoundFiles.size()) {
                newPaths.add(lastFoundFiles.get(idx));
            }
        }
        fileListModel.clear();
        for (String it : newModelItems) {
            fileListModel.addElement(it);
        }
        lastFoundFiles.clear();
        lastFoundFiles.addAll(newPaths);
        updateResultListButtons();
        updateResultCount();
        appendLog("選択以外を削除しました。" + newModelItems.size() + " 件を残しました。");
    }

    private void updateClipboardPrefixSuffixEnabled() {
        boolean enabled = clipboardAddPrefixSuffixCheckBox.isSelected();
        clipboardPrefixField.setEnabled(enabled);
        clipboardSuffixField.setEnabled(enabled);
    }

    private void doClipboardOutput() {
        int[] indices = fileList.getSelectedIndices();
        if (indices == null || indices.length == 0) return;
        String baseDirStr = getSourceDirText();
        if (baseDirStr != null) baseDirStr = baseDirStr.trim();
        if (baseDirStr == null || baseDirStr.isEmpty()) {
            showError("対象フォルダを指定してください。");
            return;
        }
        Path baseDir = Paths.get(baseDirStr);
        boolean addPrefixSuffix = clipboardAddPrefixSuffixCheckBox.isSelected();
        String prefixTemplate = addPrefixSuffix ? (clipboardPrefixField.getText() != null ? clipboardPrefixField.getText() : "") : "";
        String suffixTemplate = addPrefixSuffix ? (clipboardSuffixField.getText() != null ? clipboardSuffixField.getText() : "") : "";
        List<String> parts = new ArrayList<>();
        int[] skipped = {0};
        List<Integer> sortedIndices = Arrays.stream(indices).boxed().sorted().collect(Collectors.toList());
        for (int idx : sortedIndices) {
            if (idx >= lastFoundFiles.size()) continue;
            Path path = lastFoundFiles.get(idx);
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (!parts.isEmpty()) parts.add("");
                String prefix = addPrefixSuffix ? applyClipboardPlaceholders(prefixTemplate, path, baseDir) : "";
                String suffix = addPrefixSuffix ? applyClipboardPlaceholders(suffixTemplate, path, baseDir) : "";
                parts.add(prefix + content + suffix);
            } catch (Exception e) {
                skipped[0]++;
                parts.add("(読み取りスキップ: " + path.getFileName() + ")");
            }
        }
        String text = String.join("\n", parts);
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
            appendLog("クリップボードに " + indices.length + " 件の内容を出力しました。" + (skipped[0] > 0 ? "（スキップ " + skipped[0] + " 件）" : ""));
        } catch (Exception e) {
            appendLog("クリップボード出力エラー: " + getErrorMessage(e));
            showError("クリップボードにコピーできませんでした: " + getErrorMessage(e));
        }
    }

    private void doAiMessage() {
        if (lastFoundFiles.isEmpty()) {
            showError("まず抽出を行い、ファイル一覧を取得してください。");
            return;
        }
        int count = fileListModel.getSize();
        List<String> lines = new ArrayList<>();
        lines.add("これからファイルを分割して送ります。");
        lines.add("全部で " + count + " ファイルあります。");
        lines.add("");
        for (int i = 0; i < count; i++) {
            String path = fileListModel.getElementAt(i);
            if (path != null) path = path.replace('\\', '/');
            else path = "";
            lines.add((i + 1) + "/" + count + "  " + path);
        }
        lines.add("");
        lines.add("これから順番に送ります。");
        lines.add("すべて送るまで解析しないでください。");
        String text = String.join("\n", lines);
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
            appendLog("AI用メッセージをクリップボードに出力しました（" + count + " 件）。");
        } catch (Exception e) {
            appendLog("AI用メッセージ出力エラー: " + getErrorMessage(e));
            showError("クリップボードにコピーできませんでした: " + getErrorMessage(e));
        }
    }

    private static String applyClipboardPlaceholders(String template, Path path, Path baseDir) {
        if (template == null || template.isEmpty()) return "";
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
        String ext = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) ext = fileName.substring(dotIdx + 1);
        String relativePath = baseDir.relativize(path).toString().replace('\\', '/');
        return template
                .replaceAll("#\\{ext\\}", Matcher.quoteReplacement(ext))
                .replaceAll("#\\{filename\\}", Matcher.quoteReplacement(fileName))
                .replaceAll("#\\{filepath\\}", Matcher.quoteReplacement(relativePath));
    }

    private String getSourceDirText() {
        ComboBoxEditor editor = sourceDirCombo.getEditor();
        Object item = editor != null ? editor.getItem() : null;
        return item != null ? item.toString() : null;
    }

    private static boolean isExistingDirectory(String path) {
        if (path == null || path.trim().isEmpty()) return false;
        try {
            return Files.isDirectory(Paths.get(path.trim()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void loadSourceHistory() {
        try {
            if (Files.exists(SOURCE_HISTORY_FILE)) {
                for (String line : Files.readAllLines(SOURCE_HISTORY_FILE, StandardCharsets.UTF_8)) {
                    String v = line.trim();
                    if (!v.isEmpty() && !sourceHistory.contains(v) && isExistingDirectory(v)) {
                        sourceHistory.add(v);
                    }
                }
                applySourceFilter("");
            }
        } catch (Exception ignored) {
        }
    }

    private void loadOutputCount() {
        try {
            if (Files.exists(OUTPUT_COUNT_FILE)) {
                String line = Files.readAllLines(OUTPUT_COUNT_FILE, StandardCharsets.UTF_8).stream()
                        .filter(l -> l != null).findFirst().map(String::trim).orElse(null);
                if (line != null && !line.isEmpty()) {
                    outputCountCombo.setSelectedItem(line);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        outputCountCombo.setSelectedItem("10");
    }

    private void saveOutputCount() {
        try {
            String v = getOutputCountValue();
            if (v != null && !v.isEmpty()) {
                Files.createDirectories(CONFIG_DIR);
                Files.write(OUTPUT_COUNT_FILE, List.of(v), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }

    private String getOutputCountValue() {
        Object item = outputCountCombo.isEditable()
                ? (outputCountCombo.getEditor() != null ? outputCountCombo.getEditor().getItem() : null)
                : outputCountCombo.getSelectedItem();
        String s = item != null ? item.toString().trim() : null;
        return (s != null && !s.isEmpty()) ? s : "10";
    }

    private int getOutputCount() {
        try {
            int n = Integer.parseInt(getOutputCountValue());
            return n >= 1 ? n : 10;
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    private void saveSourceHistory() {
        try {
            List<String> existing = sourceHistory.stream().filter(FileCollectorFrame::isExistingDirectory).collect(Collectors.toList());
            if (existing.isEmpty()) return;
            Files.createDirectories(CONFIG_DIR);
            Files.write(SOURCE_HISTORY_FILE, existing, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception ignored) {
        }
    }

    private void applySourceFilter(String filterText) {
        String filter = (filterText != null && !filterText.trim().isEmpty()) ? filterText.trim().toLowerCase() : "";
        sourceDirCombo.removeAllItems();
        for (String v : sourceHistory) {
            if (filter.isEmpty() || v.toLowerCase().contains(filter)) {
                sourceDirCombo.addItem(v);
            }
        }
    }

    private void addSourceHistory(String path) {
        String v = (path != null) ? path.trim() : null;
        if (v == null || v.isEmpty() || !isExistingDirectory(v)) return;
        if (!sourceHistory.contains(v)) {
            sourceHistory.add(0, v);
            applySourceFilter(sourceFilterField.getText());
        }
        saveSourceHistory();
    }

    private void loadFormSettings() {
        try {
            if (Files.exists(PATTERN_FILE)) {
                patternArea.setText(new String(Files.readAllBytes(PATTERN_FILE), StandardCharsets.UTF_8));
            }
            if (Files.exists(EXCLUDE_PATTERN_FILE)) {
                excludePatternArea.setText(new String(Files.readAllBytes(EXCLUDE_PATTERN_FILE), StandardCharsets.UTF_8));
            }
            if (Files.exists(CLIPBOARD_PREFIX_FILE)) {
                clipboardPrefixField.setText(new String(Files.readAllBytes(CLIPBOARD_PREFIX_FILE), StandardCharsets.UTF_8));
            }
            if (Files.exists(CLIPBOARD_SUFFIX_FILE)) {
                clipboardSuffixField.setText(new String(Files.readAllBytes(CLIPBOARD_SUFFIX_FILE), StandardCharsets.UTF_8));
            }
            if (Files.exists(FILE_SUFFIX_FILE)) {
                fileSuffixField.setText(new String(Files.readAllBytes(FILE_SUFFIX_FILE), StandardCharsets.UTF_8).trim());
            }
        } catch (Exception ignored) {
        }
    }

    private void saveFormSettings() {
        try {
            Files.createDirectories(CONFIG_DIR);
            writeTextFile(PATTERN_FILE, patternArea.getText());
            writeTextFile(EXCLUDE_PATTERN_FILE, excludePatternArea.getText());
            writeTextFile(CLIPBOARD_PREFIX_FILE, clipboardPrefixField.getText());
            writeTextFile(CLIPBOARD_SUFFIX_FILE, clipboardSuffixField.getText());
            String suffix = fileSuffixField.getText();
            writeTextFile(FILE_SUFFIX_FILE, (suffix != null && !suffix.trim().isEmpty()) ? suffix.trim() : "");
        } catch (Exception ignored) {
        }
    }

    private static void writeTextFile(Path path, String content) throws java.io.IOException {
        Files.write(path, (content != null ? content : "").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void doSearch() {
        String src = getSourceDirText();
        if (src != null) src = src.trim();
        List<String> cleaned = parsePatternLines(patternArea.getText());

        if (src == null || src.isEmpty()) {
            showError("対象フォルダを指定してください。");
            return;
        }
        addSourceHistory(src);
        if (cleaned.isEmpty()) {
            showError("抽出条件を1行以上入力してください。");
            return;
        }

        Path srcDir = Paths.get(src);
        if (!Files.isDirectory(srcDir)) {
            showError("フォルダが存在しません: " + src);
            return;
        }

        searchButton.setEnabled(false);
        copyFilesButton.setEnabled(false);
        aiMessageButton.setEnabled(false);
        logArea.setText("");

        List<String> excludePatterns = parsePatternLines(excludePatternArea.getText());
        appendLog("抽出開始: " + srcDir);
        appendLog("収集ファイルパターン (glob): " + String.join(", ", cleaned));
        if (!excludePatterns.isEmpty()) {
            appendLog("除外パターン (glob): " + String.join(", ", excludePatterns));
        }

        boolean dedupeByFileName = dedupeByFileNameCheckBox.isSelected();
        new Thread(() -> {
            try {
                ensureFileListCache(srcDir, false);
                List<Path> jarFiles = findFilesFromFileList(srcDir, cleaned);
                if (!excludePatterns.isEmpty()) {
                    List<PathMatcher> excludeMatchers = buildGlobMatchers(excludePatterns);
                    if (!excludeMatchers.isEmpty()) {
                        int before = jarFiles.size();
                        jarFiles = jarFiles.stream().filter(p -> !pathMatchesAny(srcDir, p, excludeMatchers)).collect(Collectors.toList());
                        appendLog("除外適用: " + (before - jarFiles.size()) + " 件を除外し " + jarFiles.size() + " 件");
                    }
                }
                // 上書き: 既存の抽出結果を破棄し、今回の結果のみを表示する
                HashSet<String> seenNames = new HashSet<>();
                List<Path> toAdd = new ArrayList<>();
                List<Path> excludedByName = new ArrayList<>();
                for (Path p : jarFiles) {
                    if (dedupeByFileName && seenNames.contains(p.getFileName().toString())) {
                        excludedByName.add(p);
                        continue;
                    }
                    toAdd.add(p);
                    seenNames.add(p.getFileName().toString());
                }

                SwingUtilities.invokeLater(() -> {
                    fileListModel.clear();
                    lastFoundFiles.clear();
                    for (Path p : toAdd) {
                        lastFoundFiles.add(p);
                        fileListModel.addElement(srcDir.relativize(p).toString());
                    }
                    updateResultCount();
                });

                appendLog("見つかったファイル数: " + jarFiles.size() + "、結果 " + toAdd.size() + " 件で上書きしました" + (dedupeByFileName ? "（同名ファイル除外）" : ""));
                for (Path it : excludedByName) {
                    appendLog("(除外): " + srcDir.relativize(it).toString().replace("/", "\\"));
                }
                if (jarFiles.isEmpty()) {
                    appendLog("対象ファイルが見つかりませんでした。");
                }
            } catch (Exception e) {
                appendLog("エラー: " + getErrorMessage(e));
                e.printStackTrace();
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(FileCollectorFrame.this, "エラー: " + getErrorMessage(e), "エラー", JOptionPane.ERROR_MESSAGE)
                );
            } finally {
                SwingUtilities.invokeLater(() -> {
                    searchButton.setEnabled(true);
                    updateResultListButtons();
                });
            }
        }, "FileCollectorWorker").start();
    }

    private void doCopyFiles() {
        if (lastFoundFiles == null || lastFoundFiles.isEmpty()) {
            showError("まず抽出を行い、ファイル一覧を取得してください。");
            return;
        }
        String src = getSourceDirText();
        if (src != null) src = src.trim();
        if (src == null || src.isEmpty()) {
            showError("対象フォルダを指定してください。");
            return;
        }
        Path baseDir = Paths.get(src);
        String baseName = baseDir.getFileName() != null ? baseDir.getFileName().toString() : "filecollector";
        String suffix = fileSuffixField.getText();
        if (suffix != null) suffix = suffix.trim();
        else suffix = "";
        Path outDir = FILE_OUTPUT_DIR;
        try {
            if (clearBeforeOutputCheckBox.isSelected() && Files.exists(outDir)) {
                Files.walk(outDir).sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (java.io.IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
            Files.createDirectories(outDir);
            int limit = getOutputCount();
            int toCopy = Math.min(limit, lastFoundFiles.size());
            if (toCopy <= 0) return;
            Map<String, Integer> nameTotalCount = new HashMap<>();
            for (int i = 0; i < toCopy; i++) {
                String nameWithSuffix = fileNameWithSuffix(lastFoundFiles.get(i).getFileName().toString(), suffix);
                nameTotalCount.put(nameWithSuffix, nameTotalCount.getOrDefault(nameWithSuffix, 0) + 1);
            }
            Map<String, Integer> nameCount = new HashMap<>();
            for (int i = 0; i < toCopy; i++) {
                Path file = lastFoundFiles.get(i);
                String baseFileName = file.getFileName().toString();
                String nameWithSuffix = fileNameWithSuffix(baseFileName, suffix);
                String destName = uniqueFlatName(nameWithSuffix, nameCount, nameTotalCount);
                Path dest = outDir.resolve(destName);
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                appendLog("コピー: " + destName);
            }
            appendLog(toCopy + " 件を " + outDir + " に出力しました。");
            Desktop.getDesktop().open(outDir.toFile());
            appendLog("出力フォルダをエクスプローラで表示しました。");
            for (int i = 0; i < toCopy; i++) {
                fileListModel.remove(0);
                lastFoundFiles.remove(0);
            }
            updateResultCount();
            updateResultListButtons();
        } catch (Exception e) {
            appendLog("各ファイル出力中にエラー: " + getErrorMessage(e));
            e.printStackTrace();
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "各ファイル出力中にエラー: " + getErrorMessage(e), "エラー", JOptionPane.ERROR_MESSAGE)
            );
        }
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.replace("\\", "/");
    }

    private static List<String> parsePatternLines(String text) {
        if (text == null) return List.of();
        return Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static Path relativizeNormalized(Path root, Path p) {
        Path rel = root.relativize(p);
        String[] segs = normalizePath(rel.toString()).split("/");
        if (segs.length == 0) return rel;
        return rel.getFileSystem().getPath(segs[0], Arrays.copyOfRange(segs, 1, segs.length));
    }

    private static List<PathMatcher> buildGlobMatchers(List<String> patterns) {
        return patterns.stream()
                .map(FileCollectorFrame::toGlobPattern)
                .filter(p -> p != null && !p.isEmpty())
                .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
                .collect(Collectors.toList());
    }

    private static boolean pathMatchesAny(Path root, Path p, List<PathMatcher> matchers) {
        if (matchers.isEmpty()) return false;
        Path relNorm = relativizeNormalized(root, p);
        Path fileName = p.getFileName();
        for (PathMatcher m : matchers) {
            if (m.matches(relNorm) || (fileName != null && m.matches(fileName))) return true;
        }
        return false;
    }

    private void updateResultListButtons() {
        boolean hasResults = !lastFoundFiles.isEmpty();
        copyFilesButton.setEnabled(hasResults);
        aiMessageButton.setEnabled(hasResults);
    }

    private static String toGlobPattern(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = normalizePath(raw.trim());
        s = s.replace("/../", "**");
        s = s.replace("/.../", "**");
        s = s.replaceAll("/\\s*\\.\\.\\.\\s*/", "**");
        s = s.replaceAll("\\s*\\.\\.\\.\\s*", "**");
        return s.isEmpty() ? "" : "**" + s;
    }

    private static String pathToFileListHash(Path root) {
        String s = root.toAbsolutePath().normalize().toString().replace("\\", "/");
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8 && i < digest.length; i++) {
            sb.append(String.format("%02x", digest[i]));
        }
        return sb.toString();
    }

    private static Path getFileListCachePath(Path root) throws java.io.IOException {
        Files.createDirectories(FILELIST_CACHE_DIR);
        return FILELIST_CACHE_DIR.resolve(pathToFileListHash(root) + ".filelist.txt");
    }

    private void buildAndWriteFileList(Path root) throws java.io.IOException {
        Path cachePath = getFileListCachePath(root);
        List<String> lines = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .forEach(p -> lines.add(p.toAbsolutePath().normalize().toString()));
        }
        Files.write(cachePath, lines, StandardCharsets.UTF_8);
    }

    private void ensureFileListCache(Path root, boolean forceRebuild) {
        try {
            Path cachePath = getFileListCachePath(root);
            if (forceRebuild && Files.exists(cachePath)) {
                Files.delete(cachePath);
            }
            if (!Files.exists(cachePath)) {
                buildAndWriteFileList(root);
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Path> findFilesFromFileList(Path root, List<String> patterns) {
        try {
            Path cachePath = getFileListCachePath(root);
            if (!Files.exists(cachePath)) return findFiles(root, patterns);
            List<PathMatcher> matchers = buildGlobMatchers(patterns);
            List<Path> result = new ArrayList<>();
            for (String line : Files.readAllLines(cachePath, StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) continue;
                Path p = Paths.get(line);
                if (!Files.isRegularFile(p)) continue;
                if (pathMatchesAny(root, p, matchers)) {
                    result.add(p);
                    appendLog("追加: " + root.relativize(p));
                }
            }
            return result;
        } catch (java.io.IOException e) {
            return findFiles(root, patterns);
        }
    }

    private List<Path> findFiles(Path root, List<String> patterns) {
        List<PathMatcher> matchers = buildGlobMatchers(patterns);
        List<Path> result = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> pathMatchesAny(root, p, matchers))
                    .forEach(p -> {
                        result.add(p);
                        appendLog("追加: " + root.relativize(p));
                    });
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private List<String> buildTreeLines(Path root) throws java.io.IOException {
        List<String> lines = new ArrayList<>();
        String rootName = root.getFileName() != null ? root.getFileName().toString() : root.toString();
        lines.add(rootName);
        buildTreeRecursive(root, "", lines);
        return lines;
    }

    private void buildTreeRecursive(Path dir, String prefix, List<String> lines) throws java.io.IOException {
        List<Path> children = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                children.add(p);
            }
        }
        children.sort((a, b) -> a.getFileName().toString().toLowerCase().compareTo(b.getFileName().toString().toLowerCase()));

        int total = children.size();
        for (int idx = 0; idx < total; idx++) {
            Path child = children.get(idx);
            boolean last = (idx == total - 1);
            String connector = last ? "└── " : "├── ";
            String childName = child.getFileName().toString();
            lines.add(prefix + connector + childName);

            if (Files.isDirectory(child)) {
                String nextPrefix = prefix + (last ? "    " : "│   ");
                buildTreeRecursive(child, nextPrefix, lines);
            }
        }
    }

    private void doFileListOutput() {
        String src = getSourceDirText();
        if (src != null) src = src.trim();
        if (src == null || src.isEmpty()) {
            showError("対象フォルダを指定してください。");
            return;
        }

        Path root = Paths.get(src);
        if (!Files.isDirectory(root)) {
            showError("フォルダが存在しません: " + src);
            return;
        }

        appendLog("ファイル tree 出力開始: " + root);
        try {
            ensureFileListCache(root, true);

            String baseName = root.getFileName() != null ? root.getFileName().toString() : "filecollector";
            Path outDir = TREE_OUTPUT_DIR;
            Path outPath = outDir.resolve(baseName + ".tree.txt");

            if (clearBeforeOutputCheckBox.isSelected() && Files.exists(outDir)) {
                Files.walk(outDir).sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (java.io.IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
            Files.createDirectories(outDir);

            List<String> lines = buildTreeLines(root);
            Files.write(outPath, lines, StandardCharsets.UTF_8);

            appendLog("ファイル tree を " + outPath + " に出力しました。");

            Desktop.getDesktop().open(outDir.toFile());
            appendLog("出力フォルダをエクスプローラで表示しました。");
        } catch (Exception e) {
            appendLog("ファイル tree 出力中にエラー: " + getErrorMessage(e));
            e.printStackTrace();
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "ファイル tree 出力中にエラー: " + getErrorMessage(e), "エラー", JOptionPane.ERROR_MESSAGE)
            );
        }
    }

    private static String fileNameWithSuffix(String fileName, String suffix) {
        if (suffix == null || suffix.isEmpty()) return fileName;
        return fileName + suffix;
    }

    private static String uniqueFlatName(String baseFileName, Map<String, Integer> nameCount, Map<String, Integer> nameTotalCount) {
        int total = nameTotalCount.getOrDefault(baseFileName, 1);
        int count = nameCount.getOrDefault(baseFileName, 0);
        nameCount.put(baseFileName, count + 1);
        if (total <= 1) return baseFileName;
        int lastDot = baseFileName.lastIndexOf('.');
        String namePart;
        String extPart;
        if (lastDot > 0) {
            namePart = baseFileName.substring(0, lastDot);
            extPart = baseFileName.substring(lastDot);
        } else {
            namePart = baseFileName;
            extPart = "";
        }
        return namePart + "(" + (count + 1) + ")" + extPart;
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private static String getErrorMessage(Throwable t) {
        if (t == null) return "不明なエラー";
        Throwable current = t;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isEmpty()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return t.getClass().getSimpleName();
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "入力エラー", JOptionPane.WARNING_MESSAGE);
    }
}
