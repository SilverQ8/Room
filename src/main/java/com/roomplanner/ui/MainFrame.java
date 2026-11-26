package com.roomplanner.ui;

import com.roomplanner.model.Furniture;
import com.roomplanner.model.Plan;
import com.roomplanner.model.PlanIO;
import com.roomplanner.model.SamplePlan;
import com.roomplanner.service.IkeaShoppingService;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** 메인 창: 상단 도구 모음(2줄) + [2D 도면 | 3D 입체] 탭 + 하단 상태줄. */
public class MainFrame extends JFrame {

    private final PlanEditor editor = new PlanEditor();
    private final Viewer3D viewer = new Viewer3D();
    private final JTabbedPane tabs = new JTabbedPane();
    private final CatalogPanel catalog = new CatalogPanel(this::onCatalogPick);
    private final JLabel statusLabel = new JLabel(" ");
    private final Map<PlanEditor.Tool, JToggleButton> toolButtons = new EnumMap<>(PlanEditor.Tool.class);
    private final IkeaShoppingService shoppingService = new IkeaShoppingService();
    private JSpinner thicknessSpinner;
    private JSpinner heightSpinner;
    private boolean syncingSpinners;

    public MainFrame() {
        super("Room Planner — 2D 설계 → 3D 입체");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);

        tabs.addTab("2D 도면", editor);
        tabs.addTab("3D 입체", viewer);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == viewer) {
                viewer.rebuild(editor.getPlan());
                statusLabel.setText("3D:  왼쪽 드래그 = 회전   ·   오른쪽 드래그 = 이동   ·   휠 = 확대/축소   ·   빨간 상자 = 겹친 가구");
            } else {
                editor.requestFocusInWindow();
            }
        });

        editor.setStatusListener(s -> {
            if (tabs.getSelectedComponent() == editor) statusLabel.setText(s);
        });
        editor.setToolListener(t -> {
            toolButtons.get(t).setSelected(true);
            catalog.setActive(t == PlanEditor.Tool.FURNITURE ? editor.getFurnitureType() : null);   // 배치 중인 종류 강조
        });
        editor.setSelectionListener(sel -> {
            if (sel instanceof com.roomplanner.model.Wall w && thicknessSpinner != null && heightSpinner != null) {
                syncingSpinners = true;
                thicknessSpinner.setValue((int) Math.round(w.thickness()));
                heightSpinner.setValue((int) Math.round(w.height()));
                syncingSpinners = false;
            }
        });

        statusLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        JPanel north = new JPanel(new GridLayout(2, 1));
        north.add(buildToolRow());
        north.add(buildActionRow());
        add(north, BorderLayout.NORTH);
        add(catalog, BorderLayout.WEST);
        add(tabs, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        catalog.setOnRecommendAction(this::openRecommendationDialog);

        toolButtons.get(PlanEditor.Tool.SELECT).setSelected(true);
        editor.setPlan(SamplePlan.create());   // 처음엔 예제를 보여 준다
    }

    /** 카탈로그 카드를 눌렀을 때: 기본은 화면 중앙에 바로 추가, Shift면 마우스로 위치 지정 모드. */
    private void onCatalogPick(Furniture.Type type, boolean placeWithMouse) {
        tabs.setSelectedIndex(0);
        if (placeWithMouse) editor.setFurnitureType(type);
        else editor.addFurnitureAtCenter(type);
        editor.requestFocusInWindow();   // 이어서 R(회전), Delete 같은 키가 바로 먹도록
    }

    public CatalogPanel getCatalog() { return catalog; }
    public PlanEditor getEditor() { return editor; }
    public Viewer3D getViewer() { return viewer; }
    public void selectTab(int index) { tabs.setSelectedIndex(index); }

    // ------------------------------------------------------------------

    /** 윗줄: 도구 선택, 가구 종류, 벽 두께/높이. */
    private JToolBar buildToolRow() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        ButtonGroup group = new ButtonGroup();
        for (PlanEditor.Tool t : PlanEditor.Tool.values()) {
            JToggleButton b = new JToggleButton(t.label + " (" + t.key + ")");
            b.setFocusable(false);   // 포커스를 도면 캔버스에 남겨 둬야 키보드 입력이 계속 동작한다
            b.addActionListener(e -> {
                tabs.setSelectedIndex(0);
                editor.setTool(t);
            });
            group.add(b);
            bar.add(b);
            toolButtons.put(t, b);
        }

        bar.addSeparator();

        bar.add(new JLabel(" 벽 두께(mm) "));
        thicknessSpinner = new JSpinner(new SpinnerNumberModel(150, 50, 600, 10));
        thicknessSpinner.addChangeListener(e -> {
            if (!syncingSpinners) editor.setWallThickness(((Number) thicknessSpinner.getValue()).doubleValue());
        });
        thicknessSpinner.setMaximumSize(thicknessSpinner.getPreferredSize());
        bar.add(thicknessSpinner);
        bar.add(new JLabel("  벽 높이(mm) "));
        heightSpinner = new JSpinner(new SpinnerNumberModel(2400, 1000, 5000, 100));
        heightSpinner.addChangeListener(e -> {
            if (!syncingSpinners) editor.setWallHeight(((Number) heightSpinner.getValue()).doubleValue());
        });
        heightSpinner.setMaximumSize(heightSpinner.getPreferredSize());
        bar.add(heightSpinner);
        return bar;
    }

    /** 아랫줄: 편집 동작과 파일. */
    private JToolBar buildActionRow() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        addButton(bar, "색상(C)", editor::recolorSelected);
        addButton(bar, "이름(N)", editor::renameSelected);
        addButton(bar, "회전(R)", editor::rotateSelected);
        addButton(bar, "크기/길이", editor::resizeSelected);
        addButton(bar, "삭제(Del)", editor::deleteSelected);
        addButton(bar, "실행 취소", editor::undo);
        addButton(bar, "전체 보기", editor::zoomExtents);
        bar.addSeparator();
        addButton(bar, "새로 만들기", () -> {
            editor.setPlan(new Plan());
            tabs.setSelectedIndex(0);
        });
        addButton(bar, "예제", () -> {
            editor.setPlan(SamplePlan.create());
            tabs.setSelectedIndex(0);
        });
        addButton(bar, "저장", this::save);
        addButton(bar, "불러오기", this::load);

        bar.addSeparator();
        JButton recBtn = new JButton("[IKEA] 가구 추천 검색");
        recBtn.setFocusable(false);
        recBtn.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        recBtn.setForeground(new Color(0x58, 0xa6, 0xff));
        recBtn.addActionListener(e -> openRecommendationDialog());
        bar.add(recBtn);

        return bar;
    }

    private void openRecommendationDialog() {
        RecommendationDialog dlg = new RecommendationDialog(this, shoppingService, f -> {
            tabs.setSelectedIndex(0);
            editor.addCustomFurnitureAtCenter(f);
            editor.requestFocusInWindow();
        });
        dlg.setVisible(true);
    }

    private void addButton(JToolBar bar, String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        bar.add(b);
    }

    private JFileChooser chooser() {
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setFileFilter(new FileNameExtensionFilter("Room Plan (*.rplan)", "rplan"));
        return fc;
    }

    private void save() {
        JFileChooser fc = chooser();
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".rplan")) f = new File(f.getPath() + ".rplan");
        try {
            PlanIO.save(editor.getPlan(), Path.of(f.getPath()));
            statusLabel.setText("저장됨: " + f.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "저장 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void load() {
        JFileChooser fc = chooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            editor.setPlan(PlanIO.load(Path.of(fc.getSelectedFile().getPath())));
            tabs.setSelectedIndex(0);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "불러오기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
        }
    }
}
