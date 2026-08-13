package org.ttplayer.util;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;

public class ExtractFromSkin {

    public static void main(String[] args) {
        try {
            String skinPath = "F:/ttplaerr/skin/1、经典皮肤 (淡蓝+黑绿).skn";
            String outputDir = "F:/cc/my-tms/ttplayer/src/main/resources/skin/default/";
            Files.createDirectories(Paths.get(outputDir));

            extractIconFromSkin(skinPath, outputDir);
        } catch (Exception e) {
            e.printStackTrace();
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
                    System.out.println("Found: " + entry.getName());

                    String outName = name.contains("ico") ? "TTPlayer.ico" : "icon.bmp";
                    if (name.contains("player") || name.contains("main") || name.contains("icon")) {
                        InputStream is = zipFile.getInputStream(entry);
                        byte[] data = readAllBytes(is);
                        Files.write(Paths.get(outputDir, outName), data);
                        System.out.println("Extracted to: " + outName);
                        foundIcon = true;
                        break;
                    }
                }
            }

            if (!foundIcon) {
                System.out.println("No icon found, extracting all BMP files...");
                entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().toLowerCase().endsWith(".bmp")) {
                        InputStream is = zipFile.getInputStream(entry);
                        byte[] data = readAllBytes(is);
                        Files.write(Paths.get(outputDir, entry.getName()), data);
                        System.out.println("Extracted: " + entry.getName());
                    }
                }
            }

            zipFile.close();
            System.out.println("Done!");
        } catch (Exception e) {
            e.printStackTrace();
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
