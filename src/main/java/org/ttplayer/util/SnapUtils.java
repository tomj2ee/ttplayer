package org.ttplayer.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class SnapUtils {

    private static final int SNAP_THRESHOLD = 10;

    // 吸附类型常量
    private static final int SNAP_TO_RIGHT = 1;   // 本窗口左边缘吸附到目标右边缘
    private static final int SNAP_TO_LEFT = 2;    // 本窗口右边缘吸附到目标左边缘
    private static final int SNAP_TO_BOTTOM = 3;  // 本窗口上边缘吸附到目标下边缘
    private static final int SNAP_TO_TOP = 4;     // 本窗口下边缘吸附到目标上边缘

    // 所有注册的窗口
    private static final List<JFrame> allWindows = new ArrayList<>();
    private static JFrame mainPlayerWindow;

    // 每个窗口的吸附状态
    private static final Map<JFrame, WindowState> windowStates = new HashMap<>();
    private static final AWTEventListener dragEvents = SnapUtils::handleDragEvent;
    private static boolean dragEventsInstalled;

    // ====================================================================
    //  Data Structures
    // ====================================================================

    private static class SnapEdge {
        JFrame target;       // 吸附到的目标窗口
        int type;            // 吸附类型
        int offsetX;         // 相对于目标的X偏移
        int offsetY;         // 相对于目标的Y偏移
    }

    private static class WindowState {
        List<SnapEdge> edges = new ArrayList<>();    // 此窗口吸附到其他窗口
        List<JFrame> attachedBy = new ArrayList<>(); // 哪些窗口吸附到此窗口
        int pressX, pressY;    // 鼠标按下时的窗口位置
        int mouseOffsetX, mouseOffsetY; // 鼠标相对于窗口的偏移
        MouseAdapter dragHandler;
    }

    private static class GroupDragState {
        Map<JFrame, Point> positions = new HashMap<>(); // 每个窗口在拖动开始时的位置
    }

    private static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    // ====================================================================
    //  Public API
    // ====================================================================

    public static void registerMainPlayer(JFrame mainPlayer) {
        mainPlayerWindow = mainPlayer;
        registerWindow(mainPlayer);
    }

    public static void registerChildWindow(JFrame child) {
        registerWindow(child);
    }

    public static void unregisterWindow(JFrame window) {
        allWindows.remove(window);
        // 清除此窗口吸附到别人的关系
        WindowState state = windowStates.remove(window);
        if (state != null) {
            for (SnapEdge edge : state.edges) {
                WindowState targetState = windowStates.get(edge.target);
                if (targetState != null) {
                    targetState.attachedBy.remove(window);
                }
            }
        }
        // 清除别人吸附到此窗口的关系
        for (WindowState otherState : windowStates.values()) {
            otherState.edges.removeIf(edge -> edge.target == window);
        }
        if (windowStates.isEmpty() && dragEventsInstalled) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(dragEvents);
            dragEventsInstalled = false;
        }
    }

    public static void setupMainPlayerDrag(JFrame mainPlayer) {
        WindowState state = getOrCreateState(mainPlayer);
        setupWindowDragInternal(mainPlayer, state, true);
    }

    public static void setupChildWindowDrag(JFrame child) {
        WindowState state = getOrCreateState(child);
        setupWindowDragInternal(child, state, false);
    }

    // ====================================================================
    //  Internal Drag Handling
    // ====================================================================

    private static void setupWindowDragInternal(JFrame window, WindowState state, boolean isMainPlayer) {
        MouseAdapter adapter = new MouseAdapter() {
            private boolean canDrag = false;
            private GroupDragState groupDragState = null;

            @Override
            public void mousePressed(MouseEvent e) {
                canDrag = canDragWindow(window, e);
                if (canDrag) {
                    state.pressX = window.getX();
                    state.pressY = window.getY();
                    state.mouseOffsetX = e.getXOnScreen() - window.getX();
                    state.mouseOffsetY = e.getYOnScreen() - window.getY();

                    // 只有主窗口拖动时，才记录整个吸附组在拖动开始时的位置
                    if (isMainPlayer) {
                        // 先检查并更新所有窗口的吸附状态，确保吸附关系正确
                        refreshAllSnapStates();

                        groupDragState = new GroupDragState();
                        Set<JFrame> group = collectAttachedGroupFromMain();
                        for (JFrame w : group) {
                            groupDragState.positions.put(w, new Point(w.getX(), w.getY()));
                        }
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!canDrag) return;
                int newX = e.getXOnScreen() - state.mouseOffsetX;
                int newY = e.getYOnScreen() - state.mouseOffsetY;
                int dx = newX - state.pressX;
                int dy = newY - state.pressY;

                if (isMainPlayer && groupDragState != null) {
                    // 主播放器拖动：移动整个吸附组
                    for (Map.Entry<JFrame, Point> entry : groupDragState.positions.entrySet()) {
                        JFrame w = entry.getKey();
                        Point startPos = entry.getValue();
                        w.setLocation(startPos.x + dx, startPos.y + dy);
                    }
                } else {
                    // 子窗口拖动：只移动自己，并尝试吸附
                    window.setLocation(newX, newY);
                    performSnap(window);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                canDrag = false;
                groupDragState = null;
            }
        };

        state.dragHandler = adapter;
        if (!dragEventsInstalled) {
            Toolkit.getDefaultToolkit().addAWTEventListener(dragEvents,
                    AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
            dragEventsInstalled = true;
        }
    }

    private static void handleDragEvent(AWTEvent event) {
        if (!(event instanceof MouseEvent)) return;
        MouseEvent mouse = (MouseEvent) event;
        if (mouse.getID() != MouseEvent.MOUSE_PRESSED && mouse.getID() != MouseEvent.MOUSE_DRAGGED
                && mouse.getID() != MouseEvent.MOUSE_RELEASED) return;
        Component source = mouse.getComponent();
        if (source == null) return;
        Window owner = source instanceof Window ? (Window) source : SwingUtilities.getWindowAncestor(source);
        if (!(owner instanceof JFrame)) return;
        JFrame window = (JFrame) owner;
        WindowState state = windowStates.get(window);
        if (state == null || state.dragHandler == null) return;
        if (source != window && !SwingUtilities.isDescendingFrom(source, window.getContentPane())) return;
        switch (mouse.getID()) {
            case MouseEvent.MOUSE_PRESSED: state.dragHandler.mousePressed(mouse); break;
            case MouseEvent.MOUSE_DRAGGED: state.dragHandler.mouseDragged(mouse); break;
            case MouseEvent.MOUSE_RELEASED: state.dragHandler.mouseReleased(mouse); break;
            default: break;
        }
    }

    private static boolean canDragWindow(JFrame window, MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e) || Boolean.TRUE.equals(window.getRootPane().getClientProperty("independentLayout"))) return false;
        java.awt.Point pointer = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), window.getContentPane());
        Component comp = SwingUtilities.getDeepestComponentAt(window.getContentPane(), pointer.x, pointer.y);
        for (Component current = comp; current != null; current = current.getParent()) {
            if (!(current instanceof JPanel || current instanceof JLabel)) return false;
            if (Boolean.TRUE.equals(((JComponent) current).getClientProperty("innerDrag"))) return false;
            if (current == window.getContentPane()) break;
        }
        if (comp == null) return true;
        return comp instanceof JPanel || comp instanceof JLabel;
    }

    // ====================================================================
    //  Snap Logic
    // ====================================================================

    private static void performSnap(JFrame movingWindow) {
        List<JFrame> possibleTargets = new ArrayList<>();
        for (JFrame w : allWindows) {
            if (w != movingWindow && w.isVisible() && !Boolean.TRUE.equals(w.getRootPane().getClientProperty("independentLayout"))) {
                possibleTargets.add(w);
            }
        }
        JFrame snappedTo = trySnapTo(movingWindow, possibleTargets);
        updateSnapEdges(movingWindow, snappedTo);
    }

    private static JFrame trySnapTo(JFrame movingWindow, List<JFrame> targets) {
        int mx = movingWindow.getX();
        int my = movingWindow.getY();
        int mw = movingWindow.getWidth();
        int mh = movingWindow.getHeight();

        JFrame bestTarget = null;
        int bestType = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (JFrame target : targets) {
            int tx = target.getX();
            int ty = target.getY();
            int tw = target.getWidth();
            int th = target.getHeight();

            // 1. 本窗口左边缘吸附到目标右边缘
            int distRight = Math.abs(mx - (tx + tw));
            if (distRight <= SNAP_THRESHOLD && distRight < bestDistance) {
                bestDistance = distRight;
                bestTarget = target;
                bestType = SNAP_TO_RIGHT;
            }

            // 2. 本窗口右边缘吸附到目标左边缘
            int distLeft = Math.abs((mx + mw) - tx);
            if (distLeft <= SNAP_THRESHOLD && distLeft < bestDistance) {
                bestDistance = distLeft;
                bestTarget = target;
                bestType = SNAP_TO_LEFT;
            }

            // 3. 本窗口上边缘吸附到目标下边缘
            int distBottom = Math.abs(my - (ty + th));
            if (distBottom <= SNAP_THRESHOLD && distBottom < bestDistance) {
                bestDistance = distBottom;
                bestTarget = target;
                bestType = SNAP_TO_BOTTOM;
            }

            // 4. 本窗口下边缘吸附到目标上边缘
            int distTop = Math.abs((my + mh) - ty);
            if (distTop <= SNAP_THRESHOLD && distTop < bestDistance) {
                bestDistance = distTop;
                bestTarget = target;
                bestType = SNAP_TO_TOP;
            }
        }

        // 找到最佳吸附目标，执行吸附
        if (bestTarget != null) {
            applySnap(movingWindow, bestTarget, bestType);
            return bestTarget;
        }
        return null;
    }

    private static void applySnap(JFrame movingWindow, JFrame target, int snapType) {
        int tx = target.getX();
        int ty = target.getY();
        int tw = target.getWidth();
        int th = target.getHeight();
        int mw = movingWindow.getWidth();
        int mh = movingWindow.getHeight();
        int newX = movingWindow.getX();
        int newY = movingWindow.getY();

        switch (snapType) {
            case SNAP_TO_RIGHT:
                newX = tx + tw;
                break;
            case SNAP_TO_LEFT:
                newX = tx - mw;
                break;
            case SNAP_TO_BOTTOM:
                newY = ty + th;
                break;
            case SNAP_TO_TOP:
                newY = ty - mh;
                break;
        }
        movingWindow.setLocation(newX, newY);
    }

    private static void updateSnapEdges(JFrame movingWindow, JFrame snappedTarget) {
        WindowState state = getOrCreateState(movingWindow);

        // 清除旧的吸附关系
        for (SnapEdge edge : state.edges) {
            WindowState targetState = windowStates.get(edge.target);
            if (targetState != null) {
                targetState.attachedBy.remove(movingWindow);
            }
        }
        state.edges.clear();

        // 建立新的吸附关系
        if (snappedTarget != null) {
            SnapEdge edge = new SnapEdge();
            edge.target = snappedTarget;
            edge.offsetX = movingWindow.getX() - snappedTarget.getX();
            edge.offsetY = movingWindow.getY() - snappedTarget.getY();
            state.edges.add(edge);

            WindowState targetState = getOrCreateState(snappedTarget);
            if (!targetState.attachedBy.contains(movingWindow)) {
                targetState.attachedBy.add(movingWindow);
            }
        }
    }

    // ====================================================================
    //  Group Collection (for main player drag)
    // ====================================================================

    private static Set<JFrame> collectAttachedGroupFromMain() {
        Set<JFrame> group = new LinkedHashSet<>();
        if (mainPlayerWindow == null) {
            return group;
        }
        group.add(mainPlayerWindow);
        collectAttachedRecursive(mainPlayerWindow, group);
        return group;
    }

    private static void collectAttachedRecursive(JFrame window, Set<JFrame> collected) {
        WindowState state = windowStates.get(window);
        if (state == null) return;
        for (JFrame attachedWindow : state.attachedBy) {
            if (attachedWindow.isVisible() && !Boolean.TRUE.equals(attachedWindow.getRootPane().getClientProperty("independentLayout")) && !collected.contains(attachedWindow)) {
                collected.add(attachedWindow);
                collectAttachedRecursive(attachedWindow, collected);
            }
        }
    }

    // ====================================================================
    //  Helper Methods
    // ====================================================================

    /**
     * 刷新所有窗口的吸附状态：检查哪些窗口实际吸附在一起，并更新吸附关系
     */
    private static void refreshAllSnapStates() {
        // 首先，清除所有现有的吸附关系
        for (WindowState state : windowStates.values()) {
            state.edges.clear();
            state.attachedBy.clear();
        }

        // 用一个集合来跟踪哪些窗口已经被处理了（作为吸附目标）
        Set<JFrame> processedAsTarget = new HashSet<>();
        if (mainPlayerWindow != null) {
            processedAsTarget.add(mainPlayerWindow);
        }

        // 首先处理那些可以吸附到主窗口的窗口
        for (JFrame window : allWindows) {
            if (window == mainPlayerWindow) continue;

            if (mainPlayerWindow != null) {
                int snapResult = checkSnap(window, mainPlayerWindow);
                if (snapResult > 0) {
                    updateSnapEdgesWithoutMoving(window, mainPlayerWindow);
                    processedAsTarget.add(window);
                    continue; // 已经吸附到主窗口了，不再找其他目标
                }
            }
        }

        // 然后处理其他窗口，避免循环引用（只吸附到已经处理过的窗口）
        boolean changed;
        do {
            changed = false;
            for (JFrame window : allWindows) {
                if (window == mainPlayerWindow) continue;
                if (processedAsTarget.contains(window)) continue; // 已经处理过了

                // 只找已经处理过的窗口作为吸附目标
                JFrame bestTarget = null;
                int bestDistance = Integer.MAX_VALUE;

                for (JFrame target : processedAsTarget) {
                    int snapResult = checkSnap(window, target);
                    if (snapResult > 0) {
                        int distance = getSnapDistance(window, target, snapResult);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            bestTarget = target;
                        }
                    }
                }

                if (bestTarget != null) {
                    updateSnapEdgesWithoutMoving(window, bestTarget);
                    processedAsTarget.add(window);
                    changed = true;
                }
            }
        } while (changed);
    }

    /**
     * 更新吸附关系，但不移动窗口位置
     */
    private static void updateSnapEdgesWithoutMoving(JFrame movingWindow, JFrame snappedTarget) {
        WindowState state = getOrCreateState(movingWindow);

        // 建立新的吸附关系
        SnapEdge edge = new SnapEdge();
        edge.target = snappedTarget;
        edge.offsetX = movingWindow.getX() - snappedTarget.getX();
        edge.offsetY = movingWindow.getY() - snappedTarget.getY();
        state.edges.add(edge);

        WindowState targetState = getOrCreateState(snappedTarget);
        if (!targetState.attachedBy.contains(movingWindow)) {
            targetState.attachedBy.add(movingWindow);
        }
    }

    /**
     * 检查两个窗口是否处于吸附状态（在吸附阈值范围内）
     * 返回吸附类型，如果没吸附返回 0
     */
    private static int checkSnap(JFrame window, JFrame target) {
        int wx = window.getX();
        int wy = window.getY();
        int ww = window.getWidth();
        int wh = window.getHeight();
        int tx = target.getX();
        int ty = target.getY();
        int tw = target.getWidth();
        int th = target.getHeight();

        // 检查四种吸附方式
        if (Math.abs(wx - (tx + tw)) <= SNAP_THRESHOLD) {
            return SNAP_TO_RIGHT;
        }
        if (Math.abs((wx + ww) - tx) <= SNAP_THRESHOLD) {
            return SNAP_TO_LEFT;
        }
        if (Math.abs(wy - (ty + th)) <= SNAP_THRESHOLD) {
            return SNAP_TO_BOTTOM;
        }
        if (Math.abs((wy + wh) - ty) <= SNAP_THRESHOLD) {
            return SNAP_TO_TOP;
        }
        return 0;
    }

    /**
     * 获取窗口与目标之间的吸附距离
     */
    private static int getSnapDistance(JFrame window, JFrame target, int snapType) {
        int wx = window.getX();
        int wy = window.getY();
        int ww = window.getWidth();
        int wh = window.getHeight();
        int tx = target.getX();
        int ty = target.getY();
        int tw = target.getWidth();
        int th = target.getHeight();

        switch (snapType) {
            case SNAP_TO_RIGHT: return Math.abs(wx - (tx + tw));
            case SNAP_TO_LEFT: return Math.abs((wx + ww) - tx);
            case SNAP_TO_BOTTOM: return Math.abs(wy - (ty + th));
            case SNAP_TO_TOP: return Math.abs((wy + wh) - ty);
        }
        return Integer.MAX_VALUE;
    }

    private static void registerWindow(JFrame window) {
        if (!allWindows.contains(window)) {
            allWindows.add(window);
            getOrCreateState(window);
        }
    }

    private static WindowState getOrCreateState(JFrame window) {
        return windowStates.computeIfAbsent(window, w -> new WindowState());
    }
}
