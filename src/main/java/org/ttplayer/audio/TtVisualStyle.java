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
    int fps = 25;
    boolean showLabel;
    Color labelColor = Color.WHITE;
    int labelFont;

    TtVisualStyle copy() {
        TtVisualStyle result = new TtVisualStyle();
        result.top = top;
        result.middle = middle;
        result.bottom = bottom;
        result.peak = peak;
        result.scope = scope;
        result.blurSpeed = blurSpeed;
        result.blur = blur;
        result.wide = wide;
        result.fps = fps;
        result.showLabel = showLabel;
        result.labelColor = labelColor;
        result.labelFont = labelFont;
        return result;
    }
}
