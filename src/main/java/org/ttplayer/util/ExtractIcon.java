package org.ttplayer.util;

import java.io.*;
import java.nio.file.*;

public class ExtractIcon {

    public static void main(String[] args) {
        try {
            String exePath = "F:/ttplaerr/TTPlayer.exe";
            String outputPath = "src/main/resources/skin/default/TTPlayer.ico";

            extractFirstIcon(exePath, outputPath);
            System.out.println("Icon extracted successfully to: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void extractFirstIcon(String exePath, String outputPath) throws IOException {
        byte[] exeData = Files.readAllBytes(Paths.get(exePath));

        int icoOffset = findIconOffset(exeData);
        if (icoOffset < 0) {
            System.out.println("No icon found, copying from exe resource...");
            extractIconFromResources(exeData, outputPath);
            return;
        }

        System.out.println("Found icon at offset: " + icoOffset);
        byte[] icoData = extractIconData(exeData, icoOffset);

        if (icoData != null) {
            Files.write(Paths.get(outputPath), icoData);
        } else {
            System.out.println("Failed to extract icon, trying alternative method...");
            extractIconFromResources(exeData, outputPath);
        }
    }

    private static int findIconOffset(byte[] exeData) {
        for (int i = 0; i < exeData.length - 4; i++) {
            if (exeData[i] == 0x00 && exeData[i + 1] == 0x00 &&
                exeData[i + 2] == 0x01 && exeData[i + 3] == 0x00) {
                if (i + 22 < exeData.length) {
                    int type = readShort(exeData, i + 2);
                    int count = readShort(exeData, i + 4);
                    if (type == 1 && count > 0 && count < 100) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static byte[] extractIconData(byte[] exeData, int offset) {
        try {
            int count = readShort(exeData, offset + 4);
            if (count <= 0 || count > 20) return null;

            int headerSize = 6 + count * 16;
            int totalSize = headerSize;
            int dataOffset = headerSize;

            int[] sizes = new int[count];
            int[] offsets = new int[count];

            for (int i = 0; i < count; i++) {
                int entryOffset = offset + 6 + i * 16;
                sizes[i] = readInt(exeData, entryOffset + 8);
                offsets[i] = readInt(exeData, entryOffset + 12);
                totalSize += sizes[i];
            }

            byte[] icoFile = new byte[totalSize];
            System.arraycopy(exeData, offset, icoFile, 0, headerSize);

            int currentDataOffset = headerSize;
            for (int i = 0; i < count; i++) {
                int newOffset = currentDataOffset;
                System.arraycopy(exeData, offset + offsets[i], icoFile, currentDataOffset, sizes[i]);
                writeInt(icoFile, offset + 6 + i * 16 + 12, newOffset - offset);
                currentDataOffset += sizes[i];
            }

            return icoFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void extractIconFromResources(byte[] exeData, String outputPath) throws IOException {
        int[] possibleOffsets = findPossibleIconOffsets(exeData);
        for (int off : possibleOffsets) {
            try {
                byte[] icoData = tryCreateIco(exeData, off);
                if (icoData != null && icoData.length > 100) {
                    Files.write(Paths.get(outputPath), icoData);
                    System.out.println("Created icon file using offset: " + off);
                    return;
                }
            } catch (Exception ignored) {}
        }

        System.out.println("Creating placeholder BMP as icon...");
        createPlaceholderIcon(outputPath);
    }

    private static int[] findPossibleIconOffsets(byte[] exeData) {
        java.util.List<Integer> offsets = new java.util.ArrayList<>();
        for (int i = 0; i < exeData.length - 50; i++) {
            if (exeData[i] == 0x28 && exeData[i + 1] == 0x00 &&
                exeData[i + 2] == 0x00 && exeData[i + 3] == 0x00) {
                offsets.add(i);
            }
        }
        int[] result = new int[Math.min(offsets.size(), 10)];
        for (int i = 0; i < result.length; i++) {
            result[i] = offsets.get(i);
        }
        return result;
    }

    private static byte[] tryCreateIco(byte[] exeData, int bmpOffset) {
        try {
            int headerSize = 40;
            int width = readInt(exeData, bmpOffset + 4);
            int height = readInt(exeData, bmpOffset + 8);
            int bitCount = readShort(exeData, bmpOffset + 14);

            if (width <= 0 || width > 256 || height <= 0 || height > 256) return null;
            if (bitCount != 24 && bitCount != 32 && bitCount != 8 && bitCount != 4) return null;

            int bmpSize = readInt(exeData, bmpOffset + 20);
            if (bmpSize <= 0) bmpSize = (width * height * bitCount + 7) / 8 + height * 4;

            int dataSize = bmpSize + 40;
            if (bmpOffset + dataSize > exeData.length) {
                dataSize = exeData.length - bmpOffset;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeShort(baos, 0);
            writeShort(baos, 1);
            writeShort(baos, 1);
            writeByte(baos, width <= 256 ? width : 0);
            writeByte(baos, height <= 256 ? height : 0);
            writeShort(baos, 0);
            writeShort(baos, 0);
            writeInt(baos, dataSize);
            writeInt(baos, 22);

            byte[] bmpHeader = new byte[40];
            System.arraycopy(exeData, bmpOffset, bmpHeader, 0, 40);
            writeInt(bmpHeader, 8, height * 2);
            baos.write(bmpHeader);

            baos.write(exeData, bmpOffset + 40, dataSize - 40);

            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static void createPlaceholderIcon(String outputPath) throws IOException {
        int width = 16;
        int height = 16;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeShort(baos, 0);
        writeShort(baos, 1);
        writeShort(baos, 1);
        writeByte(baos, width);
        writeByte(baos, height);
        writeShort(baos, 0);
        writeShort(baos, 0);
        writeInt(baos, 40 + width * height * 4 + width * height / 2);
        writeInt(baos, 22);

        writeInt(baos, 40);
        writeInt(baos, width);
        writeInt(baos, height * 2);
        writeShort(baos, 1);
        writeShort(baos, 32);
        writeInt(baos, 0);
        writeInt(baos, width * height * 4);
        writeInt(baos, 2835);
        writeInt(baos, 2835);
        writeInt(baos, 0);
        writeInt(baos, 0);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean isBorder = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                if (isBorder) {
                    writeInt(baos, 0xFF0080FF);
                } else {
                    writeInt(baos, 0x00000000);
                }
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                baos.write(0xFF);
            }
        }

        Files.write(Paths.get(outputPath), baos.toByteArray());
    }

    private static int readShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8) |
               ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    private static void writeByte(ByteArrayOutputStream baos, int v) {
        baos.write(v);
    }

    private static void writeShort(ByteArrayOutputStream baos, int v) {
        baos.write(v & 0xFF);
        baos.write((v >> 8) & 0xFF);
    }

    private static void writeInt(byte[] data, int offset, int v) {
        data[offset] = (byte)(v & 0xFF);
        data[offset + 1] = (byte)((v >> 8) & 0xFF);
        data[offset + 2] = (byte)((v >> 16) & 0xFF);
        data[offset + 3] = (byte)((v >> 24) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream baos, int v) {
        baos.write(v & 0xFF);
        baos.write((v >> 8) & 0xFF);
        baos.write((v >> 16) & 0xFF);
        baos.write((v >> 24) & 0xFF);
    }
}
