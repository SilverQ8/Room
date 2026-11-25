package com.roomplanner.ui;

import com.roomplanner.model.Furniture;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 왼쪽에 붙는 가구 카탈로그 (기존 JavaFX SidebarCatalog의 Swing 버전).
 * 카드를 누르면 onPick(종류, shift눌림)이 호출된다.
 *   클릭        → 화면 중앙에 바로 추가
 *   Shift+클릭  → 마우스로 위치를 정해서 배치
 */
public class CatalogPanel extends JPanel {

    private static final Color BG = new Color(0x26, 0x2c, 0x36);
    private static final Color CARD = new Color(0x30, 0x36, 0x3d);
    private static final Color CARD_HOVER = new Color(0x3b, 0x43, 0x4e);
    private static final Color LINE = new Color(0x48, 0x4f, 0x58);
    private static final Color ACCENT = new Color(0x58, 0xa6, 0xff);
    private static final Color TEXT = new Color(0xe6, 0xed, 0xf3);
    private static final Color SUB = new Color(0x8b, 0x94, 0x9e);

    private final Map<Furniture.Type, Card> cards = new EnumMap<>(Furniture.Type.class);
    private Furniture.Type active;

    public CatalogPanel(BiConsumer<Furniture.Type, Boolean> onPick) {
        setLayout(new BorderLayout());
        setBackground(BG);
        setPreferredSize(new Dimension(240, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, LINE));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));
        JLabel title = new JLabel("가구 카탈로그");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setForeground(TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        JLabel desc = new JLabel("<html>클릭: 화면 중앙에 바로 추가<br>Shift+클릭: 마우스로 위치를 정해서 배치</html>");
        desc.setFont(desc.getFont().deriveFont(Font.PLAIN, 11f));
        desc.setForeground(SUB);
        desc.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(6));
        header.add(desc);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(4, 14, 14, 14));
        for (Furniture.Type type : Furniture.Type.values()) {
            Card card = new Card(type, onPick);
            cards.put(type, card);
            list.add(card);
            list.add(Box.createVerticalStrut(10));
        }

        JScrollPane scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(BG);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /** 지금 '가구 배치' 중인 종류를 강조한다 (없으면 null). */
    public void setActive(Furniture.Type type) {
        active = type;
        repaint();
    }

    public Furniture.Type getActive() { return active; }

    /** 해당 종류의 카드 컴포넌트 (테스트용). */
    public Component cardOf(Furniture.Type type) { return cards.get(type); }

    // ------------------------------------------------------------------

    private final class Card extends JComponent {
        private final Furniture.Type type;
        private boolean hover;

        Card(Furniture.Type type, BiConsumer<Furniture.Type, Boolean> onPick) {
            this.type = type;
            setPreferredSize(new Dimension(200, 64));
            setMinimumSize(new Dimension(100, 64));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            setAlignmentX(LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("클릭: 화면 중앙에 바로 추가 / Shift+클릭: 위치를 정해서 배치");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    // 카드 안에서 눌렀다 뗐을 때만 선택 (눌러 놓고 밖으로 끌어내면 취소)
                    if (SwingUtilities.isLeftMouseButton(e) && contains(e.getPoint())) {
                        onPick.accept(type, e.isShiftDown());
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            boolean isActive = type == active;

            g.setColor(hover ? CARD_HOVER : CARD);
            g.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 12, 12));
            g.setColor(isActive || hover ? ACCENT : LINE);
            g.setStroke(new BasicStroke(isActive ? 2f : 1f));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 2, h - 2, 12, 12));

            drawIcon(g, 12, (h - 44) / 2, 44);

            g.setColor(hover || isActive ? ACCENT : TEXT);
            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g.drawString(type.label, 68, h / 2 - 3);
            g.setColor(SUB);
            g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            g.drawString((int) type.width + " × " + (int) type.depth + " × " + (int) type.height + " mm", 68, h / 2 + 15);
            g.dispose();
        }

        /** 위에서 본 가구 모양(가로:깊이 비율 그대로)을 작게 그린다. 굵은 윗변이 앞면. */
        private void drawIcon(Graphics2D g, int x, int y, int size) {
            double scale = (size - 8) / Math.max(type.width, type.depth);
            double fw = type.width * scale, fd = type.depth * scale;
            double fx = x + (size - fw) / 2, fy = y + (size - fd) / 2;
            g.setColor(new Color(type.rgb));
            g.fill(new RoundRectangle2D.Double(fx, fy, fw, fd, 4, 4));
            g.setColor(new Color(0x12, 0x16, 0x1c));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(fx, fy, fw, fd, 4, 4));
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.draw(new Line2D.Double(fx + 2, fy, fx + fw - 2, fy));
        }
    }
}
