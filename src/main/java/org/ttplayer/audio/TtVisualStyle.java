package org.ttplayer.audio;

import java.awt.Color;

/** Skin values consumed by the C visualizer port. */
final class TtVisualStyle {
    Color top = new Color(18, 114, 175);
    Color middle = new Color(40, 143, 189);
    Color bottom = new Color(16, 215, 255);
    Color peak = Color.WHITE;
    Color scope = Color.CYAN;
    int blurSpeed = 3;
    boolean blur = true;
    boolean wide;
}
