package org.ttplayer.engine;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * 均衡器参数持久化 — 保存到 ~/.ttplayer/equalizer.properties。
 * 覆盖：启用、杜比环绕、preamp、10 段增益、平衡/环绕滑块、当前类别。
 */
public class EqualizerConfig {

    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".ttplayer", "equalizer.properties");

    public static class State {
        public boolean enabled;
        public boolean dolby;
        public double preampDb;
        public double[] gainsDb = new double[Equalizer.BANDS];
        public int balance = 50;
        public int surround = 50;
        public int category = -1;
    }

    public static void save(Equalizer eq, int balance, int surround, int category) {
        Properties props = new Properties();
        props.setProperty("enabled", String.valueOf(eq.isEnabled()));
        props.setProperty("dolby", String.valueOf(eq.isDolbySurround()));
        props.setProperty("preamp", String.valueOf(eq.getPreampDb()));
        for (int i = 0; i < Equalizer.BANDS; i++) {
            props.setProperty("gain." + i, String.valueOf(eq.getGainDb(i)));
        }
        props.setProperty("balance", String.valueOf(balance));
        props.setProperty("surround", String.valueOf(surround));
        props.setProperty("category", String.valueOf(category));
        writeAll(props);
    }

    public static State load() {
        State st = new State();
        Properties props = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                props.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        st.enabled = Boolean.parseBoolean(props.getProperty("enabled", "false"));
        st.dolby = Boolean.parseBoolean(props.getProperty("dolby", "false"));
        st.preampDb = parseDouble(props.getProperty("preamp"), 0.0);
        for (int i = 0; i < Equalizer.BANDS; i++) {
            st.gainsDb[i] = parseDouble(props.getProperty("gain." + i), 0.0);
        }
        st.balance = parseInt(props.getProperty("balance"), 50);
        st.surround = parseInt(props.getProperty("surround"), 50);
        st.category = parseInt(props.getProperty("category"), -1);
        return st;
    }

    private static double parseDouble(String s, double def) {
        if (s == null) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static void writeAll(Properties props) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "Equalizer config");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}