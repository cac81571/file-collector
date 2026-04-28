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
import java.util.Set;
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

import com.formdev.flatlaf.FlatClientProperties;

public class FileCollectorFrame extends JFrame {

    private final JComboBox<String> sourceDirComboA = new JComboBox<>();
    private final JComboBox<String> sourceDirComboB = new JComboBox<>();
    private final JTextField sourceTitleFieldA = new JTextField("移行前", 8);
    private final JTextField sourceTitleFieldB = new JTextField("移行後", 8);
    private final JTextField sourceFilterFieldA = new JTextField(20);
    private final JTextField sourceFilterFieldB = new JTextField(20);
    private final JTextArea patternArea = new JTextArea("", 6, 55);
    private final JTextArea excludePatternArea = new JTextArea("", 2, 55);
    private final JTextField fileSuffixExcludeExtensionsField = new JTextField("pdf,docx,doc,pptx,ppt,rtf,xlsx,xls,csv,json,txt,md,html,htm,xml", 30);
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
    private final JButton fileListButton = new JButton("tree 出力");
    private final JButton removeSelectedButton = new JButton("選択削除");
    private final JButton removeExceptSelectedButton = new JButton("選択以外削除");
    private final JCheckBox clearBeforeOutputCheckBox = new JCheckBox("既存ファイル削除", true);
    private final JLabel resultCountLabel = new JLabel("0 件");

    private List<Path> lastFoundFiles = new ArrayList<>();
    private List<Path> lastFoundRoots = new ArrayList<>();
    private final List<String> sourceHistory = new ArrayList<>();

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".filecollector");
    private static final Path FILELIST_CACHE_DIR = CONFIG_DIR;
    private static final Path FILE_OUTPUT_DIR = CONFIG_DIR.resolve("FileCollector");
    private static final Path TREE_OUTPUT_DIR = CONFIG_DIR.resolve("FileCollectorTree");
    private static final Path OUTPUT_COUNT_FILE = CONFIG_DIR.resolve("output-count.txt");
    private static final Path SOURCE_HISTORY_FILE = CONFIG_DIR.resolve("history.txt");
    private static final Path SOURCE_LAST_A_FILE = CONFIG_DIR.resolve("source-last-a.txt");
    private static final Path SOURCE_LAST_B_FILE = CONFIG_DIR.resolve("source-last-b.txt");
    private static final Path PATTERN_FILE = CONFIG_DIR.resolve("pattern.txt");
    private static final Path EXCLUDE_PATTERN_FILE = CONFIG_DIR.resolve("exclude-pattern.txt");
    private static final Path CLIPBOARD_PREFIX_FILE = CONFIG_DIR.resolve("clipboard-prefix.txt");
    private static final Path CLIPBOARD_SUFFIX_FILE = CONFIG_DIR.resolve("clipboard-suffix.txt");
    private static final Path FILE_SUFFIX_FILE = CONFIG_DIR.resolve("file-suffix.txt");
    private static final Path SOURCE_TITLE_A_FILE = CONFIG_DIR.resolve("source-title-a.txt");
    private static final Path SOURCE_TITLE_B_FILE = CONFIG_DIR.resolve("source-title-b.txt");
    /** 拡張子追加文字を付加しない拡張子一覧（ドットなし・小文字）。設定ファイル file-suffix-exclude-extensions.txt で定義。 */
    private static final Path FILE_SUFFIX_EXCLUDE_EXTENSIONS_FILE = CONFIG_DIR.resolve("file-suffix-exclude-extensions.txt");

    private Set<String> fileSuffixExcludeExtensions = new HashSet<>();

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
                addSourceHistory(getSourceDirTextA());
                addSourceHistory(getSourceDirTextB());
                saveLastSourceSelection(SOURCE_LAST_A_FILE, getSourceDirTextA());
                saveLastSourceSelection(SOURCE_LAST_B_FILE, getSourceDirTextB());
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

        c.insets = new Insets(0, 4, 4, 4);
        JPanel sourceRowsPanel = new JPanel();
        sourceRowsPanel.setLayout(new BoxLayout(sourceRowsPanel, BoxLayout.Y_AXIS));
        JPanel sourceRowPanel = new JPanel(new BorderLayout(4, 0));
        JPanel sourceLeftPanel = new JPanel();
        sourceLeftPanel.setLayout(new BoxLayout(sourceLeftPanel, BoxLayout.LINE_AXIS));
        JLabel sourceLabel = new JLabel("フォルダA");
        sourceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        sourceLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        sourceFilterFieldA.setColumns(15);
        sourceFilterFieldA.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "フィルタ条件（部分一致）");
        sourceFilterFieldA.setAlignmentY(Component.CENTER_ALIGNMENT);
        sourceLeftPanel.add(sourceLabel);
        sourceLeftPanel.add(Box.createHorizontalStrut(6));
        sourceTitleFieldA.setToolTipText("抽出結果の先頭ラベルに表示するタイトル");
        sourceLeftPanel.add(sourceTitleFieldA);
        sourceLeftPanel.add(Box.createHorizontalStrut(6));
        sourceLeftPanel.add(sourceFilterFieldA);
        sourceRowPanel.add(sourceLeftPanel, BorderLayout.WEST);

        JPanel sourceCenterPanel = new JPanel(new BorderLayout(4, 0));
        sourceDirComboA.setEditable(true);
        sourceDirComboA.setPreferredSize(new Dimension(500, sourceDirComboA.getPreferredSize().height));
        sourceCenterPanel.add(sourceDirComboA, BorderLayout.CENTER);
        sourceRowPanel.add(sourceCenterPanel, BorderLayout.CENTER);
        sourceRowsPanel.add(sourceRowPanel);
        JPanel sourceRowPanelB = new JPanel(new BorderLayout(4, 0));
        JPanel sourceLeftPanelB = new JPanel();
        sourceLeftPanelB.setLayout(new BoxLayout(sourceLeftPanelB, BoxLayout.LINE_AXIS));
        JLabel sourceLabelB = new JLabel("フォルダB");
        sourceLabelB.setHorizontalAlignment(SwingConstants.RIGHT);
        sourceLabelB.setAlignmentY(Component.CENTER_ALIGNMENT);
        sourceLeftPanelB.add(sourceLabelB);
        sourceLeftPanelB.add(Box.createHorizontalStrut(6));
        sourceTitleFieldB.setToolTipText("抽出結果の先頭ラベルに表示するタイトル");
        sourceLeftPanelB.add(sourceTitleFieldB);
        sourceLeftPanelB.add(Box.createHorizontalStrut(6));
        sourceFilterFieldB.setColumns(15);
        sourceFilterFieldB.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "フィルタ条件（部分一致）");
        sourceFilterFieldB.setAlignmentY(Component.CENTER_ALIGNMENT);
        sourceLeftPanelB.add(sourceFilterFieldB);
        sourceRowPanelB.add(sourceLeftPanelB, BorderLayout.WEST);

        JPanel sourceCenterPanelB = new JPanel(new BorderLayout(4, 0));
        sourceDirComboB.setEditable(true);
        sourceDirComboB.setPreferredSize(new Dimension(500, sourceDirComboB.getPreferredSize().height));
        sourceCenterPanelB.add(sourceDirComboB, BorderLayout.CENTER);
        sourceRowPanelB.add(sourceCenterPanelB, BorderLayout.CENTER);
        sourceRowsPanel.add(Box.createVerticalStrut(4));
        sourceRowsPanel.add(sourceRowPanelB);

        Dimension treeBtnPref = fileListButton.getPreferredSize();
        fileListButton.setPreferredSize(new Dimension(treeBtnPref.width, 46));
        fileListButton.setMaximumSize(new Dimension(treeBtnPref.width, 46));
        JPanel sourceGroupPanel = new JPanel(new BorderLayout(4, 0));
        sourceGroupPanel.add(sourceRowsPanel, BorderLayout.CENTER);
        JPanel sourceButtonWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        sourceButtonWrap.add(fileListButton);
        sourceGroupPanel.add(sourceButtonWrap, BorderLayout.EAST);

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        c.weightx = 1.0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(sourceGroupPanel, c);
        c.gridwidth = 1;
        c.insets = new Insets(4, 4, 4, 4);

        row++;
        // 抽出条件（左）・除外条件（右）を同一行に、ラベルはテキストエリアの上に配置
        JPanel patternLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel patternLabel = new JLabel("抽出条件 (glob パターン)");
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
        patternLabelPanel.add(patternLabel);
        patternLabelPanel.add(Box.createHorizontalStrut(4));
        patternLabelPanel.add(helpIconButton);

        JPanel patternLeftPanel = new JPanel(new BorderLayout(4, 2));
        patternLeftPanel.add(patternLabelPanel, BorderLayout.NORTH);
        JScrollPane patternScroll = new JScrollPane(patternArea);
        patternScroll.setMinimumSize(new Dimension(150, 90));
        patternArea.setLineWrap(true);
        patternArea.setWrapStyleWord(true);
        patternLeftPanel.add(patternScroll, BorderLayout.CENTER);

        JPanel excludeLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        excludeLabelPanel.add(new JLabel("除外条件 (glob)"));
        int excludeLabelTop = Math.max(0, patternLabelPanel.getPreferredSize().height - excludeLabelPanel.getPreferredSize().height);
        if (excludeLabelTop > 0) {
            excludeLabelPanel.setBorder(new EmptyBorder(excludeLabelTop, 0, 0, 0));
        }

        JPanel excludeRightPanel = new JPanel(new BorderLayout(4, 2));
        excludeRightPanel.add(excludeLabelPanel, BorderLayout.NORTH);
        excludePatternArea.setLineWrap(true);
        excludePatternArea.setWrapStyleWord(true);
        excludePatternArea.setToolTipText("ここにマッチしたファイルは抽出結果に含めません。1行1パターン。空欄なら除外なし。");
        JScrollPane excludeScroll = new JScrollPane(excludePatternArea);
        excludeScroll.setMinimumSize(new Dimension(150, 90));
        excludeRightPanel.add(excludeScroll, BorderLayout.CENTER);

        Dimension searchBtnPref = searchButton.getPreferredSize();
        searchButton.setPreferredSize(new Dimension(searchBtnPref.width, 40));
        searchButton.setMaximumSize(new Dimension(searchBtnPref.width, 40));
        JPanel searchBtnWrap = new JPanel(new BorderLayout());
        searchBtnWrap.add(searchButton, BorderLayout.NORTH);
        dedupeByFileNameCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchBtnWrap.add(dedupeByFileNameCheckBox, BorderLayout.SOUTH);

        JPanel patternExcludeRowPanel = new JPanel(new GridBagLayout());
        GridBagConstraints pec = new GridBagConstraints();
        pec.insets = new Insets(0, 0, 0, 8);
        pec.fill = GridBagConstraints.BOTH;
        pec.weightx = 0.5;
        pec.weighty = 1.0;
        pec.gridx = 0;
        pec.gridy = 0;
        patternExcludeRowPanel.add(patternLeftPanel, pec);
        pec.weightx = 0.5;
        pec.gridx = 1;
        patternExcludeRowPanel.add(excludeRightPanel, pec);
        pec.weightx = 0.0;
        pec.fill = GridBagConstraints.NONE;
        pec.anchor = GridBagConstraints.SOUTH;
        pec.gridx = 2;
        patternExcludeRowPanel.add(searchBtnWrap, pec);

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 3;
        c.weightx = 1.0;
        c.weighty = 0.0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.BOTH;
        form.add(patternExcludeRowPanel, c);
        c.gridwidth = 1;
        c.weighty = 0.0;

        JPanel optionsRow = new JPanel(new BorderLayout(8, 0));
        JPanel optionsWest = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel prefixLabel2 = new JLabel("クリップボード出力");
        prefixLabel2.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsWest.add(prefixLabel2);
        optionsWest.add(Box.createHorizontalStrut(20));
        JLabel prefixLabel = new JLabel("<html>先頭<br/>付加</html>");
        prefixLabel.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsWest.add(prefixLabel);
        optionsWest.add(Box.createHorizontalStrut(4));
        clipboardPrefixField.setToolTipText("クリップボード出力時にファイルの先頭に追加。#{title} #{ext} #{filename} #{filepath} で置換");
        clipboardPrefixField.setLineWrap(true);
        clipboardPrefixField.setWrapStyleWord(true);
        JScrollPane prefixScroll = new JScrollPane(clipboardPrefixField);
        prefixScroll.setPreferredSize(new Dimension(200, 40));
        prefixScroll.setMinimumSize(new Dimension(80, 40));
        optionsRow.add(optionsWest, BorderLayout.WEST);
        optionsRow.add(prefixScroll, BorderLayout.CENTER);

        JPanel optionsEast = new JPanel();
        optionsEast.setLayout(new BoxLayout(optionsEast, BoxLayout.LINE_AXIS));
        JLabel suffixLabel = new JLabel("<html>末尾<br/>付加</html>");
        suffixLabel.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsEast.add(suffixLabel);
        optionsEast.add(Box.createHorizontalStrut(4));
        clipboardSuffixField.setToolTipText("クリップボード出力時にファイルの末尾に追加。#{title} #{ext} #{filename} #{filepath} で置換");
        clipboardSuffixField.setLineWrap(true);
        clipboardSuffixField.setWrapStyleWord(true);
        JScrollPane suffixScroll = new JScrollPane(clipboardSuffixField);
        suffixScroll.setPreferredSize(new Dimension(240, 40));
        suffixScroll.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsEast.add(suffixScroll);
        optionsEast.add(Box.createHorizontalStrut(8));
        JPanel clipboardButtonPanel = new JPanel();
        clipboardButtonPanel.setLayout(new BoxLayout(clipboardButtonPanel, BoxLayout.Y_AXIS));
        clipboardOutputButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        clipboardButtonPanel.add(clipboardOutputButton);
        clipboardAddPrefixSuffixCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        clipboardButtonPanel.add(clipboardAddPrefixSuffixCheckBox);
        clipboardButtonPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        optionsEast.add(clipboardButtonPanel);

        updateClipboardPrefixSuffixEnabled();
        optionsRow.add(optionsEast, BorderLayout.EAST);

        JPanel excludeExtRowPanel = new JPanel(new BorderLayout(8, 0));
        excludeExtRowPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        excludeExtRowPanel.add(new JLabel("拡張子追加 対象外"), BorderLayout.WEST);
        fileSuffixExcludeExtensionsField.setToolTipText("ファイル出力時に拡張子追加文字を付加しない拡張子。カンマ区切り、ドットなし（例: java, md, txt）");
        excludeExtRowPanel.add(fileSuffixExcludeExtensionsField, BorderLayout.CENTER);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(form);
        topPanel.add(optionsRow);
        topPanel.add(excludeExtRowPanel);
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

        Runnable onFilterAChanged = () -> {
            applySourceFilter(sourceFilterFieldA.getText(), sourceDirComboA, sourceHistory);
            if (sourceDirComboA.getItemCount() > 0) {
                sourceDirComboA.showPopup();
            } else {
                sourceDirComboA.hidePopup();
            }
        };
        sourceFilterFieldA.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { onFilterAChanged.run(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onFilterAChanged.run(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onFilterAChanged.run(); }
        });

        Runnable onFilterBChanged = () -> {
            applySourceFilter(sourceFilterFieldB.getText(), sourceDirComboB, sourceHistory);
            if (sourceDirComboB.getItemCount() > 0) {
                sourceDirComboB.showPopup();
            } else {
                sourceDirComboB.hidePopup();
            }
        };
        sourceFilterFieldB.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { onFilterBChanged.run(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onFilterBChanged.run(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onFilterBChanged.run(); }
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
                if (idx < lastFoundRoots.size()) {
                    lastFoundRoots.remove(idx);
                }
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
        List<Path> newRoots = new ArrayList<>();
        for (int idx : selectedIndicesList) {
            if (idx < lastFoundRoots.size()) {
                newRoots.add(lastFoundRoots.get(idx));
            }
        }
        fileListModel.clear();
        for (String it : newModelItems) {
            fileListModel.addElement(it);
        }
        lastFoundFiles.clear();
        lastFoundFiles.addAll(newPaths);
        lastFoundRoots.clear();
        lastFoundRoots.addAll(newRoots);
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
        if (lastFoundRoots.size() != lastFoundFiles.size()) {
            showError("抽出結果の内部状態が不正です。再抽出してください。");
            return;
        }
        boolean addPrefixSuffix = clipboardAddPrefixSuffixCheckBox.isSelected();
        String prefixTemplate = addPrefixSuffix ? (clipboardPrefixField.getText() != null ? clipboardPrefixField.getText() : "") : "";
        String suffixTemplate = addPrefixSuffix ? (clipboardSuffixField.getText() != null ? clipboardSuffixField.getText() : "") : "";
        List<String> parts = new ArrayList<>();
        int[] skipped = {0};
        List<Integer> sortedIndices = Arrays.stream(indices).boxed().sorted().collect(Collectors.toList());
        for (int idx : sortedIndices) {
            if (idx >= lastFoundFiles.size()) continue;
            Path path = lastFoundFiles.get(idx);
            Path baseDir = lastFoundRoots.get(idx);
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (!parts.isEmpty()) parts.add("");
                String title = getSourceTitleForRoot(baseDir);
                String prefix = addPrefixSuffix ? applyClipboardPlaceholders(prefixTemplate, path, baseDir, title) : "";
                String suffix = addPrefixSuffix ? applyClipboardPlaceholders(suffixTemplate, path, baseDir, title) : "";
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

    private static String applyClipboardPlaceholders(String template, Path path, Path baseDir, String title) {
        if (template == null || template.isEmpty()) return "";
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
        String ext = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) ext = fileName.substring(dotIdx + 1);
        String relativePath = baseDir.relativize(path).toString().replace('\\', '/');
        return template
                .replaceAll("#\\{title\\}", Matcher.quoteReplacement(title != null ? title : ""))
                .replaceAll("#\\{ext\\}", Matcher.quoteReplacement(ext))
                .replaceAll("#\\{filename\\}", Matcher.quoteReplacement(fileName))
                .replaceAll("#\\{filepath\\}", Matcher.quoteReplacement(relativePath));
    }

    private String getSourceDirTextA() {
        ComboBoxEditor editor = sourceDirComboA.getEditor();
        Object item = editor != null ? editor.getItem() : null;
        return item != null ? item.toString() : null;
    }

    private String getSourceDirTextB() {
        ComboBoxEditor editor = sourceDirComboB.getEditor();
        Object item = editor != null ? editor.getItem() : null;
        return item != null ? item.toString() : null;
    }

    private String getSourceTitleA() {
        String v = sourceTitleFieldA.getText();
        return (v != null && !v.trim().isEmpty()) ? v.trim() : "フォルダA";
    }

    private String getSourceTitleB() {
        String v = sourceTitleFieldB.getText();
        return (v != null && !v.trim().isEmpty()) ? v.trim() : "フォルダB";
    }

    private String getSourceTitleForRoot(Path root) {
        if (root == null) return "フォルダ";
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            String srcA = getSourceDirTextA();
            if (srcA != null && !srcA.trim().isEmpty()) {
                Path pathA = Paths.get(srcA.trim()).toAbsolutePath().normalize();
                if (normalizedRoot.equals(pathA)) return getSourceTitleA();
            }
            String srcB = getSourceDirTextB();
            if (srcB != null && !srcB.trim().isEmpty()) {
                Path pathB = Paths.get(srcB.trim()).toAbsolutePath().normalize();
                if (normalizedRoot.equals(pathB)) return getSourceTitleB();
            }
        } catch (Exception ignored) {
        }
        String rootName = root.getFileName() != null ? root.getFileName().toString() : "フォルダ";
        return (rootName != null && !rootName.isEmpty()) ? rootName : "フォルダ";
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
            }
            applySourceFilter(sourceFilterFieldA.getText(), sourceDirComboA, sourceHistory);
            applySourceFilter(sourceFilterFieldB.getText(), sourceDirComboB, sourceHistory);
            loadLastSourceSelection(SOURCE_LAST_A_FILE, sourceDirComboA);
            loadLastSourceSelection(SOURCE_LAST_B_FILE, sourceDirComboB);
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

    private void applySourceFilter(String filterText, JComboBox<String> targetCombo, List<String> sourceHistory) {
        String filter = (filterText != null && !filterText.trim().isEmpty()) ? filterText.trim().toLowerCase() : "";
        targetCombo.removeAllItems();
        for (String v : sourceHistory) {
            if (filter.isEmpty() || v.toLowerCase().contains(filter)) {
                targetCombo.addItem(v);
            }
        }
    }

    private void addSourceHistory(String path) {
        String v = (path != null) ? path.trim() : null;
        if (v == null || v.isEmpty() || !isExistingDirectory(v)) return;
        if (!sourceHistory.contains(v)) {
            sourceHistory.add(0, v);
            applySourceFilter(sourceFilterFieldA.getText(), sourceDirComboA, sourceHistory);
            applySourceFilter(sourceFilterFieldB.getText(), sourceDirComboB, sourceHistory);
        }
        saveSourceHistory();
    }

    private void saveLastSourceSelection(Path file, String value) {
        try {
            Files.createDirectories(CONFIG_DIR);
            writeTextFile(file, value != null ? value.trim() : "");
        } catch (Exception ignored) {
        }
    }

    private void loadLastSourceSelection(Path file, JComboBox<String> combo) {
        try {
            if (!Files.exists(file)) return;
            String value = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
            if (!value.isEmpty()) {
                combo.getEditor().setItem(value);
            }
        } catch (Exception ignored) {
        }
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
            if (Files.exists(SOURCE_TITLE_A_FILE)) {
                sourceTitleFieldA.setText(new String(Files.readAllBytes(SOURCE_TITLE_A_FILE), StandardCharsets.UTF_8).trim());
            }
            if (Files.exists(SOURCE_TITLE_B_FILE)) {
                sourceTitleFieldB.setText(new String(Files.readAllBytes(SOURCE_TITLE_B_FILE), StandardCharsets.UTF_8).trim());
            }
            if (Files.exists(FILE_SUFFIX_EXCLUDE_EXTENSIONS_FILE)) {
                List<String> exts = new ArrayList<>();
                for (String line : Files.readAllLines(FILE_SUFFIX_EXCLUDE_EXTENSIONS_FILE, StandardCharsets.UTF_8)) {
                    for (String part : line.split("[,]")) {
                        String ext = part.trim();
                        if (!ext.isEmpty() && !ext.startsWith("#")) exts.add(ext);
                    }
                }
                fileSuffixExcludeExtensionsField.setText(String.join(", ", exts));
            }
            updateFileSuffixExcludeExtensionsFromField();
        } catch (Exception ignored) {
        }
    }

    private void updateFileSuffixExcludeExtensionsFromField() {
        fileSuffixExcludeExtensions.clear();
        for (String part : fileSuffixExcludeExtensionsField.getText().split("[,]")) {
            String ext = part.trim();
            if (ext.isEmpty()) continue;
            fileSuffixExcludeExtensions.add(ext.toLowerCase());
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
            String sourceTitleA = sourceTitleFieldA.getText();
            String sourceTitleB = sourceTitleFieldB.getText();
            writeTextFile(SOURCE_TITLE_A_FILE, sourceTitleA != null ? sourceTitleA.trim() : "");
            writeTextFile(SOURCE_TITLE_B_FILE, sourceTitleB != null ? sourceTitleB.trim() : "");
            String excludeExts = fileSuffixExcludeExtensionsField.getText();
            writeTextFile(FILE_SUFFIX_EXCLUDE_EXTENSIONS_FILE, excludeExts != null ? excludeExts.trim() : "");
        } catch (Exception ignored) {
        }
    }

    private static void writeTextFile(Path path, String content) throws java.io.IOException {
        Files.write(path, (content != null ? content : "").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void doSearch() {
        String srcA = getSourceDirTextA();
        if (srcA != null) srcA = srcA.trim();
        String srcB = getSourceDirTextB();
        if (srcB != null) srcB = srcB.trim();
        List<String> cleaned = parsePatternLines(patternArea.getText());

        if ((srcA == null || srcA.isEmpty()) && (srcB == null || srcB.isEmpty())) {
            showError("フォルダA または フォルダBを指定してください。");
            return;
        }
        if (srcA != null && !srcA.isEmpty()) {
            addSourceHistory(srcA);
        }
        if (srcB != null && !srcB.isEmpty()) {
            addSourceHistory(srcB);
        }
        if (cleaned.isEmpty()) {
            showError("抽出条件を1行以上入力してください。");
            return;
        }

        List<Path> sourceDirs = new ArrayList<>();
        Map<Path, String> sourceTitles = new HashMap<>();
        if (srcA != null && !srcA.isEmpty()) {
            Path pathA = Paths.get(srcA);
            sourceDirs.add(pathA);
            sourceTitles.put(pathA, getSourceTitleA());
        }
        if (srcB != null && !srcB.isEmpty()) {
            Path pathB = Paths.get(srcB);
            if (sourceDirs.stream().noneMatch(pathB::equals)) {
                sourceDirs.add(pathB);
                sourceTitles.put(pathB, getSourceTitleB());
            }
        }
        for (Path sourceDir : sourceDirs) {
            if (!Files.isDirectory(sourceDir)) {
                showError("フォルダが存在しません: " + sourceDir);
                return;
            }
        }

        searchButton.setEnabled(false);
        copyFilesButton.setEnabled(false);
        aiMessageButton.setEnabled(false);
        logArea.setText("");

        List<String> excludePatterns = parsePatternLines(excludePatternArea.getText());
        appendLog("抽出開始: " + sourceDirs.stream().map(Path::toString).collect(Collectors.joining(", ")));
        appendLog("収集ファイルパターン (glob): " + String.join(", ", cleaned));
        if (!excludePatterns.isEmpty()) {
            appendLog("除外パターン (glob): " + String.join(", ", excludePatterns));
        }

        boolean dedupeByFileName = dedupeByFileNameCheckBox.isSelected();
        new Thread(() -> {
            try {
                List<PathMatcher> excludeMatchers = buildGlobMatchers(excludePatterns);
                List<Path> toAdd = new ArrayList<>();
                List<Path> toAddRoots = new ArrayList<>();
                List<Path> excludedByName = new ArrayList<>();
                int totalFound = 0;
                for (Path srcDir : sourceDirs) {
                    HashSet<String> seenNamesInSource = new HashSet<>();
                    ensureFileListCache(srcDir, false);
                    List<Path> found = findFilesFromFileList(srcDir, cleaned);
                    if (!excludeMatchers.isEmpty()) {
                        int before = found.size();
                        found = found.stream().filter(p -> !pathMatchesAny(srcDir, p, excludeMatchers)).collect(Collectors.toList());
                        appendLog("除外適用(" + srcDir + "): " + (before - found.size()) + " 件を除外し " + found.size() + " 件");
                    }
                    totalFound += found.size();
                    for (Path p : found) {
                        if (dedupeByFileName && seenNamesInSource.contains(p.getFileName().toString())) {
                            excludedByName.add(p);
                            continue;
                        }
                        toAdd.add(p);
                        toAddRoots.add(srcDir);
                        seenNamesInSource.add(p.getFileName().toString());
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    fileListModel.clear();
                    lastFoundFiles.clear();
                    lastFoundRoots.clear();
                    for (int i = 0; i < toAdd.size(); i++) {
                        Path p = toAdd.get(i);
                        Path root = toAddRoots.get(i);
                        lastFoundFiles.add(p);
                        lastFoundRoots.add(root);
                        String title = sourceTitles.getOrDefault(root, root.getFileName() != null ? root.getFileName().toString() : root.toString());
                        fileListModel.addElement("[" + title + "] " + root.relativize(p));
                    }
                    updateResultCount();
                });

                appendLog("見つかったファイル数: " + totalFound + "、結果 " + toAdd.size() + " 件で上書きしました" + (dedupeByFileName ? "（同名ファイル除外）" : ""));
                for (Path it : excludedByName) {
                    appendLog("(除外): " + it.getFileName());
                }
                if (totalFound == 0) {
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
        String suffix = fileSuffixField.getText();
        if (suffix != null) suffix = suffix.trim();
        else suffix = "";
        Path outDir = FILE_OUTPUT_DIR;
        try {
            updateFileSuffixExcludeExtensionsFromField();
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
                Path root = i < lastFoundRoots.size() ? lastFoundRoots.get(i) : null;
                String titlePrefix = getSourceTitleForRoot(root).replaceAll("[\\\\/:*?\"<>|]", "_");
                String nameWithSuffix = fileNameWithSuffix(lastFoundFiles.get(i).getFileName().toString(), suffix);
                nameWithSuffix = titlePrefix + "_" + nameWithSuffix;
                nameTotalCount.put(nameWithSuffix, nameTotalCount.getOrDefault(nameWithSuffix, 0) + 1);
            }
            Map<String, Integer> nameCount = new HashMap<>();
            for (int i = 0; i < toCopy; i++) {
                Path file = lastFoundFiles.get(i);
                Path root = i < lastFoundRoots.size() ? lastFoundRoots.get(i) : null;
                String titlePrefix = getSourceTitleForRoot(root).replaceAll("[\\\\/:*?\"<>|]", "_");
                String baseFileName = file.getFileName().toString();
                String nameWithSuffix = fileNameWithSuffix(baseFileName, suffix);
                nameWithSuffix = titlePrefix + "_" + nameWithSuffix;
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
                if (!lastFoundRoots.isEmpty()) {
                    lastFoundRoots.remove(0);
                }
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
        String srcA = getSourceDirTextA();
        String srcB = getSourceDirTextB();
        if (srcA != null) srcA = srcA.trim();
        if (srcB != null) srcB = srcB.trim();

        if ((srcA == null || srcA.isEmpty()) && (srcB == null || srcB.isEmpty())) {
            showError("フォルダA または フォルダBを指定してください。");
            return;
        }
        try {
            if (Files.exists(TREE_OUTPUT_DIR)) {
                Files.walk(TREE_OUTPUT_DIR).sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (java.io.IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }
            Files.createDirectories(TREE_OUTPUT_DIR);
        } catch (Exception e) {
            appendLog("ファイル tree 出力前のクリーン中にエラー: " + getErrorMessage(e));
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "ファイル tree 出力前のクリーン中にエラー: " + getErrorMessage(e), "エラー", JOptionPane.ERROR_MESSAGE)
            );
            return;
        }
        if (srcA != null && !srcA.isEmpty()) {
            doFileListOutputSingle(srcA, "フォルダA", getSourceTitleA());
        }
        if (srcB != null && !srcB.isEmpty()) {
            doFileListOutputSingle(srcB, "フォルダB", getSourceTitleB());
        }
    }

    private void doFileListOutputSingle(String src, String label, String title) {
        if (src != null) src = src.trim();
        if (src == null || src.isEmpty()) {
            showError(label + "を指定してください。");
            return;
        }

        Path root = Paths.get(src);
        if (!Files.isDirectory(root)) {
            showError("フォルダが存在しません: " + src);
            return;
        }

        appendLog("ファイル tree 出力開始(" + label + "): " + root);
        try {
            ensureFileListCache(root, true);

            String baseName = root.getFileName() != null ? root.getFileName().toString() : "filecollector";
            Path outDir = TREE_OUTPUT_DIR;
            Path outPath = resolveUniqueTreeOutputPath(outDir, baseName, title, label);

            Files.createDirectories(outDir);

            List<String> lines = buildTreeLines(root);
            Files.write(outPath, lines, StandardCharsets.UTF_8);

            appendLog("ファイル tree(" + label + ") を " + outPath + " に出力しました。");

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

    private static Path resolveUniqueTreeOutputPath(Path outDir, String baseName, String title, String label) {
        String safeBaseName = sanitizeFileNamePart(baseName, "filecollector");
        String safeTitle = sanitizeFileNamePart(title, label);
        String rootName = safeTitle + "_" + safeBaseName;
        Path firstChoice = outDir.resolve(rootName + ".tree.txt");
        if (!Files.exists(firstChoice)) return firstChoice;

        int index = 2;
        while (true) {
            Path candidate = outDir.resolve(rootName + "(" + index + ").tree.txt");
            if (!Files.exists(candidate)) return candidate;
            index++;
        }
    }

    private static String sanitizeFileNamePart(String value, String fallback) {
        String normalized = value != null ? value.trim() : "";
        if (normalized.isEmpty()) normalized = fallback != null ? fallback.trim() : "";
        if (normalized.isEmpty()) normalized = "filecollector";
        String sanitized = normalized.replaceAll("[\\\\/:*?\"<>|]", "_");
        return sanitized.isEmpty() ? "filecollector" : sanitized;
    }

    private String fileNameWithSuffix(String fileName, String suffix) {
        if (suffix == null || suffix.isEmpty()) return fileName;
        if (!fileSuffixExcludeExtensions.isEmpty()) {
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < fileName.length() - 1) {
                String ext = fileName.substring(lastDot + 1).toLowerCase();
                if (fileSuffixExcludeExtensions.contains(ext)) return fileName;
            }
        }
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
