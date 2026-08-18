package org.ttplayer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;

public class ExtractFromSkin {
    private static final Logger log = LoggerFactory.getLogger(ExtractFromSkin.class);

    public static void main(String[] args) {
        try {
            String skinPath = "F:/ttplaerr/skin/1、经典皮肤 (淡蓝+黑绿).skn";
            String outputDir = "F:/cc/my-tms/ttplayer/src/main/resources/skin/default/";
            Files.createDirectories(Paths.get(outputDir));

            extractIconFromSkin(skinPath, outputDir);
        } catch (Exception e) {
            log.error("Skin icon extraction failed", e);
        }
    }

    public static void extractIconFromSkin(String skinPath, String outputDir) {
        try {
            File skinFile = new File(skinPath);

            ZipFile zipFile;
            try {
                zipFile = new ZipFile(skinFile, Charset.forName("GBK"));
            } catch (Exception e) {
                zipFile = new ZipFile(skinFile, StandardCharsets.UTF_8);
            }

            boolean foundIcon = false;
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().toLowerCase();

                if (name.endsWith(".ico") || name.endsWith(".bmp")) {
                    log.info("Found: {}", entry.getName());

                    String outName = name.contains("ico") ? "TTPlayer.ico" : "icon.bmp";
                    if (name.contains("player") || name.contains("main") || name.contains("icon")) {
                        InputStream is = zipFile.getInputStream(entry);
                        byte[] data = readAllBytes(is);
                        Files.write(Paths.get(outputDir, outName), data);
                        log.info("Extracted to: {}", outName);
                        foundIcon = true;
                        break;
                    }
                }
            }

            if (!foundIcon) {
                log.info("No icon found, extracting all BMP files...");
                entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().toLowerCase().endsWith(".bmp")) {
                        InputStream is = zipFile.getInputStream(entry);
                        byte[] data = readAllBytes(is);
                        Files.write(Paths.get(outputDir, entry.getName()), data);
                        log.info("Extracted: {}", entry.getName());
                    }
                }
            }

            zipFile.close();
            log.info("Done!");
        } catch (Exception e) {
            log.error("Skin extraction failed", e);
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
