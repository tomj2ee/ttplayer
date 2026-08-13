package org.ttplayer;

import org.ttplayer.app.WindowHub;
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
        // 首先设置应用图标
        setApplicationIcon();

        // 恢复上次选择的语言
        String lang = WindowConfig.restoreLanguage();
        if (lang != null && !lang.isEmpty()) {
            Messages.setLocale(Messages.fromTag(lang));
        }

        // 恢复上次使用的皮肤，创建整套窗口
        String savedSkin = WindowConfig.restoreSkin();
        hub.createUI((savedSkin != null && !savedSkin.isEmpty())
                ? savedSkin : WindowHub.DEFAULT_SKIN);
    }

    private void setApplicationIcon() {
        try {
            ClassLoader cl = getClass().getClassLoader();
            java.util.List<Image> icons = UIUtils.loadApplicationIcons(cl,
                "ico/ttplayer_16x16_32bpp.png",
                "ico/ttplayer_32x32_32bpp.png",
                "ico/ttplayer_48x48_32bpp.png"
            );

            if (!icons.isEmpty()) {
                // 设置Mac的Dock图标
                UIUtils.setMacDockIcon(icons.get(icons.size() - 1));
            }
        } catch (Exception ignored) {}
    }
}