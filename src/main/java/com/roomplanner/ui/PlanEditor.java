package com.roomplanner.ui;

import com.roomplanner.model.Furniture;
import com.roomplanner.model.Opening;
import com.roomplanner.model.Plan;
import com.roomplanner.model.PlanIO;
import com.roomplanner.model.Point2D;
import com.roomplanner.model.Room;
import com.roomplanner.model.Wall;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * AutoCAD 스타일 2D 도면 편집기.
 * 좌표 규칙: 모델(Plan)은 항상 mm 세계 좌표, 화면은 px. 변환은 screenToWorld / sx / sy 한 곳에서만 한다.
 */
public class PlanEditor extends JPanel {

    public enum Tool {
        WALL("벽 그리기", "L"), SELECT("선택", "V"), DOOR("문", "D"), WINDOW("창문", "W"), FURNITURE("가구 배치", "F");
        public final String label;
        public final String key;
        Tool(String label, String key) { this.label = label; this.key = key; }
    }

    private enum SnapKind { NONE, GRID, END }

    private static final int[] PALETTE = {0xF4C7C3, 0xC6E0B4, 0xBDD7EE, 0xFFE699, 0xD9C3E8, 0xF8CBAD};
    private static final Color WALL_COLOR = new Color(45, 45, 50);
    private static final Color SELECT_COLOR = new Color(230, 60, 60);
    private static final double SNAP_RADIUS_PX = 10;
    private static final int UNDO_LIMIT = 100;

    // ---- 데이터 ----
    private Plan plan = new Plan();
    private final Deque<String> undoStack = new ArrayDeque<>();
    private int roomCounter = 1;

    // ---- 보기(뷰) 상태: 화면 (0,0)이 가리키는 세계 좌표 = (offX, offY) ----
    private double scale = 0.1;          // 1mm = 0.1px
    private double offX = -1000, offY = -1000;
    private boolean pendingFit;

    // ---- 도구/모드 ----
    private Tool tool = Tool.SELECT;
    private boolean gridSnap = true;
    private boolean ortho = false;
    private boolean objectSnap = true;
    private double wallThickness = 150;
    private double wallHeight = 2400;

    // ---- 작업 중 상태 ----
    private final List<Point2D> chain = new ArrayList<>();   // 지금 그리고 있는 연속 벽의 점들
    private Point2D cursor;                                  // 보정된 마우스 위치
    private SnapKind snapKind = SnapKind.NONE;
    private final StringBuilder input = new StringBuilder(); // 숫자로 입력 중인 길이
    private Object selected;                                 // Wall / Room / Opening
    private Opening ghost;                                   // 문/창 배치 미리보기

    // ---- 가구 ----
    private Furniture.Type furnitureType = Furniture.Type.BED;
    private Furniture ghostFurniture;      // 가구 배치 미리보기 (아직 도면에 없음)
    private Furniture dragging;            // 선택 도구로 끌고 있는 가구
    private Point2D dragOffset;            // 잡은 위치와 가구 중심의 차이
    private String dragSnapshot;           // 끌기 시작 전 상태 (움직였을 때만 실행 취소에 저장)
    private boolean dragMoved;
    private Wall draggingWall;             // 선택 도구로 끝점을 끌고 있는 벽
    private int dragWallHandle = -1;       // 0: start, 1: end
    private Consumer<Object> selectionListener;

    private int lastMouseX, lastMouseY;
    private boolean panning;

    private Consumer<String> statusListener = s -> {};
    private Consumer<Tool> toolListener = t -> {};

    public PlanEditor() {
        setBackground(Color.WHITE);
        setFocusable(true);
        installMouse();
        installKeys();
    }

    // =====================================================================
    // 공개 API (MainFrame이 사용)
    // =====================================================================

    public Plan getPlan() { return plan; }

    public void setPlan(Plan p) {
        plan = p;
        undoStack.clear();
        chain.clear();
        input.setLength(0);
        selected = null;
        ghost = null;
        ghostFurniture = null;
        dragging = null;
        roomCounter = p.getRooms().size() + 1;
        if (p.isEmpty()) {
            scale = 0.1; offX = -1000; offY = -1000;
        } else {
            pendingFit = true;
            zoomExtents();
        }
        fireStatus();
        repaint();
    }

    public Tool getTool() { return tool; }

    public void setTool(Tool t) {
        tool = t;
        chain.clear();
        input.setLength(0);
        selected = null;
        ghost = null;
        if (t == Tool.FURNITURE) ensureGhost();
        toolListener.accept(t);
        fireStatus();
        repaint();
    }

    public void setWallThickness(double v) {
        wallThickness = v;
        if (selected instanceof Wall w && Math.abs(w.thickness() - v) > 1e-4) {
            pushUndo();
            Wall newWall = new Wall(w.start(), w.end(), v, w.height());
            plan.replaceWall(w, newWall);
            setSelected(newWall);
            repaint();
        }
    }

    public void setWallHeight(double v) {
        wallHeight = v;
        if (selected instanceof Wall w && Math.abs(w.height() - v) > 1e-4) {
            pushUndo();
            Wall newWall = new Wall(w.start(), w.end(), w.thickness(), v);
            plan.replaceWall(w, newWall);
            setSelected(newWall);
            repaint();
        }
    }

    public void setSelectionListener(Consumer<Object> l) { selectionListener = l; }

    public Object getSelected() { return selected; }

    public void setSelected(Object obj) {
        selected = obj;
        if (selectionListener != null) selectionListener.accept(selected);
        repaint();
    }
    public void setStatusListener(Consumer<String> l) { statusListener = l; }
    public void setToolListener(Consumer<Tool> l) { toolListener = l; }

    public void undo() {
        if (undoStack.isEmpty()) return;
        plan = PlanIO.fromText(undoStack.pop());
        selected = null;
        chain.clear();
        input.setLength(0);
        repaint();
    }

    public void deleteSelected() {
        if (selected == null) return;
        pushUndo();
        if (selected instanceof Wall w) plan.removeWall(w);
        else if (selected instanceof Room r) plan.removeRoom(r);
        else if (selected instanceof Opening o) plan.removeOpening(o);
        else if (selected instanceof Furniture f) plan.removeFurniture(f);
        selected = null;
        repaint();
    }

    public void recolorSelected() {
        int current;
        if (selected instanceof Room r) current = r.getFillRgb();
        else if (selected instanceof Furniture f) current = f.getRgb();
        else {
            JOptionPane.showMessageDialog(this, "'선택' 도구로 색을 바꿀 방이나 가구를 먼저 클릭하세요.");
            return;
        }
        Color c = JColorChooser.showDialog(this, "색상", new Color(current));
        if (c == null) return;
        pushUndo();
        if (selected instanceof Room r) r.setFillRgb(c.getRGB());
        else if (selected instanceof Furniture f) f.setRgb(c.getRGB());
        repaint();
    }

    public void renameSelected() {
        String current;
        if (selected instanceof Room r) current = r.getName();
        else if (selected instanceof Furniture f) current = f.getName();
        else {
            JOptionPane.showMessageDialog(this, "'선택' 도구로 이름을 바꿀 방이나 가구를 먼저 클릭하세요.");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "이름", current);
        if (name == null || name.isBlank()) return;
        pushUndo();
        if (selected instanceof Room r) r.setName(name.trim());
        else if (selected instanceof Furniture f) f.setName(name.trim());
        repaint();
    }

    public Furniture.Type getFurnitureType() { return furnitureType; }

    /**
     * 카탈로그에서 누르면 호출: 화면 중앙(과 가장 가까운 겹치지 않는 빈 자리)에 가구를 바로 추가하고 선택한다.
     * 이어서 선택 도구로 드래그해서 옮기거나 R로 돌릴 수 있다.
     */
    public Furniture addFurnitureAtCenter(Furniture.Type type) {
        Point2D center = screenToWorld(getWidth() / 2.0, getHeight() / 2.0);
        double baseX = gridSnap ? snap(center.x(), 100) : center.x();
        double baseY = gridSnap ? snap(center.y(), 100) : center.y();

        double step = 500;
        double bestX = baseX, bestY = baseY;
        Furniture probe = new Furniture(type, baseX, baseY);
        search:
        for (int ring = 0; ring <= 8; ring++) {                // 중앙부터 바깥으로 한 겹씩
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;
                    probe.setX(baseX + dx * step);
                    probe.setY(baseY + dy * step);
                    if (!plan.collides(probe)) {
                        bestX = probe.getX();
                        bestY = probe.getY();
                        break search;
                    }
                }
            }
        }

        pushUndo();
        setTool(Tool.SELECT);                                  // (도구를 바꾸면 선택이 지워지므로 그 다음에 선택)
        Furniture placed = new Furniture(type, bestX, bestY);
        plan.addFurniture(placed);
        selected = placed;
        repaint();
        return placed;
    }

    /** 외부(쇼핑 추천 등)에서 커스텀 치수/이름으로 생성된 가구를 중앙에 배치하고 선택한다. */
    public Furniture addCustomFurnitureAtCenter(Furniture custom) {
        Point2D center = screenToWorld(getWidth() / 2.0, getHeight() / 2.0);
        double baseX = gridSnap ? snap(center.x(), 100) : center.x();
        double baseY = gridSnap ? snap(center.y(), 100) : center.y();

        custom.setX(baseX);
        custom.setY(baseY);

        pushUndo();
        setTool(Tool.SELECT);
        plan.addFurniture(custom);
        selected = custom;
        repaint();
        return custom;
    }

    /** 가구 배치 도구로 전환하고, 놓을 가구 종류를 정한다. */
    public void setFurnitureType(Furniture.Type type) {
        furnitureType = type;
        ensureGhost();
        setTool(Tool.FURNITURE);
    }

    /** 가구 배치 중이면 미리보기를, 아니면 선택된 가구를 90도 돌린다. */
    public void rotateSelected() {
        if (tool == Tool.FURNITURE && ghostFurniture != null) {
            ghostFurniture.rotate90();
            repaint();
        } else if (selected instanceof Furniture f) {
            pushUndo();
            f.rotate90();
            repaint();
        }
    }

    /** 선택된 벽이나 가구의 크기/길이(mm)를 입력창으로 바꾼다. */
    public void resizeSelected() {
        if (selected instanceof Wall w) {
            JSpinner lenSpin = new JSpinner(new SpinnerNumberModel((int) Math.round(w.length()), 50, 50000, 100));
            JSpinner thickSpin = new JSpinner(new SpinnerNumberModel((int) Math.round(w.thickness()), 50, 1000, 10));
            JSpinner hSpin = new JSpinner(new SpinnerNumberModel((int) Math.round(w.height()), 500, 5000, 100));
            JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
            panel.add(new JLabel("벽 길이 (mm)")); panel.add(lenSpin);
            panel.add(new JLabel("벽 두께 (mm)")); panel.add(thickSpin);
            panel.add(new JLabel("벽 높이 (mm)")); panel.add(hSpin);
            int ok = JOptionPane.showConfirmDialog(this, panel, "벽 치수 수정",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            pushUndo();
            double newLen = ((Number) lenSpin.getValue()).doubleValue();
            double newThick = ((Number) thickSpin.getValue()).doubleValue();
            double newH = ((Number) hSpin.getValue()).doubleValue();
            double angle = w.angle();
            Point2D newEnd = new Point2D(w.start().x() + Math.cos(angle) * newLen,
                                         w.start().y() + Math.sin(angle) * newLen);
            Wall newWall = new Wall(w.start(), newEnd, newThick, newH);
            plan.replaceWall(w, newWall);
            this.wallThickness = newThick;
            this.wallHeight = newH;
            setSelected(newWall);
            repaint();
            return;
        }
        if (!(selected instanceof Furniture f)) {
            JOptionPane.showMessageDialog(this, "'선택' 도구로 크기/길이를 바꿀 벽이나 가구를 먼저 클릭하세요.");
            return;
        }
        JSpinner w = new JSpinner(new SpinnerNumberModel((int) f.getWidth(), 100, 6000, 50));
        JSpinner d = new JSpinner(new SpinnerNumberModel((int) f.getDepth(), 100, 6000, 50));
        JSpinner h = new JSpinner(new SpinnerNumberModel((int) f.getHeight(), 100, 4000, 50));
        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        panel.add(new JLabel("가로 (mm)")); panel.add(w);
        panel.add(new JLabel("깊이 (mm)")); panel.add(d);
        panel.add(new JLabel("높이 (mm)")); panel.add(h);
        int ok = JOptionPane.showConfirmDialog(this, panel, f.getName() + " 크기",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        pushUndo();
        f.setWidth(((Number) w.getValue()).doubleValue());
        f.setDepth(((Number) d.getValue()).doubleValue());
        f.setHeight(((Number) h.getValue()).doubleValue());
        repaint();
    }

    /** 도면 전체가 화면에 들어오도록 확대/이동. */
    public void zoomExtents() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean any = false;
        for (Wall w : plan.getWalls()) {
            for (Point2D p : new Point2D[]{w.start(), w.end()}) {
                minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
                minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
                any = true;
            }
        }
        if (!any) {
            scale = 0.1; offX = -1000; offY = -1000;
            repaint();
            return;
        }
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) { pendingFit = true; return; }   // 아직 화면에 안 나왔으면 나중에
        double bw = Math.max(maxX - minX, 1), bh = Math.max(maxY - minY, 1);
        scale = clamp(Math.min(w * 0.8 / bw, h * 0.8 / bh), 0.005, 2);
        offX = minX - (w / scale - bw) / 2;
        offY = minY - (h / scale - bh) / 2;
        pendingFit = false;
        repaint();
    }

    // =====================================================================
    // 좌표 변환
    // =====================================================================

    private Point2D screenToWorld(double mx, double my) {
        return new Point2D(mx / scale + offX, my / scale + offY);
    }

    private double sx(double wx) { return (wx - offX) * scale; }
    private double sy(double wy) { return (wy - offY) * scale; }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // =====================================================================
    // 점 보정: 끝점 스냅 → 직교 → 그리드 스냅  (미리보기와 클릭이 같은 함수를 쓴다)
    // =====================================================================

    private double gridStep() {
        double step = 500;
        while (step * scale < 10) step *= 2;
        while (step * scale > 120 && step > 50) step /= 2;
        return step;
    }

    private static double snap(double v, double step) {
        return Math.round(v / step) * step;
    }

    private Point2D lastPoint() {
        return chain.isEmpty() ? null : chain.get(chain.size() - 1);
    }

    private Point2D resolvePoint(double mx, double my) {
        Point2D p = screenToWorld(mx, my);
        snapKind = SnapKind.NONE;
        if (objectSnap) {
            Point2D hit = findEndpointNear(p);
            if (hit != null) {
                snapKind = SnapKind.END;
                return hit;
            }
        }
        double step = gridStep();
        Point2D start = lastPoint();
        if (ortho && start != null && tool == Tool.WALL) {
            double dx = Math.abs(p.x() - start.x()), dy = Math.abs(p.y() - start.y());
            if (dx >= dy) return new Point2D(gridSnap ? snap(p.x(), step) : p.x(), start.y());
            return new Point2D(start.x(), gridSnap ? snap(p.y(), step) : p.y());
        }
        if (gridSnap) {
            snapKind = SnapKind.GRID;
            return new Point2D(snap(p.x(), step), snap(p.y(), step));
        }
        return p;
    }

    private Point2D findEndpointNear(Point2D p) {
        double best = SNAP_RADIUS_PX / scale;
        Point2D found = null;
        for (Wall w : plan.getWalls()) {
            for (Point2D q : new Point2D[]{w.start(), w.end()}) {
                double d = q.distanceTo(p);
                if (d < best) { best = d; found = q; }
            }
        }
        for (Point2D q : chain) {
            double d = q.distanceTo(p);
            if (d < best) { best = d; found = q; }
        }
        return found;
    }

    // =====================================================================
    // 편집 동작
    // =====================================================================

    private void pushUndo() {
        undoStack.push(PlanIO.toText(plan));
        while (undoStack.size() > UNDO_LIMIT) undoStack.removeLast();
    }

    /** 점 하나를 확정한다. 첫 점이면 시작점, 아니면 벽 생성. 첫 점으로 되돌아오면 방을 만든다. */
    private void commitPoint(Point2D p) {
        Point2D start = lastPoint();
        if (start == null) {
            chain.add(p);
            return;
        }
        if (p.distanceTo(start) < 1) return;   // 길이 0인 벽 방지
        pushUndo();
        plan.addWall(new Wall(start, p, wallThickness, wallHeight));
        if (chain.size() >= 3 && p.distanceTo(chain.get(0)) < 1) {
            int n = roomCounter++;
            plan.addRoom(new Room("방 " + n, PALETTE[(n - 1) % PALETTE.length], new ArrayList<>(chain)));
            chain.clear();
        } else {
            chain.add(p);
        }
    }

    private void finishChain() {
        chain.clear();
        input.setLength(0);
    }

    /** 입력해 둔 숫자(mm)만큼, 마우스가 가리키는 방향으로 벽을 만든다. */
    private void applyTypedLength() {
        Point2D start = lastPoint();
        if (start == null || cursor == null || input.length() == 0) return;
        double len;
        try {
            len = Double.parseDouble(input.toString());
        } catch (NumberFormatException ex) {
            input.setLength(0);
            return;
        }
        double dx = cursor.x() - start.x(), dy = cursor.y() - start.y();
        double d = Math.hypot(dx, dy);
        input.setLength(0);
        if (d < 1e-6 || len <= 0) return;
        commitPoint(new Point2D(start.x() + dx / d * len, start.y() + dy / d * len));
    }

    private static double distToSegment(Point2D p, Point2D a, Point2D b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : clamp(((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / len2, 0, 1);
        return p.distanceTo(new Point2D(a.x() + dx * t, a.y() + dy * t));
    }

    private Object pick(Point2D p) {
        double tol = 8 / scale;
        for (Opening o : plan.getOpenings()) {
            Wall w = o.wall();
            if (distToSegment(p, w.pointAt(o.offset()), w.pointAt(o.endOffset())) <= Math.max(tol, w.thickness() / 2)) {
                return o;
            }
        }
        List<Furniture> items = plan.getFurniture();
        for (int i = items.size() - 1; i >= 0; i--) {   // 나중에 놓은(위에 그려진) 가구부터
            if (items.get(i).contains(p)) return items.get(i);
        }
        for (Wall w : plan.getWalls()) {
            if (w.distanceTo(p) <= Math.max(tol, w.thickness() / 2)) return w;
        }
        List<Room> rooms = plan.getRooms();
        for (int i = rooms.size() - 1; i >= 0; i--) {
            if (rooms.get(i).contains(p)) return rooms.get(i);
        }
        return null;
    }

    private Wall nearestWall(Point2D p) {
        double tol = 12 / scale;
        Wall best = null;
        double bestD = Double.MAX_VALUE;
        for (Wall w : plan.getWalls()) {
            double d = w.distanceTo(p);
            if (d <= Math.max(tol, w.thickness() / 2) && d < bestD) { best = w; bestD = d; }
        }
        return best;
    }

    private void ensureGhost() {
        if (ghostFurniture != null && ghostFurniture.getType() == furnitureType) return;
        double rotation = ghostFurniture == null ? 0 : ghostFurniture.getRotation();   // 종류를 바꿔도 방향은 유지
        ghostFurniture = new Furniture(furnitureType, 0, 0);
        ghostFurniture.setRotation(rotation);
    }

    /** 가구는 100mm 단위로 붙는다 (그리드 스냅이 켜져 있을 때). */
    private Point2D placePoint(double mx, double my) {
        Point2D p = screenToWorld(mx, my);
        return gridSnap ? new Point2D(snap(p.x(), 100), snap(p.y(), 100)) : p;
    }

    private void updateGhost(double mx, double my) {
        ghost = null;
        if (tool == Tool.FURNITURE) {
            ensureGhost();
            Point2D p = placePoint(mx, my);
            ghostFurniture.setX(p.x());
            ghostFurniture.setY(p.y());
            return;
        }
        if (tool != Tool.DOOR && tool != Tool.WINDOW) return;
        Point2D p = screenToWorld(mx, my);
        Wall wall = nearestWall(p);
        if (wall == null) return;
        Opening o = Opening.place(tool == Tool.DOOR ? Opening.Type.DOOR : Opening.Type.WINDOW, wall, wall.alongOf(p));
        if (o == null) return;
        for (Opening other : plan.openingsOf(wall)) {
            if (o.overlaps(other)) return;
        }
        ghost = o;
    }

    // =====================================================================
    // 입력 연결
    // =====================================================================

    private void installMouse() {
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    panning = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (tool == Tool.FURNITURE) setTool(Tool.SELECT);
                    else if (tool == Tool.WALL && !chain.isEmpty()) finishChain();
                    else selected = null;
                    repaint();
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                switch (tool) {
                    case WALL -> commitPoint(resolvePoint(e.getX(), e.getY()));
                    case SELECT -> {
                        Point2D w = screenToWorld(e.getX(), e.getY());
                        // 이미 선택된 벽의 끝점 핸들을 클릭했는지 먼저 확인
                        if (selected instanceof Wall selWall) {
                            double tol = 12.0 / scale;
                            if (w.distanceTo(selWall.start()) <= tol) {
                                draggingWall = selWall;
                                dragWallHandle = 0;
                                dragSnapshot = PlanIO.toText(plan);
                                dragMoved = false;
                                return;
                            } else if (w.distanceTo(selWall.end()) <= tol) {
                                draggingWall = selWall;
                                dragWallHandle = 1;
                                dragSnapshot = PlanIO.toText(plan);
                                dragMoved = false;
                                return;
                            }
                        }
                        setSelected(pick(w));
                        if (selected instanceof Furniture f) {   // 가구는 잡고 끌 수 있다
                            dragging = f;
                            dragOffset = new Point2D(f.getX() - w.x(), f.getY() - w.y());
                            dragSnapshot = PlanIO.toText(plan);
                            dragMoved = false;
                        }
                    }
                    case FURNITURE -> {
                        updateGhost(e.getX(), e.getY());
                        pushUndo();
                        Furniture placed = new Furniture(furnitureType, ghostFurniture.getX(), ghostFurniture.getY());
                        placed.setRotation(ghostFurniture.getRotation());
                        plan.addFurniture(placed);   // 계속 놓을 수 있도록 도구는 유지
                    }
                    case DOOR, WINDOW -> {
                        updateGhost(e.getX(), e.getY());
                        if (ghost != null) {
                            pushUndo();
                            plan.addOpening(ghost);
                            selected = ghost;
                            ghost = null;
                        }
                    }
                }
                repaint();
            }

            @Override public void mouseReleased(MouseEvent e) {
                dragging = null;
                draggingWall = null;
                dragWallHandle = -1;
                if (panning) {
                    panning = false;
                    setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (panning) {
                    offX -= (e.getX() - lastMouseX) / scale;
                    offY -= (e.getY() - lastMouseY) / scale;
                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    repaint();
                } else if (draggingWall != null) {
                    Point2D p = resolvePoint(e.getX(), e.getY());
                    Point2D newStart = (dragWallHandle == 0) ? p : draggingWall.start();
                    Point2D newEnd = (dragWallHandle == 1) ? p : draggingWall.end();
                    if (newStart.distanceTo(newEnd) >= 10) {
                        if (!dragMoved) {
                            undoStack.push(dragSnapshot);
                            dragMoved = true;
                        }
                        Wall updated = new Wall(newStart, newEnd, draggingWall.thickness(), draggingWall.height());
                        plan.replaceWall(draggingWall, updated);
                        draggingWall = updated;
                        setSelected(updated);
                        repaint();
                    }
                } else if (dragging != null) {
                    Point2D w = screenToWorld(e.getX(), e.getY());
                    double nx = w.x() + dragOffset.x(), ny = w.y() + dragOffset.y();
                    if (gridSnap) { nx = snap(nx, 100); ny = snap(ny, 100); }
                    if (nx != dragging.getX() || ny != dragging.getY()) {
                        if (!dragMoved) {              // 실제로 움직였을 때만 실행 취소 기록
                            undoStack.push(dragSnapshot);
                            dragMoved = true;
                        }
                        dragging.setX(nx);
                        dragging.setY(ny);
                        repaint();
                    }
                } else {
                    mouseMoved(e);
                }
            }

            @Override public void mouseMoved(MouseEvent e) {
                cursor = resolvePoint(e.getX(), e.getY());
                updateGhost(e.getX(), e.getY());
                fireStatus();
                repaint();
            }

            @Override public void mouseExited(MouseEvent e) {
                cursor = null;
                ghost = null;
                repaint();
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                Point2D before = screenToWorld(e.getX(), e.getY());
                scale = clamp(scale * Math.pow(1.15, -e.getPreciseWheelRotation()), 0.005, 3);
                offX = before.x() - e.getX() / scale;   // 마우스 아래 점이 제자리에 있도록
                offY = before.y() - e.getY() / scale;
                cursor = resolvePoint(e.getX(), e.getY());
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
    }

    private void installKeys() {
        // 길이 숫자 입력: 숫자/점/백스페이스/Enter
        addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) {
                if (tool != Tool.WALL || lastPoint() == null) return;
                char c = e.getKeyChar();
                if (Character.isDigit(c) || c == '.') {
                    input.append(c);
                } else if (c == '\b') {
                    if (input.length() > 0) input.setLength(input.length() - 1);
                } else if (c == '\n' || c == '\r') {
                    applyTypedLength();
                }
                repaint();
            }
        });

        // 창 어디에 포커스가 있어도 동작하는 키
        bind(JComponent.WHEN_IN_FOCUSED_WINDOW, "ESCAPE", () -> {
            finishChain();
            selected = null;
            if (tool == Tool.FURNITURE) setTool(Tool.SELECT);
            else repaint();
        });
        bind(JComponent.WHEN_IN_FOCUSED_WINDOW, "F8", () -> { ortho = !ortho; fireStatus(); repaint(); });
        bind(JComponent.WHEN_IN_FOCUSED_WINDOW, "F9", () -> { gridSnap = !gridSnap; fireStatus(); repaint(); });
        bind(JComponent.WHEN_IN_FOCUSED_WINDOW, "F3", () -> { objectSnap = !objectSnap; fireStatus(); repaint(); });
        bind(JComponent.WHEN_IN_FOCUSED_WINDOW, "control Z", this::undo);

        // 캔버스에 포커스가 있을 때만 동작하는 키 (스피너 같은 입력창과 충돌 방지)
        bind(JComponent.WHEN_FOCUSED, "DELETE", this::deleteSelected);
        bind(JComponent.WHEN_FOCUSED, "HOME", this::zoomExtents);
        for (Tool t : Tool.values()) {
            bind(JComponent.WHEN_FOCUSED, t.key, () -> setTool(t));
        }
        bind(JComponent.WHEN_FOCUSED, "C", this::recolorSelected);
        bind(JComponent.WHEN_FOCUSED, "N", this::renameSelected);
        bind(JComponent.WHEN_FOCUSED, "R", this::rotateSelected);
    }

    private void bind(int condition, String keyStroke, Runnable action) {
        String name = "action:" + keyStroke;
        getInputMap(condition).put(KeyStroke.getKeyStroke(keyStroke), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private void fireStatus() {
        String pos = cursor == null ? "" : String.format("    X=%.0f  Y=%.0f mm", cursor.x(), cursor.y());
        statusListener.accept("도구: " + tool.label
                + "    직교(F8) " + onOff(ortho)
                + "   그리드 스냅(F9) " + onOff(gridSnap)
                + "   객체 스냅(F3) " + onOff(objectSnap) + pos);
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }

    // =====================================================================
    // 그리기
    // =====================================================================

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (pendingFit && getWidth() > 0) zoomExtents();
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawGrid(g);
        drawRooms(g);
        drawWalls(g);
        for (Opening o : plan.getOpenings()) drawOpening(g, o, false, o == selected);
        if (ghost != null) drawOpening(g, ghost, true, false);
        Set<Furniture> colliding = plan.collidingFurniture();
        for (Furniture f : plan.getFurniture()) drawFurniture(g, f, false, colliding.contains(f), f == selected);
        if (tool == Tool.FURNITURE && ghostFurniture != null && cursor != null) {
            drawFurniture(g, ghostFurniture, true, plan.collides(ghostFurniture), false);
        }
        drawSelectedWallOverlay(g);
        drawPreview(g);
        drawHud(g);
        g.dispose();
    }

    private void drawGrid(Graphics2D g) {
        double step = gridStep();
        double x1 = offX + getWidth() / scale, y1 = offY + getHeight() / scale;
        g.setStroke(new BasicStroke(1f));
        for (long i = (long) Math.floor(offX / step); i * step <= x1; i++) {
            double x = i * step;
            g.setColor(isMajor(x) ? new Color(205, 210, 220) : new Color(234, 237, 242));
            g.draw(new Line2D.Double(sx(x), 0, sx(x), getHeight()));
        }
        for (long i = (long) Math.floor(offY / step); i * step <= y1; i++) {
            double y = i * step;
            g.setColor(isMajor(y) ? new Color(205, 210, 220) : new Color(234, 237, 242));
            g.draw(new Line2D.Double(0, sy(y), getWidth(), sy(y)));
        }
    }

    private static boolean isMajor(double v) {
        return Math.abs(Math.rint(v / 1000) * 1000 - v) < 1e-6;   // 1m마다 진한 선
    }

    private Path2D.Double screenPath(List<Point2D> pts) {
        Path2D.Double path = new Path2D.Double();
        boolean first = true;
        for (Point2D p : pts) {
            if (first) path.moveTo(sx(p.x()), sy(p.y()));
            else path.lineTo(sx(p.x()), sy(p.y()));
            first = false;
        }
        path.closePath();
        return path;
    }

    private void drawRooms(Graphics2D g) {
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        FontMetrics fm = g.getFontMetrics();
        for (Room r : plan.getRooms()) {
            Path2D.Double path = screenPath(r.getCorners());
            Color c = new Color(r.getFillRgb());
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 170));
            g.fill(path);
            if (r == selected) {
                g.setColor(SELECT_COLOR);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
                g.draw(path);
            }
            Point2D ctr = r.centroid();
            String l1 = r.getName();
            String l2 = String.format("%.1f m²", r.area() / 1_000_000.0);
            g.setColor(new Color(40, 40, 50));
            g.drawString(l1, (float) (sx(ctr.x()) - fm.stringWidth(l1) / 2.0), (float) sy(ctr.y()));
            g.drawString(l2, (float) (sx(ctr.x()) - fm.stringWidth(l2) / 2.0), (float) (sy(ctr.y()) + fm.getHeight()));
        }
    }

    private void drawWalls(Graphics2D g) {
        for (Wall w : plan.getWalls()) {
            float px = (float) Math.max(2, w.thickness() * scale);
            g.setStroke(new BasicStroke(px, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g.setColor(w == selected ? SELECT_COLOR : WALL_COLOR);
            g.draw(new Line2D.Double(sx(w.start().x()), sy(w.start().y()), sx(w.end().x()), sy(w.end().y())));
        }
    }

    /** 선택된 벽의 끝점 핸들과 치수 라벨을 문/가구 위에 최상단으로 그린다. */
    private void drawSelectedWallOverlay(Graphics2D g) {
        if (!(selected instanceof Wall w)) return;

        double r = 6;
        g.setColor(SELECT_COLOR);
        g.fill(new Ellipse2D.Double(sx(w.start().x()) - r, sy(w.start().y()) - r, r * 2, r * 2));
        g.fill(new Ellipse2D.Double(sx(w.end().x()) - r, sy(w.end().y()) - r, r * 2, r * 2));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Ellipse2D.Double(sx(w.start().x()) - r, sy(w.start().y()) - r, r * 2, r * 2));
        g.draw(new Ellipse2D.Double(sx(w.end().x()) - r, sy(w.end().y()) - r, r * 2, r * 2));

        // 벽 중앙에 길이 치수 텍스트 표시
        Point2D mid = w.pointAt(w.length() / 2.0);
        String dim = String.format("%,d mm (두께 %,d mm)", Math.round(w.length()), Math.round(w.thickness()));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(dim);
        g.setColor(new Color(255, 255, 255, 240));
        g.fillRect((int) sx(mid.x()) - tw / 2 - 4, (int) sy(mid.y()) - fm.getAscent() - 2, tw + 8, fm.getHeight() + 4);
        g.setColor(new Color(20, 20, 25));
        g.setStroke(new BasicStroke(1f));
        g.drawRect((int) sx(mid.x()) - tw / 2 - 4, (int) sy(mid.y()) - fm.getAscent() - 2, tw + 8, fm.getHeight() + 4);
        g.setColor(SELECT_COLOR);
        g.drawString(dim, (float) (sx(mid.x()) - tw / 2.0), (float) sy(mid.y()));
    }

    private void drawOpening(Graphics2D g, Opening o, boolean isGhost, boolean isSelected) {
        Wall w = o.wall();
        Point2D a = w.pointAt(o.offset()), b = w.pointAt(o.endOffset());
        double ax = sx(a.x()), ay = sy(a.y()), bx = sx(b.x()), by = sy(b.y());
        double len = Math.hypot(bx - ax, by - ay);
        if (len < 1) return;
        double ux = (bx - ax) / len, uy = (by - ay) / len, nx = -uy, ny = ux;
        float px = (float) Math.max(2, w.thickness() * scale);

        // 벽을 흰색으로 지워서 개구부를 만든다
        g.setStroke(new BasicStroke(px + 2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(Color.WHITE);
        g.draw(new Line2D.Double(ax, ay, bx, by));

        Color ink = isSelected ? SELECT_COLOR
                : isGhost ? new Color(240, 130, 0)
                : o.type() == Opening.Type.DOOR ? new Color(139, 90, 43) : new Color(30, 110, 200);
        g.setColor(ink);
        g.setStroke(new BasicStroke(1.4f));
        if (o.type() == Opening.Type.WINDOW) {
            for (double k : new double[]{-0.5, 0, 0.5}) {
                double off = k * px;
                g.draw(new Line2D.Double(ax + nx * off, ay + ny * off, bx + nx * off, by + ny * off));
            }
        } else {
            g.draw(new Line2D.Double(ax, ay, ax + nx * len, ay + ny * len));   // 문짝
            Path2D.Double arc = new Path2D.Double();                           // 문이 열리는 궤적
            for (int i = 0; i <= 16; i++) {
                double t = Math.PI / 2 * i / 16;
                double x = ax + len * (Math.cos(t) * ux + Math.sin(t) * nx);
                double y = ay + len * (Math.cos(t) * uy + Math.sin(t) * ny);
                if (i == 0) arc.moveTo(x, y); else arc.lineTo(x, y);
            }
            g.draw(arc);
        }
    }

    /** 가구를 위에서 본 모습: 회전된 사각형 + 앞면(굵은 선) + 이름. 겹치면 빨간색. */
    private void drawFurniture(Graphics2D g, Furniture f, boolean isGhost, boolean bad, boolean isSelected) {
        List<Point2D> c = f.corners();
        Path2D.Double path = screenPath(c);
        Color base = new Color(f.getRgb());
        int alpha = isGhost ? 110 : 215;
        g.setColor(bad ? new Color(235, 70, 70, alpha) : new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
        g.fill(path);
        g.setStroke(new BasicStroke(isSelected ? 2.5f : 1.2f));
        g.setColor(isSelected ? SELECT_COLOR : bad ? new Color(170, 0, 0) : new Color(40, 40, 50));
        g.draw(path);

        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));   // 앞면 표시
        g.setColor(new Color(20, 20, 30, 210));
        g.draw(new Line2D.Double(sx(c.get(0).x()), sy(c.get(0).y()), sx(c.get(1).x()), sy(c.get(1).y())));

        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g.getFontMetrics();
        String text = f.getName();
        double luminance = 0.299 * base.getRed() + 0.587 * base.getGreen() + 0.114 * base.getBlue();
        g.setColor(bad || luminance < 140 ? Color.WHITE : new Color(20, 20, 30));
        g.drawString(text, (float) (sx(f.getX()) - fm.stringWidth(text) / 2.0), (float) (sy(f.getY()) + fm.getAscent() / 2.0 - 1));
    }

    private void drawPreview(Graphics2D g) {
        if (tool != Tool.WALL) return;
        Point2D start = lastPoint();

        g.setColor(new Color(90, 90, 100));
        g.setStroke(new BasicStroke(1f));
        for (Point2D p : chain) {
            g.fill(new java.awt.geom.Ellipse2D.Double(sx(p.x()) - 3, sy(p.y()) - 3, 6, 6));
        }

        if (start != null && cursor != null) {
            g.setColor(Color.GRAY);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 6f}, 0f));
            g.draw(new Line2D.Double(sx(start.x()), sy(start.y()), sx(cursor.x()), sy(cursor.y())));

            String text = input.length() > 0
                    ? "길이 입력: " + input + " mm  (Enter)"
                    : String.format("%.0f mm", start.distanceTo(cursor));
            g.setColor(new Color(30, 30, 30));
            g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            g.drawString(text, (float) sx(cursor.x()) + 12, (float) sy(cursor.y()) - 10);
        }

        if (cursor != null && snapKind != SnapKind.NONE) {
            double x = sx(cursor.x()), y = sy(cursor.y());
            g.setStroke(new BasicStroke(1.5f));
            if (snapKind == SnapKind.END) {
                g.setColor(new Color(230, 150, 0));
                g.draw(new Line2D.Double(x - 6, y - 6, x + 6, y - 6));
                g.draw(new Line2D.Double(x + 6, y - 6, x + 6, y + 6));
                g.draw(new Line2D.Double(x + 6, y + 6, x - 6, y + 6));
                g.draw(new Line2D.Double(x - 6, y + 6, x - 6, y - 6));
            } else {
                g.setColor(new Color(120, 120, 130));
                g.draw(new Line2D.Double(x - 4, y, x + 4, y));
                g.draw(new Line2D.Double(x, y - 4, x, y + 4));
            }
        }
    }

    private void drawHud(Graphics2D g) {
        g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        g.setColor(new Color(90, 90, 100));
        String hint = switch (tool) {
            case WALL -> "클릭: 점 지정 · 시작점으로 돌아오면 방 자동 생성 · 숫자+Enter: 길이 입력 · 우클릭/ESC: 종료";
            case SELECT -> "클릭: 선택 · 가구는 드래그로 이동 · R: 회전 · C: 색상 · N: 이름 · Delete: 삭제";
            case DOOR -> "벽 위에 마우스를 올리고 클릭하면 문이 놓입니다";
            case WINDOW -> "벽 위에 마우스를 올리고 클릭하면 창문이 놓입니다";
            case FURNITURE -> "클릭: 가구 배치 · R: 90도 회전 · 굵은 선이 앞면 · 빨강: 겹침 · 우클릭/ESC: 끝내기";
        };
        g.drawString(hint, 10, 18);
        g.drawString("휠: 확대/축소 · 휠 버튼 드래그: 이동 · Home: 전체 보기 · Ctrl+Z: 실행 취소 · L/V/D/W/F: 도구 전환", 10, getHeight() - 10);
    }
}
