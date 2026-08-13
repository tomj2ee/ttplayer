package org.ttplayer.util;

import org.ttplayer.skin.TtSkin;
import org.ttplayer.ui.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class WindowLayoutUtils {

    public static void layoutBySkin(PlayerWindow p, LyricWindow l, EqualizerWindow e, PlaylistWindow pl) {
        TtSkin.WindowDef pDef = p.def;
        TtSkin.WindowDef lDef = l.def;
        TtSkin.WindowDef eDef = e.def;
        TtSkin.WindowDef plDef = pl.def;

        int baseX = 100, baseY = 100;
        int px = baseX, py = baseY;
        int lx = baseX, ly = baseY;
        int ex = baseX, ey = baseY;
        int plx = baseX, ply = baseY;

        if (pDef != null) { px = baseX + pDef.left; py = baseY + pDef.top; }
        if (lDef != null) { lx = baseX + lDef.left; ly = baseY + lDef.top; }
        if (eDef != null) { ex = baseX + eDef.left; ey = baseY + eDef.top; }
        if (plDef != null) { plx = baseX + plDef.left; ply = baseY + plDef.top; }

        if (lDef == null && pDef != null) { lx = px + (pDef.right - pDef.left); ly = py; }
        if (eDef == null && lDef != null) { ex = lx; ey = ly + (lDef.bottom - lDef.top); }
        if (plDef == null && pDef != null) { plx = px; ply = py + (pDef.bottom - pDef.top); }

        p.setLocation(px, py);
        l.setLocation(lx, ly);
        e.setLocation(ex, ey);
        pl.setLocation(plx, ply);
    }

    public static void setWindowIcon(javax.swing.JFrame window, TtSkin skin) {
        try {
            ClassLoader cl = WindowLayoutUtils.class.getClassLoader();
            List<java.awt.Image> icons = new ArrayList<>();

            // 尝试加载多个尺寸的图标
            tryLoadIcon(cl, "ico/ttplayer_16x16_32bpp.png", 16, icons);
            tryLoadIcon(cl, "ico/ttplayer_32x32_32bpp.png", 32, icons);
            tryLoadIcon(cl, "ico/ttplayer_48x48_32bpp.png", 48, icons);

            // 如果没有加载到图标，尝试从皮肤加载
            if (icons.isEmpty() && skin != null) {
                try {
                    byte[] iconData = skin.getBmp("TTPlayer.ico");
                    if (iconData != null) {
                        ByteArrayInputStream bais = new ByteArrayInputStream(iconData);
                        java.awt.image.BufferedImage img = ImageIO.read(bais);
                        if (img != null) {
                            // 添加多个尺寸的图标
                            icons.add(img.getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH));
                            icons.add(img.getScaledInstance(32, 32, java.awt.Image.SCALE_SMOOTH));
                            icons.add(img.getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH));
                            icons.add(img.getScaledInstance(64, 64, java.awt.Image.SCALE_SMOOTH));
                            icons.add(img.getScaledInstance(128, 128, java.awt.Image.SCALE_SMOOTH));
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (!icons.isEmpty()) {
                window.setIconImages(icons);

                // 为Mac系统设置Dock图标
                try {
                    setMacDockIcon(icons);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static void tryLoadIcon(ClassLoader cl, String path, int size, List<java.awt.Image> icons) {
        try {
            InputStream is = cl.getResourceAsStream(path);
            if (is != null) {
                java.awt.image.BufferedImage img = ImageIO.read(is);
                if (img != null) {
                    icons.add(img);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void setMacDockIcon(List<java.awt.Image> icons) {
        if (icons.isEmpty()) return;

        // 使用最大的图标作为Dock图标
        java.awt.Image largestIcon = icons.get(icons.size() - 1);
        try {
            // 利用反射设置Mac的Dock图标
            Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
            Object application = applicationClass.getMethod("getApplication").invoke(null);
            applicationClass.getMethod("setDockIconImage", java.awt.Image.class).invoke(application, largestIcon);
        } catch (Exception ignored) {
            // 如果不是Mac系统或者反射失败，忽略
        }
    }

    public static void setAllWindowIcons(PlayerWindow player, LyricWindow lyric,
                                         EqualizerWindow eq, PlaylistWindow playlist,
                                         MiniWindow miniWindow, TtSkin skin) {
        setWindowIcon(player, skin);
        setWindowIcon(lyric, skin);
        setWindowIcon(eq, skin);
        setWindowIcon(playlist, skin);
        setWindowIcon(miniWindow, skin);

        // 设置Mac的Dock名称
        setMacDockName("ttplayer");
    }

    private static void setMacDockName(String name) {
        try {
            // 方法1: 使用反射
            Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
            Object application = applicationClass.getMethod("getApplication").invoke(null);

            // 尝试设置Dock名称的各种方法
            try {
                // 尝试使用setDockIconBadge间接设置
                applicationClass.getMethod("setDockIconBadge", String.class)
                    .invoke(application, "");
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }
}
