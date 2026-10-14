package com.kmba.arthas;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.Utils.ArthasWsRequest;
import com.kmba.Utils.Dict;
import com.kmba.Utils.OGNLUtils;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import com.kmba.Utils.Util;
import com.kmba.tunnel.ArthasWsWrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/arthas")
public class ArthasController {
    private static final Logger logger = LoggerFactory.getLogger(ArthasController.class);
    private static final String EMBEDDED_ARTHAS_BOOT = "/arthas/arthas-boot.jar";
    private static final String[] EMBEDDED_ARTHAS_FILES = {
            "arthas-boot.jar", "arthas-agent.jar", "arthas-core.jar",
            "arthas-spy.jar", "arthas-client.jar", "arthas.properties", "logback.xml"
    };
    private static final String[] EMBEDDED_ARTHAS_LIB = {
            "libArthasJniLibrary-x64.dll",
            "libArthasJniLibrary-x64.so",
            "libArthasJniLibrary-aarch64.so",
            "libArthasJniLibrary.dylib"
    };
    private static final String[] EMBEDDED_ASYNC_PROFILER = {
            "libasyncProfiler-linux-x64.so",
            "libasyncProfiler-linux-arm64.so",
            "libasyncProfiler-mac.dylib"
    };
    private static volatile String cachedArthasBootPath;
    private static volatile String cachedArthasHome;
    private static volatile Process localArthasProcess;
    private static volatile java.lang.Thread kmbaMainThread;
    private static volatile boolean shuttingDown = false;
    private static volatile boolean hookRegistered = false;
    private static volatile Path lastArthasBootLog;
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
            // 清理上次异常退出残留的临时文件
            cleanupStaleTempFiles();
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
            ProcessBuilder pb = new ProcessBuilder(resolveJpsBin(), "-l").redirectErrorStream(true);
            Process process = pb.start();
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

            // 完整清理旧实例（agent stop → boot 进程 kill → 端口释放）
            if (!stopAllArthas()) {
                return "error: 端口 " + LOCAL_WS_PORT + " 被占用且无法停止旧 arthas agent（请重启目标 JVM 后重试）";
            }
            captureMainThreadIfNeeded();

            final Process bootProc = startLocalArthasBoot(pid);
            localArthasProcess = bootProc;
            startMainThreadGuard(bootProc, pid);

            // 等待 attach 完成，期间检测典型失败（进程不存在等）快速返回
            String waitError = waitForArthasReady(bootProc, pid);
            if (waitError != null) {
                return waitError;
            }
            ArthasWsWrapper.setGlobalAgentInfo("127.0.0.1", LOCAL_WS_PORT);
            try {
                if (!Util.checkConnect("127.0.0.1", LOCAL_WS_PORT, 3)) {
                    return "error: arthas ws port open but connect failed (pid=" + pid + ")";
                }
            } catch (WebsocketNotConnectedException e) {}

            // 与 CLI 一致：连接建立后立刻关 OGNL strict（static final 折叠机制
            // 见 CliHandler 同名注释），否则连接后先跑的 vmtool 组件会把 true
            // 折叠进编译码，之后的 flip/options 全部失效
            OGNLUtils.setStrictModeClose();

            return "success";
        } catch (Exception e) {
            logger.error("Failed to connect to process " + pid, e);
            return "error: " + e.getMessage();
        }
    }

    @RequestMapping("/connectRemote")
    public String connectRemote(@RequestParam String ip, @RequestParam int port) {
        try {
            if (!waitPortOpen(ip, port, 30000)) {
                return "error: 无法连接 " + ip + ":" + port + "，请确认目标地址可达且 Arthas WebSocket 端口已开启";
            }
            stopAllArthas();
            ArthasWsWrapper.setGlobalAgentInfo(ip, port);
            if (!Util.checkConnect(ip, port, 3)) {
                return "error: WebSocket port open but connect failed (" + ip + ":" + port + ")";
            }
            // 与 connect() 一致：连接建立后立刻关 OGNL strict（static final 折叠
            // 机制见 CliHandler 同名注释）；远端 arthas 若已被折叠成 true 则无法
            // 挽救，但新起的远端会话必须赶在其 vmtool 求值之前翻成 false
            OGNLUtils.setStrictModeClose();

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

    /**
     * 完整停止 arthas：
     * 1. 通过 WebSocket 向目标 JVM 中的 agent 发送 stop 命令
     * 2. 杀掉 arthas-boot 进程
     * 3. 等待端口释放
     *
     * @return true 清理完成（端口已释放或本来就没占用），false 端口仍被占用
     */
    private boolean stopAllArthas() {
        if (shuttingDown) {
            forceStopAllArthas();
            return true;
        }

        // Step 1: 优先通过 stop 命令优雅停止 agent（最小影响原则）
        boolean agentWasRunning = isPortReachable("127.0.0.1", LOCAL_WS_PORT, 1000);
        if (agentWasRunning) {
            sendStopToAgent();
            // stop 命令会让 agent 关闭 WebSocket 并释放端口，等待端口释放
            if (waitForPortRelease(LOCAL_WS_PORT, 8000)) {
                // agent 已优雅停止，清理 boot 进程
                killBootProcess();
                cleanupArthasTempFiles();
                return true;
            }
            logger.warn("arthas agent did not stop gracefully after 8s, falling back to force kill");
        }

        // Step 2: stop 失败或没有 agent 在运行 → 清理 boot 进程
        killBootProcess();

        // Step 3: 再等一次端口释放（boot 进程退出后 agent 可能也会退出）
        boolean released = waitForPortRelease(LOCAL_WS_PORT, 3000);
        cleanupArthasTempFiles();
        return released;
    }

    /**
     * 通过 WebSocket 向已有 arthas agent 发送 stop 命令。
     * stop 命令会导致 agent 关闭 WebSocket 并 shutdown，runCmd 返回 null 是预期行为。
     */
    private void sendStopToAgent() {
        try {
            logger.info("Sending stop to existing arthas agent on port {}...", LOCAL_WS_PORT);
            closeGlobalWsWrapperQuietly();
            ArthasWsWrapper.setGlobalAgentInfo("127.0.0.1", LOCAL_WS_PORT);
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
//            wrapper.runCmd("stop");
            // runCmd 返回 null 是正常的：agent 收到 stop 后关闭 WS，轮询超时返回 null
            // 不管了，直接发给socket发stop。
            wrapper.wsClient.send(JSON.toJSONString(new ArthasWsRequest(Dict.STOP)));
        } catch (Exception e) {
            logger.warn("Could not send stop to arthas agent: {}", e.getMessage());
        } finally {
            closeGlobalWsWrapperQuietly();
        }
    }

    private void killBootProcess() {
        Process p = localArthasProcess;
        if (p != null) {
            try {
                p.destroy();
                long deadline = System.currentTimeMillis() + 2000;
                while (System.currentTimeMillis() < deadline && p.isAlive()) {
                    java.lang.Thread.sleep(120);
                }
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
                logger.info("Killed arthas-boot process");
            } catch (Exception e) {
                logger.error("Error killing arthas-boot process", e);
            } finally {
                localArthasProcess = null;
            }
        }
    }

    /**
     * 停止 arthas 后清理临时文件（arthas-home 解压目录、boot jar、boot 日志），
     * 避免 /tmp 下残留大量 kmba-arthas-* 文件
     */
    private static void cleanupArthasTempFiles() {
        String home = cachedArthasHome;
        if (home != null) {
            try {
                deleteRecursively(Paths.get(home));
            } catch (Exception e) {
                logger.warn("Failed to delete arthas home temp dir: {}", e.getMessage());
            }
            cachedArthasHome = null;
        }
        String bootPath = cachedArthasBootPath;
        if (bootPath != null) {
            try {
                Files.deleteIfExists(Paths.get(bootPath));
            } catch (Exception e) {
                logger.warn("Failed to delete arthas boot jar: {}", e.getMessage());
            }
            cachedArthasBootPath = null;
        }
        if (lastArthasBootLog != null) {
            try {
                Files.deleteIfExists(lastArthasBootLog);
            } catch (Exception ignored) {
            }
            lastArthasBootLog = null;
        }
        // 目标 JVM 中 arthas 的 vmtool 会将 JNI 库复制到 tmpdir 后加载且从不删除，
        // 停止时一并清理（POSIX 下已加载的库可安全 unlink，Windows 上被锁定时删除失败忽略）
        cleanupArthasJniTmpFiles();
    }

    /**
     * 递归删除目录（单个文件删除失败时忽略，例如 Windows 上被锁定的 dll）
     */
    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            logger.warn("Failed to walk temp dir {}: {}", dir, e.getMessage());
        }
    }

    /**
     * 启动时清理历史残留的 kmba-arthas-* 临时文件（上次异常退出留下的）
     */
    private static void cleanupStaleTempFiles() {
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null || tmp.isEmpty()) return;
        try {
            Path tmpDir = Paths.get(tmp);
            if (!Files.isDirectory(tmpDir)) return;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(tmpDir, "kmba-arthas-*")) {
                for (Path p : ds) {
                    try {
                        if (Files.isDirectory(p)) {
                            deleteRecursively(p);
                        } else {
                            Files.deleteIfExists(p);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            // 上次异常退出时，arthas 的 JNI 库 tmp 文件也无法被清理
            cleanupArthasJniTmpFiles();
        } catch (Exception e) {
            logger.warn("Failed to clean stale arthas temp files: {}", e.getMessage());
        }
    }

    /**
     * 清理 arthas vmtool 在 tmpdir 中创建的 ArthasJniLibrary*.tmp 残留文件。
     * 该类文件由目标 JVM 中的 arthas 生成（vmtool 初始化时将 JNI 库复制到
     * 临时文件后 System.load），arthas 自身不删除，只能由 KMBA 清理。
     */
    private static void cleanupArthasJniTmpFiles() {
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null || tmp.isEmpty()) return;
        try {
            Path tmpDir = Paths.get(tmp);
            if (!Files.isDirectory(tmpDir)) return;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(tmpDir, "ArthasJniLibrary*.tmp")) {
                for (Path p : ds) {
                    try {
                        Files.deleteIfExists(p);
                        logger.debug("Cleaned arthas jni tmp file: {}", p);
                    } catch (Exception ignored) {
                        // Windows 上文件可能被目标 JVM 锁定，删除失败忽略，下次启动再清理
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to clean arthas jni tmp files: {}", e.getMessage());
        }
    }

    private boolean waitForPortRelease(int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!isPortReachable("127.0.0.1", port, 300)) {
                return true; // 端口已释放
            }
            try {
                java.lang.Thread.sleep(300);
            } catch (InterruptedException e) {
                java.lang.Thread.currentThread().interrupt();
                return false;
            }
        }
        return !isPortReachable("127.0.0.1", port, 300);
    }

    private static boolean isPortReachable(String ip, int port, long timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), (int) timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void forceStopAllArthas() {
        Process p = localArthasProcess;
        if (p != null) {
            try {
                p.destroyForcibly();
            } catch (Exception ignored) {
            } finally {
                localArthasProcess = null;
            }
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
     * 等待 arthas-boot attach 完成（WS 端口就绪）。
     * 等待期间监控 boot 进程状态与日志内容，典型失败快速返回：
     * - 日志出现 "No such process" 等 attach 失败特征 → process not found
     * - boot 进程提前退出 → 返回退出原因
     * - 超时未就绪 → 返回超时错误
     *
     * @return null 端口就绪，否则返回错误信息
     */
    private String waitForArthasReady(Process bootProc, String pid) {
        long deadline = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < deadline) {
            // 端口已就绪
            if (isPortReachable("127.0.0.1", LOCAL_WS_PORT, 300)) {
                return null;
            }
            String tail = readLastLines(lastArthasBootLog, 40);
            // 目标进程不存在 / 无法 attach
            if (containsAttachFailure(tail)) {
                logger.error("arthas-boot failed to attach pid {}. Last output:\n{}", pid, tail);
                return "error: process not found or not attachable (pid=" + pid + ")";
            }
            // boot 进程提前退出
            if (!bootProc.isAlive()) {
                logger.error("arthas-boot exited unexpectedly. Last output:\n{}", tail);
                return "error: arthas-boot exited unexpectedly (pid=" + pid + ")"
                        + (tail.isEmpty() ? "" : "\n--- arthas-boot output ---\n" + tail);
            }
            try {
                java.lang.Thread.sleep(150);
            } catch (InterruptedException ie) {
                java.lang.Thread.currentThread().interrupt();
                return "error: interrupted while waiting for arthas";
            }
        }
        // 超时未就绪
        String tail = readLastLines(lastArthasBootLog, 30);
        logger.error("arthas-boot did not open ws port {} in time. Last output:\n{}", LOCAL_WS_PORT, tail);
        return "error: local arthas ws port not ready: " + LOCAL_WS_PORT
                + (tail.isEmpty() ? "" : "\n--- arthas-boot output ---\n" + tail);
    }

    /**
     * 判断 arthas-boot 日志是否包含典型 attach 失败特征
     */
    private static boolean containsAttachFailure(String log) {
        if (log == null || log.isEmpty()) return false;
        return log.contains("No such process")
                || log.contains("AttachNotSupportedException")
                || log.contains("not responding");
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

    private void startMainThreadGuard(Process bootProc, String targetPid) {
        java.lang.Thread guard = new java.lang.Thread(() -> {
            while (bootProc.isAlive()) {
                try {
                    if (shouldStopLocalArthas()) {
                        logger.info("Detected KMBA main thread exit/interruption, stop local arthas pid={}", targetPid);
                        stopIfSameProcess(bootProc);
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

    private void stopIfSameProcess(Process expected) {
        Process cur = localArthasProcess;
        if (cur == null || cur != expected) return;
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

    private static String resolveJavaBin() {
        String javaHome = System.getProperty("java.home");
        String exeName = isWindows() ? "java.exe" : "java";
        if (javaHome != null && !javaHome.isEmpty()) {
            File f = new File(javaHome, "bin" + File.separator + exeName);
            if (f.isFile()) return f.getAbsolutePath();
        }
        return exeName;
    }

    private static String resolveJpsBin() {
        String javaHome = System.getProperty("java.home");
        String exeName = isWindows() ? "jps.exe" : "jps";
        if (javaHome != null && !javaHome.isEmpty()) {
            File f = new File(javaHome, "bin" + File.separator + exeName);
            if (f.isFile()) return f.getAbsolutePath();
            // Java 8 JRE 嵌在 JDK 内的常见布局：<jdk>/jre 是 java.home，jps 在 <jdk>/bin。
            File parentBin = new File(new File(javaHome).getParentFile(), "bin" + File.separator + exeName);
            if (parentBin.isFile()) return parentBin.getAbsolutePath();
        }
        return exeName;
    }

    private Process startLocalArthasBoot(String targetJvmPid) throws IOException {
        String arthasBootPath = ensureArthasBootPath();
        String arthasHome = ensureArthasHome();
        Path logFile = Files.createTempFile("kmba-arthas-boot-", ".log");
        logFile.toFile().deleteOnExit();
        lastArthasBootLog = logFile;
        File devNull = new File(isWindows() ? "NUL" : "/dev/null");
        ProcessBuilder pb = new ProcessBuilder(
                resolveJavaBin(), "-jar", arthasBootPath,
                "--arthas-home", arthasHome,
                "--attach-only",
                targetJvmPid)
                .redirectInput(ProcessBuilder.Redirect.from(devNull))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));
        return pb.start();
    }

    private static String readLastLines(Path file, int n) {
        if (file == null) return "";
        try {
            if (!Files.exists(file)) return "";
            List<String> all = Files.readAllLines(file);
            int from = Math.max(0, all.size() - n);
            return String.join("\n", all.subList(from, all.size()));
        } catch (Exception e) {
            return "";
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

    /**
     * 将 classpath 中嵌入的完整 Arthas 发行包解压到临时目录，
     * 作为 --arthas-home 使用，实现完全离线运行。
     */
    private static String ensureArthasHome() throws IOException {
        String home = cachedArthasHome;
        if (home != null && Files.isDirectory(Paths.get(home))) {
            return home;
        }
        synchronized (ArthasController.class) {
            home = cachedArthasHome;
            if (home != null && Files.isDirectory(Paths.get(home))) {
                return home;
            }
            Path homeDir = Files.createTempDirectory("kmba-arthas-home-");
            homeDir.toFile().deleteOnExit();

            // 解压顶层 jar / 配置文件
            for (String name : EMBEDDED_ARTHAS_FILES) {
                copyResource("/arthas/" + name, homeDir.resolve(name));
            }

            // 解压 lib/ 下的 native 库
            Path libDir = homeDir.resolve("lib");
            Files.createDirectories(libDir);
            for (String name : EMBEDDED_ARTHAS_LIB) {
                copyResource("/arthas/lib/" + name, libDir.resolve(name));
            }

            // 解压 async-profiler/ 下的 native 库
            Path profilerDir = homeDir.resolve("async-profiler");
            Files.createDirectories(profilerDir);
            for (String name : EMBEDDED_ASYNC_PROFILER) {
                copyResource("/arthas/async-profiler/" + name, profilerDir.resolve(name));
            }

            cachedArthasHome = homeDir.toAbsolutePath().toString();
            logger.info("Arthas home prepared at: {}", cachedArthasHome);
            return cachedArthasHome;
        }
    }

    private static void copyResource(String resourcePath, Path target) throws IOException {
        try (InputStream in = ArthasController.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Missing embedded resource: " + resourcePath);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
