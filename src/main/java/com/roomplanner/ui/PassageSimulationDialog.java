package com.roomplanner.ui;

import com.roomplanner.model.*;
import com.roomplanner.service.MovingSofaEngine;
import com.roomplanner.service.MovingSofaEngine.ProofReport;
import com.roomplanner.service.MovingSofaEngine.SofaStep;
import com.roomplanner.service.PassageSimulationEngine;
import com.roomplanner.service.PassageSimulationEngine.Orientation;
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
 * 가구 문 통과 및 '소파 옮기기 문제(L자 코너 회전)' 시뮬레이션 다이얼로그.
 * 수학적 해석 공식(Singmaster & Croft) 기반 한계 계산 및 2D/3D 입체 궤적 렌더링 제공.
 */
public class PassageSimulationDialog extends JDialog {

    public enum SimMode {
        SOFA_L_CORNER("L자 코너 회전 (소파 옮기기 문제)"),
        STRAIGHT("문 직진 통과");

        public final String label;
        SimMode(String label) { this.label = label; }
    }

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
    private final PassageSimulationEngine straightEngine;

    private SimMode currentMode = SimMode.SOFA_L_CORNER;
    private Orientation currentOrientation = Orientation.NORMAL;

    // 직선 통과용 스텝
    private List<StepState> straightSteps;
    // 소파 옮기기용 스텝 및 수학 리포트
    private List<SofaStep> sofaSteps;
    private ProofReport sofaReport;

    private int currentStepIndex = 0;

    private final SimulationCanvas canvas = new SimulationCanvas();
    private final PassageSimulation3DViewer viewer3D = new PassageSimulation3DViewer();
    private final JTabbedPane viewTabs = new JTabbedPane();

    private final JSlider progressSlider = new JSlider(0, 100, 0);
    private final JButton playBtn = createStyledButton("재생 ▶", ACCENT_BLUE, Color.BLACK, 12, true);
    private final JButton resetBtn = createStyledButton("처음부터 ↺", new Color(0x38, 0x42, 0x50), TEXT_LIGHT, 12, false);
    private final JLabel statusLabel = new JLabel("시뮬레이션 준비 완료");

    // 수학적 공식 요약 패널
    private final JPanel proofPanel = new JPanel(new BorderLayout(8, 0));
    private final JLabel formulaLbl = new JLabel();
    private final JLabel proofResultBadge = new JLabel();

    private Timer animationTimer;
    private boolean isPlaying = false;

    public PassageSimulationDialog(JFrame parent, Plan plan, Furniture furniture, Opening door) {
        super(parent, "가구 문 통과 및 소파 옮기기 문제 시뮬레이션", true);
        this.plan = plan;
        this.furniture = furniture;
        this.door = door;
        this.straightEngine = new PassageSimulationEngine(plan, furniture, door);

        setSize(1080, 800);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DARK);

        recomputeAll();

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

    private void recomputeAll() {
        double w1 = door.width();
        double w2 = door.width();

        double[] dims = straightEngine.getEffectiveDimensions(currentOrientation);
        // 가구의 길이(L)와 폭(W)
        double L = Math.max(dims[0], dims[1]);
        double W = Math.min(dims[0], dims[1]);
        double H = dims[2];

        // 1. 소파 옮기기 수학적 분석 및 궤적 생성
        sofaReport = MovingSofaEngine.analyze(w1, w2, L, W);
        sofaSteps = MovingSofaEngine.generateTrajectory(w1, w2, L, W, 120);

        // 2. 직선 통과 궤적 생성
        straightSteps = straightEngine.computeSimulation(currentOrientation, 120);

        if (currentMode == SimMode.SOFA_L_CORNER) {
            viewer3D.setupLHallway(w1, w2, 2400);
        } else {
            viewer3D.setup(plan, door);
        }

        updateProofUI();
        updateStepDisplay();
    }

    private void updateProofUI() {
        if (currentMode == SimMode.SOFA_L_CORNER && sofaReport != null) {
            formulaLbl.setText(String.format("<html><b>[소파 옮기기 수학 증명]</b> %s<br>" +
                            "복도폭 w=%.0fcm | 가구 폭 W=%.0fcm → <b>이론상 최대 허용 길이 L_max = %.1fcm</b> (임계각도 %.1f°)</html>",
                    sofaReport.formulaText(), sofaReport.corridorWidth1() / 10.0, sofaReport.sofaWidth() / 10.0,
                    sofaReport.maxAllowedLength() / 10.0, sofaReport.criticalAngleDeg()));

            proofResultBadge.setText(" " + sofaReport.conclusionText() + " ");
            proofResultBadge.setOpaque(true);
            proofResultBadge.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
            if (sofaReport.canPass()) {
                proofResultBadge.setBackground(new Color(0x1a, 0x40, 0x24));
                proofResultBadge.setForeground(new Color(0x7e, 0xe7, 0x87));
                proofResultBadge.setBorder(new LineBorder(SUCCESS_GREEN, 1, true));
            } else {
                proofResultBadge.setBackground(new Color(0x40, 0x1a, 0x1e));
                proofResultBadge.setForeground(new Color(0xf8, 0x51, 0x49));
                proofResultBadge.setBorder(new LineBorder(DANGER_RED, 1, true));
            }
            proofPanel.setVisible(true);
        } else {
            proofPanel.setVisible(false);
        }
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(PANEL_BG);
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(12, 16, 12, 16)
        ));

        // 1행: 타이틀 및 모드 선택 라디오
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);

        JLabel titleLbl = new JLabel("🛋️ 가구 반입 & 소파 옮기기 문제(Moving Sofa) 시뮬레이션");
        titleLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 15));
        titleLbl.setForeground(ACCENT_BLUE);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        modePanel.setOpaque(false);
        ButtonGroup modeGroup = new ButtonGroup();
        for (SimMode mode : SimMode.values()) {
            JRadioButton rb = new JRadioButton(mode.label, mode == currentMode);
            rb.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
            rb.setForeground(mode == currentMode ? ACCENT_BLUE : TEXT_LIGHT);
            rb.setOpaque(false);
            rb.setFocusPainted(false);
            rb.addActionListener(e -> {
                currentMode = mode;
                recomputeAll();
            });
            modeGroup.add(rb);
            modePanel.add(rb);
        }

        row1.add(titleLbl, BorderLayout.WEST);
        row1.add(modePanel, BorderLayout.EAST);
        header.add(row1);
        header.add(Box.createVerticalStrut(10));

        // 2행: 수학 증명 박스
        proofPanel.setOpaque(true);
        proofPanel.setBackground(new Color(0x19, 0x22, 0x30));
        proofPanel.setBorder(new CompoundBorder(
                new LineBorder(new Color(0x38, 0x50, 0x70), 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        formulaLbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        formulaLbl.setForeground(TEXT_LIGHT);
        proofPanel.add(formulaLbl, BorderLayout.CENTER);
        proofPanel.add(proofResultBadge, BorderLayout.EAST);
        header.add(proofPanel);
        header.add(Box.createVerticalStrut(10));

        // 3행: 운반 자세 선택
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row3.setOpaque(false);

        JLabel orientLbl = new JLabel("운반 자세 선택:");
        orientLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        orientLbl.setForeground(TEXT_LIGHT);
        row3.add(orientLbl);

        ButtonGroup orientGroup = new ButtonGroup();
        for (Orientation orient : Orientation.values()) {
            double[] dims = straightEngine.getEffectiveDimensions(orient);
            String label = String.format("%s (투영 %.0f×%.0f, 높이 %.0fcm)",
                    orient.label, dims[0]/10.0, dims[1]/10.0, dims[2]/10.0);
            JRadioButton rb = new JRadioButton(label, orient == currentOrientation);
            rb.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
            rb.setForeground(TEXT_LIGHT);
            rb.setOpaque(false);
            rb.setFocusPainted(false);
            rb.addActionListener(e -> {
                currentOrientation = orient;
                recomputeAll();
            });
            orientGroup.add(rb);
            row3.add(rb);
        }
        header.add(row3);

        return header;
    }

    private JPanel buildBottomControlBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 8));
        bar.setBackground(PANEL_BG);
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                new EmptyBorder(10, 16, 12, 16)
        ));

        // 상단: 슬라이더
        progressSlider.setOpaque(false);
        progressSlider.setBackground(PANEL_BG);
        progressSlider.setForeground(ACCENT_BLUE);
        progressSlider.addChangeListener(e -> {
            if (!isPlaying) {
                int total = currentMode == SimMode.SOFA_L_CORNER ? sofaSteps.size() : straightSteps.size();
                if (total > 0) {
                    currentStepIndex = (int) (progressSlider.getValue() / 100.0 * (total - 1));
                    updateStepDisplay();
                }
            }
        });
        bar.add(progressSlider, BorderLayout.NORTH);

        // 하단 좌측: 컨트롤 버튼들
        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBtns.setOpaque(false);

        playBtn.setPreferredSize(new Dimension(100, 32));
        playBtn.addActionListener(e -> togglePlay());

        resetBtn.setPreferredSize(new Dimension(100, 32));
        resetBtn.addActionListener(e -> resetSimulation());

        leftBtns.add(playBtn);
        leftBtns.add(resetBtn);

        // 하단 중앙: 상태 레이블
        statusLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // 하단 우측: 닫기 버튼
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
            int total = currentMode == SimMode.SOFA_L_CORNER ? sofaSteps.size() : straightSteps.size();
            if (currentStepIndex < total - 1) {
                currentStepIndex++;
                progressSlider.setValue((int) ((double) currentStepIndex / (total - 1) * 100));
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
            int total = currentMode == SimMode.SOFA_L_CORNER ? sofaSteps.size() : straightSteps.size();
            if (currentStepIndex >= total - 1) {
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

        double[] dims = straightEngine.getEffectiveDimensions(currentOrientation);
        double L = Math.max(dims[0], dims[1]);
        double W = Math.min(dims[0], dims[1]);
        double H = dims[2];

        if (currentMode == SimMode.SOFA_L_CORNER && !sofaSteps.isEmpty()) {
            SofaStep step = sofaSteps.get(Math.min(currentStepIndex, sofaSteps.size() - 1));
            viewer3D.updateSofaStep(step, W, L, H);
        } else if (currentMode == SimMode.STRAIGHT && !straightSteps.isEmpty()) {
            StepState step = straightSteps.get(Math.min(currentStepIndex, straightSteps.size() - 1));
            viewer3D.updateStep(step, dims);
        }
    }

    private void updateStatus() {
        if (currentMode == SimMode.SOFA_L_CORNER && !sofaSteps.isEmpty()) {
            SofaStep state = sofaSteps.get(Math.min(currentStepIndex, sofaSteps.size() - 1));
            statusLabel.setText(state.statusMsg());
            if (state.collided()) {
                statusLabel.setForeground(DANGER_RED);
            } else if (state.statusMsg().contains("스침") || state.statusMsg().contains("주의")) {
                statusLabel.setForeground(WARN_YELLOW);
            } else {
                statusLabel.setForeground(new Color(0x7e, 0xe7, 0x87));
            }
        } else if (currentMode == SimMode.STRAIGHT && !straightSteps.isEmpty()) {
            StepState state = straightSteps.get(Math.min(currentStepIndex, straightSteps.size() - 1));
            statusLabel.setText(state.statusMessage());
            if (state.collided()) {
                statusLabel.setForeground(DANGER_RED);
            } else if (state.statusMessage().contains("주의")) {
                statusLabel.setForeground(WARN_YELLOW);
            } else {
                statusLabel.setForeground(new Color(0x7e, 0xe7, 0x87));
            }
        }
    }

    /** 2D 시뮬레이션 캔버스 */
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

            int w = getWidth(), h = getHeight();

            if (currentMode == SimMode.SOFA_L_CORNER) {
                paintSofaMode(g, w, h);
            } else {
                paintStraightMode(g, w, h);
            }

            g.dispose();
        }

        private void paintSofaMode(Graphics2D g, int w, int h) {
            if (sofaSteps == null || sofaSteps.isEmpty()) return;

            double w1 = door.width();
            double w2 = door.width();
            double armLen = 3000.0;

            // 좌표계: 안쪽 코너 (0, 0)가 중앙 근처에 오도록 변환
            double boundW = armLen + w1 + 1000;
            double boundH = armLen + w2 + 1000;
            double scale = Math.min((w - 100) / boundW, (h - 100) / boundH);

            g.translate(w / 2.0 - (w1 / 2.0) * scale, h / 2.0 + (w2 / 2.0) * scale);
            g.scale(scale, -scale); // Y축 위로

            // 1. L자 복도 벽체 렌더링
            g.setColor(new Color(0x30, 0x36, 0x3d));
            double thick = 150.0;

            // 안쪽 벽 1: x = 0 (y >= 0)
            fillPolygon(g, Geometry.thickLine(new Point2D(0, 0), new Point2D(0, armLen), thick));
            // 안쪽 벽 2: y = 0 (x <= 0)
            fillPolygon(g, Geometry.thickLine(new Point2D(0, 0), new Point2D(-armLen, 0), thick));
            // 바깥벽 1: x = w1 (y >= -w2)
            fillPolygon(g, Geometry.thickLine(new Point2D(w1, -w2), new Point2D(w1, armLen), thick));
            // 바깥벽 2: y = -w2 (x <= w1)
            fillPolygon(g, Geometry.thickLine(new Point2D(-armLen, -w2), new Point2D(w1, -w2), thick));

            g.setColor(new Color(0x6e, 0x76, 0x81));
            g.setStroke(new BasicStroke(15f));
            drawPolygon(g, Geometry.thickLine(new Point2D(0, 0), new Point2D(0, armLen), thick));
            drawPolygon(g, Geometry.thickLine(new Point2D(0, 0), new Point2D(-armLen, 0), thick));
            drawPolygon(g, Geometry.thickLine(new Point2D(w1, -w2), new Point2D(w1, armLen), thick));
            drawPolygon(g, Geometry.thickLine(new Point2D(-armLen, -w2), new Point2D(w1, -w2), thick));

            // 안쪽 코너 피벗 점 강조 (오렌지색 점)
            g.setColor(new Color(0xf0, 0x88, 0x3e));
            g.fillOval(-40, -40, 80, 80);

            // 2. 가구 궤적 및 현재 위치 렌더링
            SofaStep currentStep = sofaSteps.get(Math.min(currentStepIndex, sofaSteps.size() - 1));

            // 가구 최종 통과 위치 고스트 점선
            List<Point2D> finalCorners = sofaSteps.get(sofaSteps.size() - 1).corners();
            g.setColor(new Color(0x58, 0xa6, 0xff, 35));
            fillPolygon(g, finalCorners);
            g.setColor(new Color(0x58, 0xa6, 0xff, 120));
            g.setStroke(new BasicStroke(15f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{40f, 30f}, 0f));
            drawPolygon(g, finalCorners);

            // 현재 이동 중인 가구
            List<Point2D> corners = currentStep.corners();
            if (currentStep.collided()) {
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

            // 앞머리 진행선
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(30f));
            g.drawLine((int) corners.get(2).x(), (int) corners.get(2).y(), (int) corners.get(3).x(), (int) corners.get(3).y());

            // 피벗 모서리와의 여유 점선
            if (currentStep.progress() >= 0.25 && currentStep.progress() <= 0.75) {
                g.setColor(currentStep.innerCornerDistMm() < 25 ? DANGER_RED : WARN_YELLOW);
                g.setStroke(new BasicStroke(12f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{30f, 30f}, 0f));
                g.drawLine(0, 0, (int) currentStep.center().x(), (int) currentStep.center().y());
            }
        }

        private void paintStraightMode(Graphics2D g, int w, int h) {
            if (straightSteps == null || straightSteps.isEmpty()) return;

            Wall wall = door.wall();
            Point2D pDoor = wall.pointAt(door.offset() + door.width() / 2.0);
            Point2D pStart = straightSteps.get(0).position();
            Point2D pEnd = straightSteps.get(straightSteps.size() - 1).position();

            double minX = Math.min(Math.min(pDoor.x(), pStart.x()), pEnd.x()) - 1200;
            double maxX = Math.max(Math.max(pDoor.x(), pStart.x()), pEnd.x()) + 1200;
            double minY = Math.min(Math.min(pDoor.y(), pStart.y()), pEnd.y()) - 1200;
            double maxY = Math.max(Math.max(pDoor.y(), pStart.y()), pEnd.y()) + 1200;

            double boundW = Math.max(100, maxX - minX);
            double boundH = Math.max(100, maxY - minY);
            double scale = Math.min((w - 80) / boundW, (h - 80) / boundH);
            double midX = (minX + maxX) / 2.0;
            double midY = (minY + maxY) / 2.0;

            g.translate(w / 2.0, h / 2.0);
            g.scale(scale, -scale);
            g.translate(-midX, -midY);

            // 벽들 그리기
            for (Wall wl : plan.getWalls()) {
                drawWallWithDoor(g, wl);
            }

            // 고스트 가이드
            List<Point2D> finalCorners = straightSteps.get(straightSteps.size() - 1).corners();
            g.setColor(new Color(0x58, 0xa6, 0xff, 35));
            fillPolygon(g, finalCorners);
            g.setColor(new Color(0x58, 0xa6, 0xff, 120));
            g.setStroke(new BasicStroke(15f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{40f, 30f}, 0f));
            drawPolygon(g, finalCorners);

            // 이동 중 가구
            StepState state = straightSteps.get(Math.min(currentStepIndex, straightSteps.size() - 1));
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
        }

        private void drawWallWithDoor(Graphics2D g, Wall wl) {
            double thickness = wl.thickness();
            double len = wl.length();

            if (wl != door.wall()) {
                g.setColor(new Color(0x30, 0x36, 0x3d));
                fillPolygon(g, Geometry.thickLine(wl.start(), wl.end(), thickness));
                return;
            }

            if (door.offset() > 5) {
                fillPolygon(g, Geometry.thickLine(wl.pointAt(0), wl.pointAt(door.offset()), thickness));
            }
            if (len - door.endOffset() > 5) {
                fillPolygon(g, Geometry.thickLine(wl.pointAt(door.endOffset()), wl.pointAt(len), thickness));
            }

            Point2D dStart = wl.pointAt(door.offset());
            Point2D dEnd = wl.pointAt(door.endOffset());
            g.setColor(new Color(0xf0, 0x88, 0x3e));
            g.fillOval((int) dStart.x() - 40, (int) dStart.y() - 40, 80, 80);
            g.fillOval((int) dEnd.x() - 40, (int) dEnd.y() - 40, 80, 80);
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
