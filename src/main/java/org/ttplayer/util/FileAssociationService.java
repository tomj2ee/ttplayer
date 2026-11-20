package org.ttplayer.util;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class FileAssociationService {
    public static final List<String> EXTENSIONS = Collections.unmodifiableList(Arrays.asList(
            ".mp3", ".flac", ".wav", ".ogg", ".opus", ".wma", ".aac", ".m4a", ".mp4", ".ape", ".wv", ".aiff", ".aif"));
    static final String PROG_ID = "TTPlayer.Java.Audio";
    private static final String CLASSES = "Software\\Classes\\";
    private static final String CAPABILITIES = "Software\\TTPlayerJava\\Capabilities";
    private final Registry registry;

    interface Registry {
        String read(String path, String name) throws IOException;
        String defaultClass(String extension) throws IOException;
        void write(String path, String name, String value) throws IOException;
        void deleteValue(String path, String name) throws IOException;
        void deleteKey(String path) throws IOException;
        void changed();
    }

    public FileAssociationService() { registry = Platform.isWindows() ? new WindowsRegistry() : null; }
    FileAssociationService(Registry registry) { this.registry = registry; }
    public boolean isSupported() { return registry != null || Platform.isMac(); }

    public static final class State {
        public final Set<String> registered = new LinkedHashSet<>(), defaults = new LinkedHashSet<>();
    }

    public State query() throws IOException {
        State state = new State();
        if (registry == null) return Platform.isMac() ? MacAssociations.query() : state;
        boolean hasCommand = registry.read(CLASSES + PROG_ID + "\\shell\\open\\command", "") != null;
        for (String extension : EXTENSIONS) {
            if (hasCommand && registry.read(CLASSES + extension + "\\OpenWithProgids", PROG_ID) != null)
                state.registered.add(extension);
            String selected = registry.read("Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\" + extension + "\\UserChoice", "ProgId");
            if (selected == null) selected = registry.defaultClass(extension);
            if (PROG_ID.equalsIgnoreCase(selected)) state.defaults.add(extension);
        }
        return state;
    }

    public static String launchCommand(Path javaExecutable, Path jar) {
        return quote(javaExecutable) + " -jar " + quote(jar) + " \"%1\"";
    }

    private static String quote(Path path) {
        String value = path.toAbsolutePath().normalize().toString();
        if (value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid application path");
        return "\"" + value + "\"";
    }

    public void register(Set<String> extensions) throws IOException {
        Set<String> selected = checked(extensions);
        if (selected.isEmpty()) throw new IOException(Messages.get("options.associations.selectFirst"));
        if (registry == null) {
            if (Platform.isMac()) { MacAssociations.register(selected); return; }
            throw new IOException(Messages.get("options.associations.unsupported"));
        }
        Path jar;
        try { jar = Paths.get(FileAssociationService.class.getProtectionDomain().getCodeSource().getLocation().toURI()); }
        catch (Exception error) { throw new IOException(error); }
        Path executable = Paths.get(System.getProperty("java.home"), "bin", "javaw.exe");
        if (!Files.isRegularFile(jar) || !jar.toString().toLowerCase(Locale.ROOT).endsWith(".jar") || !Files.isRegularFile(executable))
            throw new IOException(Messages.get("options.associations.packageFirst"));
        Path icon = Paths.get(System.getProperty("user.home"), ".ttplayer", "TTPlayer.ico");
        Files.createDirectories(icon.getParent());
        try (InputStream input = FileAssociationService.class.getResourceAsStream("/ico/TTPlayer.ico")) {
            if (input == null) throw new IOException("Missing TTPlayer.ico");
            Files.copy(input, icon, StandardCopyOption.REPLACE_EXISTING);
        }
        register(selected, launchCommand(executable, jar), quote(icon) + ",0");
    }

    void register(Set<String> extensions, String command, String icon) throws IOException {
        Set<String> selected = checked(extensions);
        registry.write(CLASSES + PROG_ID, "", "TTPlayer Audio");
        registry.write(CLASSES + PROG_ID + "\\DefaultIcon", "", icon);
        registry.write(CLASSES + PROG_ID + "\\shell\\open\\command", "", command);
        registry.write(CAPABILITIES, "ApplicationName", "TTPlayer Java");
        registry.write(CAPABILITIES, "ApplicationDescription", "TTPlayer desktop music player");
        registry.write(CAPABILITIES, "ApplicationIcon", icon);
        for (String extension : selected) {
            registry.write(CLASSES + extension + "\\OpenWithProgids", PROG_ID, "");
            registry.write(CAPABILITIES + "\\FileAssociations", extension, PROG_ID);
        }
        registry.write("Software\\RegisteredApplications", "TTPlayer Java", CAPABILITIES);
        registry.changed();
    }

    public void unregister() throws IOException {
        if (registry == null) throw new IOException(Messages.get("options.associations.unsupported"));
        for (String extension : EXTENSIONS) registry.deleteValue(CLASSES + extension + "\\OpenWithProgids", PROG_ID);
        registry.deleteValue("Software\\RegisteredApplications", "TTPlayer Java");
        registry.deleteKey(CLASSES + PROG_ID);
        registry.deleteKey("Software\\TTPlayerJava\\Capabilities");
        registry.changed();
    }

    public void openDefaults(Path audio) throws IOException {
        if (Platform.isWindows()) new ProcessBuilder("explorer.exe", "ms-settings:defaultapps").start();
        else if (Platform.isMac()) {
            if (audio == null || !Files.exists(audio)) throw new IOException(Messages.get("options.associations.chooseAudio"));
            new ProcessBuilder("/usr/bin/open", "-R", audio.toAbsolutePath().toString()).start();
        } else throw new IOException(Messages.get("options.associations.unsupported"));
    }

    private static Set<String> checked(Set<String> extensions) {
        Set<String> selected = new LinkedHashSet<>(extensions);
        if (!EXTENSIONS.containsAll(selected)) throw new IllegalArgumentException("Unsupported file extension");
        return selected;
    }

    public interface Advapi32 extends StdCallLibrary {
        int RegOpenKeyExW(Pointer root, WString path, int reserved, int access, PointerByReference key);
        int RegCreateKeyExW(Pointer root, WString path, int reserved, WString type, int options, int access, Pointer security, PointerByReference key, IntByReference disposition);
        int RegQueryValueExW(Pointer key, WString name, Pointer reserved, IntByReference type, byte[] data, IntByReference size);
        int RegSetValueExW(Pointer key, WString name, int reserved, int type, byte[] data, int size);
        int RegDeleteValueW(Pointer key, WString name);
        int RegDeleteTreeW(Pointer root, WString path);
        int RegCloseKey(Pointer key);
    }

    public interface Shell32 extends StdCallLibrary { void SHChangeNotify(int event, int flags, Pointer first, Pointer second); }

    private static final class WindowsRegistry implements Registry {
        private static final Advapi32 API = Native.load("advapi32", Advapi32.class);
        private static final Pointer USER = Pointer.createConstant(-2147483647L), ROOT = Pointer.createConstant(-2147483648L);

        private static void check(int status) throws IOException {
            if (status != 0) throw new IOException("Windows registry error: " + status);
        }

        private String read(Pointer root, String path, String name) throws IOException {
            PointerByReference handle = new PointerByReference();
            int status = API.RegOpenKeyExW(root, new WString(path), 0, 0x20019, handle);
            if (status == 2) return null;
            check(status);
            try {
                byte[] data = new byte[32768];
                IntByReference size = new IntByReference(data.length), type = new IntByReference();
                status = API.RegQueryValueExW(handle.getValue(), new WString(name), null, type, data, size);
                if (status == 2) return null;
                check(status);
                return new String(data, 0, Math.max(0, size.getValue() - 2), StandardCharsets.UTF_16LE);
            } finally { API.RegCloseKey(handle.getValue()); }
        }

        public String read(String path, String name) throws IOException { return read(USER, path, name); }
        public String defaultClass(String extension) throws IOException { return read(ROOT, extension, ""); }
        public void write(String path, String name, String value) throws IOException {
            PointerByReference handle = new PointerByReference();
            check(API.RegCreateKeyExW(USER, new WString(path), 0, null, 0, 0x20006, null, handle, null));
            try {
                byte[] data = (value + "\0").getBytes(StandardCharsets.UTF_16LE);
                check(API.RegSetValueExW(handle.getValue(), new WString(name), 0, 1, data, data.length));
            } finally { API.RegCloseKey(handle.getValue()); }
        }
        public void deleteValue(String path, String name) throws IOException {
            PointerByReference handle = new PointerByReference();
            int status = API.RegOpenKeyExW(USER, new WString(path), 0, 0x20006, handle);
            if (status == 2) return;
            check(status);
            try { status = API.RegDeleteValueW(handle.getValue(), new WString(name)); if (status != 2) check(status); }
            finally { API.RegCloseKey(handle.getValue()); }
        }
        public void deleteKey(String path) throws IOException {
            int status = API.RegDeleteTreeW(USER, new WString(path));
            if (status != 2) check(status);
        }
        public void changed() { Native.load("shell32", Shell32.class).SHChangeNotify(0x08000000, 0, null, null); }
    }

    public interface CoreFoundation extends Library {
        Pointer CFStringCreateWithCString(Pointer allocator, byte[] string, int encoding);
        byte CFStringGetCString(Pointer string, byte[] buffer, long capacity, int encoding);
        void CFRelease(Pointer value);
        long CFArrayGetCount(Pointer array);
        Pointer CFArrayGetValueAtIndex(Pointer array, long index);
        Pointer CFURLCreateWithFileSystemPath(Pointer allocator, Pointer path, int style, byte directory);
    }

    public interface CoreServices extends Library {
        Pointer UTTypeCreatePreferredIdentifierForTag(Pointer tagClass, Pointer tag, Pointer conformsTo);
        Pointer LSCopyDefaultRoleHandlerForContentType(Pointer type, int roles);
        Pointer LSCopyAllRoleHandlersForContentType(Pointer type, int roles);
        int LSSetDefaultRoleHandlerForContentType(Pointer type, int roles, Pointer identifier);
        int LSRegisterURL(Pointer url, byte update);
    }

    private static final class MacAssociations {
        private static final CoreFoundation FOUNDATION = Native.load("CoreFoundation", CoreFoundation.class);
        private static final CoreServices SERVICES = Native.load("CoreServices", CoreServices.class);
        private static Pointer string(String text) { return FOUNDATION.CFStringCreateWithCString(null, (text + "\0").getBytes(StandardCharsets.UTF_8), 0x08000100); }
        private static void release(Pointer pointer) { if (pointer != null && !Pointer.NULL.equals(pointer)) FOUNDATION.CFRelease(pointer); }
        private static String text(Pointer pointer) {
            if (pointer == null) return "";
            byte[] bytes = new byte[4096];
            if (FOUNDATION.CFStringGetCString(pointer, bytes, bytes.length, 0x08000100) == 0) return "";
            return Native.toString(bytes, "UTF-8");
        }
        private static Path bundle() throws IOException {
            try {
                Path path = Paths.get(FileAssociationService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                while (path != null) { if (path.toString().endsWith(".app")) return path; path = path.getParent(); }
            } catch (Exception error) { throw new IOException(error); }
            throw new IOException(Messages.get("options.associations.macBundle"));
        }
        private static String identifier() throws IOException {
            Process process = new ProcessBuilder("/usr/bin/plutil", "-extract", "CFBundleIdentifier", "raw", "-o", "-",
                    bundle().resolve("Contents/Info.plist").toString()).redirectErrorStream(true).start();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("plutil timeout"); }
                byte[] bytes = new byte[4096];
                int count = process.getInputStream().read(bytes);
                if (process.exitValue() != 0 || count <= 0) throw new IOException(Messages.get("options.associations.macBundle"));
                return new String(bytes, 0, count, StandardCharsets.UTF_8).trim();
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            finally { process.destroy(); }
        }
        private static Pointer type(String extension) {
            Pointer tagClass = string("public.filename-extension"), tag = string(extension.substring(1));
            try { return SERVICES.UTTypeCreatePreferredIdentifierForTag(tagClass, tag, null); }
            finally { release(tagClass); release(tag); }
        }
        static State query() throws IOException {
            State state = new State();
            String identifier;
            try { identifier = identifier(); } catch (IOException error) { return state; }
            for (String extension : EXTENSIONS) {
                Pointer type = type(extension), current = null, handlers = null;
                if (type == null) continue;
                try {
                    current = SERVICES.LSCopyDefaultRoleHandlerForContentType(type, -1);
                    if (identifier.equals(text(current))) state.defaults.add(extension);
                    handlers = SERVICES.LSCopyAllRoleHandlersForContentType(type, -1);
                    if (handlers != null) for (long index = 0; index < FOUNDATION.CFArrayGetCount(handlers); index++)
                        if (identifier.equals(text(FOUNDATION.CFArrayGetValueAtIndex(handlers, index)))) state.registered.add(extension);
                } finally { release(type); release(current); release(handlers); }
            }
            return state;
        }
        static void register(Set<String> selected) throws IOException {
            Pointer identifier = string(identifier()), path = string(bundle().toString());
            Pointer url = FOUNDATION.CFURLCreateWithFileSystemPath(null, path, 0, (byte) 1);
            try {
                int status = SERVICES.LSRegisterURL(url, (byte) 1);
                if (status != 0) throw new IOException("LaunchServices: " + status);
                for (String extension : selected) {
                    Pointer type = type(extension);
                    if (type == null) throw new IOException("Unknown audio type: " + extension);
                    try {
                        status = SERVICES.LSSetDefaultRoleHandlerForContentType(type, -1, identifier);
                        if (status != 0) throw new IOException("LaunchServices: " + status);
                    } finally { release(type); }
                }
            } finally { release(identifier); release(path); release(url); }
        }
    }
}
