package com.roomplanner.ui;

import com.roomplanner.model.Furniture;
import com.roomplanner.model.Opening;
import com.roomplanner.model.Plan;
import com.roomplanner.model.ShoppingProduct;
import com.roomplanner.service.DoorPassageChecker;
import com.roomplanner.service.DoorPassageChecker.CheckResult;
import com.roomplanner.service.DoorPassageChecker.Status;
import com.roomplanner.service.IkeaShoppingService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 이케아(IKEA) 실시간 검색 연동 가구 추천 및 근사치 수치 매칭 다이얼로그.
 * 별도 API 키 발급 없이 즉시 사용 가능하다.
 */
public class RecommendationDialog extends JDialog {

    private static final Color BG_DARK = new Color(0x1e, 0x23, 0x2a);
    private static final Color PANEL_BG = new Color(0x26, 0x2c, 0x36);
    private static final Color CARD_BG = new Color(0x30, 0x36, 0x3d);
    private static final Color CARD_HOVER = new Color(0x38, 0x42, 0x50);
    private static final Color BORDER_COLOR = new Color(0x48, 0x4f, 0x58);
    private static final Color ACCENT_BLUE = new Color(0x58, 0xa6, 0xff);
    private static final Color IKEA_YELLOW = new Color(0xff, 0xda, 0x1a);
    private static final Color IKEA_BLUE = new Color(0x00, 0x58, 0xa3);
    private static final Color TEXT_LIGHT = new Color(0xe6, 0xed, 0xf3);
    private static final Color TEXT_MUTED = new Color(0x8b, 0x94, 0x9e);
    private static final Color BADGE_BG = new Color(0x1f, 0x35, 0x4d);

    private final IkeaShoppingService shoppingService;
    private final Consumer<Furniture> onPlaceFurniture;
    private final List<Opening> doors;
    private final DoorPassageChecker passageChecker = new DoorPassageChecker();

    private final JComboBox<Furniture.Type> typeCombo = new JComboBox<>(Furniture.Type.values());
    private final JComboBox<String> doorCombo = new JComboBox<>();
    private final JTextField keywordField = new JTextField(10);
    private final JTextField widthField = new JTextField(5);
    private final JTextField depthField = new JTextField(5);
    private final JTextField heightField = new JTextField(5);

    private final JPanel resultsContainer = new JPanel();
    private final JLabel statusHeaderLabel = new JLabel("가구 종류와 원하는 수치(cm)를 입력한 후 [이케아 추천 검색]을 누르세요.");
    private final JButton searchBtn = createStyledButton("이케아 추천 검색", IKEA_BLUE, IKEA_YELLOW, 13, true);

    private List<ShoppingProduct> lastProducts = List.of();
    private Furniture.Type lastType;
    private double lastTargetW, lastTargetD;

    public RecommendationDialog(JFrame parent, Plan plan, IkeaShoppingService shoppingService, Consumer<Furniture> onPlaceFurniture) {
        super(parent, "이케아(IKEA) 가구 규격 추천 및 실시간 검색", true);
        this.shoppingService = shoppingService;
        this.onPlaceFurniture = onPlaceFurniture;
        this.doors = (plan != null)
                ? plan.getOpenings().stream().filter(o -> o.type() == Opening.Type.DOOR).toList()
                : List.of();

        setSize(1040, 740);
        setMinimumSize(new Dimension(900, 520));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DARK);

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildResultsPanel(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        // Enter 키를 누르면 바로 검색
        keywordField.addActionListener(e -> performSearch());
        widthField.addActionListener(e -> performSearch());
        depthField.addActionListener(e -> performSearch());
        heightField.addActionListener(e -> performSearch());

        // 초기 수치 세팅
        updateDefaultDimensions((Furniture.Type) typeCombo.getSelectedItem());
        typeCombo.addActionListener(e -> updateDefaultDimensions((Furniture.Type) typeCombo.getSelectedItem()));
    }

    private void updateDefaultDimensions(Furniture.Type type) {
        if (type == null) return;
        widthField.setText(String.valueOf((int) (type.width / 10.0)));
        depthField.setText(String.valueOf((int) (type.depth / 10.0)));
        heightField.setText(String.valueOf((int) (type.height / 10.0)));
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(PANEL_BG);
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(14, 16, 14, 16)
        ));

        // 1행: 제목 및 인증 불필요 안내 뱃지
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);

        JLabel titleLbl = new JLabel("[IKEA] 실시간 가구 추천 및 규격 매칭");
        titleLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 15));
        titleLbl.setForeground(ACCENT_BLUE);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topRight.setOpaque(false);

        JLabel doorLbl = createLabel("통과 판정 기준 문:");
        doorLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        if (doors.isEmpty()) {
            doorCombo.addItem("표준 방문 (폭 90 × 높이 210 cm)");
        } else {
            for (int i = 0; i < doors.size(); i++) {
                Opening d = doors.get(i);
                doorCombo.addItem(String.format("도면 문 #%d (폭 %.0f × 높이 %.0f cm)", i + 1, d.width() / 10.0, d.height() / 10.0));
            }
        }
        doorCombo.setFont(new Font("Malgun Gothic", Font.BOLD, 11));
        doorCombo.setBackground(Color.WHITE);
        doorCombo.setForeground(new Color(0x1f, 0x23, 0x28));
        doorCombo.addActionListener(e -> {
            if (!lastProducts.isEmpty() && lastType != null) {
                displayResults(lastProducts, lastType, lastTargetW, lastTargetD);
            }
        });

        JLabel noKeyBadge = new JLabel(" 별도 API 키 없이 즉시 검색 가능 ");
        noKeyBadge.setOpaque(true);
        noKeyBadge.setBackground(new Color(0x1a, 0x40, 0x24));
        noKeyBadge.setForeground(new Color(0x7e, 0xe7, 0x87));
        noKeyBadge.setFont(new Font("Malgun Gothic", Font.BOLD, 11));
        noKeyBadge.setBorder(new LineBorder(new Color(0x23, 0x86, 0x36), 1, true));

        topRight.add(doorLbl);
        topRight.add(doorCombo);
        topRight.add(noKeyBadge);

        topRow.add(titleLbl, BorderLayout.WEST);
        topRow.add(topRight, BorderLayout.EAST);
        header.add(topRow);
        header.add(Box.createVerticalStrut(12));

        // 2행: 검색 입력 폼 (왼쪽 입력폼 + 오른쪽 검색 버튼 고정)
        JPanel formRow = new JPanel(new BorderLayout(10, 0));
        formRow.setOpaque(false);

        JPanel inputsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        inputsLeft.setOpaque(false);

        inputsLeft.add(createLabel("종류:"));
        typeCombo.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        inputsLeft.add(typeCombo);

        inputsLeft.add(createLabel("키워드:"));
        keywordField.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        keywordField.setColumns(8);
        keywordField.setToolTipText("예: 원목, 화이트, 코너, 게이밍");
        inputsLeft.add(keywordField);

        inputsLeft.add(createLabel("가로:"));
        widthField.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        widthField.setColumns(4);
        inputsLeft.add(widthField);
        inputsLeft.add(createLabel("cm  세로:"));
        depthField.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        depthField.setColumns(4);
        inputsLeft.add(depthField);
        inputsLeft.add(createLabel("cm  높이:"));
        heightField.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        heightField.setColumns(4);
        inputsLeft.add(heightField);
        inputsLeft.add(createLabel("cm"));

        searchBtn.setPreferredSize(new Dimension(160, 34));
        searchBtn.addActionListener(e -> performSearch());

        formRow.add(inputsLeft, BorderLayout.CENTER);
        formRow.add(searchBtn, BorderLayout.EAST);
        header.add(formRow);

        return header;
    }

    private JPanel buildResultsPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(10, 16, 10, 16));

        statusHeaderLabel.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        statusHeaderLabel.setForeground(TEXT_MUTED);
        statusHeaderLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        wrapper.add(statusHeaderLabel, BorderLayout.NORTH);

        resultsContainer.setLayout(new BoxLayout(resultsContainer, BoxLayout.Y_AXIS));
        resultsContainer.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(resultsContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        wrapper.add(scrollPane, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(PANEL_BG);
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(10, 16, 10, 16)
        ));

        JLabel guide = new JLabel("[안내] [도면에 배치]를 누르면 이케아 실측 규격으로 2D/3D 도면에 즉시 가구가 배치됩니다.");
        guide.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        guide.setForeground(TEXT_MUTED);

        JButton closeBtn = createStyledButton("닫기", new Color(0x38, 0x42, 0x50), TEXT_LIGHT, 12, false);
        closeBtn.setPreferredSize(new Dimension(80, 30));
        closeBtn.addActionListener(e -> dispose());

        bar.add(guide, BorderLayout.WEST);
        bar.add(closeBtn, BorderLayout.EAST);
        return bar;
    }

    private void performSearch() {
        Furniture.Type type = (Furniture.Type) typeCombo.getSelectedItem();
        String keyword = keywordField.getText().trim();

        double targetW = parseDouble(widthField.getText(), type.width / 10.0) * 10.0;
        double targetD = parseDouble(depthField.getText(), type.depth / 10.0) * 10.0;
        double targetH = parseDouble(heightField.getText(), type.height / 10.0) * 10.0;

        searchBtn.setEnabled(false);
        statusHeaderLabel.setText("이케아(IKEA)에서 실시간으로 상품을 조회하고 규격을 비교하는 중입니다...");
        resultsContainer.removeAll();
        resultsContainer.revalidate();
        resultsContainer.repaint();

        // 비동기 검색 (SwingWorker)
        new SwingWorker<List<ShoppingProduct>, Void>() {
            @Override
            protected List<ShoppingProduct> doInBackground() throws Exception {
                return shoppingService.searchAndRecommend(type, keyword, targetW, targetD, targetH);
            }

            @Override
            protected void done() {
                searchBtn.setEnabled(true);
                try {
                    List<ShoppingProduct> products = get();
                    displayResults(products, type, targetW, targetD);
                } catch (Exception ex) {
                    statusHeaderLabel.setText("검색 중 오류 발생: " + ex.getMessage());
                    JOptionPane.showMessageDialog(RecommendationDialog.this,
                            "검색 중 문제가 발생했습니다:\n" + ex.getMessage(),
                            "검색 오류", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private Opening getSelectedDoor() {
        if (doors.isEmpty()) {
            return new Opening(Opening.Type.DOOR, null, 0, 900, 0, 2100);
        }
        int idx = doorCombo.getSelectedIndex();
        return (idx >= 0 && idx < doors.size()) ? doors.get(idx) : doors.get(0);
    }

    private void displayResults(List<ShoppingProduct> products, Furniture.Type type, double targetW, double targetD) {
        this.lastProducts = products;
        this.lastType = type;
        this.lastTargetW = targetW;
        this.lastTargetD = targetD;

        resultsContainer.removeAll();

        if (products.isEmpty()) {
            statusHeaderLabel.setText("조건에 맞는 상품을 찾을 수 없습니다.");
            resultsContainer.revalidate();
            resultsContainer.repaint();
            return;
        }

        statusHeaderLabel.setText(String.format("총 %d개의 이케아 가구를 찾았습니다 (목표 가로 %.0fcm × 세로 %.0fcm 근사치 순 정렬)",
                products.size(), targetW / 10.0, targetD / 10.0));

        NumberFormat nf = NumberFormat.getInstance(Locale.KOREA);
        Opening refDoor = getSelectedDoor();

        for (ShoppingProduct p : products) {
            JPanel card = createProductCard(p, type, nf, refDoor);
            resultsContainer.add(card);
            resultsContainer.add(Box.createVerticalStrut(8));
        }

        resultsContainer.revalidate();
        resultsContainer.repaint();
    }

    private JPanel createProductCard(ShoppingProduct p, Furniture.Type type, NumberFormat nf, Opening refDoor) {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
        card.setPreferredSize(new Dimension(860, 108));

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { card.setBackground(CARD_HOVER); }
            @Override public void mouseExited(MouseEvent e) { card.setBackground(CARD_BG); }
        });

        // 1. 좌측 썸네일
        JLabel imgLabel = new JLabel();
        imgLabel.setPreferredSize(new Dimension(75, 75));
        imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imgLabel.setBackground(PANEL_BG);
        imgLabel.setOpaque(true);
        imgLabel.setText("IKEA");
        imgLabel.setForeground(TEXT_MUTED);

        if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
            loadAsyncImage(p.getImageUrl(), imgLabel);
        }
        card.add(imgLabel, BorderLayout.WEST);

        // 2. 중앙 정보 (제목, 가격, 규격 배지)
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel titleLbl = new JLabel(p.getCleanTitle());
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 13f));
        titleLbl.setForeground(TEXT_LIGHT);
        titleLbl.setToolTipText(p.getCleanTitle());

        JPanel subInfoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        subInfoRow.setOpaque(false);

        JLabel priceLbl = new JLabel(p.getLprice() > 0 ? nf.format(p.getLprice()) + "원" : "가격 정보 없음");
        priceLbl.setFont(priceLbl.getFont().deriveFont(Font.BOLD, 13f));
        priceLbl.setForeground(new Color(0xff, 0x7b, 0x72));

        JLabel brandLbl = new JLabel("[IKEA 공식]");
        brandLbl.setForeground(new Color(0x7e, 0xe7, 0x87));
        brandLbl.setFont(brandLbl.getFont().deriveFont(Font.BOLD, 11f));

        subInfoRow.add(priceLbl);
        subInfoRow.add(brandLbl);

        // 규격 및 오차 배지
        JLabel dimBadge = new JLabel(" " + p.getDimensionString() + " ");
        dimBadge.setOpaque(true);
        dimBadge.setBackground(BADGE_BG);
        dimBadge.setForeground(ACCENT_BLUE);
        dimBadge.setFont(dimBadge.getFont().deriveFont(Font.PLAIN, 11f));
        dimBadge.setBorder(new LineBorder(ACCENT_BLUE, 1, true));
        subInfoRow.add(dimBadge);

        // 문 통과 여부 판정 배지 (3D 실측 기반)
        Furniture tempFurn = new Furniture(type, 0, 0);
        tempFurn.setWidth(p.getWidthMm() > 0 ? p.getWidthMm() : type.width);
        tempFurn.setDepth(p.getDepthMm() > 0 ? p.getDepthMm() : type.depth);
        tempFurn.setHeight(p.getHeightMm() > 0 ? p.getHeightMm() : type.height);
        CheckResult passRes = passageChecker.check(tempFurn, refDoor);

        Color passBg, passFg, passBorder;
        if (passRes.status() == Status.PASS_NORMAL) {
            passBg = new Color(0x1a, 0x40, 0x24);
            passFg = new Color(0x7e, 0xe7, 0x87);
            passBorder = new Color(0x23, 0x86, 0x36);
        } else if (passRes.status() == Status.PASS_ADJUSTED) {
            passBg = new Color(0x4a, 0x35, 0x08);
            passFg = new Color(0xf0, 0xc0, 0x50);
            passBorder = new Color(0xd2, 0x99, 0x22);
        } else {
            passBg = new Color(0x4c, 0x1a, 0x1a);
            passFg = new Color(0xff, 0x7b, 0x72);
            passBorder = new Color(0xf8, 0x51, 0x49);
        }

        JLabel passBadge = new JLabel(" " + passRes.status().label + " — " + passRes.summary() + " ");
        passBadge.setOpaque(true);
        passBadge.setBackground(passBg);
        passBadge.setForeground(passFg);
        passBadge.setFont(new Font("Malgun Gothic", Font.BOLD, 11));
        passBadge.setBorder(new LineBorder(passBorder, 1, true));
        passBadge.setToolTipText(passRes.recommendation());

        JPanel passRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        passRow.setOpaque(false);
        passRow.add(passBadge);

        infoPanel.add(titleLbl);
        infoPanel.add(Box.createVerticalStrut(3));
        infoPanel.add(subInfoRow);
        infoPanel.add(passRow);

        card.add(infoPanel, BorderLayout.CENTER);

        // 3. 우측 액션 버튼들 (도면에 배치, 구매 링크 열기)
        JPanel actionPanel = new JPanel(new GridLayout(2, 1, 0, 6));
        actionPanel.setOpaque(false);
        actionPanel.setPreferredSize(new Dimension(140, 0));

        JButton placeBtn = createStyledButton("도면에 배치", new Color(0x23, 0x86, 0x36), Color.WHITE, 12, true);
        placeBtn.addActionListener(e -> placeOnPlan(p, type));

        JButton browseBtn = createStyledButton("이케아몰 열기", new Color(0x30, 0x48, 0x60), new Color(0x79, 0xc0, 0xff), 11, false);
        browseBtn.addActionListener(e -> openBrowser(p.getLink()));

        actionPanel.add(placeBtn);
        actionPanel.add(browseBtn);
        card.add(actionPanel, BorderLayout.EAST);

        return card;
    }

    /**
     * LookAndFeel에 영향받지 않고 지정한 배경색과 글자색으로 선명하게 렌더링되는 둥근 모던 버튼 생성
     */
    private static JButton createStyledButton(String text, Color bg, Color fg, int fontSize, boolean bold) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(60, 65, 75));
                } else if (getModel().isPressed()) {
                    g2.setColor(bg.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(bg.brighter());
                } else {
                    g2.setColor(bg);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Malgun Gothic", bold ? Font.BOLD : Font.PLAIN, fontSize));
        btn.setForeground(fg);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void placeOnPlan(ShoppingProduct p, Furniture.Type type) {
        double w = p.getWidthMm() > 0 ? p.getWidthMm() : type.width;
        double d = p.getDepthMm() > 0 ? p.getDepthMm() : type.depth;
        double h = p.getHeightMm() > 0 ? p.getHeightMm() : type.height;

        Furniture f = new Furniture(type, 0, 0);
        f.setName(p.getCleanTitle());
        f.setWidth(w);
        f.setDepth(d);
        f.setHeight(h);

        onPlaceFurniture.accept(f);
        String dimText = "가로 " + (int)(w/10) + "cm × 세로 " + (int)(d/10) + "cm"
                + (p.getHeightMm() > 0 ? " × 높이 " + (int)(h/10) + "cm" : " (3D 높이는 표준 " + (int)(h/10) + "cm 적용)");
        JOptionPane.showMessageDialog(this,
                "선택한 가구가 도면 중앙에 배치되었습니다!\n• 가구명: " + p.getCleanTitle() + "\n• 규격: " + dimText + "\n2D 도면 및 3D 입체 탭에서 확인해 보세요.",
                "도면 배치 완료", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openBrowser(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                new ProcessBuilder("cmd", "/c", "start", url).start();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "브라우저를 열 수 없습니다:\n" + url);
        }
    }

    private void loadAsyncImage(String imageUrl, JLabel targetLabel) {
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                URL url = URI.create(imageUrl).toURL();
                try (InputStream in = url.openStream()) {
                    BufferedImage img = ImageIO.read(in);
                    if (img == null) return null;
                    Image scaled = img.getScaledInstance(75, 75, Image.SCALE_SMOOTH);
                    return new ImageIcon(scaled);
                }
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        targetLabel.setText("");
                        targetLabel.setIcon(icon);
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_LIGHT);
        l.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        return l;
    }

    private double parseDouble(String text, double fallback) {
        try { return Double.parseDouble(text.trim()); } catch (Exception e) { return fallback; }
    }
}
