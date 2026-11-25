package com.roomplanner.ui;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.util.Collections;
import java.util.Locale;

/**
 * 모든 Swing 컴포넌트의 기본 글꼴을 한글 글꼴(맑은 고딕)로 바꾼다.
 * 기본 글꼴 그대로면 한글 글자 폭을 잘못 재서 버튼 글자가 "..."로 잘릴 수 있다.
 * 컴포넌트를 만들기 전에 한 번만 호출한다. (맑은 고딕이 없는 OS에서는 아무것도 바꾸지 않는다)
 */
public final class UiFonts {
    private UiFonts() {}

    public static void install() {
        String family = "Malgun Gothic";
        // getFamily()는 한국어 Windows에서 "맑은 고딕"처럼 현지 이름을 돌려주므로 영어 이름으로 비교한다
        // (글꼴이 없으면 Java는 Dialog로 대체하므로, 영어 이름이 다르면 설치되지 않은 것)
        if (!new Font(family, Font.PLAIN, 12).getFamily(Locale.ENGLISH).equals(family)) return;

        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource old) {
                UIManager.put(key, new FontUIResource(family, old.getStyle(), old.getSize()));
            }
        }
    }
}
