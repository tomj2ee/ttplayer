package org.ttplayer.ui;

import org.ttplayer.model.Playlist;
import org.ttplayer.model.Song;
import org.ttplayer.skin.TtSkin;
import org.ttplayer.util.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class OptionsMediaPages {
    private final SystemOptionsDialog view;
    final FileAssociationService associations = new FileAssociationService();
    private final Set<String> selectedTypes = new LinkedHashSet<>(FileAssociationService.EXTENSIONS);
    private FileAssociationService.State associationState;
    private SwingWorker<FileAssociationService.State, Void> associationWorker;
    private String associationMessage = "";
    private SwingWorker<AudioConversionService.Result, AudioConversionService.Progress> conversionWorker;
    private final AtomicBoolean conversionCancelled = new AtomicBoolean();
    private String conversionMessage = "";
    private JLabel conversionStatus;
    private Path conversionSource, conversionDirectory;
    private boolean conversionInitialized;
    private List<SkinEntry> skins;
    private SwingWorker<List<SkinEntry>, Void> skinWorker;
    private String selectedSkin;
    private boolean disposed;

    OptionsMediaPages(SystemOptionsDialog view) { this.view = view; selectedSkin = view.hub.getCurrentSkinPath(); }
    boolean isConverting() { return conversionWorker != null; }
    boolean isAssociating() { return associationWorker != null; }
    boolean hasConversionSource() { return conversionSource != null && Files.isRegularFile(conversionSource); }

    void resetConversionSelection() {
        if (isConverting()) return;
        conversionInitialized = false;
        conversionSource = null; conversionDirectory = null; conversionMessage = "";
    }

    OptionsStyle.Rows conversionPage() {
        if (!conversionInitialized) {
            conversionInitialized = true;
            setConversionSource(currentPlayingFile());
        }
        OptionsStyle.Rows rows = new OptionsStyle.Rows();
        ConversionSettings settings = ConversionSettings.load();
        rows.row(OptionsStyle.label(Messages.get("options.conversion.chooseFormat")));
        JLabel source = OptionsStyle.note(Messages.get("options.conversion.source") + ": "
                + (conversionSource == null ? Messages.get("options.conversion.noSource") : conversionSource));
        source.setName("options.conversion.source"); rows.row(source, 38);
        JButton chooseFile = namedButton("options.conversion.chooseFile", this::chooseConversionSource);
        chooseFile.setEnabled(!isConverting());
        JButton current = namedButton("options.conversion.useCurrent", () -> {
            Path file = currentPlayingFile();
            if (file == null) { view.error(new IllegalStateException(Messages.get("options.conversion.noCurrent"))); return; }
            setConversionSource(file); view.refreshPage();
        });
        current.setEnabled(!isConverting() && currentPlayingFile() != null);
        rows.pair(chooseFile, current);
        ConversionSettings.Format[] formats = ConversionSettings.Format.values();
        JButton[] buttons = new JButton[formats.length];
        for (int index = 0; index < formats.length; index++) {
            final ConversionSettings.Format format = formats[index];
            buttons[index] = OptionsStyle.button(format.toString(), () -> {
                ConversionSettings next = ConversionSettings.load(); next.format = format; next.save(); view.refreshPage();
            });
            buttons[index].setName("options.conversion.format." + format.extension);
            buttons[index].setSelected(settings.format == format);
            buttons[index].setEnabled(!isConverting() && (conversionSource == null
                    || !format.extension.equalsIgnoreCase(SongUtils.getFileExtension(conversionSource.getFileName().toString()))));
        }
        for (int index = 0; index < buttons.length; index += 2) rows.pair(buttons[index], buttons[index + 1]);
        view.valueRow(rows, "options.conversion.quality", 32, 320, settings.quality, "kbps", value -> {
            ConversionSettings next = ConversionSettings.load(); next.quality = value; next.save();
        });
        Path directory = conversionDirectory != null ? conversionDirectory : conversionSource == null ? null : conversionSource.getParent();
        JLabel output = OptionsStyle.note(Messages.get("options.conversion.output") + ": "
                + (directory == null ? Messages.get("options.conversion.sameFolder") : directory));
        output.setName("options.conversion.output"); rows.row(output, 38);
        JButton choose = OptionsStyle.button(Messages.get("options.conversion.chooseFolder"), () -> {
            JFileChooser chooser = new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (directory != null) chooser.setCurrentDirectory(directory.toFile());
            if (chooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
                conversionDirectory = chooser.getSelectedFile().toPath().toAbsolutePath().normalize(); view.refreshPage();
            }
        });
        choose.setName("options.conversion.chooseFolder"); choose.setEnabled(!isConverting());
        JButton same = OptionsStyle.button(Messages.get("options.conversion.sameFolder"), () -> {
            conversionDirectory = null; view.refreshPage();
        });
        same.setName("options.conversion.sameFolder"); same.setEnabled(!isConverting()); rows.pair(choose, same);
        view.check(rows, "options.conversion.skipExisting", settings.skipExisting, value -> {
            ConversionSettings next = ConversionSettings.load(); next.skipExisting = value; next.save();
        });
        rows.row(OptionsStyle.note(Messages.get("options.conversion.keepSource")));
        conversionStatus = OptionsStyle.note(conversionMessage.isEmpty() ? Messages.get("options.conversion.focusHint") : conversionMessage);
        rows.row(conversionStatus, 38);
        return rows;
    }

    private Path currentPlayingFile() {
        Song song = view.hub.getCurrentSong();
        if (song == null || song.filePath == null) return null;
        try {
            Path file = Paths.get(song.filePath).toAbsolutePath().normalize();
            return Files.isRegularFile(file) ? file : null;
        } catch (InvalidPathException error) { return null; }
    }

    private void setConversionSource(Path source) {
        conversionSource = source; conversionDirectory = null; conversionMessage = "";
        if (source == null) return;
        ConversionSettings settings = ConversionSettings.load();
        if (settings.format.extension.equalsIgnoreCase(SongUtils.getFileExtension(source.getFileName().toString()))) {
            settings.format = settings.format == ConversionSettings.Format.FLAC ? ConversionSettings.Format.WAV : ConversionSettings.Format.FLAC;
            settings.save();
        }
    }

    private void chooseConversionSource() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Messages.get("options.conversion.chooseFile"));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY); chooser.setMultiSelectionEnabled(false);
        chooser.setFileFilter(new FileNameExtensionFilter(Messages.get("options.conversion.audioFiles"),
                FileAssociationService.EXTENSIONS.stream().map(extension -> extension.substring(1)).toArray(String[]::new)));
        if (conversionSource != null) {
            chooser.setCurrentDirectory(conversionSource.getParent().toFile()); chooser.setSelectedFile(conversionSource.toFile());
        }
        if (chooser.showOpenDialog(view) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) { view.error(new IllegalStateException(Messages.get("options.conversion.sourceMissing"))); return; }
        setConversionSource(source); view.refreshPage();
    }

    private List<Song> selectedSongs() {
        List<Song> songs = view.hub.getPlaylist().getSelectedSongs();
        if (songs.isEmpty()) {
            Playlist playlist = view.hub.getPlaylistManager().getCurrentPlaylist();
            if (playlist != null && !playlist.songs.isEmpty()) songs.add(playlist.songs.get(0));
        }
        return songs;
    }

    void toggleConversion() {
        if (isConverting()) { conversionCancelled.set(true); setConversionMessage(Messages.get("options.conversion.cancelling")); return; }
        if (!hasConversionSource()) { view.error(new IllegalStateException(Messages.get("options.conversion.selectFirst"))); return; }
        List<Path> files = Collections.singletonList(conversionSource);
        ConversionSettings settings = ConversionSettings.load();
        settings.directory = conversionDirectory == null ? "" : conversionDirectory.toString();
        conversionCancelled.set(false);
        conversionWorker = new SwingWorker<AudioConversionService.Result, AudioConversionService.Progress>() {
            @Override protected AudioConversionService.Result doInBackground() {
                return AudioConversionService.convert(files, settings, conversionCancelled::get, progress -> publish(progress));
            }
            @Override protected void process(List<AudioConversionService.Progress> values) {
                if (disposed || values.isEmpty()) return;
                AudioConversionService.Progress progress = values.get(values.size() - 1);
                setConversionMessage(Messages.get("options.conversion.progress", progress.completed, progress.total, progress.filename));
            }
            @Override protected void done() {
                conversionWorker = null;
                if (disposed) return;
                try {
                    AudioConversionService.Result result = get();
                    setConversionMessage(Messages.get("options.conversion.finished", result.succeeded, result.failed, result.skipped)
                            + (result.cancelled ? " · " + Messages.get("options.conversion.cancelled") : "")
                            + (result.error.isEmpty() ? "" : " · " + result.error));
                } catch (Exception error) { setConversionMessage(error.getMessage()); }
                if (view.getSelectedPage() == 2) view.refreshPage();
                else view.updateFooter();
            }
        };
        setConversionMessage(Messages.get("options.conversion.starting"));
        conversionWorker.execute(); view.refreshPage();
    }

    private void setConversionMessage(String message) {
        conversionMessage = message;
        if (conversionStatus != null) { conversionStatus.setText(message); conversionStatus.setToolTipText(message); }
    }

    OptionsStyle.Rows associationsPage() {
        OptionsStyle.Rows rows = new OptionsStyle.Rows();
        if (!associations.isSupported()) { rows.row(OptionsStyle.note(Messages.get("options.associations.unsupported"))); return rows; }
        boolean mac = com.sun.jna.Platform.isMac();
        rows.row(OptionsStyle.label(Messages.get(mac ? "options.associations.macSelect" : "options.associations.select")));
        rows.row(OptionsStyle.note(Messages.get(mac ? "options.associations.macConfirm" : "options.associations.confirm")));
        JPanel actions = new JPanel(new GridLayout(1, 3, 6, 0)); actions.setOpaque(false);
        actions.add(namedButton("options.associations.all", () -> { selectedTypes.addAll(FileAssociationService.EXTENSIONS); view.refreshPage(); }));
        actions.add(namedButton("options.associations.none", () -> { selectedTypes.clear(); view.refreshPage(); }));
        actions.add(namedButton("options.associations.refresh", this::refreshAssociations)); rows.row(actions);
        JButton defaults = namedButton(mac ? "options.associations.finder" : "options.associations.defaults", () -> {
            try {
                List<Song> songs = selectedSongs();
                associations.openDefaults(songs.isEmpty() ? null : Paths.get(songs.get(0).filePath));
            } catch (Exception error) { view.error(error); }
        });
        if (mac) rows.row(defaults);
        else rows.pair(namedButton("options.associations.remove", () -> {
            if (JOptionPane.showConfirmDialog(view, Messages.get("options.associations.removeConfirm"), view.getTitle(),
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) runAssociation(true);
        }), defaults);
        rows.row(OptionsStyle.note(associationMessage.isEmpty() ? Messages.get("options.associations.status") : associationMessage), 38);
        FileAssociationService.State state = associationState == null ? new FileAssociationService.State() : associationState;
        JPanel formats = new JPanel(new GridLayout(0, view.getWidth() >= 720 ? 2 : 1, 8, 0)); formats.setOpaque(false);
        for (String extension : FileAssociationService.EXTENSIONS) {
            String label = extension + "   " + Messages.get(state.registered.contains(extension) ? "options.associations.registered" : "options.associations.notRegistered")
                    + " · " + (state.defaults.contains(extension) ? "TTPlayer" : Messages.get("options.associations.other"));
            JCheckBox checkbox = OptionsStyle.checkbox(label, selectedTypes.contains(extension), selected -> {
                if (selected) selectedTypes.add(extension); else selectedTypes.remove(extension);
            });
            checkbox.setName("options.associations.type" + extension); formats.add(checkbox);
        }
        int columns = view.getWidth() >= 720 ? 2 : 1;
        rows.row(formats, ((FileAssociationService.EXTENSIONS.size() + columns - 1) / columns) * 30);
        rows.row(OptionsStyle.note(Messages.get(mac ? "options.associations.macInstall" : "options.associations.moved")));
        rows.row(OptionsStyle.note(Messages.get(mac ? "options.associations.macUnchecked" : "options.associations.noForce")));
        if (associationState == null && !isAssociating()) refreshAssociations();
        return rows;
    }

    private JButton namedButton(String key, Runnable action) {
        JButton button = OptionsStyle.button(Messages.get(key), action); button.setName(key); return button;
    }

    void registerFiles() { runAssociation(false); }

    private void runAssociation(boolean remove) {
        if (isAssociating()) return;
        Set<String> types = new LinkedHashSet<>(selectedTypes);
        queryAssociations(() -> {
            if (remove) associations.unregister(); else associations.register(types);
            associationMessage = Messages.get(remove ? "options.associations.removed" : "options.associations.applied");
        });
    }

    private void refreshAssociations() { if (!isAssociating()) queryAssociations(() -> {}); }

    private interface AssociationAction { void run() throws Exception; }
    private void queryAssociations(AssociationAction action) {
        associationWorker = new SwingWorker<FileAssociationService.State, Void>() {
            @Override protected FileAssociationService.State doInBackground() throws Exception { action.run(); return associations.query(); }
            @Override protected void done() {
                associationWorker = null;
                if (disposed) return;
                try { associationState = get(); }
                catch (Exception error) {
                    associationState = new FileAssociationService.State();
                    Throwable cause = error.getCause() == null ? error : error.getCause(); associationMessage = cause.getMessage();
                }
                if (view.getSelectedPage() == 6) view.refreshPage();
                view.updateFooter();
            }
        };
        associationWorker.execute(); view.updateFooter();
    }

    int skinCount() { return skins == null ? -1 : skins.size(); }

    OptionsStyle.Rows skinsPage() {
        OptionsStyle.Rows rows = new OptionsStyle.Rows();
        if (skins == null) {
            rows.row(OptionsStyle.note(Messages.get("options.skins.loading")));
            loadSkins();
        } else {
            JPanel gallery = new JPanel(new OptionsLayout.Columns(4, 190, 12)); gallery.setOpaque(false);
            gallery.setName("options.skins.gallery");
            for (SkinEntry entry : skins) gallery.add(new SkinCard(entry, gallery));
            if (!skins.isEmpty()) rows.row(gallery, 0);
            else rows.row(OptionsStyle.note(Messages.get("skinSelect.notFound")));
        }
        return rows;
    }

    JPanel skinsFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0)); footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));
        footer.add(namedButton("options.skins.open", () -> {
            JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter("TTPlayer (*.skn)", "skn"));
            if (chooser.showOpenDialog(view) != JFileChooser.APPROVE_OPTION) return;
            String specification = SkinLoader.findSkinPath(chooser.getSelectedFile());
            SkinEntry entry = SkinEntry.load(specification);
            if (entry == null) { view.error(new IllegalArgumentException(Messages.get("dialog.invalidSkin"))); return; }
            if (skins == null) skins = new ArrayList<>();
            skins.removeIf(existing -> existing.specification.equals(specification)); skins.add(entry);
            selectedSkin = specification; view.refreshPage();
        }), BorderLayout.WEST);
        footer.add(OptionsStyle.note(Messages.get("options.skins.doubleClick")));
        return footer;
    }

    private void loadSkins() {
        if (skinWorker != null) return;
        skinWorker = new SwingWorker<List<SkinEntry>, Void>() {
            @Override protected List<SkinEntry> doInBackground() {
                List<SkinEntry> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
                List<String> specifications = new ArrayList<>(SkinLoader.listClasspathSkins());
                specifications.add(view.hub.getCurrentSkinPath());
                for (String specification : specifications) {
                    if (isCancelled()) break;
                    if (specification == null || !seen.add(skinName(specification).toLowerCase(Locale.ROOT))) continue;
                    SkinEntry entry = SkinEntry.load(specification); if (entry != null) result.add(entry);
                }
                result.sort(Comparator.comparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER)); return result;
            }
            @Override protected void done() {
                skinWorker = null;
                if (disposed) return;
                try { skins = get(); } catch (Exception error) { skins = new ArrayList<>(); }
                if (view.getSelectedPage() == 3) view.refreshPage();
            }
        };
        skinWorker.execute();
    }

    void applySelectedSkin() { applySkin(selectedSkin); }
    void applySkin(String specification) {
        if (specification == null || !SkinLoader.loadSkinFrom(new TtSkin(), specification)) {
            view.error(new IllegalArgumentException(Messages.get("dialog.invalidSkin"))); return;
        }
        selectedSkin = specification;
        SwingUtilities.invokeLater(() -> { if (!disposed) { view.hub.reloadSkin(specification); view.toFront(); } });
    }

    private static String skinName(String specification) {
        String name = specification.replace('\\', '/'); return name.substring(name.lastIndexOf('/') + 1);
    }

    private static final class SkinEntry {
        String specification, name;
        BufferedImage image;
        static SkinEntry load(String specification) {
            if (specification == null) return null;
            TtSkin skin = new TtSkin(); if (!SkinLoader.loadSkinFrom(skin, specification)) return null;
            SkinEntry entry = new SkinEntry(); entry.specification = specification;
            String filename = skinName(specification);
            entry.name = skin.getSkinName();
            if (entry.name == null || entry.name.trim().isEmpty()) entry.name = filename.replaceFirst("(?i)\\.skn$", "");
            for (TtSkin.WindowDef definition : skin.getWindows()) {
                if (!"player_window".equalsIgnoreCase(definition.name) || definition.image == null) continue;
                byte[] data = skin.getBmp(definition.image);
                if (data == null) break;
                BufferedImage image = SkinWindow.decodeBmp(data, skin.getTransparentColor());
                if (image != null) {
                    entry.image = new BufferedImage(188, 82, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D canvas = entry.image.createGraphics(); canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    double scale = Math.min(188.0 / image.getWidth(), 82.0 / image.getHeight());
                    int width = Math.max(1, (int) (image.getWidth() * scale)), height = Math.max(1, (int) (image.getHeight() * scale));
                    canvas.drawImage(image, (188 - width) / 2, (82 - height) / 2, width, height, null); canvas.dispose();
                }
                break;
            }
            return entry;
        }
    }

    private final class SkinCard extends JButton {
        private final SkinEntry entry;
        SkinCard(SkinEntry entry, JPanel gallery) {
            this.entry = entry; setName("options.skins.card:" + entry.specification);
            setPreferredSize(new Dimension(190, 120)); setToolTipText(entry.name);
            setContentAreaFilled(false); setBorderPainted(false); setRolloverEnabled(true);
            addActionListener(event -> { selectedSkin = entry.specification; gallery.repaint(); });
            addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2) applySkin(entry.specification); } });
            registerKeyboardAction(event -> applySkin(entry.specification), KeyStroke.getKeyStroke("ENTER"), WHEN_FOCUSED);
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = OptionsStyle.graphics(graphics);
            boolean selected = skinName(entry.specification).equalsIgnoreCase(skinName(selectedSkin));
            OptionsStyle.paintRoundedBox(canvas, getWidth(), getHeight(), 12,
                    selected ? OptionsStyle.HEADER : OptionsStyle.SURFACE,
                    selected || hasFocus() ? OptionsStyle.ACCENT : OptionsStyle.EDGE);
            canvas.setFont(FontUtils.getDefaultChineseFont(12));
            FontMetrics metrics = canvas.getFontMetrics();
            int captionTop = getHeight() - 8 - metrics.getHeight();
            int availableWidth = Math.max(0, getWidth() - 16), availableHeight = Math.max(0, captionTop - 14);
            if (entry.image != null && availableWidth > 0 && availableHeight > 0) {
                double scale = Math.min(1.0, Math.min((double) availableWidth / entry.image.getWidth(), (double) availableHeight / entry.image.getHeight()));
                int width = Math.max(1, (int) (entry.image.getWidth() * scale)), height = Math.max(1, (int) (entry.image.getHeight() * scale));
                canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                canvas.drawImage(entry.image, (getWidth() - width) / 2, 8 + (availableHeight - height) / 2, width, height, null);
            }
            canvas.setColor(selected ? OptionsStyle.ACCENT : OptionsStyle.TEXT);
            canvas.drawString(OptionsStyle.fitText(metrics, entry.name, availableWidth), 8, captionTop + metrics.getAscent());
            if (skinName(entry.specification).equalsIgnoreCase(skinName(view.hub.getCurrentSkinPath()))) {
                String active = OptionsStyle.fitText(metrics, Messages.get("options.skins.active"), availableWidth - 12);
                if (!active.isEmpty()) {
                    int width = metrics.stringWidth(active) + 12;
                    canvas.setColor(OptionsStyle.ACCENT); canvas.fillRoundRect(getWidth() - width - 8, 8, width, metrics.getHeight() + 4, 6, 6);
                    canvas.setColor(Color.WHITE); canvas.drawString(active, getWidth() - width - 2, 10 + metrics.getAscent());
                }
            }
            canvas.dispose();
        }
    }

    void dispose() {
        disposed = true; conversionCancelled.set(true);
        if (skinWorker != null) skinWorker.cancel(true);
        if (associationWorker != null) associationWorker.cancel(true);
    }
}
