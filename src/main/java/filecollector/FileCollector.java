/**
 * ファイル収集ツール (FileCollector)
 * 対象フォルダ内のファイルを glob パターンで絞り込み、一覧表示・tree 出力・ファイル出力を行う Swing アプリ。
 */
package filecollector;

import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLightLaf;

public class FileCollector {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            new FileCollectorFrame().setVisible(true);
        });
    }
}
