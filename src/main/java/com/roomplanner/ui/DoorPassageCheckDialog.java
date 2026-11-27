package com.roomplanner.ui;

import com.roomplanner.model.Furniture;
import com.roomplanner.model.Opening;
import com.roomplanner.service.DoorPassageChecker;
import com.roomplanner.service.DoorPassageChecker.CheckResult;
import com.roomplanner.service.DoorPassageChecker.PoseResult;
import com.roomplanner.service.DoorPassageChecker.Status;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 도면에 배치된 가구들이 선택한 문을 통과할 수 있는지 여부를 즉시 판정하여 보여주는 다이얼로그.
 */
public class DoorPassageCheckDialog extends JDialog {

    private static final Color BG_DARK = new Color(0x1a, 0x1e, 0x24);
    private static final Color PANEL_BG = new Color(0x22, 0x27, 0x30);
    private static final Color CARD_BG = new Color(0x16, 0x1b, 0x22);
    private static final Color BORDER_COLOR = new Color(0x3d, 0x44, 0x4d);
    private static final Color TEXT_LIGHT = new Color(0xe6, 0xed, 0xf3);
    private static final Color TEXT_MUTED = new Color(0x8b, 0x94, 0x9e);
    private static final Color ACCENT_BLUE = new Color(0x58, 0xa6, 0xff);
    private static final Color COLOR_PASS = new Color(0x3f, 0xb9, 0x50);
    private static final Color COLOR_WARN = new Color(0xd2, 0x99, 0x22);
    private static final Color COLOR_FAIL = new Color(0xf8, 0x51, 0x49);

    private final List<Furniture> furnitures;
    private final List<Opening> doors;
    private final DoorPassageChecker checker = new DoorPassageChecker();

    private final JComboBox<String> doorCombo = new JComboBox<>();
    private final ResultTableModel tableModel = new ResultTableModel();
    private final JTable resultTable = new JTable(tableModel);

    private final JLabel detailBadgeLbl = new JLabel();
    private final JLabel detailSummaryLbl = new JLabel();
    private final JLabel detailRecLbl = new JLabel();
    private final JPanel posesPanel = new JPanel();

    private List<CheckResult> currentResults = new ArrayList<>();

    public DoorPassageCheckDialog(JFrame parent, List<Furniture> furnitures, List<Opening> doors,
                                  Furniture preselectedFurniture, Opening preselectedDoor) {
        super(parent, "가구 문 통과 가능 여부 판정", true);
        this.furnitures = furnitures;
        this.doors = doors;

        setSize(900, 640);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DARK);

        add(buildHeaderPanel(preselectedDoor), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        evaluateAll();

        // 에디터에서 선택한 가구가 있으면 해당 행 자동 선택
        int initialRow = 0;
        if (preselectedFurniture != null) {
            for (int i = 0; i < furnitures.size(); i++) {
                if (furnitures.get(i) == preselectedFurniture) {
                    initialRow = i;
                    break;
                }
            }
        }
        if (!currentResults.isEmpty()) {
            resultTable.setRowSelectionInterval(initialRow, initialRow);
            updateDetailPanel(currentResults.get(initialRow));
        }
    }

    private JPanel buildHeaderPanel(Opening preselectedDoor) {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(PANEL_BG);
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(14, 18, 14, 18)
        ));

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JLabel titleLbl = new JLabel("🚪 가구 문 통과 가능 여부 판정");
        titleLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
        titleLbl.setForeground(ACCENT_BLUE);

        JLabel subLbl = new JLabel("가구의 3차원 크기(가로·세로·높이)와 문 규격을 대조해 통과 여부를 즉시 판정합니다.");
        subLbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        subLbl.setForeground(TEXT_MUTED);

        titleBox.add(titleLbl);
        titleBox.add(Box.createVerticalStrut(3));
        titleBox.add(subLbl);

        JPanel doorSelectPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        doorSelectPanel.setOpaque(false);

        JLabel doorLbl = new JLabel("기준 문:");
        doorLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        doorLbl.setForeground(TEXT_LIGHT);

        int defaultIdx = 0;
        for (int i = 0; i < doors.size(); i++) {
            Opening d = doors.get(i);
            doorCombo.addItem(String.format("문 #%d (폭 %.0f cm × 높이 %.0f cm)", i + 1, d.width() / 10.0, d.height() / 10.0));
            if (d == preselectedDoor) defaultIdx = i;
        }
        doorCombo.setSelectedIndex(defaultIdx);
        doorCombo.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        doorCombo.setBackground(BG_DARK);
        doorCombo.setForeground(TEXT_LIGHT);
        doorCombo.addActionListener(e -> evaluateAll());

        doorSelectPanel.add(doorLbl);
        doorSelectPanel.add(doorCombo);

        header.add(titleBox, BorderLayout.WEST);
        header.add(doorSelectPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setBackground(BG_DARK);
        center.setBorder(new EmptyBorder(14, 18, 10, 18));

        // 상단: 전체 가구 판정 테이블
        resultTable.setBackground(CARD_BG);
        resultTable.setForeground(TEXT_LIGHT);
        resultTable.setSelectionBackground(new Color(0x26, 0x4f, 0x78));
        resultTable.setSelectionForeground(Color.WHITE);
        resultTable.setGridColor(BORDER_COLOR);
        resultTable.setRowHeight(34);
        resultTable.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        resultTable.getTableHeader().setBackground(PANEL_BG);
        resultTable.getTableHeader().setForeground(TEXT_LIGHT);
        resultTable.getTableHeader().setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        resultTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        resultTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        resultTable.getColumnModel().getColumn(3).setPreferredWidth(350);

        // 판정 컬럼 컬러 렌더러
        resultTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
                if (row < currentResults.size()) {
                    Status st = currentResults.get(row).status();
                    if (!isSelected) {
                        lbl.setForeground(colorForStatus(st));
                    }
                }
                return lbl;
            }
        });

        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = resultTable.getSelectedRow();
                if (row >= 0 && row < currentResults.size()) {
                    updateDetailPanel(currentResults.get(row));
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(resultTable);
        tableScroll.getViewport().setBackground(CARD_BG);
        tableScroll.setBorder(new LineBorder(BORDER_COLOR));
        tableScroll.setPreferredSize(new Dimension(0, 220));

        // 하단: 선택된 가구 상세 판정 카드
        JPanel detailCard = new JPanel(new BorderLayout(0, 10));
        detailCard.setBackground(PANEL_BG);
        detailCard.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JPanel detailHeader = new JPanel(new BorderLayout(10, 4));
        detailHeader.setOpaque(false);

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        topRow.setOpaque(false);

        detailBadgeLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        detailBadgeLbl.setOpaque(true);
        detailBadgeLbl.setBorder(new EmptyBorder(4, 10, 4, 10));

        detailSummaryLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 15));
        detailSummaryLbl.setForeground(TEXT_LIGHT);

        topRow.add(detailBadgeLbl);
        topRow.add(detailSummaryLbl);

        detailRecLbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        detailRecLbl.setForeground(TEXT_MUTED);
        detailRecLbl.setBorder(new EmptyBorder(4, 2, 0, 0));

        detailHeader.add(topRow, BorderLayout.NORTH);
        detailHeader.add(detailRecLbl, BorderLayout.CENTER);

        posesPanel.setLayout(new BoxLayout(posesPanel, BoxLayout.Y_AXIS));
        posesPanel.setOpaque(false);

        JScrollPane posesScroll = new JScrollPane(posesPanel);
        posesScroll.setOpaque(false);
        posesScroll.getViewport().setOpaque(false);
        posesScroll.setBorder(null);

        detailCard.add(detailHeader, BorderLayout.NORTH);
        detailCard.add(posesScroll, BorderLayout.CENTER);

        center.add(tableScroll, BorderLayout.NORTH);
        center.add(detailCard, BorderLayout.CENTER);
        return center;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 10));
        bar.setBackground(PANEL_BG);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));

        JButton closeBtn = new JButton("닫기");
        closeBtn.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        closeBtn.setBackground(new Color(0x38, 0x42, 0x50));
        closeBtn.setForeground(TEXT_LIGHT);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setPreferredSize(new Dimension(90, 32));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());

        bar.add(closeBtn);
        return bar;
    }

    private void evaluateAll() {
        int doorIdx = doorCombo.getSelectedIndex();
        if (doorIdx < 0 || doorIdx >= doors.size()) return;
        Opening door = doors.get(doorIdx);

        int prevSelected = resultTable.getSelectedRow();
        currentResults = new ArrayList<>();
        for (Furniture f : furnitures) {
            currentResults.add(checker.check(f, door));
        }
        tableModel.fireTableDataChanged();

        if (!currentResults.isEmpty()) {
            int row = (prevSelected >= 0 && prevSelected < currentResults.size()) ? prevSelected : 0;
            resultTable.setRowSelectionInterval(row, row);
            updateDetailPanel(currentResults.get(row));
        }
    }

    private void updateDetailPanel(CheckResult res) {
        Color stColor = colorForStatus(res.status());
        detailBadgeLbl.setText(badgeText(res.status()));
        detailBadgeLbl.setBackground(new Color(stColor.getRed(), stColor.getGreen(), stColor.getBlue(), 40));
        detailBadgeLbl.setForeground(stColor);

        detailSummaryLbl.setText(res.summary());
        detailRecLbl.setText(res.recommendation());

        posesPanel.removeAll();
        for (PoseResult pose : res.poses()) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setBackground(CARD_BG);
            row.setBorder(new CompoundBorder(
                    new LineBorder(BORDER_COLOR),
                    new EmptyBorder(8, 12, 8, 12)
            ));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

            JLabel statusIcon = new JLabel(pose.pass() ? "PASS" : "FAIL");
            statusIcon.setFont(new Font("Malgun Gothic", Font.BOLD, 11));
            statusIcon.setForeground(pose.pass() ? COLOR_PASS : COLOR_FAIL);
            statusIcon.setPreferredSize(new Dimension(42, 20));

            JLabel nameLbl = new JLabel(pose.poseName());
            nameLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
            nameLbl.setForeground(TEXT_LIGHT);

            JLabel noteLbl = new JLabel(pose.note());
            noteLbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
            noteLbl.setForeground(pose.pass() ? TEXT_LIGHT : TEXT_MUTED);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            left.add(statusIcon);
            left.add(nameLbl);

            row.add(left, BorderLayout.WEST);
            row.add(noteLbl, BorderLayout.EAST);

            posesPanel.add(row);
            posesPanel.add(Box.createVerticalStrut(6));
        }
        posesPanel.revalidate();
        posesPanel.repaint();
    }

    private Color colorForStatus(Status st) {
        return switch (st) {
            case PASS_NORMAL -> COLOR_PASS;
            case PASS_ADJUSTED -> COLOR_WARN;
            case FAIL -> COLOR_FAIL;
        };
    }

    private String badgeText(Status st) {
        return switch (st) {
            case PASS_NORMAL -> "통과 가능";
            case PASS_ADJUSTED -> "자세 변경 시 통과";
            case FAIL -> "통과 불가";
        };
    }

    private class ResultTableModel extends AbstractTableModel {
        private final String[] cols = {"가구", "규격 (가로×세로×높이)", "판정", "판정 요약"};

        @Override public int getRowCount() { return currentResults.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int col) { return cols[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            CheckResult r = currentResults.get(row);
            Furniture f = r.furniture();
            return switch (col) {
                case 0 -> f.getName() != null ? f.getName() : f.getType().label;
                case 1 -> String.format("%.0f × %.0f × %.0f cm",
                        f.getWidth() / 10.0, f.getDepth() / 10.0, f.getHeight() / 10.0);
                case 2 -> r.status().label;
                case 3 -> r.summary();
                default -> "";
            };
        }
    }
}
