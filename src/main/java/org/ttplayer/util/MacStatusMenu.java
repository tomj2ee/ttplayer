package org.ttplayer.util;

import com.sun.jna.Callback;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

final class MacStatusMenu {
    private static final double BUTTON_WIDTH = 26;
    private static final long CONTROL = 1L << 18;
    private final byte[] icon;
    private final Runnable restore;
    private final Supplier<JPopupMenu> menu;
    private volatile String toolTip = "TTPlayer";
    private Pointer status, button, target, mouseView;
    private List<Runnable> commands;
    private boolean controlClick;

    MacStatusMenu(BufferedImage image, Runnable restore, Supplier<JPopupMenu> menu) {
        this.restore = restore;
        this.menu = menu;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            icon = output.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        show();
    }

    void show() {
        Cocoa.run(() -> {
            if (status != null) return;
            status = Cocoa.object(Cocoa.statusBar(), "statusItemWithLength:", BUTTON_WIDTH);
            Cocoa.send(status, "retain");
            button = Cocoa.object(status, "button");
            target = Cocoa.object(Cocoa.targetClass(), "new");
            Cocoa.TARGETS.put(target, this);
            Pointer data = Cocoa.object(Cocoa.type("NSData"), "dataWithBytes:length:", icon, (long) icon.length);
            Pointer image = Cocoa.object(Cocoa.object(Cocoa.type("NSImage"), "alloc"), "initWithData:", data);
            Cocoa.send(image, "setSize:", new Point(18, 18));
            Cocoa.send(button, "setImage:", image);
            Cocoa.send(image, "release");
            Cocoa.send(button, "setToolTip:", Cocoa.string(toolTip));
            Cocoa.send(button, "setTarget:", target);
            Cocoa.send(button, "setAction:", Cocoa.selector("restorePlayer:"));
            mouseView = Cocoa.object(Cocoa.mouseViewClass(), "new");
            Cocoa.TARGETS.put(mouseView, this);
            Cocoa.send(mouseView, "setFrameSize:", new Point(BUTTON_WIDTH, Cocoa.decimal(Cocoa.statusBar(), "thickness")));
            Cocoa.send(mouseView, "setAutoresizingMask:", 18L); // NSViewWidthSizable | NSViewHeightSizable
            Cocoa.send(button, "addSubview:", mouseView);
            Cocoa.send(mouseView, "release");
        });
    }

    void setToolTip(String value) {
        toolTip = value;
        Cocoa.run(() -> {
            if (button != null) Cocoa.send(button, "setToolTip:", Cocoa.string(toolTip));
        });
    }

    void remove() {
        Cocoa.run(() -> {
            if (status == null) return;
            Cocoa.send(Cocoa.statusBar(), "removeStatusItem:", status);
            Cocoa.TARGETS.remove(target);
            Cocoa.TARGETS.remove(mouseView);
            Cocoa.send(target, "release");
            Cocoa.send(status, "release");
            status = button = target = mouseView = null;
            controlClick = false;
        });
    }

    private void openMenu() {
        Pointer clickedTarget = target;
        EventQueue.invokeLater(() -> {
            List<Entry> items = Entry.read(menu.get().getComponents());
            Cocoa.run(() -> {
                if (clickedTarget.equals(target) && commands == null) showMenu(items);
            });
        });
    }

    private void showMenu(List<Entry> entries) {
        commands = new ArrayList<>();
        Pointer nativeMenu = buildMenu(entries);
        try {
            boolean flipped = Cocoa.bool(button, "isFlipped");
            double bottom = flipped ? Cocoa.decimal(Cocoa.statusBar(), "thickness") : 0;
            Cocoa.bool(nativeMenu, "popUpMenuPositioningItem:atLocation:inView:", null, new Point(0, bottom), button);
        } finally {
            commands = null;
            Cocoa.send(nativeMenu, "release");
        }
    }

    private Pointer buildMenu(List<Entry> entries) {
        Pointer nativeMenu = Cocoa.object(Cocoa.type("NSMenu"), "new");
        Cocoa.send(nativeMenu, "setAutoenablesItems:", (byte) 0);
        for (Entry entry : entries) {
            if (entry == null) {
                Cocoa.send(nativeMenu, "addItem:", Cocoa.object(Cocoa.type("NSMenuItem"), "separatorItem"));
                continue;
            }
            Pointer item = Cocoa.object(Cocoa.object(Cocoa.type("NSMenuItem"), "alloc"),
                    "initWithTitle:action:keyEquivalent:", Cocoa.string(entry.title),
                    entry.action == null ? null : Cocoa.selector("menuClicked:"), Cocoa.string(""));
            Cocoa.send(item, "setEnabled:", (byte) (entry.enabled ? 1 : 0));
            if (entry.children != null) {
                Pointer submenu = buildMenu(entry.children);
                Cocoa.send(item, "setSubmenu:", submenu);
                Cocoa.send(submenu, "release");
            } else {
                Cocoa.send(item, "setTarget:", target);
                Cocoa.send(item, "setTag:", (long) commands.size());
                Cocoa.send(item, "setState:", entry.checked ? 1L : 0L);
                commands.add(entry.action);
            }
            Cocoa.send(nativeMenu, "addItem:", item);
            Cocoa.send(item, "release");
        }
        return nativeMenu;
    }

    private static final class Entry {
        final String title;
        final boolean enabled, checked;
        final List<Entry> children;
        final Runnable action;

        Entry(JMenuItem item) {
            title = item.getText();
            enabled = item.isEnabled();
            checked = item.isSelected();
            children = item instanceof JMenu ? read(((JMenu) item).getMenuComponents()) : null;
            action = children == null ? () -> item.doClick(0) : null;
        }

        static List<Entry> read(Component[] components) {
            List<Entry> entries = new ArrayList<>();
            for (Component component : components) {
                if (component instanceof JSeparator) entries.add(null);
                else if (component instanceof JMenuItem) entries.add(new Entry((JMenuItem) component));
            }
            return entries;
        }
    }

    @Structure.FieldOrder({"x", "y"})
    public static final class Point extends Structure implements Structure.ByValue {
        public double x, y;
        public Point() {}
        Point(double x, double y) { this.x = x; this.y = y; }
    }

    public interface Dispatch extends Callback { void call(Pointer context); }
    public interface Action extends Callback { void call(Pointer receiver, Pointer selector, Pointer sender); }

    private static final class Cocoa {
        private static final NativeLibrary OBJC = NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib");
        private static final NativeLibrary SYSTEM = NativeLibrary.getInstance("/usr/lib/libSystem.B.dylib");
        private static final Function MSG = OBJC.getFunction("objc_msgSend");
        private static final Function DISPATCH = SYSTEM.getFunction("dispatch_async_f");
        private static final Pointer MAIN_QUEUE = SYSTEM.getGlobalVariableAddress("_dispatch_main_q");
        private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
        private static final Map<Pointer, MacStatusMenu> TARGETS = new HashMap<>();
        private static final Dispatch RUN = context -> TASKS.remove().run();
        private static final Action RESTORE = (receiver, selector, sender) -> {
            MacStatusMenu owner = TARGETS.get(receiver);
            if (owner != null) EventQueue.invokeLater(owner.restore);
        };
        private static final Action MOUSE_DOWN = (receiver, selector, event) -> {
            MacStatusMenu owner = TARGETS.get(receiver);
            if (owner != null) owner.controlClick = (integer(event, "modifierFlags") & CONTROL) != 0;
        };
        private static final Action MOUSE_UP = (receiver, selector, event) -> {
            MacStatusMenu owner = TARGETS.get(receiver);
            if (owner == null) return;
            if (owner.controlClick || (integer(event, "modifierFlags") & CONTROL) != 0) owner.openMenu();
            else EventQueue.invokeLater(owner.restore);
            owner.controlClick = false;
        };
        private static final Action RIGHT_MOUSE_UP = (receiver, selector, event) -> {
            MacStatusMenu owner = TARGETS.get(receiver);
            if (owner != null) owner.openMenu();
        };
        private static final Action IGNORE = (receiver, selector, event) -> {};
        private static final Action MENU = (receiver, selector, sender) -> {
            MacStatusMenu owner = TARGETS.get(receiver);
            if (owner != null && owner.commands != null) {
                int tag = (int) integer(sender, "tag");
                EventQueue.invokeLater(owner.commands.get(tag));
            }
        };
        private static Pointer targetClass, mouseViewClass;

        static void run(Runnable task) {
            TASKS.add(task);
            DISPATCH.invokeVoid(new Object[] {MAIN_QUEUE, null, RUN});
        }

        static Pointer targetClass() {
            if (targetClass == null) {
                targetClass = allocateClass("NSObject", "TTPlayerStatusMenuTarget");
                addAction(targetClass, "restorePlayer:", RESTORE);
                addAction(targetClass, "menuClicked:", MENU);
                registerClass(targetClass);
            }
            return targetClass;
        }

        static Pointer mouseViewClass() {
            if (mouseViewClass == null) {
                mouseViewClass = allocateClass("NSView", "TTPlayerStatusMouseView");
                addAction(mouseViewClass, "mouseDown:", MOUSE_DOWN);
                addAction(mouseViewClass, "mouseUp:", MOUSE_UP);
                addAction(mouseViewClass, "rightMouseDown:", IGNORE);
                addAction(mouseViewClass, "rightMouseUp:", RIGHT_MOUSE_UP);
                registerClass(mouseViewClass);
            }
            return mouseViewClass;
        }

        private static Pointer allocateClass(String parent, String name) {
            return OBJC.getFunction("objc_allocateClassPair").invokePointer(new Object[] {type(parent), name, 0L});
        }

        private static void addAction(Pointer type, String name, Action callback) {
            OBJC.getFunction("class_addMethod").invoke(Byte.TYPE, new Object[] {type, selector(name), callback, "v@:@"});
        }

        private static void registerClass(Pointer type) {
            OBJC.getFunction("objc_registerClassPair").invokeVoid(new Object[] {type});
        }

        static Pointer type(String name) {
            return OBJC.getFunction("objc_getClass").invokePointer(new Object[] {name});
        }

        static Pointer selector(String name) {
            return OBJC.getFunction("sel_registerName").invokePointer(new Object[] {name});
        }

        static Pointer statusBar() { return object(type("NSStatusBar"), "systemStatusBar"); }
        static Pointer string(String text) { return object(type("NSString"), "stringWithUTF8String:", text); }
        static Pointer object(Pointer target, String name, Object... args) {
            return MSG.invokePointer(arguments(target, name, args));
        }
        static boolean bool(Pointer target, String name, Object... args) {
            return (Byte) MSG.invoke(Byte.TYPE, arguments(target, name, args)) != 0;
        }
        static long integer(Pointer target, String name, Object... args) {
            return MSG.invokeLong(arguments(target, name, args));
        }
        static double decimal(Pointer target, String name) {
            return MSG.invokeDouble(arguments(target, name, new Object[0]));
        }
        static void send(Pointer target, String name, Object... args) {
            MSG.invokeVoid(arguments(target, name, args));
        }
        private static Object[] arguments(Pointer target, String name, Object[] args) {
            Object[] result = new Object[args.length + 2];
            result[0] = target;
            result[1] = selector(name);
            System.arraycopy(args, 0, result, 2, args.length);
            return result;
        }
    }
}
