package org.ttplayer;

import org.ttplayer.app.WindowHub;
import org.ttplayer.util.AppIcons;
import org.ttplayer.util.Messages;
import org.ttplayer.util.UIUtils;
import org.ttplayer.util.WindowConfig;

import java.awt.Image;

/**
 * 应用入口门面
 * 全部窗口/托盘/拖放/播放事件协作已拆分到 org.ttplayer.app 包：
 *   WindowHub / TrayController / FileDropController / PlayerEventListener
 */
public class TTPlayerApplication {

    private final WindowHub hub = new WindowHub();

    public void start() {
        start(new String[0]);
    }

    public void start(String[] arguments) {
        // 首先设置应用图标
        setApplicationIcon();

        // 恢复上次选择的语言
        String lang = WindowConfig.restoreLanguage();
        if (lang != null && !lang.isEmpty()) {
            Messages.setLocale(Messages.fromTag(lang));
        }

        java.util.List<java.io.File> files = new java.util.ArrayList<>();
        for (String argument : arguments) {
            java.io.File file = new java.io.File(argument);
            if (file.isFile()) files.add(file);
        }
        String savedSkin = WindowConfig.restoreSkin();
        hub.createUI((savedSkin != null && !savedSkin.isEmpty())
                ? savedSkin : WindowHub.DEFAULT_SKIN, files.isEmpty());
        if (!files.isEmpty()) javax.swing.SwingUtilities.invokeLater(() -> hub.fileDrop.handle(files));
    }

    private void setApplicationIcon() {
        try {
            Image icon = AppIcons.getLargestImage();
            if (icon != null) UIUtils.setMacDockIcon(icon);
        } catch (Exception ignored) {}
    }
}
