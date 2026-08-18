package org.ttplayer.skin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.*;

public class TtSkin {
    private static final Logger log = LoggerFactory.getLogger(TtSkin.class);

    /** 窗口中一个控件的定义 */
    public static class Ctl {
        public String tag;
        public int left, top, right, bottom;
        public String image;
        public int frameWidth, frameHeight;
        public int cols;
        public String barImage, thumbImage, fillImage, buttonsImage, hotImage;
        public boolean vertical;
        public String align;
        public String color, bkgnd, font;
        public int fontSize;
        public int thumbResizeCenter;
        public int thumbResizeTile;

        void parsePosition(String pos) {
            if (pos == null || pos.isEmpty()) return;
            String[] p = pos.split(",");
            left = Integer.parseInt(p[0].trim());
            top = Integer.parseInt(p[1].trim());
            right = Integer.parseInt(p[2].trim());
            bottom = Integer.parseInt(p[3].trim());
        }
    }

    public static class WindowDef {
        public String name;
        public String image;
        public int left, top, right, bottom;
        public int width, height;
        public String resizeRect;
        public int resizeTile;
        public final List<Ctl> elements = new ArrayList<>();
        public String eqInterval;

        void parsePosition(String pos) {
            if (pos == null || pos.isEmpty()) return;
            String[] p = pos.split(",");
            left = Integer.parseInt(p[0].trim());
            top = Integer.parseInt(p[1].trim());
            right = Integer.parseInt(p[2].trim());
            bottom = Integer.parseInt(p[3].trim());
        }
    }

    private final Map<String, byte[]> files = new HashMap<>();
    private Document xmlDoc;
    private String skinName;
    private Color transparentColor = new Color(255, 0, 255);

    public String getSkinName() { return skinName; }
    public Color getTransparentColor() { return transparentColor; }

    public void load(File sknFile) throws IOException {
        files.clear();
        readZip(sknFile);
        parseXml();
    }

    public void loadDir(File dir) throws IOException {
        files.clear();
        File[] list = dir.listFiles();
        if (list != null) {
            for (File f : list) {
                if (f.isFile()) {
                    files.put(f.getName(), Files.readAllBytes(f.toPath()));
                }
            }
        }
        parseXml();
    }

    public void loadFromClasspath(String resource) throws IOException {
        files.clear();
        ClassLoader cl = getClass().getClassLoader();
        java.net.URL url = cl.getResource(resource);
        if (url == null) throw new IOException("Cannot find classpath resource: " + resource);

        // 目录资源（解压后的皮肤目录）以 "/" 结尾，单个 .skn 文件不是
        if (url.toExternalForm().endsWith("/")) {
            if ("file".equals(url.getProtocol())) {
                try {
                    loadDir(new File(url.toURI()));
                } catch (java.net.URISyntaxException e) {
                    throw new IOException("Invalid resource URI: " + url, e);
                }
            } else if ("jar".equals(url.getProtocol())) {
                String path = url.getPath();
                int idx = path.indexOf('!');
                String jarFile = path.substring(5, idx);
                String prefix = resource.endsWith("/") ? resource : resource + "/";

                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(
                         java.net.URLDecoder.decode(jarFile, "UTF-8"))) {
                    java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        java.util.zip.ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith(prefix) && !name.equals(prefix)) {
                            String rel = name.substring(prefix.length());
                            if (!rel.contains("/")) {
                                files.put(rel, readAllBytes(zf.getInputStream(entry)));
                            }
                        }
                    }
                }
                parseXml();
            }
        } else {
            // 单个 .skn 文件资源（fat jar 里的皮肤就是这种形式）
            try (java.io.InputStream is = cl.getResourceAsStream(resource)) {
                if (is == null) throw new IOException("Cannot find classpath resource: " + resource);
                readZipStream(is);
            }
            parseXml();
        }
    }

    private void readZipStream(InputStream is) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(is)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.isEmpty() || entry.isDirectory()) continue;
                files.put(name, readAllBytes(zis));
            }
        }
    }

    public byte[] getBmp(String name) {
        byte[] data = files.get(name);

        // 如果皮肤目录没有，尝试从resources/ico目录读取
        if (data == null) {
            String lowerName = name.toLowerCase();

            // 先尝试直接从ico目录读取
            data = loadFromResources("ico/" + name);

            // 如果是ICO文件，尝试用PNG替代
            if (data == null && lowerName.endsWith(".ico")) {
                String baseName = name.substring(0, name.length() - 4);
                data = loadFromResources("ico/" + baseName + "_16x16_32bpp.png");
                if (data == null) {
                    data = loadFromResources("ico/" + baseName + "_32x32_32bpp.png");
                }
            }
        }

        return data;
    }

    private byte[] loadFromResources(String path) {
        try {
            ClassLoader cl = getClass().getClassLoader();
            java.io.InputStream is = cl.getResourceAsStream(path);
            if (is != null) {
                try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    return baos.toByteArray();
                }
            }
        } catch (Exception e) {
            log.warn("Could not load resource: {}", path);
        }
        return null;
    }

    public List<WindowDef> getWindows() {
        List<WindowDef> result = new ArrayList<>();
        if (xmlDoc == null) return result;

        org.w3c.dom.Element root = xmlDoc.getDocumentElement();
        NodeList children = root.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            org.w3c.dom.Element el = (org.w3c.dom.Element) node;
            String tag = el.getTagName();

            if (!tag.endsWith("_window")) continue;

            WindowDef wd = new WindowDef();
            wd.name = tag;
            wd.image = el.getAttribute("image");
            wd.resizeRect = el.getAttribute("resize_rect");
            String tile = el.getAttribute("resize_tile");
            wd.resizeTile = tile.isEmpty() ? 0 : Integer.parseInt(tile);
            wd.eqInterval = el.getAttribute("eq_interval");
            wd.parsePosition(el.getAttribute("position"));
            wd.width = wd.right - wd.left;
            wd.height = wd.bottom - wd.top;

            NodeList subs = el.getChildNodes();
            for (int j = 0; j < subs.getLength(); j++) {
                Node sub = subs.item(j);
                if (sub.getNodeType() != Node.ELEMENT_NODE) continue;
                org.w3c.dom.Element subEl = (org.w3c.dom.Element) sub;

                Ctl btn = new Ctl();
                btn.tag = subEl.getTagName();
                btn.parsePosition(subEl.getAttribute("position"));
                btn.image = subEl.getAttribute("image");
                btn.barImage = subEl.getAttribute("bar_image");
                btn.thumbImage = subEl.getAttribute("thumb_image");
                btn.fillImage = subEl.getAttribute("fill_image");
                btn.buttonsImage = subEl.getAttribute("buttons_image");
                btn.hotImage = subEl.getAttribute("hot_image");
                btn.vertical = "true".equals(subEl.getAttribute("vertical"));
                btn.align = subEl.getAttribute("align");
                btn.color = subEl.getAttribute("color");
                btn.bkgnd = subEl.getAttribute("bkgnd");
                btn.font = subEl.getAttribute("font");
                String fs = subEl.getAttribute("font_size");
                btn.fontSize = fs.isEmpty() ? 12 : Integer.parseInt(fs);
                String trc = subEl.getAttribute("thumb_resize_center");
                btn.thumbResizeCenter = trc.isEmpty() ? 0 : Integer.parseInt(trc);
                String trt = subEl.getAttribute("thumb_resize_tile");
                btn.thumbResizeTile = trt.isEmpty() ? 0 : Integer.parseInt(trt);

                if (btn.right > btn.left && btn.bottom > btn.top) {
                    btn.frameWidth = btn.right - btn.left;
                    btn.frameHeight = btn.bottom - btn.top;
                }

                if (btn.image != null && !btn.image.isEmpty()) {
                    byte[] bmp = files.get(btn.image);
                    if (bmp != null) {
                        int iw = TtSkin.readInt(bmp, 18);
                        int ih = Math.abs(TtSkin.readInt(bmp, 22));

                        if ("led".equals(btn.tag)) {
                            btn.cols = 12;
                            btn.frameWidth = iw / 12;
                            btn.frameHeight = ih;
                        } else if (btn.frameWidth > 0 && iw % btn.frameWidth == 0) {
                            btn.cols = iw / btn.frameWidth;
                        } else {
                            btn.cols = 1;
                        }
                    }
                }

                wd.elements.add(btn);
            }
            result.add(wd);
        }
        return result;
    }

    private void readZip(File file) throws IOException {
        boolean found = false;
        try {
            try (ZipFile zf = new ZipFile(file, StandardCharsets.UTF_8)) {
                found = readZipEntries(zf);
            }
        } catch (IOException | IllegalArgumentException e) {
            found = false;
        }
        if (!found) {
            try (ZipFile zf = new ZipFile(file, Charset.forName("GBK"))) {
                readZipEntries(zf);
            }
        }
    }

    private boolean readZipEntries(ZipFile zf) {
        Enumeration<? extends ZipEntry> entries = zf.entries();
        boolean hasSkinXml = false;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.isEmpty() || entry.isDirectory()) continue;
            if (name.equalsIgnoreCase("Skin.xml") || name.equalsIgnoreCase("skin.xml")) {
                hasSkinXml = true;
            }
            try (InputStream in = zf.getInputStream(entry)) {
                files.put(name, readAllBytes(in));
            } catch (IOException ignored) {}
        }
        return hasSkinXml;
    }

    private void parseXml() {
        byte[] data = null;
        for (String key : files.keySet()) {
            if (key.equalsIgnoreCase("Skin.xml")) {
                data = files.get(key);
                break;
            }
        }
        if (data == null) return;

        String xmlStr = decodeXml(data);
        xmlStr = fixSkinXml(xmlStr);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            xmlDoc = builder.parse(new ByteArrayInputStream(xmlStr.getBytes(StandardCharsets.UTF_8)));
            org.w3c.dom.Element root = xmlDoc.getDocumentElement();
            if (root != null) {
                skinName = root.getAttribute("name");
                String tc = root.getAttribute("transparent_color");
                if (tc != null && tc.startsWith("#")) {
                    try {
                        transparentColor = Color.decode(tc);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse skin XML", e);
        }
    }

    private String decodeXml(byte[] data) {
        if (data.length >= 3 && data[0] == (byte)0xEF && data[1] == (byte)0xBB && data[2] == (byte)0xBF) {
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        }

        String header = new String(data, 0, Math.min(200, data.length), StandardCharsets.US_ASCII);
        if (header.contains("encoding=")) {
            if (header.contains("GBK") || header.contains("gb2312") || header.contains("GB2312")) {
                return new String(data, Charset.forName("GBK"));
            }
            if (header.contains("UTF-8") || header.contains("utf-8")) {
                return new String(data, StandardCharsets.UTF_8);
            }
        }

        String utf8 = new String(data, StandardCharsets.UTF_8);
        if (containsChinese(utf8)) return utf8;
        return new String(data, Charset.forName("GBK"));
    }

    private boolean containsChinese(String s) {
        for (char c : s.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }

    private static String fixSkinXml(String xml) {
        if (xml == null || xml.isEmpty()) return xml;
        xml = xml.replaceAll("(?<=\\s)(\\w+)=(\\d+)(?=\\s|/?>)", "$1=\"$2\"");
        xml = xml.replaceAll("\"([a-zA-Z_]\\w*=)", "\" $1");
        xml = xml.replaceAll("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)", "&amp;");
        return xml;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    static int readInt(byte[] data, int off) {
        return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8)
                | ((data[off + 2] & 0xFF) << 16) | ((data[off + 3] & 0xFF) << 24);
    }

}
