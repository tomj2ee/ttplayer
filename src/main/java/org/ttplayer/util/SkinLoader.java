package org.ttplayer.util;

import org.ttplayer.skin.TtSkin;

import java.io.File;
import java.util.*;

public class SkinLoader {

    private static final String[][] SKIN_FALLBACKS = {
        {"classpath:skin/default.skn"},
        {"fs:src/main/resources/skin/default.skn"},
        {"fs:resources/skin/default.skn"},
        {"fs:skin/default.skn"},
    };

    public static boolean loadSkinFrom(TtSkin skin, String spec) {
        if (spec == null) return false;
        try {
            if (spec.startsWith("classpath:")) {
                String res = spec.substring(10);
                skin.loadFromClasspath(res);
            } else if (spec.startsWith("fs:")) {
                String filePath = spec.substring(3);
                File f = new File(filePath);
                if (!f.exists()) return false;
                if (f.isDirectory()) skin.loadDir(f);
                else skin.load(f);
            } else {
                try {
                    skin.loadFromClasspath(spec);
                } catch (Exception ignored) {
                    File f = new File(spec);
                    if (!f.exists()) return false;
                    if (f.isDirectory()) skin.loadDir(f);
                    else skin.load(f);
                }
            }
            return !skin.getWindows().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public static void loadWithFallback(TtSkin skin, String skinPath) {
        if (!loadSkinFrom(skin, skinPath) || skin.getWindows().isEmpty()) {
            for (String[] pair : SKIN_FALLBACKS) {
                if (loadSkinFrom(skin, pair[0])) break;
            }
        }
    }

    public static String findSkinPath(File skinFile) {
        if (skinFile == null || !skinFile.exists()) return null;
        if (skinFile.isDirectory()) return "fs:" + skinFile.getAbsolutePath();
        String name = skinFile.getName().toLowerCase();
        if (name.endsWith(".skn")) {
            return "fs:" + skinFile.getAbsolutePath();
        }
        return null;
    }

    /**
     * 列出所有皮肤。优先从运行代码所在的 jar / classes 目录里枚举 skin/ 下的 .skn
     * （fat jar 打包后没有文件系统目录，只能这样找），并附带开发模式的 fs 目录兜底。
     * 返回形如 "classpath:skin/xxx.skn" 或 "fs:/abs/path/xxx.skn" 的 spec。
     */
    public static List<String> listClasspathSkins() {
        Set<String> specs = new LinkedHashSet<>();
        ClassLoader cl = SkinLoader.class.getClassLoader();

        try {
            java.net.URL loc = SkinLoader.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null && "file".equals(loc.getProtocol())) {
                File f = new File(loc.toURI());
                if (f.isDirectory()) {
                    File skinDir = new File(f, "skin");
                    File[] files = skinDir.listFiles((d, n) -> n.toLowerCase().endsWith(".skn"));
                    if (files != null) {
                        for (File sf : files) specs.add("classpath:skin/" + sf.getName());
                    }
                } else if (f.isFile() && f.getName().endsWith(".jar")) {
                    try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(f)) {
                        java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
                        while (en.hasMoreElements()) {
                            String n = en.nextElement().getName();
                            if (n.startsWith("skin/") && n.endsWith(".skn")) {
                                specs.add("classpath:" + n);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 开发模式兜底：直接扫文件系统目录
        for (String d : new String[]{"skin", "resources/skin", "src/main/resources/skin"}) {
            File dir = new File(d);
            File[] files = dir.listFiles((dd, n) -> n.toLowerCase().endsWith(".skn"));
            if (files != null) {
                for (File sf : files) specs.add("fs:" + sf.getAbsolutePath());
            }
        }

        return new ArrayList<>(specs);
    }
}
