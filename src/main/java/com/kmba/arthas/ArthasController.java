package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import com.kmba.tunnel.ArthasWsWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/arthas")
public class ArthasController {
    private static final Logger logger = LoggerFactory.getLogger(ArthasController.class);
    private static final Map<String, Process> arthasProcesses = new ConcurrentHashMap<>();
    private static final String EMBEDDED_ARTHAS_BOOT = "/arthas/arthas-boot.jar";
    private static volatile String cachedArthasBootPath;
    private static volatile Long localArthasBootPid;
    private static volatile java.lang.Thread kmbaMainThread;
    private static volatile boolean shuttingDown = false;
    private static volatile boolean hookRegistered = false;
    private static final int LOCAL_WS_PORT = 8563;

    @PostConstruct
    public void installShutdownHook() {
        if (hookRegistered) return;
        synchronized (ArthasController.class) {
            if (hookRegistered) return;
            Runtime.getRuntime().addShutdownHook(new java.lang.Thread(() -> {
                shuttingDown = true;
                forceStopAllArthas();
                closeGlobalWsWrapperQuietly();
            }, "kmba-arthas-shutdown-hook"));
            hookRegistered = true;
        }
    }

    @RequestMapping("/checkPort")
    public String checkPort(@RequestParam String ip, @RequestParam int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 2000);
            return "open";
        } catch (Exception e) {
            return "closed";
        }
    }

    @RequestMapping("/processes")
    public JSONArray listProcesses() {
        JSONArray processes = new JSONArray();
        try {
            final long selfPid = getCurrentPid();
            Process process = Runtime.getRuntime().exec("jps -l");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    long pidLong;
                    try { pidLong = Long.parseLong(parts[0]); } catch (Exception nfe) { continue; }
                    JSONObject proc = new JSONObject();
                    proc.put("pid", parts[0]);
                    proc.put("name", parts[1]);

                    if ((selfPid <= 0 || pidLong != selfPid)
                            && !parts[1].contains("sun.tools.jps.Jps")
                            && !parts[1].contains("sun.tools.jcmd.JCmd")
                            && !parts[1].contains("com.kmba.KmbaApplication")) {
                        processes.add(proc);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to list processes", e);
        }
        return processes;
    }

    @RequestMapping("/connect")
    public String connect(@RequestParam String pid) {
        try {
            final long selfPid = getCurrentPid();
            try {
                if (selfPid > 0 && Long.parseLong(pid) == selfPid) {
                    return "error: can not attach arthas to KMBA itself (pid=" + selfPid + ")";
                }
            } catch (Exception ignored) {}

            stopAllArthas();
            closeGlobalWsWrapperQuietly();
            captureMainThreadIfNeeded();

            final long bootPid = startLocalArthasBoot(pid);
            System.out.println(bootPid);
            if (bootPid <= 0) {
                return "error: failed to start local arthas-boot";
            }
            localArthasBootPid = bootPid;
            startMainThreadGuard(bootPid, pid);

            // Wait until local ws port is ready, so subsequent list/jad calls won't hang.
            if (!waitPortOpen("127.0.0.1", LOCAL_WS_PORT, 8000)) {
                return "error: local arthas ws port not ready: " + LOCAL_WS_PORT;
            }
            ArthasWsWrapper.setGlobalAgentInfo("127.0.0.1", LOCAL_WS_PORT);
            return "success";
        } catch (Exception e) {
            logger.error("Failed to connect to process " + pid, e);
            return "error: " + e.getMessage();
        }
    }

    @RequestMapping("/connectRemote")
    public String connectRemote(@RequestParam String ip, @RequestParam int port) {
        try {
            stopAllArthas();
            closeGlobalWsWrapperQuietly();
            ArthasWsWrapper.setGlobalAgentInfo(ip, port);
            return "success";
        } catch (Exception e) {
            logger.error("Failed to connect to remote " + ip + ":" + port, e);
            return "error: " + e.getMessage();
        }
    }

    @RequestMapping("/stop")
    public String stop() {
        stopAllArthas();
        return "success";
    }

    @PreDestroy
    public void onShutdown() {
        logger.info("Application is shutting down, stopping Arthas...");
        shuttingDown = true;
        forceStopAllArthas();
        closeGlobalWsWrapperQuietly();
    }

    @RequestMapping("/exec")
    public String exec(@RequestParam String cmd) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            List<String> result = wrapper.runCmd(cmd);
            if (result == null) return "Error: Connection failed or timeout";
            return String.join("\n", result);
        } catch (Exception e) {
            logger.error("Failed to execute command: " + cmd, e);
            return "Error: " + e.getMessage();
        }
    }

    private void stopAllArthas() {
        if (shuttingDown) {
            forceStopAllArthas();
            return;
        }
        Long pid = localArthasBootPid;
        if (pid != null) {
            try {
                killPid(pid, false);
                long deadline = System.currentTimeMillis() + 2000;
                while (System.currentTimeMillis() < deadline && isPidAlive(pid)) {
                    java.lang.Thread.sleep(120);
                }
                if (isPidAlive(pid)) {
                    killPid(pid, true);
                }
                logger.info("Stopped old Arthas process (pid={})", pid);
            } catch (Exception e) {
                logger.error("Error stopping Arthas process (pid=" + pid + ")", e);
            } finally {
                localArthasBootPid = null;
            }
        }
        Process p = arthasProcesses.remove("current");
        if (p != null) {
            try {
                p.destroyForcibly();
            } catch (Exception ignored) {}
        }
    }

    private static void forceStopAllArthas() {
        Long pid = localArthasBootPid;
        if (pid != null) {
            try {
                killPid(pid, true);
            } catch (Exception ignored) {
            } finally {
                localArthasBootPid = null;
            }
        }
        Process p = arthasProcesses.remove("current");
        if (p != null) {
            try { p.destroyForcibly(); } catch (Exception ignored) {}
        }
    }

    private static long getCurrentPid() {
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            if (name == null) return -1;
            int at = name.indexOf('@');
            String pid = at > 0 ? name.substring(0, at) : name;
            return Long.parseLong(pid);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static boolean waitPortOpen(String ip, int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(1, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 400);
                return true;
            } catch (Exception ignored) {
                try {
                    java.lang.Thread.sleep(150);
                } catch (InterruptedException ie) {
                    java.lang.Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Ensure WebSocket client's internal non-daemon threads won't keep JVM alive after Ctrl+C.
     * Use reflection to avoid creating a new wrapper during shutdown.
     */
    private static void closeGlobalWsWrapperQuietly() {
        try {
            Field f = ArthasWsWrapper.class.getDeclaredField("globalWrapper");
            f.setAccessible(true);
            Object w = f.get(null);
            if (w instanceof ArthasWsWrapper) {
                ArthasWsWrapper wrapper = (ArthasWsWrapper) w;
                try {
                    if (wrapper.wsClient != null) {
                        wrapper.wsClient.close();
                    }
                } catch (Exception ignored) {}
            }
            try { f.set(null, null); } catch (Exception ignored) {}
        } catch (Throwable ignored) {
            // best effort
        }
    }

    private void captureMainThreadIfNeeded() {
        java.lang.Thread current = kmbaMainThread;
        if (current != null && current.isAlive()) return;
        for (java.lang.Thread t : java.lang.Thread.getAllStackTraces().keySet()) {
            if ("main".equals(t.getName())) {
                kmbaMainThread = t;
                break;
            }
        }
    }

    private void startMainThreadGuard(long localBootPid, String targetPid) {
        java.lang.Thread guard = new java.lang.Thread(() -> {
            while (isPidAlive(localBootPid)) {
                try {
                    if (shouldStopLocalArthas()) {
                        logger.info("Detected KMBA main thread exit/interruption, stop local arthas pid={}", targetPid);
                        stopIfSamePid(localBootPid);
                        break;
                    }
                    java.lang.Thread.sleep(500);
                } catch (InterruptedException e) {
                    java.lang.Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "kmba-arthas-main-guard");
        guard.setDaemon(true);
        guard.start();
    }

    private boolean shouldStopLocalArthas() {
        if (shuttingDown) return true;
        java.lang.Thread main = kmbaMainThread;
        return main != null && (main.isInterrupted() || !main.isAlive());
    }

    private void stopIfSamePid(long expectedPid) {
        Long cur = localArthasBootPid;
        if (cur == null || cur.longValue() != expectedPid) return;
        stopAllArthas();
    }

    private static boolean isWindows() {
        try {
            String os = System.getProperty("os.name");
            return os != null && os.toLowerCase().contains("win");
        } catch (Exception e) {
            return false;
        }
    }

    private static String shQuote(String s) {
        String x = String.valueOf(s);
        return "'" + x.replace("'", "'\"'\"'") + "'";
    }

    private static void killPid(long pid, boolean force) throws IOException {
        if (pid <= 0) return;
        if (isWindows()) {
            throw new IOException("kill is not supported on Windows");
        }
        String sig = force ? "-KILL" : "-TERM";
        Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "kill " + sig + " " + pid});
    }

    private static boolean isPidAlive(long pid) {
        if (pid <= 0) return false;
        if (isWindows()) return false;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "kill -0 " + pid + " >/dev/null 2>&1"});
            try { p.waitFor(400, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (Throwable ignored) {}
            return p.exitValue() == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private long startLocalArthasBoot(String targetJvmPid) throws IOException {
        if (isWindows()) {
            throw new IOException("local arthas boot start via sh is not supported on Windows");
        }
        String arthasBootPath = ensureArthasBootPath();

        // Use Runtime.exec + sh to start in background and capture PID ($!).
        String cmd =
                "java -jar " + shQuote(arthasBootPath) + " " + shQuote(targetJvmPid) +
                        " </dev/null >/dev/null 2>&1 & echo $!";
        Process p = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = r.readLine();
            if (line == null) return -1;
            try {
                return Long.parseLong(line.trim());
            } catch (NumberFormatException nfe) {
                return -1;
            }
        } finally {
            try { p.destroy(); } catch (Exception ignored) {}
        }
    }

    private String ensureArthasBootPath() throws IOException {
        String path = cachedArthasBootPath;
        if (path != null && Files.exists(Paths.get(path))) {
            return path;
        }
        synchronized (ArthasController.class) {
            path = cachedArthasBootPath;
            if (path != null && Files.exists(Paths.get(path))) {
                return path;
            }
            try (InputStream in = ArthasController.class.getResourceAsStream(EMBEDDED_ARTHAS_BOOT)) {
                if (in == null) {
                    throw new IOException("Embedded arthas-boot.jar not found in classpath: " + EMBEDDED_ARTHAS_BOOT);
                }
                Path tmp = Files.createTempFile("kmba-arthas-boot-", ".jar");
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                tmp.toFile().deleteOnExit();
                cachedArthasBootPath = tmp.toAbsolutePath().toString();
                return cachedArthasBootPath;
            }
        }
    }
}
