package org.ttplayer.ui;

import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.model.Song;
import org.ttplayer.util.Messages;
import org.ttplayer.util.ReplayGainService;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;

public final class ReplayGainDialog {
    private ReplayGainDialog() {}
    public static void show(Window owner, List<Song> songs, PlayerEngine engine, boolean write) {
        if (songs.isEmpty()) return;
        if (write && JOptionPane.showConfirmDialog(owner, Messages.get("replaygain.confirm"), Messages.get("menu.replaygain"),
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        List<Song> selection = new ArrayList<>(songs);
        JDialog dialog = new JDialog(owner, Messages.get("menu.replaygain"));
        JTextArea report = new JTextArea(16, 64);
        report.setEditable(false);
        JProgressBar progress = new JProgressBar(0, selection.size());
        progress.setStringPainted(true);
        JButton cancel = new JButton(Messages.get("replaygain.cancel"));
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(progress, BorderLayout.CENTER);
        footer.add(cancel, BorderLayout.EAST);
        dialog.add(new JScrollPane(report), BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            protected Void doInBackground() {
                for (Song song : selection) {
                    if (isCancelled()) break;
                    try {
                        ReplayGainService.Result result = ReplayGainService.scan(Paths.get(song.filePath), this::isCancelled);
                        if (write) ReplayGainService.write(result, this::isCancelled, () -> engine.getCurrentSong() != null
                                && song.filePath.equals(engine.getCurrentSong().filePath) && (engine.isPlaying() || engine.isPaused()));
                        publish(song.getFileName() + "  " + result.gain + "  " + result.peak + "\n");
                    } catch (Exception error) {
                        if (!isCancelled()) publish(song.getFileName() + "  " + Messages.get("replaygain.failed") + ": " + error.getMessage() + "\n");
                    }
                }
                return null;
            }
            protected void process(List<String> chunks) {
                for (String line : chunks) { report.append(line); progress.setValue(progress.getValue() + 1); }
            }
            protected void done() { cancel.setText(Messages.get("menu.close")); }
        };
        cancel.addActionListener(event -> { worker.cancel(true); dialog.dispose(); });
        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event) { worker.cancel(true); }
            public void windowClosed(WindowEvent event) { worker.cancel(true); }
        });
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        worker.execute();
    }
}
