package com.roomplanner.ui;

import com.roomplanner.model.*;
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
 * 가구가 문을 통과하여 방 안으로 반입되는 과정을 실시간 2D 애니메이션으로 보여주는 시뮬레이션 다이얼로그.
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

    private List<StepState> steps;
    private int currentStepIndex = 0;
    private Orientation currentOrientation = Orientation.NORMAL;

    private final SimulationCanvas canvas = new SimulationCanvas();
    private final JSlider progressSlider = new JSlider(0, 100, 0);
    private final JButton playBtn = createStyledButton("▶ 재생", ACCENT_BLUE, Color.BLACK, 12, true);
    private final JButton resetBtn = createStyledButton("↺ 처음부터", new Color(0x38, 0x42, 0x50), TEXT_LIGHT, 12, false);
    private final JLabel statusLabel = new JLabel("시뮬레이션 준비 완료");

    private Timer animationTimer;
    private boolean isPlaying = false;

    public PassageSimulationDialog(JFrame parent, Plan plan, Furniture furniture, Opening door) {
        super(parent, "가구 문 통과 및 반입 시뮬레이션", true);
        this.plan = plan;
        this.furniture = furniture;
        this.door = door;
        this.engine = new PassageSimulationEngine(plan, furniture, door);

        setSize(1000, 750);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DARK);

        recomputeSteps();

        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
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
        steps = engine.computeSimulation(currentOrientation, 120);
        if (currentStepIndex >= steps.size()) {
            currentStepIndex = 0;
        }
        updateStatus();
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(PANEL_BG);
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                new EmptyBorder(12, 16, 12, 16)
        ));

        // 1행: 타이틀 및 규격 요약
        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);

        JLabel titleLbl = new JLabel("🚪 가구 반입(문 통과) 이동 시뮬레이션");
        titleLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 15));
        titleLbl.setForeground(ACCENT_BLUE);

        String infoText = String.format("가구: %s (가로 %.0f × 세로 %.0f × 높이 %.0f cm) | 문 폭: %.0f cm (높이 %.0f cm)",
                furniture.getName() != null ? furniture.getName() : furniture.getType().label,
                furniture.getWidth() / 10.0, furniture.getDepth() / 10.0, furniture.getHeight() / 10.0,
                door.width() / 10.0, door.height() / 10.0);
        JLabel infoLbl = new JLabel(infoText);
        infoLbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        infoLbl.setForeground(TEXT_LIGHT);

        row1.add(titleLbl, BorderLayout.WEST);
        row1.add(infoLbl, BorderLayout.EAST);
        header.add(row1);
        header.add(Box.createVerticalStrut(10));

        // 2행: 운반 자세 선택 라디오 버튼
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        row2.setOpaque(false);

        JLabel orientLbl = new JLabel("운반 자세 선택:");
        orientLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        orientLbl.setForeground(TEXT_LIGHT);
        row2.add(orientLbl);

        ButtonGroup group = new ButtonGroup();
        for (Orientation orient : Orientation.values()) {
            double[] dims = engine.getEffectiveDimensions(orient);
            String label = String.format("%s (투영 %.0f×%.0f, 높이 %.0fcm)", orient.label, dims[0]/10.0, dims[1]/10.0, dims[2]/10.0);
            JRadioButton rb = new JRadioButton(label, orient == currentOrientation);
            rb.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
            rb.setForeground(TEXT_LIGHT);
            rb.setOpaque(false);
            rb.setFocusPainted(false);
            rb.addActionListener(e -> {
                currentOrientation = orient;
                recomputeSteps();
                canvas.repaint();
            });
            group.add(rb);
            row2.add(rb);
        }

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

        // 상단: 슬라이더
        progressSlider.setOpaque(false);
        progressSlider.setBackground(PANEL_BG);
        progressSlider.setForeground(ACCENT_BLUE);
        progressSlider.addChangeListener(e -> {
            if (!isPlaying && !steps.isEmpty()) {
                currentStepIndex = (int) (progressSlider.getValue() / 100.0 * (steps.size() - 1));
                updateStatus();
                canvas.repaint();
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
        // 30 FPS 부드러운 애니메이션
        animationTimer = new Timer(33, e -> {
            if (currentStepIndex < steps.size() - 1) {
                currentStepIndex++;
                progressSlider.setValue((int) ((double) currentStepIndex / (steps.size() - 1) * 100));
                updateStatus();
                canvas.repaint();
            } else {
                stopAnimation();
            }
        });
    }

    private void togglePlay() {
        if (isPlaying) {
            stopAnimation();
        } else {
            if (currentStepIndex >= steps.size() - 1) {
                currentStepIndex = 0;
            }
            startAnimation();
        }
    }

    private void startAnimation() {
        isPlaying = true;
        playBtn.setText("⏸ 일시정지");
        playBtn.setBackground(new Color(0xf0, 0x88, 0x3e));
        animationTimer.start();
    }

    private void stopAnimation() {
        isPlaying = false;
        playBtn.setText("▶ 재생");
        playBtn.setBackground(ACCENT_BLUE);
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    private void resetSimulation() {
        stopAnimation();
        currentStepIndex = 0;
        progressSlider.setValue(0);
        updateStatus();
        canvas.repaint();
    }

    private void updateStatus() {
        if (steps.isEmpty()) return;
        StepState state = steps.get(Math.min(currentStepIndex, steps.size() - 1));
        statusLabel.setText(state.statusMessage());
        if (state.collided()) {
            statusLabel.setForeground(DANGER_RED);
        } else if (state.statusMessage().contains("주의")) {
            statusLabel.setForeground(WARN_YELLOW);
        } else {
            statusLabel.setForeground(new Color(0x7e, 0xe7, 0x87));
        }
    }

    /** 2D 시뮬레이션 전용 캔버스 */
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

            // 문 및 타겟 위치를 기준으로 중심 변환 및 스케일 자동 계산
            Wall wall = door.wall();
            Point2D pDoor = wall.pointAt(door.offset() + door.width() / 2.0);
            Point2D pTarget = new Point2D(furniture.getX(), furniture.getY());

            double minX = Math.min(pDoor.x(), pTarget.x()) - 1800;
            double maxX = Math.max(pDoor.x(), pTarget.x()) + 1800;
            double minY = Math.min(pDoor.y(), pTarget.y()) - 1800;
            double maxY = Math.max(pDoor.y(), pTarget.y()) + 1800;

            double boundW = Math.max(100, maxX - minX);
            double boundH = Math.max(100, maxY - minY);

            double scale = Math.min((w - 80) / boundW, (h - 80) / boundH);
            double midX = (minX + maxX) / 2.0;
            double midY = (minY + maxY) / 2.0;

            g.translate(w / 2.0, h / 2.0);
            g.scale(scale, -scale); // 수학적 Y축 위로
            g.translate(-midX, -midY);

            // 1. 도면의 벽들 그리기
            for (Wall wl : plan.getWalls()) {
                drawWallWithDoor(g, wl);
            }

            // 2. 가구 최종 목표 위치 (점선 가이드)
            drawTargetGhost(g);

            // 3. 현재 프레임의 이동 중인 가구 그리기
            StepState state = steps.get(Math.min(currentStepIndex, steps.size() - 1));
            drawFurnitureAtState(g, state);

            // 4. 문틀 여유 간격 치수 보선 표시
            if (state.progress() >= 0.35 && state.progress() <= 0.65) {
                drawClearanceLines(g, state);
            }

            g.dispose();
        }

        private void drawWallWithDoor(Graphics2D g, Wall wl) {
            double thickness = wl.thickness();
            double len = wl.length();

            if (wl != door.wall()) {
                // 일반 벽
                g.setColor(new Color(0x30, 0x36, 0x3d));
                fillPolygon(g, Geometry.thickLine(wl.start(), wl.end(), thickness));
                g.setColor(new Color(0x6e, 0x76, 0x81));
                g.setStroke(new BasicStroke(15f));
                drawPolygon(g, Geometry.thickLine(wl.start(), wl.end(), thickness));
                return;
            }

            // 문이 있는 벽: 좌측 조각
            if (door.offset() > 5) {
                List<Point2D> leftPoly = Geometry.thickLine(wl.pointAt(0), wl.pointAt(door.offset()), thickness);
                g.setColor(new Color(0x30, 0x36, 0x3d));
                fillPolygon(g, leftPoly);
                g.setColor(new Color(0x8b, 0x94, 0x9e));
                g.setStroke(new BasicStroke(15f));
                drawPolygon(g, leftPoly);
            }

            // 문이 있는 벽: 우측 조각
            if (len - door.endOffset() > 5) {
                List<Point2D> rightPoly = Geometry.thickLine(wl.pointAt(door.endOffset()), wl.pointAt(len), thickness);
                g.setColor(new Color(0x30, 0x36, 0x3d));
                fillPolygon(g, rightPoly);
                g.setColor(new Color(0x8b, 0x94, 0x9e));
                g.setStroke(new BasicStroke(15f));
                drawPolygon(g, rightPoly);
            }

            // 문 열림 궤적 및 문틀 표시
            Point2D dStart = wl.pointAt(door.offset());
            Point2D dEnd = wl.pointAt(door.endOffset());

            // 문틀 기둥 강조 (주황색 점)
            g.setColor(new Color(0xf0, 0x88, 0x3e));
            g.fillOval((int) dStart.x() - 40, (int) dStart.y() - 40, 80, 80);
            g.fillOval((int) dEnd.x() - 40, (int) dEnd.y() - 40, 80, 80);

            // 열린 문짝 선 (점선)
            g.setColor(new Color(0x58, 0xa6, 0xff, 180));
            g.setStroke(new BasicStroke(20f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{60f, 40f}, 0f));
            g.drawLine((int) dStart.x(), (int) dStart.y(), (int) dEnd.x(), (int) dEnd.y());
        }

        private void drawTargetGhost(Graphics2D g) {
            double[] dims = engine.getEffectiveDimensions(currentOrientation);
            List<Point2D> corners = furniture.corners();
            g.setColor(new Color(0x58, 0xa6, 0xff, 40));
            fillPolygon(g, corners);
            g.setColor(new Color(0x58, 0xa6, 0xff, 120));
            g.setStroke(new BasicStroke(15f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{40f, 30f}, 0f));
            drawPolygon(g, corners);
        }

        private void drawFurnitureAtState(Graphics2D g, StepState state) {
            List<Point2D> corners = state.corners();
            if (corners == null || corners.size() < 4) return;

            if (state.collided()) {
                // 충돌 시 빨간색 강조
                g.setColor(new Color(0xda, 0x36, 0x33, 140));
                fillPolygon(g, corners);
                g.setColor(DANGER_RED);
                g.setStroke(new BasicStroke(35f));
                drawPolygon(g, corners);
            } else {
                // 정상 통과 시 청록/파란색
                g.setColor(new Color(0x23, 0x86, 0x36, 110));
                fillPolygon(g, corners);
                g.setColor(new Color(0x7e, 0xe7, 0x87));
                g.setStroke(new BasicStroke(25f));
                drawPolygon(g, corners);
            }

            // 앞면 표시선 (진행 방향 식별)
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(30f));
            g.drawLine((int) corners.get(0).x(), (int) corners.get(0).y(), (int) corners.get(1).x(), (int) corners.get(1).y());
        }

        private void drawClearanceLines(Graphics2D g, StepState state) {
            Wall wl = door.wall();
            Point2D pLeft = wl.pointAt(door.offset());
            Point2D pRight = wl.pointAt(door.endOffset());

            g.setFont(new Font("Malgun Gothic", Font.BOLD, 90));
            g.setColor(state.leftClearanceMm() < 30 ? DANGER_RED : new Color(0x7e, 0xe7, 0x87));

            // 문자열이 수학적 상하 반전되지 않도록 affine 보정 필요 시 심플한 텍스트 렌더링
            // 치수 텍스트는 하단 상태바에 실시간으로 표시되므로 캔버스에는 깔끔한 치수 점선만 표시
            g.setStroke(new BasicStroke(10f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{30f, 30f}, 0f));
            g.drawLine((int) pLeft.x(), (int) pLeft.y(), (int) state.position().x(), (int) state.position().y());
            g.drawLine((int) pRight.x(), (int) pRight.y(), (int) state.position().x(), (int) state.position().y());
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
