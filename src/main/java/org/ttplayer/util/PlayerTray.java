package org.ttplayer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.ui.PlayerWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public class PlayerTray {
    private static final Logger log = LoggerFactory.getLogger(PlayerTray.class);
    private TrayIcon trayIcon;
    private SystemTray tray;
    private PlayerWindow playerWindow;
    private PlayerEngine playerEngine;
    private JPopupMenu popupMenu;
    private Runnable onSwitchSkin;
    private Runnable onShowMainWindow;
    private java.util.function.Consumer<java.util.Locale> onLanguageChanged;
    // 用于触发弹出的 dummy AWT PopupMenu
    private PopupMenu dummyPopup;

    public PlayerTray(PlayerWindow playerWindow, PlayerEngine playerEngine) {
        this.playerWindow = playerWindow;
        this.playerEngine = playerEngine;
        initTray();
    }

    public void setOnSwitchSkin(Runnable onSwitchSkin) {
        this.onSwitchSkin = onSwitchSkin;
    }

    public void setOnShowMainWindow(Runnable onShowMainWindow) {
        this.onShowMainWindow = onShowMainWindow;
    }

    public void setOnLanguageChanged(java.util.function.Consumer<java.util.Locale> l) {
        this.onLanguageChanged = l;
    }

    public void updatePlayerWindow(PlayerWindow playerWindow) {
        this.playerWindow = playerWindow;
    }

    private void initTray() {
        // 检查系统是否支持托盘
        if (!SystemTray.isSupported()) {
            log.warn(org.ttplayer.util.Messages.get("tray.notSupported"));
            return;
        }

        tray = SystemTray.getSystemTray();

        // 创建 Swing 弹出菜单
        createPopupMenu();

        // 创建一个 dummy AWT PopupMenu（不会实际显示）
        dummyPopup = new PopupMenu();

        // 加载图标
        Image image = loadTrayIcon();
        if (image != null) {
            trayIcon = new TrayIcon(image, "TTPlayer", dummyPopup);
            trayIcon.setImageAutoSize(true);

            // 添加鼠标监听器来处理点击和弹出菜单
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // 左键单击显示主窗口
                    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                        showMainWindow();
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShowPopup(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShowPopup(e);
                }

                private void maybeShowPopup(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showJPopupMenu();
                    }
                }
            });

            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                log.error(org.ttplayer.util.Messages.get("tray.addFailPrefix") + e.getMessage(), e);
            }
        }
    }

    /**
     * 在鼠标位置显示 Swing JPopupMenu
     */
    private void showJPopupMenu() {
        if (popupMenu == null) {
            createPopupMenu();
        }

        // 获取鼠标的屏幕位置
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        Point mouseLoc = pointerInfo.getLocation();

        // 创建一个不可见的窗口作为 popup 的父组件
        JWindow window = new JWindow();
        window.setAlwaysOnTop(true);
        window.setSize(1, 1);
        window.setLocation(0, 0);
        window.setVisible(true);

        // 显示菜单 - 使用鼠标屏幕位置
        popupMenu.show(window, mouseLoc.x, mouseLoc.y);

        // 添加监听器，当菜单消失时 dispose window
        popupMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {}

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                window.dispose();
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                window.dispose();
            }
        });
    }

    private Image loadTrayIcon() {
        try {
            // 尝试加载图标
            ClassLoader cl = getClass().getClassLoader();
            InputStream is = cl.getResourceAsStream("ico/ttplayer_16x16_32bpp.png");
            if (is != null) {
                return javax.imageio.ImageIO.read(is);
            }
        } catch (Exception e) {
            log.error(org.ttplayer.util.Messages.get("tray.iconLoadFail"), e);
        }
        return createDefaultTrayIcon();
    }

    private Image createDefaultTrayIcon() {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x00, 0x80, 0xFF));
        g.fillOval(1, 1, size-2, size-2);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2));
        g.drawLine(size/2, 3, size/2, size-3);
        g.fillOval(size/2-4, size-6, 8, 6);
        g.drawOval(size/2+1, 5, 6, 4);
        g.dispose();
        return image;
    }

    private void createPopupMenu() {
        popupMenu = new JPopupMenu();
        java.awt.Font swingFont = FontUtils.getDefaultChineseFont(12);
        popupMenu.setFont(swingFont);
        fillMenu(popupMenu, swingFont);
    }

    /** 语言切换后重建托盘菜单（同时更新所有托盘文案的语言） */
    private void rebuildMenu() {
        createPopupMenu();
    }

    private void fillMenu(JPopupMenu popup, java.awt.Font font) {
        // 显示主窗口
        JMenuItem showItem = org.ttplayer.util.UIUtils.createMenuItem(
            org.ttplayer.util.Messages.get("tray.show"),
            org.ttplayer.util.MenuIcons.showWindow(),
            e -> { popup.setVisible(false); showMainWindow(); });
        popup.add(showItem);

        popup.addSeparator();

        // 上一首
        JMenuItem prevItem = org.ttplayer.util.UIUtils.createMenuItem(
            org.ttplayer.util.Messages.get("tooltip.prev"),
            org.ttplayer.util.MenuIcons.previous(),
            e -> { popup.setVisible(false); playPrevious(); });
        popup.add(prevItem);

        // 播放/暂停
        JMenuItem playItem = org.ttplayer.util.UIUtils.createMenuItem(
            org.ttplayer.util.Messages.get("tray.playPause"),
            org.ttplayer.util.MenuIcons.play(),
            e -> { popup.setVisible(false); togglePlayPause(); });
        popup.add(playItem);

        // 下一首
        JMenuItem nextItem = org.ttplayer.util.UIUtils.createMenuItem(
            org.ttplayer.util.Messages.get("tooltip.next"),
            org.ttplayer.util.MenuIcons.next(),
            e -> { popup.setVisible(false); playNext(); });
        popup.add(nextItem);

        popup.addSeparator();

        // 切换皮肤
        JMenuItem skinItem = org.ttplayer.util.UIUtils.createMenuItem(
            org.ttplayer.util.Messages.get("tray.skin"),
            org.ttplayer.util.MenuIcons.skin(),
            e -> { popup.setVisible(false); switchSkin(); });
        popup.add(skinItem);

        // 语言切换
        JMenu langMenu = new JMenu(org.ttplayer.util.Messages.get("menu.language"));
        langMenu.setFont(font);
        langMenu.setIcon(org.ttplayer.util.MenuIcons.language());
        for (String[] lang : org.ttplayer.util.Messages.SUPPORTED_LANGUAGES) {
            JMenuItem item = new JMenuItem(lang[1], org.ttplayer.util.Flags.iconFor(lang[0]));
            item.setFont(font);
            item.addActionListener(e -> {
                popup.setVisible(false);
                changeLanguage(org.ttplayer.util.Messages.fromTag(lang[0]));
            });
            langMenu.add(item);
        }
        popup.add(langMenu);

        popup.addSeparator();

        // 退出
        JMenuItem exitItem = org.ttplayer.util.UIUtils.createMenuItem(
            org.ttplayer.util.Messages.get("tooltip.quit"),
            org.ttplayer.util.MenuIcons.exit(),
            e -> { popup.setVisible(false); exitApplication(); });
        popup.add(exitItem);
    }

    private void changeLanguage(java.util.Locale locale) {
        org.ttplayer.util.Messages.setLocale(locale);
        org.ttplayer.util.WindowConfig.saveLanguage(locale.toString());
        rebuildMenu();
        if (onLanguageChanged != null) {
            onLanguageChanged.accept(locale);
        }
    }

    private void switchSkin() {
        if (onSwitchSkin != null) {
            onSwitchSkin.run();
        }
    }

    private void showMainWindow() {
        if (onShowMainWindow != null) {
            onShowMainWindow.run();
        } else if (playerWindow != null) {
            playerWindow.setVisible(true);
            playerWindow.setState(Frame.NORMAL);
            playerWindow.toFront();
        }
    }

    private void playPrevious() {
        if (playerEngine != null) {
            playerEngine.previous();
        }
    }

    private void togglePlayPause() {
        if (playerEngine != null) {
            playerEngine.playPause();
        }
    }

    private void playNext() {
        if (playerEngine != null) {
            playerEngine.next();
        }
    }

    private void exitApplication() {
        if (tray != null && trayIcon != null) {
            tray.remove(trayIcon);
        }
        System.exit(0);
    }

    public void updateToolTip(String text) {
        if (trayIcon != null) {
            trayIcon.setToolTip(text != null ? text : "TTPlayer");
        }
    }

    public void ensureTrayVisible() {
        if (tray == null || trayIcon == null) {
            initTray();
        } else {
            boolean found = false;
            for (TrayIcon icon : tray.getTrayIcons()) {
                if (icon == trayIcon) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                try {
                    tray.add(trayIcon);
                } catch (AWTException e) {
                    log.error(org.ttplayer.util.Messages.get("tray.retryFailPrefix") + e.getMessage(), e);
                }
            }
        }
    }

    public void remove() {
        if (tray != null && trayIcon != null) {
            tray.remove(trayIcon);
        }
    }
}
