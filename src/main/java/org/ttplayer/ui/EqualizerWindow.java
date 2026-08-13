package org.ttplayer.ui;

import org.ttplayer.controls.TtButton;
import org.ttplayer.controls.TtHSlider;
import org.ttplayer.controls.TtVSlider;
import org.ttplayer.engine.Equalizer;
import org.ttplayer.engine.EqualizerConfig;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.skin.TtSkin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class EqualizerWindow extends SkinWindow {

    public TtButton btnEnabled, btnProfile, btnReset;
    public TtButton btnClose;
    public TtHSlider balanceSlider, surroundSlider;
    public TtVSlider preampSlider;
    public TtVSlider[] eqSliders;

    private PlayerEngine playerEngine;
    private int categoryIndex = -1;   // 当前应用的可选类别索引（-1 无）

    // ============ 预设类别（原版类别名 → 10 段增益 dB） ============
    private static final String[] CATEGORY_NAMES = {
        "流行音乐", "摇滚", "金属乐", "舞曲", "电子乐", "乡村音乐", "爵士乐",
        "古典乐", "布鲁斯", "怀旧音乐", "歌剧", "语音", "环绕"
    };
    private static final double[][] CATEGORY_PRESETS = {
        // 60   170  310  600  1K   3K   6K   12K  14K  16K
        {-1,   2,   0,   0,   1,   1,   0,   0,   0,   0 },  // 流行音乐
        { 4,   2,   0,  -2,  -1,   2,   3,   3,   3,   3 },  // 摇滚
        { 5,   3,   0,  -1,  -1,   1,   3,   4,   4,   4 },  // 金属乐
        { 5,   3,   1,   0,   0,  -1,  -1,  -1,  -2,  -2 },  // 舞曲
        { 4,   2,   0,  -1,   0,   1,   2,   3,   3,   4 },  // 电子乐
        { 0,   0,   0,   1,   1,   1,  -1,  -1,   0,  -1 },  // 乡村音乐
        { 2,   1,  -1,  -1,  -1,   0,   1,   1,   2,   2 },  // 爵士乐
        { 3,   2,   1,   0,  -1,  -1,   0,   0,   1,   1 },  // 古典乐
        { 4,   3,   1,   0,  -1,  -1,   0,   1,   1,   2 },  // 布鲁斯
        { 1,   1,   0,   0,   0,  -1,  -1,   0,   0,   0 },  // 怀旧音乐
        { 2,   1,   0,   0,  -1,   0,   1,   1,   2,   2 },  // 歌剧
        {-2,  -2,  -1,   0,   2,   3,   2,   0,  -1,  -2 },  // 语音
        { 4,   2,   0,   0,   0,   0,   0,  -1,  -1,  -2 }   // 环绕
    };

    public EqualizerWindow(TtSkin skin) {
        this(skin, null);
    }

    public EqualizerWindow(TtSkin skin, PlayerEngine engine) {
        super(skin, findWindow(skin, "equalizer_window"), false);
        this.playerEngine = engine;
        setTitle(org.ttplayer.util.Messages.get("menu.equalizer"));
        TtSkin.WindowDef def = findWindow(skin, "equalizer_window");
        if (def != null && def.width > 0 && def.height > 0) {
            setMinSize(def.width, def.height);
        } else {
            setMinSize(268, 165);
        }
    }

    public void setPlayerEngine(PlayerEngine engine) { this.playerEngine = engine; }

    @Override
    protected void buildControls() {
        eqSliders = new TtVSlider[10];

        for (TtSkin.Ctl c : def.elements) {
            switch (c.tag) {
                case "title":
                    createTitleImage(c);
                    break;
                case "close":
                    btnClose = createButton(c);
                    if (btnClose != null) {
                        btnClose.addActionListener(e -> setVisible(false));
                    }
                    break;
                case "enabled":
                    btnEnabled = createButton(c);
                    break;
                case "profile":
                    btnProfile = createButton(c);
                    break;
                case "reset":
                    btnReset = createButton(c);
                    break;
                case "balance":
                    createBalanceSlider(c);
                    break;
                case "surround":
                    createSurroundSlider(c);
                    break;
                case "preamp":
                    createPreampSlider(c);
                    break;
                case "eqfactor":
                    createEqSliders(c);
                    break;
            }
        }
        bindBehaviors();
    }

    /** 绑定启用开关与滑块 → 均衡器 */
    private void bindBehaviors() {
        final Equalizer eq = (playerEngine != null) ? playerEngine.getEqualizer() : new Equalizer();

        // ---- 载入上次保存的配置 ----
        EqualizerConfig.State state = EqualizerConfig.load();
        eq.setEnabled(state.enabled);
        eq.setDolbySurround(state.dolby);
        eq.setPreampDb(state.preampDb);
        eq.applyPreset(state.gainsDb);
        categoryIndex = state.category;
        if (balanceSlider != null) balanceSlider.setValue(state.balance);
        if (surroundSlider != null) surroundSlider.setValue(state.surround);
        if (preampSlider != null) preampSlider.setValue((int) Math.round(50 + state.preampDb * 50.0 / Equalizer.DB_RANGE));
        if (eqSliders != null) {
            for (int i = 0; i < eqSliders.length && i < state.gainsDb.length; i++) {
                int v = (int) Math.round(50 + state.gainsDb[i] * 50.0 / Equalizer.DB_RANGE);
                eqSliders[i].setValue(Math.max(0, Math.min(100, v)));
            }
        }

        // 启用开关：点击在 启用/禁用 间切换，按下帧驻留显示
        if (btnEnabled != null) {
            btnEnabled.setSelected(eq.isEnabled());
            btnEnabled.addActionListener(e -> {
                boolean on = !eq.isEnabled();
                eq.setEnabled(on);
                btnEnabled.setSelected(on);
                saveConfig();
            });
        }

        // 10 段均衡滑块
        if (eqSliders != null && playerEngine != null) {
            for (int i = 0; i < eqSliders.length; i++) {
                final int band = i;
                eqSliders[i].setListener(v -> {
                    playerEngine.getEqualizer().setGainDb(band, Equalizer.sliderToDb(v));
                    saveConfig();
                });
            }
        }

        // preamp
        if (preampSlider != null && playerEngine != null) {
            preampSlider.setListener(v -> {
                playerEngine.getEqualizer().setPreampDb(Equalizer.sliderToDb(v));
                saveConfig();
            });
        }

        // 复位：全部滑块回中心并清空增益
        if (btnReset != null) {
            btnReset.addActionListener(e -> {
                eq.reset();
                eq.setDolbySurround(false);
                categoryIndex = -1;
                if (eqSliders != null) {
                    for (TtVSlider s : eqSliders) s.setValue(50);
                }
                if (preampSlider != null) preampSlider.setValue(50);
                saveConfig();
            });
        }


        // 右键菜单：启用均衡器 / 启用杜比环绕 / 可选类别
        final JPopupMenu menu = buildPopupMenu(eq);

        // 绑定右键：窗口内任何位置（包括背景面板）弹出
         addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                if (e.isPopupTrigger()) menu.show(getContentPane(), e.getX(), e.getY());
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                if (e.isPopupTrigger()) menu.show(getContentPane(), e.getX(), e.getY());
            }
        });
        // 子控件（滑块/按钮）上右键也响应
        if (eqSliders != null) {
            for (TtVSlider s : eqSliders) addPopup(s, menu);
        }
        if (preampSlider != null) addPopup(preampSlider, menu);
        if (balanceSlider != null) addPopup(balanceSlider, menu);
        if (surroundSlider != null) addPopup(surroundSlider, menu);
        for (Component c : getContentPane().getComponents()) {
            if (c instanceof TtButton) addPopup(c, menu);
        }
    }

    /** 收集当前状态持久化到 config/equalizer.properties */
    private void saveConfig() {
        if (playerEngine == null) return;
        int balance = balanceSlider != null ? balanceSlider.getValue() : 50;
        int surround = surroundSlider != null ? surroundSlider.getValue() : 50;
        EqualizerConfig.save(playerEngine.getEqualizer(), balance, surround, categoryIndex);
    }


    private void addPopup(java.awt.Component comp, JPopupMenu menu) {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                 super.mousePressed(e);
                if (e.isPopupTrigger()) menu.show(e.getComponent(), e.getX(), e.getY());
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                if (e.isPopupTrigger()) menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    private JPopupMenu buildPopupMenu(Equalizer eq) {
        JPopupMenu menu = new JPopupMenu();

        JCheckBoxMenuItem eqOn = new JCheckBoxMenuItem("启用均衡器", eq.isEnabled());
        eqOn.addActionListener(e -> {
            boolean on = eqOn.isSelected();
            eq.setEnabled(on);
            if (btnEnabled != null) btnEnabled.setSelected(on);
            saveConfig();
        });
        menu.add(eqOn);

        JCheckBoxMenuItem dolby = new JCheckBoxMenuItem("启用杜比环绕", eq.isDolbySurround());
        dolby.addActionListener(e -> {
            eq.setDolbySurround(dolby.isSelected());
            saveConfig();
        });
        menu.add(dolby);

        menu.addSeparator();

        JMenu categories = new JMenu("可选类别");
        ButtonGroup categoryGroup = new ButtonGroup();
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            final String name = CATEGORY_NAMES[i];
            final double[] gains = CATEGORY_PRESETS[i];
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(name);
            categoryGroup.add(item);   // 互斥单选：选中的项显示 ✓
            final int idx = i;
            item.addActionListener(e -> {
                if (item.isSelected()) applyCategory(eq, idx, gains);
            });
            categories.add(item);
            // 恢复上次选择
            if (categoryIndex == i) item.setSelected(true);
        }
        menu.add(categories);

        return menu;
    }

    /** 应用预设：重置增益 → 套用曲线 → 自动启用均衡器并同步按钮与滑块 */
    private void applyCategory(Equalizer eq, int index, double[] gains) {
        categoryIndex = index < CATEGORY_NAMES.length ? index : -1;
        eq.reset();
        eq.applyPreset(gains);
        eq.setEnabled(true);
        if (btnEnabled != null) btnEnabled.setSelected(true);
        if (eqSliders != null) {
            for (int i = 0; i < eqSliders.length && i < gains.length; i++) {
                int v = (int) Math.round(50 + gains[i] * 50.0 / Equalizer.DB_RANGE);
                eqSliders[i].setValue(Math.max(0, Math.min(100, v)));
            }
        }
        saveConfig();
    }

    private void createBalanceSlider(TtSkin.Ctl ctl) {
        byte[] thumb = skin.getBmp(ctl.thumbImage);
        balanceSlider = new TtHSlider(thumb);
        balanceSlider.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        balanceSlider.setValue(50);
        getContentPane().add(balanceSlider);
        addControl(balanceSlider, ctl);
    }

    private void createSurroundSlider(TtSkin.Ctl ctl) {
        byte[] thumb = skin.getBmp(ctl.thumbImage);
        surroundSlider = new TtHSlider(thumb);
        surroundSlider.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        surroundSlider.setValue(50);
        getContentPane().add(surroundSlider);
        addControl(surroundSlider, ctl);
    }

    private void createPreampSlider(TtSkin.Ctl ctl) {
        byte[] thumb = skin.getBmp(ctl.thumbImage);
        byte[] fill = skin.getBmp(ctl.fillImage);
        preampSlider = new TtVSlider(thumb, fill);
        preampSlider.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        preampSlider.setValue(50);
        getContentPane().add(preampSlider);
        addControl(preampSlider, ctl);
    }

    private void createEqSliders(TtSkin.Ctl ctl) {
        byte[] thumb = skin.getBmp(ctl.thumbImage);
        byte[] fill = skin.getBmp(ctl.fillImage);
        int width = ctl.right - ctl.left;
        int height = ctl.bottom - ctl.top;

        int interval = 2;
        if (def.eqInterval != null && !def.eqInterval.isEmpty()) {
            try { interval = Integer.parseInt(def.eqInterval); } catch (Exception ignored) {}
        }
        int step = width + interval;

        for (int i = 0; i < 10 && i < eqSliders.length; i++) {
            int x = ctl.left + i * step;
            eqSliders[i] = new TtVSlider(thumb, fill);
            eqSliders[i].setBounds(x, ctl.top, width, height);
            eqSliders[i].setValue(50);
            getContentPane().add(eqSliders[i]);
            addControl(eqSliders[i], ctl);
        }
    }

    static TtSkin.WindowDef findWindow(TtSkin skin, String name) {
        return MiniWindow.findWindow(skin, name);
    }
}
