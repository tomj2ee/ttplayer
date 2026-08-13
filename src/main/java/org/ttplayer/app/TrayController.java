package org.ttplayer.app;

import org.ttplayer.util.Messages;
import org.ttplayer.util.PlayerTray;

/**
 * 系统托盘控制器（从 TTPlayerApplication 拆分）。
 */
public class TrayController {

    private final WindowHub hub;
    private PlayerTray playerTray;

    public TrayController(WindowHub hub) {
        this.hub = hub;
    }

    public void init() {
        try {
            if (playerTray == null) {
                playerTray = new PlayerTray(hub.getPlayer(), hub.getPlayerEngine());
                playerTray.setOnSwitchSkin(() -> {
                    if (hub.getPlaylist() != null) {
                        hub.getPlaylist().doSkin();
                    }
                });
            } else {
                playerTray.updatePlayerWindow(hub.getPlayer());
            }
            playerTray.setOnShowMainWindow(hub::showAllWindowsFromTray);
            playerTray.setOnLanguageChanged(locale -> hub.reloadForLanguage());
        } catch (Exception e) {
            System.out.println(Messages.get("tray.initFailPrefix") + e.getMessage());
        }
    }

    public void ensureVisible() {
        if (playerTray == null) {
            init();
        }
        playerTray.ensureTrayVisible();
    }

    public void updateToolTip(String text) {
        if (playerTray != null) {
            playerTray.updateToolTip(text != null && !text.isEmpty() ? "TTPlayer - " + text : "TTPlayer");
        }
    }
}