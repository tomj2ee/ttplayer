package org.ttplayer.util;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public final class TtMenuGlyphs {
    private static final Map<Integer, List<Command>> COMMANDS = load();
    private static final Map<String, Integer> ACTIONS = actions();
    private static final Map<Integer, Icon> ICONS = new HashMap<>();

    private TtMenuGlyphs() {}

    public static Icon icon(String action) {
        return icon(glyph(action, false));
    }

    public static Icon icon(int glyph) {
        return ICONS.computeIfAbsent(glyph, key -> new Icon() {
            public int getIconWidth() { return 16; }
            public int getIconHeight() { return 16; }
            public void paintIcon(Component component, Graphics graphics, int left, int top) {
                Graphics2D canvas = (Graphics2D) graphics.create();
                try {
                    canvas.translate(left, top);
                    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    canvas.setStroke(new BasicStroke(1));
                    for (Command command : COMMANDS.getOrDefault(key, COMMANDS.get(21))) command.paint(canvas);
                } finally {
                    canvas.dispose();
                }
            }
        });
    }

    public static int glyph(String action, boolean submenu) {
        String name = action == null ? "" : action;
        Integer exact = ACTIONS.get(name);
        if (exact != null) return exact;
        String[] prefixes = {"skin", "sort_", "mode", "visual", "opacity", "volume", "language", "preset",
                "link_", "list:", "playlist", "play_list", "eq_", "replaygain"};
        int[] glyphs = {12, 26, 5, 8, 13, 4, 33, 10, 1, 6, 6, 6, 10, 10};
        for (int index = 0; index < prefixes.length; index++) {
            if (name.startsWith(prefixes[index])) return glyphs[index];
        }
        return submenu ? 6 : 21;
    }

    private static Map<String, Integer> actions() {
        Map<String, Integer> result = new HashMap<>();
        result.put("properties", 32);
        result.put("open_file_location", 22);
        result.put("play_pause", 3);
        String[] names = {"show", "pause_after", "open_skin", "desktop_lines", "open", "folder", "add_url",
                "add_search", "new_list", "clear_list", "delete", "delete_other", "delete_duplicates", "delete_list",
                "import", "export", "save_all", "rename_list", "filename_format", "batch_info", "about", "select_all",
                "select_invert", "find", "find_next", "locate", "sort_random", "sort_artist", "sort_path", "sort_filename",
                "play", "pause", "stop", "prev", "next", "mute", "volume_up", "volume_down", "lyric", "lyric_search",
                "loadlrc", "karaoke", "desktop", "lock", "minimode", "ontop", "resetlayout", "fullscreen_lyrics",
                "fullscreen_visual", "fullscreen_lyric_video", "fullscreen", "fullscreen_off", "exit", "quit", "reset",
                "equalizer", "enabled", "dolby", "preferences", "settings", "tools", "playlist_options", "skin_default",
                "profile", "cover"};
        int[] glyphs = {38,42,22,9,21,22,1,14,23,25,24,45,25,24,29,30,28,31,31,32,32,44,45,14,41,34,27,48,22,21,
                3,42,43,40,41,39,49,50,9,14,21,47,36,35,37,34,38,9,8,47,19,38,20,20,46,10,10,4,2,2,2,2,18,10,18};
        for (int index = 0; index < names.length; index++) result.put(names[index], glyphs[index]);
        return result;
    }

    private static Map<Integer, List<Command>> load() {
        Map<Integer, List<Command>> result = new HashMap<>();
        InputStream resource = TtMenuGlyphs.class.getResourceAsStream("/menu-glyphs.txt");
        if (resource == null) throw new IllegalStateException("Missing C menu glyphs");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(" ");
                int glyph = Integer.parseInt(fields[0]);
                result.computeIfAbsent(glyph, key -> new ArrayList<>()).add(new Command(fields));
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load C menu glyphs", error);
        }
        return result;
    }

    private static final class Command {
        final Shape shape;
        final int fill;
        final int stroke;

        Command(String[] fields) {
            int primitive = Integer.parseInt(fields[1]);
            fill = Integer.parseInt(fields[2]);
            stroke = Integer.parseInt(fields[3]);
            double left = Double.parseDouble(fields[5]);
            double top = Double.parseDouble(fields[6]);
            double width = Double.parseDouble(fields[7]);
            double height = Double.parseDouble(fields[8]);
            if (primitive == 3) {
                Path2D path = new Path2D.Double();
                int count = Integer.parseInt(fields[9]);
                for (int index = 0; index < count; index++) {
                    double horizontal = Double.parseDouble(fields[10 + index * 2]);
                    double vertical = Double.parseDouble(fields[11 + index * 2]);
                    if (index == 0) path.moveTo(horizontal, vertical);
                    else path.lineTo(horizontal, vertical);
                }
                if ("1".equals(fields[4])) path.closePath();
                shape = path;
            } else if (primitive == 2) shape = new Ellipse2D.Double(left, top, width, height);
            else if (primitive == 1) shape = new RoundRectangle2D.Double(left, top, width, height, 4, 4);
            else shape = new Rectangle2D.Double(left, top, width, height);
        }

        void paint(Graphics2D graphics) {
            if (fill != -1) {
                graphics.setColor(new Color(fill));
                graphics.fill(shape);
            }
            if (stroke != -1) {
                graphics.setColor(new Color(stroke));
                graphics.draw(shape);
            }
        }
    }
}
