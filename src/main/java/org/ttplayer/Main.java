package org.ttplayer;

public class Main {

    public static void main(String[] args) {
        // 最重要：首先设置所有系统属性，必须在任何 UI 或 AWT 类加载之前
        setSystemProperties();

        // 现在加载应用
        new TTPlayerApplication().start();
    }

    private static void setSystemProperties() {
        // 设置编码相关属性（必须在最前面）
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");

        // 设置 Mac 系统的应用名称 - 这些必须是第一个设置的
        System.setProperty("apple.awt.application.name", "ttplayer");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "ttplayer");

        // 其他系统属性
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("com.apple.mrj.application.growbox.intrudes", "false");

        // 设置默认外观
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }


    }
}
