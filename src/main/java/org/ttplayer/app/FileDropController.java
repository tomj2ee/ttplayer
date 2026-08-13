package org.ttplayer.app;

import org.ttplayer.util.SkinLoader;
import org.ttplayer.util.SongUtils;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件拖放控制器（从 TTPlayerApplication 拆分）。
 * 歌曲 → 加入播放列表并播放；.skn → 换肤。
 */
public class FileDropController {

    private final WindowHub hub;

    public FileDropController(WindowHub hub) {
        this.hub = hub;
    }

    /** 对一批窗口注册拖放接收 */
    public void setupAll(Component... targets) {
        for (Component comp : targets) {
            if (comp == null) continue;
            setup(comp);
        }
    }

    private void setup(Component comp) {
        comp.setDropTarget(new DropTarget(comp, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Object data = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (data instanceof List) {
                        List<?> list = (List<?>) data;
                        List<File> files = new ArrayList<>();
                        for (Object o : list) {
                            if (o instanceof File) files.add((File) o);
                        }
                        handle(files);
                        dtde.dropComplete(true);
                    } else {
                        dtde.dropComplete(false);
                    }
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
        }));
    }

    public void handle(List<File> files) {
        if (files == null || files.isEmpty()) return;

        // .skn 皮肤文件 → 直接换肤（换肤会销毁重建窗口，延后到拖放事件结束再执行）
        for (File f : files) {
            if (f != null && f.getName().toLowerCase().endsWith(".skn")) {
                final String spec = SkinLoader.findSkinPath(f);
                if (spec != null) {
                    javax.swing.SwingUtilities.invokeLater(() -> hub.reloadSkin(spec));
                    return;
                }
            }
        }

        // 音频文件 → 加入播放列表并播放第一个
        List<File> audio = new ArrayList<>();
        for (File f : files) {
            if (SongUtils.isAudioFile(f)) audio.add(f);
        }
        if (!audio.isEmpty() && hub.getPlaylist() != null) {
            hub.getPlaylist().loadSongsAndPlay(audio);
        }
    }
}