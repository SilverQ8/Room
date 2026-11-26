package com.roomplanner.ui;

import com.roomplanner.model.Furniture;
import com.roomplanner.model.ShoppingProduct;
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

    private final JComboBox<Furniture.Type> typeCombo = new JComboBox<>(Furniture.Type.values());
    private final JTextField keywordField = new JTextField(10);
    private final JTextField widthField = new JTextField(5);
    private final JTextField depthField = new JTextField(5);
    private final JTextField heightField = new JTextField(5);

    private final JPanel resultsContainer = new JPanel();
    private final JLabel statusHeaderLabel = new JLabel("가구 종류와 원하는 수치(cm)를 입력한 후 [추천 검색]을 누르세요.");
    private final JButton searchBtn = new JButton("🔍 이케아 추천 검색");

    public RecommendationDialog(JFrame parent, IkeaShoppingService shoppingService, Consumer<Furniture> onPlaceFurniture) {
        super(parent, "이케아(IKEA) 가구 규격 추천 및 실시간 검색", true);
        this.shoppingService = shoppingService;
        this.onPlaceFurniture = onPlaceFurniture;

        setSize(960, 720);
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

        JLabel titleLbl = new JLabel("🛋️ 이케아(IKEA) 실시간 가구 추천 (근사치 매칭)");
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 15f));
        titleLbl.setForeground(ACCENT_BLUE);

        JLabel noKeyBadge = new JLabel(" ✔️ 별도 API 키 없이 즉시 검색 가능 ");
        noKeyBadge.setOpaque(true);
        noKeyBadge.setBackground(new Color(0x1a, 0x40, 0x24));
        noKeyBadge.setForeground(new Color(0x7e, 0xe7, 0x87));
        noKeyBadge.setFont(noKeyBadge.getFont().deriveFont(Font.BOLD, 11f));
        noKeyBadge.setBorder(new LineBorder(new Color(0x23, 0x86, 0x36), 1, true));

        topRow.add(titleLbl, BorderLayout.WEST);
        topRow.add(noKeyBadge, BorderLayout.EAST);
        header.add(topRow);
        header.add(Box.createVerticalStrut(12));

        // 2행: 검색 입력 폼 (왼쪽 입력폼 + 오른쪽 검색 버튼 고정)
        JPanel formRow = new JPanel(new BorderLayout(10, 0));
        formRow.setOpaque(false);

        JPanel inputsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        inputsLeft.setOpaque(false);

        inputsLeft.add(createLabel("종류:"));
        inputsLeft.add(typeCombo);

        inputsLeft.add(createLabel("키워드:"));
        keywordField.setColumns(8);
        keywordField.setToolTipText("예: 원목, 화이트, 코너, 게이밍");
        inputsLeft.add(keywordField);

        inputsLeft.add(createLabel("가로:"));
        widthField.setColumns(4);
        inputsLeft.add(widthField);
        inputsLeft.add(createLabel("cm  세로:"));
        depthField.setColumns(4);
        inputsLeft.add(depthField);
        inputsLeft.add(createLabel("cm  높이:"));
        heightField.setColumns(4);
        inputsLeft.add(heightField);
        inputsLeft.add(createLabel("cm"));

        searchBtn.setPreferredSize(new Dimension(150, 32));
        searchBtn.setBackground(IKEA_BLUE);
        searchBtn.setForeground(IKEA_YELLOW);
        searchBtn.setFont(searchBtn.getFont().deriveFont(Font.BOLD, 13f));
        searchBtn.setFocusPainted(false);
        searchBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

        JLabel guide = new JLabel("💡 [도면에 배치]를 누르면 이케아 실측 규격으로 2D/3D 도면에 즉시 가구가 배치됩니다.");
        guide.setFont(guide.getFont().deriveFont(Font.PLAIN, 12f));
        guide.setForeground(TEXT_MUTED);

        JButton closeBtn = new JButton("닫기");
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

    private void displayResults(List<ShoppingProduct> products, Furniture.Type type, double targetW, double targetD) {
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

        for (ShoppingProduct p : products) {
            JPanel card = createProductCard(p, type, nf);
            resultsContainer.add(card);
            resultsContainer.add(Box.createVerticalStrut(8));
        }

        resultsContainer.revalidate();
        resultsContainer.repaint();
    }

    private JPanel createProductCard(ShoppingProduct p, Furniture.Type type, NumberFormat nf) {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
        card.setPreferredSize(new Dimension(820, 95));

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

        infoPanel.add(titleLbl);
        infoPanel.add(Box.createVerticalStrut(4));
        infoPanel.add(subInfoRow);

        card.add(infoPanel, BorderLayout.CENTER);

        // 3. 우측 액션 버튼들 (구매 링크 열기, 도면에 배치)
        JPanel actionPanel = new JPanel(new GridLayout(2, 1, 0, 6));
        actionPanel.setOpaque(false);
        actionPanel.setPreferredSize(new Dimension(135, 0));

        JButton browseBtn = new JButton("🌐 이케아 몰 열기");
        browseBtn.setFont(browseBtn.getFont().deriveFont(Font.PLAIN, 11f));
        browseBtn.addActionListener(e -> openBrowser(p.getLink()));

        JButton placeBtn = new JButton("➕ 도면에 배치");
        placeBtn.setFont(placeBtn.getFont().deriveFont(Font.BOLD, 11f));
        placeBtn.setBackground(new Color(0x23, 0x86, 0x36));
        placeBtn.setForeground(Color.WHITE);
        placeBtn.addActionListener(e -> placeOnPlan(p, type));

        actionPanel.add(placeBtn);
        actionPanel.add(browseBtn);
        card.add(actionPanel, BorderLayout.EAST);

        return card;
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
        JOptionPane.showMessageDialog(this,
                "선택한 가구가 도면 중앙에 배치되었습니다!\n• 가구명: " + p.getCleanTitle() + "\n• 규격: 가로 " + (int)(w/10) + "cm × 세로 " + (int)(d/10) + "cm × 높이 " + (int)(h/10) + "cm\n2D 도면 및 3D 입체 탭에서 확인해 보세요.",
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
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        return l;
    }

    private double parseDouble(String text, double fallback) {
        try { return Double.parseDouble(text.trim()); } catch (Exception e) { return fallback; }
    }
}
