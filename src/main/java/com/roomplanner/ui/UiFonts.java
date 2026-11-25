package com.roomplanner.ui;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 모든 Swing 컴포넌트의 기본 글꼴을 한글 글꼴(맑은 고딕 등)로 바꾼다.
 * 글자 폭 오차로 인한 버튼 글자 잘림("...") 및 글자 깨짐을 방지한다.
 */
public final class UiFonts {
    private UiFonts() {}

    public static void install() {
        String[] preferred = { "맑은 고딕", "Malgun Gothic", "NanumGothic", "Apple SD Gothic Neo" };
        Set<String> available = new HashSet<>();
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            available.add(f.toLowerCase());
        }

        String targetFamily = null;
        for (String candidate : preferred) {
            if (available.contains(candidate.toLowerCase())) {
                targetFamily = candidate;
                break;
            }
        }

        if (targetFamily == null) {
            targetFamily = Font.SANS_SERIF;
        }

        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource old) {
                UIManager.put(key, new FontUIResource(targetFamily, old.getStyle(), old.getSize()));
            } else if (key.toString().endsWith(".font")) {
                Font f = UIManager.getFont(key);
                if (f != null) {
                    UIManager.put(key, new FontUIResource(targetFamily, f.getStyle(), f.getSize()));
                }
            }
        }
    }
}
