package com.roomplanner;

import com.roomplanner.ui.MainFrame;
import com.roomplanner.ui.UiFonts;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // 기본 룩앤필 사용
            }
            UiFonts.install();   // 한글 글꼴 지정 (컴포넌트를 만들기 전에)
            new MainFrame().setVisible(true);
        });
    }
}
