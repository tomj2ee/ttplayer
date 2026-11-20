package org.ttplayer.util;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import java.awt.Window;

public final class NativeWindowSupport {
    private static final int EXTENDED_STYLE = -20;
    private static final int TRANSPARENT = 0x20;
    private static final int NO_ACTIVATE = 0x08000000;

    public interface User32 extends StdCallLibrary {
        int GetWindowLongW(Pointer window, int index);
        int SetWindowLongW(Pointer window, int index, int value);
    }

    private static final class Windows {
        static final User32 API = Native.load("user32", User32.class);
    }

    private NativeWindowSupport() {}

    public static void setClickThrough(Window window, boolean enabled) {
        if (!Platform.isWindows() || !window.isDisplayable()) return;
        try {
            Pointer handle = Native.getWindowPointer(window);
            int style = Windows.API.GetWindowLongW(handle, EXTENDED_STYLE);
            Windows.API.SetWindowLongW(handle, EXTENDED_STYLE,
                    enabled ? style | TRANSPARENT | NO_ACTIVATE : style & ~TRANSPARENT & ~NO_ACTIVATE);
        } catch (RuntimeException | LinkageError error) {
            System.err.println("Desktop lyric click-through: " + error.getMessage());
        }
    }

    public static boolean isClickThrough(Window window) {
        return Platform.isWindows() && window.isDisplayable() &&
                (Windows.API.GetWindowLongW(Native.getWindowPointer(window), EXTENDED_STYLE) & TRANSPARENT) != 0;
    }
}
