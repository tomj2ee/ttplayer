package org.ttplayer.lyrics;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LRCParser {

    private static final Pattern LRC_TAG = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]");
    private static final Pattern ID_TAG = Pattern.compile("\\[(ti|ar|al|by|offset):(.*)\\]", Pattern.CASE_INSENSITIVE);

    private long offsetMs;

    public static class LRCData {
        public List<LRCLine> lines;
        public String title;
        public String artist;
        public String album;
        public long offsetMs;

        LRCData() {
            lines = new ArrayList<>();
        }
    }

    public LRCData parse(File file) throws IOException {
        return parse(file, detectCharset(file));
    }

    public LRCData parse(File file, Charset charset) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), charset);
        return parseLines(lines);
    }

    public LRCData parse(String content) {
        return parseLines(Arrays.asList(content.split("\\r?\\n")));
    }

    private LRCData parseLines(List<String> lines) {
        LRCData data = new LRCData();
        offsetMs = 0;
        List<LRCLine> allLines = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher idMatcher = ID_TAG.matcher(line);
            if (idMatcher.matches()) {
                String tag = idMatcher.group(1).toLowerCase();
                String value = idMatcher.group(2).trim();
                switch (tag) {
                    case "ti": data.title = value; break;
                    case "ar": data.artist = value; break;
                    case "al": data.album = value; break;
                    case "offset":
                        try { offsetMs = Long.parseLong(value); } catch (NumberFormatException ignored) {}
                        break;
                }
                continue;
            }

            Matcher matcher = LRC_TAG.matcher(line);
            List<LRCLine> currentGroup = new ArrayList<>();

            while (matcher.find()) {
                int minutes = Integer.parseInt(matcher.group(1));
                String secStr = matcher.group(2);
                long timeMs;
                if (secStr.contains(".")) {
                    double seconds = Double.parseDouble(secStr);
                    timeMs = (long) (minutes * 60000 + seconds * 1000);
                } else {
                    timeMs = minutes * 60000 + Long.parseLong(secStr) * 1000;
                }
                timeMs += offsetMs;
                if (timeMs < 0) timeMs = 0;

                int start = matcher.end();
                Matcher nextMatcher = LRC_TAG.matcher(line);
                String text;
                if (nextMatcher.find(matcher.end())) {
                    text = line.substring(start, nextMatcher.start()).trim();
                } else {
                    text = line.substring(start).trim();
                }
                currentGroup.add(new LRCLine(timeMs, text));
            }

            if (currentGroup.isEmpty()) continue;
            allLines.addAll(currentGroup);
        }

        Collections.sort(allLines);
        for (int i = 0; i < allLines.size(); i++) {
            LRCLine cur = allLines.get(i);
            long nextTime = (i + 1 < allLines.size()) ? allLines.get(i + 1).getTimeMs()
                                                      : cur.getTimeMs() + 4000;
            if (cur.getText() != null && cur.getText().indexOf('<') >= 0) {
                cur.parseCharsFromEnhanced(cur.getText());
            }
            if (!cur.hasChars()) {
                cur.buildChars(nextTime - cur.getTimeMs());
            }
        }
        data.lines = allLines;
        data.offsetMs = offsetMs;
        return data;
    }

    public static File findLRCFile(File audioFile) {
        String path = audioFile.getAbsolutePath();
        int dot = path.lastIndexOf('.');
        if (dot > 0) {
            File lrcFile = new File(path.substring(0, dot) + ".lrc");
            if (lrcFile.exists()) return lrcFile;
            lrcFile = new File(path.substring(0, dot) + ".txt");
            if (lrcFile.exists()) return lrcFile;
        }
        return null;
    }

    private static Charset detectCharset(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            byte[] bom = new byte[3];
            int read = is.read(bom);
            if (read >= 3 && bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF)
                return Charset.forName("UTF-8");
            if (read >= 2 && bom[0] == (byte) 0xFF && bom[1] == (byte) 0xFE)
                return Charset.forName("UTF-16LE");
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (isValidUTF8(bytes)) return Charset.forName("UTF-8");
            return Charset.forName("GBK");
        } catch (Exception e) {
            return Charset.forName("GBK");
        }
    }

    private static boolean isValidUTF8(byte[] bytes) {
        try {
            new String(bytes, "UTF-8");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
