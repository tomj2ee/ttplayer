package org.ttplayer.util;

import java.awt.*;

public final class MiniLyricLayout {
    private MiniLyricLayout() {}

    public static Rectangle usableBounds(Window window) {
        return usableBounds(window.getGraphicsConfiguration());
    }

    public static Rectangle usableBounds(Rectangle preferred) {
        GraphicsConfiguration best = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        long largest = -1;
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration candidate = device.getDefaultConfiguration();
            Rectangle intersection = candidate.getBounds().intersection(preferred);
            long area = intersection.width > 0 && intersection.height > 0 ? (long) intersection.width * intersection.height : 0;
            if (area > largest) { largest = area; best = candidate; }
        }
        return usableBounds(best);
    }

    private static Rectangle usableBounds(GraphicsConfiguration configuration) {
        Rectangle area = new Rectangle(configuration.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        area.x += insets.left;
        area.y += insets.top;
        area.width -= insets.left + insets.right;
        area.height -= insets.top + insets.bottom;
        return area;
    }

    public static Rectangle adjacent(Rectangle anchor, Rectangle area, Dimension preferred, int[] side) {
        int width = Math.min(preferred.width, area.width), height = Math.min(preferred.height, area.height);
        if (width <= 0 || height <= 0) return new Rectangle();
        long right = (long) area.x + area.width, bottom = (long) area.y + area.height;
        int centerX = coordinate(Math.max(area.x, Math.min((long) anchor.x + (anchor.width - width) / 2L, right - width)));
        int centerY = coordinate(Math.max(area.y, Math.min((long) anchor.y + (anchor.height - height) / 2L, bottom - height)));
        long[] horizontal = {(long) anchor.x + anchor.width, (long) anchor.x - width, centerX, centerX};
        long[] vertical = {centerY, centerY, (long) anchor.y + anchor.height, (long) anchor.y - height};
        int preferredSide = side != null && side[0] >= 0 && side[0] < 4 ? side[0] : 0;
        for (int attempt = 0; attempt < 5; attempt++) {
            int index = attempt == 0 ? preferredSide : attempt - 1;
            if (horizontal[index] >= area.x && horizontal[index] + width <= right &&
                    vertical[index] >= area.y && vertical[index] + height <= bottom) {
                if (side != null) side[0] = index;
                return new Rectangle(coordinate(horizontal[index]), coordinate(vertical[index]), width, height);
            }
        }
        long leftSpace = Math.min((long) anchor.x, right) - area.x;
        long rightStart = Math.max((long) anchor.x + anchor.width, area.x), rightSpace = right - rightStart;
        if (rightSpace > 0 || leftSpace > 0) {
            if (rightSpace >= leftSpace) return new Rectangle(coordinate(rightStart), centerY,
                    (int) Math.min(width, rightSpace), height);
            int fitted = (int) Math.min(width, leftSpace);
            return new Rectangle(coordinate((long) area.x + leftSpace - fitted), centerY, fitted, height);
        }
        return new Rectangle(centerX, (long) anchor.y + anchor.height / 2L < (long) area.y + area.height / 2L
                ? coordinate(bottom - height) : area.y, width, height);
    }

    private static int coordinate(long value) { return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value)); }
}
