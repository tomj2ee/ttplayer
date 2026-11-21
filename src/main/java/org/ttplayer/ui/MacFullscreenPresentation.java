package org.ttplayer.ui;

import com.sun.jna.Callback;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

import java.awt.EventQueue;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

final class MacFullscreenPresentation {
    private static final List<Window> fullscreenWindows = new ArrayList<>();
    private static volatile Request request = new Request(null, false);

    private MacFullscreenPresentation() {}

    static synchronized void update(Window window, boolean visible) {
        if (!Platform.isMac()) return;
        if (visible) {
            if (request.window == window) return;
            fullscreenWindows.remove(window);
            fullscreenWindows.add(window);
        } else if (!fullscreenWindows.remove(window) || request.window != window) return;

        Window fullscreen = fullscreenWindows.isEmpty() ? null : fullscreenWindows.get(fullscreenWindows.size() - 1);
        request = new Request(fullscreen, visible);
        AppKit.applyOnMainQueue();
    }

    private static final class Request {
        final Window window;
        final boolean activate;

        Request(Window window, boolean activate) {
            this.window = window;
            this.activate = activate;
        }
    }

    public interface DispatchCallback extends Callback {
        void run(Pointer context);
    }

    private static final class AppKit {
        private static final long HIDE_DOCK_AND_MENU_BAR = (1L << 1) | (1L << 3);
        private static final NativeLibrary OBJC = NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib");
        private static final NativeLibrary SYSTEM = NativeLibrary.getInstance("/usr/lib/libSystem.B.dylib");
        private static final Function MESSAGE = OBJC.getFunction("objc_msgSend");
        private static final Function DISPATCH = SYSTEM.getFunction("dispatch_async_f");
        private static final Pointer MAIN_QUEUE = SYSTEM.getGlobalVariableAddress("_dispatch_main_q");
        // Keep the callback alive until AppKit has processed the queued calls.
        private static final DispatchCallback APPLY = context -> apply();
        private static Long previousOptions;
        private static Request applied;

        private static Pointer selector(String name) {
            return OBJC.getFunction("sel_registerName").invokePointer(new Object[] {name});
        }

        static void applyOnMainQueue() {
            DISPATCH.invokeVoid(new Object[] {MAIN_QUEUE, null, APPLY});
        }

        private static void apply() {
            Request next = request;
            if (next == applied) return;
            applied = next;
            Pointer appClass = OBJC.getFunction("objc_getClass").invokePointer(new Object[] {"NSApplication"});
            Pointer app = MESSAGE.invokePointer(new Object[] {appClass, selector("sharedApplication")});
            Window window = next.window;
            if (window == null) {
                if (previousOptions != null) {
                    MESSAGE.invokeVoid(new Object[] {app, selector("setPresentationOptions:"), previousOptions});
                    previousOptions = null;
                }
                return;
            }

            if (previousOptions == null) previousOptions = MESSAGE.invokeLong(new Object[] {app, selector("presentationOptions")});
            MESSAGE.invokeVoid(new Object[] {app, selector("setPresentationOptions:"), HIDE_DOCK_AND_MENU_BAR});
            // A status menu click can open the dialog without activating the application.
            if (next.activate && (Byte) MESSAGE.invoke(Byte.TYPE, new Object[] {app, selector("isActive")}) == 0) {
                Pointer activate = selector("activate");
                if ((Byte) MESSAGE.invoke(Byte.TYPE, new Object[] {app, selector("respondsToSelector:"), activate}) != 0)
                    MESSAGE.invokeVoid(new Object[] {app, activate});
                else
                    MESSAGE.invokeVoid(new Object[] {app, selector("activateIgnoringOtherApps:"), (byte) 1});
            }
            // AppKit can constrain the initial frame while the desktop bars are still visible.
            EventQueue.invokeLater(() -> {
                if (request == next && window.isShowing()) {
                    window.setBounds(window.getGraphicsConfiguration().getBounds());
                }
            });
        }
    }
}
