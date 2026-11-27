package com.roomplanner.ui;

import com.roomplanner.model.*;
import com.roomplanner.service.PassageSimulationEngine;
import com.roomplanner.service.PassageSimulationEngine.Orientation;
import com.roomplanner.service.PassageSimulationEngine.PassageMode;
import com.roomplanner.service.PassageSimulationEngine.StepState;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * 가구 문 통과 시뮬레이션 다이얼로그.
 * 가구를 비스듬히 기울여 한쪽 모서리부터 넣고 문틀 사이에서 비틀며 회전(Wiggle) 통과하는 현실적인 반입 모션 제공.
 */
public class PassageSimulationDialog extends JDialog {

    private static final Color BG_DARK = new Color(0x1a, 0x1e, 0x24);
    private static final Color PANEL_BG = new Color(0x22, 0x27, 0x30);
    private static final Color CANVAS_BG = new Color(0x13, 0x16, 0x1b);
    private static final Color BORDER_COLOR = new Color(0x3d, 0x44, 0x4d);
    private static final Color TEXT_LIGHT = new Color(0xe6, 0xed, 0xf3);
    private static final Color TEXT_MUTED = new Color(0x8b, 0x94, 0x9e);
    private static final Color ACCENT_BLUE = new Color(0x58, 0xa6, 0xff);
    private static final Color SUCCESS_GREEN = new Color(0x23, 0x86, 0x36);
    private static final Color DANGER_RED = new Color(0xda, 0x36, 0x33);
    private static final Color WARN_YELLOW = new Color(0xd2, 0x99, 0x22);

    private final Plan plan;
    private final Furniture furniture;
    private final Opening door;
    private final PassageSimulationEngine engine;

    private PassageMode currentMode = PassageMode.WIGGLE;
    private Orientation currentOrientation = Orientation.NORMAL;
    private List<StepState> steps;
    private int currentStepIndex = 0;

    private final SimulationCanvas canvas = new SimulationCanvas();
    private final PassageSimulation3DViewer viewer3D = new PassageSimulation3DViewer();
    private final JTabbedPane viewTabs = new JTabbedPane();

    private final JSlider progressSlider = new JSlider(0, 100, 0);
    private final JButton playBtn = createStyledButton("재생 ▶", ACCENT_BLUE, Color.BLACK, 12, true);
    private final JButton resetBtn = createStyledButton("처음부터 ↺", new Color(0x38, 0x42, 0x50), TEXT_LIGHT, 12, false);
    private final JLabel statusLabel = new JLabel("시뮬레이션 준비 완료");

    private Timer animationTimer;
    private boolean isPlaying = false;

    public PassageSimulationDialog(JFrame parent, Plan plan, Furniture furniture, Opening door) {
        super(parent, "가구 문 통과 및 비틀기 반입 시뮬레이션", true);
        this.plan = plan;
        this.furniture = furniture;
        this.door = door;
        this.engine = new PassageSimulationEngine(plan, furniture, door);

        setSize(1040, 780);
        setMinimumSize(new Dimension(880, 640));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DARK);

        viewer3D.setup(plan, door);
        recomputeSteps();

        add(buildHeaderPanel(), BorderLayout.NORTH);

        viewTabs.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        viewTabs.setBackground(PANEL_BG);
        viewTabs.setForeground(TEXT_LIGHT);
        viewTabs.addTab("📐 2D 평면 시뮬레이션", canvas);
        viewTabs.addTab("🧊 3D 입체 시뮬레이션", viewer3D);
        add(viewTabs, BorderLayout.CENTER);

        add(buildBottomControlBar(), BorderLayout.SOUTH);

        setupTimer();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAnimation();
            }
        });
    }

    private void recomputeSteps() {
        steps = engine.computeSimulation(currentMode, currentOrientation, 120);
        if (currentStepIndex >= steps.size()) {
            currentStepIndex = 0;
        }
        updateStepDisplay();
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(PANEL_BG);
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(12, 16, 12, 16)
        ));

        // 1행: 타이틀 및 통과 방식 선택 (비틀기 / 직진)
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);

        JLabel titleLbl = new JLabel("🚪 가구 문 통과 (비틀어 넣기 & 직진) 시뮬레이션");
        titleLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 15));
        titleLbl.setForeground(ACCENT_BLUE);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        modePanel.setOpaque(false);
        ButtonGroup modeGroup = new ButtonGroup();
        for (PassageMode mode : PassageMode.values()) {
            JRadioButton rb = new JRadioButton(mode.label, mode == currentMode);
            rb.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
            rb.setForeground(mode == currentMode ? ACCENT_BLUE : TEXT_LIGHT);
            rb.setOpaque(false);
            rb.setFocusPainted(false);
            rb.addActionListener(e -> {
                currentMode = mode;
                recomputeSteps();
            });
            modeGroup.add(rb);
            modePanel.add(rb);
        }

        row1.add(titleLbl, BorderLayout.WEST);
        row1.add(modePanel, BorderLayout.EAST);
        header.add(row1);
        header.add(Box.createVerticalStrut(10));

        // 2행: 규격 요약 및 운반 자세 선택
        JPanel row2 = new JPanel(new BorderLayout());
        row2.setOpaque(false);

        String infoText = String.format("가구: %s (가로 %.0f × 세로 %.0f × 높이 %.0f cm) | 문 폭: %.0f cm",
                furniture.getName() != null ? furniture.getName() : furniture.getType().label,
                furniture.getWidth() / 10.0, furniture.getDepth() / 10.0, furniture.getHeight() / 10.0,
                door.width() / 10.0);
        JLabel infoLbl = new JLabel(infoText);
        infoLbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        infoLbl.setForeground(TEXT_LIGHT);
        row2.add(infoLbl, BorderLayout.WEST);

        JPanel orientPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        orientPanel.setOpaque(false);
        JLabel orientLbl = new JLabel("운반 자세:");
        orientLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        orientLbl.setForeground(TEXT_LIGHT);
        orientPanel.add(orientLbl);

        ButtonGroup orientGroup = new ButtonGroup();
        for (Orientation orient : Orientation.values()) {
            double[] dims = engine.getEffectiveDimensions(orient);
            String label = String.format("%s (%.0f×%.0f)", orient.label.replace(" 반입", ""), dims[0]/10.0, dims[1]/10.0);
            JRadioButton rb = new JRadioButton(label, orient == currentOrientation);
            rb.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
            rb.setForeground(TEXT_LIGHT);
            rb.setOpaque(false);
            rb.setFocusPainted(false);
            rb.addActionListener(e -> {
                currentOrientation = orient;
                recomputeSteps();
            });
            orientGroup.add(rb);
            orientPanel.add(rb);
        }
        row2.add(orientPanel, BorderLayout.EAST);
        header.add(row2);

        return header;
    }

    private JPanel buildBottomControlBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 8));
        bar.setBackground(PANEL_BG);
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(10, 16, 12, 16)
        ));

        // 슬라이더
        progressSlider.setOpaque(false);
        progressSlider.setBackground(PANEL_BG);
        progressSlider.setForeground(ACCENT_BLUE);
        progressSlider.addChangeListener(e -> {
            if (!isPlaying && steps != null && !steps.isEmpty()) {
                currentStepIndex = (int) (progressSlider.getValue() / 100.0 * (steps.size() - 1));
                updateStepDisplay();
            }
        });
        bar.add(progressSlider, BorderLayout.NORTH);

        // 버튼들
        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBtns.setOpaque(false);

        playBtn.setPreferredSize(new Dimension(100, 32));
        playBtn.addActionListener(e -> togglePlay());

        resetBtn.setPreferredSize(new Dimension(100, 32));
        resetBtn.addActionListener(e -> resetSimulation());

        leftBtns.add(playBtn);
        leftBtns.add(resetBtn);

        statusLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JButton closeBtn = createStyledButton("닫기", new Color(0x38, 0x42, 0x50), TEXT_LIGHT, 12, false);
        closeBtn.setPreferredSize(new Dimension(80, 32));
        closeBtn.addActionListener(e -> dispose());

        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        centerPanel.setOpaque(false);
        centerPanel.add(statusLabel);

        bar.add(leftBtns, BorderLayout.WEST);
        bar.add(centerPanel, BorderLayout.CENTER);
        bar.add(closeBtn, BorderLayout.EAST);

        return bar;
    }

    private void setupTimer() {
        animationTimer = new Timer(33, e -> {
            if (steps != null && currentStepIndex < steps.size() - 1) {
                currentStepIndex++;
                progressSlider.setValue((int) ((double) currentStepIndex / (steps.size() - 1) * 100));
                updateStepDisplay();
            } else {
                stopAnimation();
            }
        });
    }

    private void togglePlay() {
        if (isPlaying) {
            stopAnimation();
        } else {
            if (steps != null && currentStepIndex >= steps.size() - 1) {
                currentStepIndex = 0;
            }
            startAnimation();
        }
    }

    private void startAnimation() {
        isPlaying = true;
        playBtn.setText("일시정지 ⏸");
        playBtn.setBackground(new Color(0xf0, 0x88, 0x3e));
        animationTimer.start();
    }

    private void stopAnimation() {
        isPlaying = false;
        playBtn.setText("재생 ▶");
        playBtn.setBackground(ACCENT_BLUE);
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    private void resetSimulation() {
        stopAnimation();
        currentStepIndex = 0;
        progressSlider.setValue(0);
        updateStepDisplay();
    }

    private void updateStepDisplay() {
        updateStatus();
        canvas.repaint();
        if (steps != null && !steps.isEmpty()) {
            StepState step = steps.get(Math.min(currentStepIndex, steps.size() - 1));
            viewer3D.updateStep(step, engine.getEffectiveDimensions(currentOrientation));
        }
    }

    private void updateStatus() {
        if (steps == null || steps.isEmpty()) return;
        StepState state = steps.get(Math.min(currentStepIndex, steps.size() - 1));
        statusLabel.setText(state.statusMessage());
        if (state.collided()) {
            statusLabel.setForeground(DANGER_RED);
        } else if (state.statusMessage().contains("스침") || state.statusMessage().contains("주의")) {
            statusLabel.setForeground(WARN_YELLOW);
        } else {
            statusLabel.setForeground(new Color(0x7e, 0xe7, 0x87));
        }
    }

    /** 2D 시뮬레이션 캔버스 (도면의 실제 문과 벽체 기반) */
    private class SimulationCanvas extends JPanel {
        public SimulationCanvas() {
            setBackground(CANVAS_BG);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (steps == null || steps.isEmpty()) {
                g.dispose();
                return;
            }

            int w = getWidth(), h = getHeight();

            Wall wall = door.wall();
            Point2D pDoor = wall.pointAt(door.offset() + door.width() / 2.0);
            Point2D pStart = steps.get(0).position();
            Point2D pEnd = steps.get(steps.size() - 1).position();

            double minX = Math.min(Math.min(pDoor.x(), pStart.x()), pEnd.x()) - 1000;
            double maxX = Math.max(Math.max(pDoor.x(), pStart.x()), pEnd.x()) + 1000;
            double minY = Math.min(Math.min(pDoor.y(), pStart.y()), pEnd.y()) - 1000;
            double maxY = Math.max(Math.max(pDoor.y(), pStart.y()), pEnd.y()) + 1000;

            double boundW = Math.max(100, maxX - minX);
            double boundH = Math.max(100, maxY - minY);
            double scale = Math.min((w - 80) / boundW, (h - 80) / boundH);
            double midX = (minX + maxX) / 2.0;
            double midY = (minY + maxY) / 2.0;

            g.translate(w / 2.0, h / 2.0);
            g.scale(scale, -scale);
            g.translate(-midX, -midY);

            // 1. 도면의 실제 벽들 렌더링
            for (Wall wl : plan.getWalls()) {
                drawWallWithDoor(g, wl);
            }

            // 2. 가구 최종 통과 위치 고스트 점선
            List<Point2D> finalCorners = steps.get(steps.size() - 1).corners();
            g.setColor(new Color(0x58, 0xa6, 0xff, 35));
            fillPolygon(g, finalCorners);
            g.setColor(new Color(0x58, 0xa6, 0xff, 120));
            g.setStroke(new BasicStroke(15f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{40f, 30f}, 0f));
            drawPolygon(g, finalCorners);

            // 3. 현재 이동/회전 중인 가구
            StepState state = steps.get(Math.min(currentStepIndex, steps.size() - 1));
            List<Point2D> corners = state.corners();
            if (state.collided()) {
                g.setColor(new Color(0xda, 0x36, 0x33, 140));
                fillPolygon(g, corners);
                g.setColor(DANGER_RED);
                g.setStroke(new BasicStroke(35f));
                drawPolygon(g, corners);
            } else {
                g.setColor(new Color(0x23, 0x86, 0x36, 110));
                fillPolygon(g, corners);
                g.setColor(new Color(0x7e, 0xe7, 0x87));
                g.setStroke(new BasicStroke(25f));
                drawPolygon(g, corners);
            }

            // 앞머리 진행선 강조
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(30f));
            g.drawLine((int) corners.get(0).x(), (int) corners.get(0).y(), (int) corners.get(1).x(), (int) corners.get(1).y());

            // 문틀 통과 중 문틀 기둥과의 아슬아슬 간극 점선
            if (state.progress() >= 0.35 && state.progress() <= 0.65) {
                Point2D pLeft = wall.pointAt(door.offset());
                Point2D pRight = wall.pointAt(door.endOffset());
                g.setColor(state.leftClearanceMm() < 25 ? DANGER_RED : WARN_YELLOW);
                g.setStroke(new BasicStroke(10f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{25f, 25f}, 0f));
                g.drawLine((int) pLeft.x(), (int) pLeft.y(), (int) state.position().x(), (int) state.position().y());
                g.drawLine((int) pRight.x(), (int) pRight.y(), (int) state.position().x(), (int) state.position().y());
            }

            g.dispose();
        }

        private void drawWallWithDoor(Graphics2D g, Wall wl) {
            double thickness = wl.thickness();
            double len = wl.length();

            if (wl != door.wall()) {
                g.setColor(new Color(0x30, 0x36, 0x3d));
                fillPolygon(g, Geometry.thickLine(wl.start(), wl.end(), thickness));
                g.setColor(new Color(0x6e, 0x76, 0x81));
                g.setStroke(new BasicStroke(15f));
                drawPolygon(g, Geometry.thickLine(wl.start(), wl.end(), thickness));
                return;
            }

            // 문이 뚫린 벽: 좌측 조각
            if (door.offset() > 5) {
                List<Point2D> lPoly = Geometry.thickLine(wl.pointAt(0), wl.pointAt(door.offset()), thickness);
                g.setColor(new Color(0x30, 0x36, 0x3d));
                fillPolygon(g, lPoly);
                g.setColor(new Color(0x8b, 0x94, 0x9e));
                g.setStroke(new BasicStroke(15f));
                drawPolygon(g, lPoly);
            }
            // 문이 뚫린 벽: 우측 조각
            if (len - door.endOffset() > 5) {
                List<Point2D> rPoly = Geometry.thickLine(wl.pointAt(door.endOffset()), wl.pointAt(len), thickness);
                g.setColor(new Color(0x30, 0x36, 0x3d));
                fillPolygon(g, rPoly);
                g.setColor(new Color(0x8b, 0x94, 0x9e));
                g.setStroke(new BasicStroke(15f));
                drawPolygon(g, rPoly);
            }

            // 문틀 기둥 강조 (주황색 점)
            Point2D dStart = wl.pointAt(door.offset());
            Point2D dEnd = wl.pointAt(door.endOffset());
            g.setColor(new Color(0xf0, 0x88, 0x3e));
            g.fillOval((int) dStart.x() - 40, (int) dStart.y() - 40, 80, 80);
            g.fillOval((int) dEnd.x() - 40, (int) dEnd.y() - 40, 80, 80);

            // 열린 문짝 선 (점선)
            g.setColor(new Color(0x58, 0xa6, 0xff, 180));
            g.setStroke(new BasicStroke(15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{50f, 35f}, 0f));
            g.drawLine((int) dStart.x(), (int) dStart.y(), (int) dEnd.x(), (int) dEnd.y());
        }

        private void fillPolygon(Graphics2D g, List<Point2D> pts) {
            Path2D p = new Path2D.Double();
            p.moveTo(pts.get(0).x(), pts.get(0).y());
            for (int i = 1; i < pts.size(); i++) p.lineTo(pts.get(i).x(), pts.get(i).y());
            p.closePath();
            g.fill(p);
        }

        private void drawPolygon(Graphics2D g, List<Point2D> pts) {
            Path2D p = new Path2D.Double();
            p.moveTo(pts.get(0).x(), pts.get(0).y());
            for (int i = 1; i < pts.size(); i++) p.lineTo(pts.get(i).x(), pts.get(i).y());
            p.closePath();
            g.draw(p);
        }
    }

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
}
