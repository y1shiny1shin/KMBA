package com.kmba.cli;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.arthas.ArthasController;
import com.kmba.arthas.Executor;
import com.kmba.arthas.Filter;
import com.kmba.arthas.JAD;
import com.kmba.arthas.Listener;
import com.kmba.arthas.ProxyValve;
import com.kmba.arthas.Servlet;
import com.kmba.arthas.Socket;
import com.kmba.arthas.SpringMvcController;
import com.kmba.arthas.SpringMvcInterceptor;
import com.kmba.arthas.Timer;
import com.kmba.arthas.Upgrade;
import com.kmba.arthas.Valve;
import com.kmba.Utils.OGNLUtils;
import com.kmba.Utils.Util;
import com.kmba.tunnel.ArthasWsWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.kmba.cli.CompHandler.*;

/**
 * KMBA CLI 入口，解析参数并调用 arthas 包下已有控制器的方法
 */
public class CliHandler {
    // ──── 主入口 ────
    /**
     * CLI 主入口，args 为去掉 "cli" 之后的部分
     */
    public static void handle(String[] args) throws Exception {
        // 取出 --log 开关
        boolean logEnabled = false;
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        for (int i = 0; i < argList.size() - 1; i++) {
            if ("--log".equals(argList.get(i))) {
                logEnabled = "true".equals(argList.get(i + 1));
                argList.remove(i);
                argList.remove(i);
                break;
            }
        }
        args = argList.toArray(new String[0]);

        // --help 任意位置生效
        for (String a : args) {
            if ("--help".equals(a)) {
                System.out.println(buildHelp());
                return;
            }
        }

        // 默认关闭日志
        if (!logEnabled) {
            LogConfig.disableLogging();
        }

        if (args.length < 2) {
            System.out.println(buildHelp());
            return;
        }

        // 连接前校验参数，非法参数直接返回，不浪费时间 attach
        String argError = validateArgs(args);
        if (argError != null) {
            System.out.println(argError);
            return;
        }

        // 连接目标 JVM
        String pid = args[0];
        System.out.println("[*] connecting to PID: " + pid);
        ArthasController arthas = new ArthasController();
        String connectResult = arthas.connect(pid);
        if (!"success".equals(connectResult)) {
            System.err.println("[!] connect failed: " + connectResult);
            return;
        }
        System.out.println("[+] connected");

        // 连接建立后立刻关 OGNL strict：_useStricterInvocation 是 static final，
        // HotSpot 会把 strict=true 期间跑热的方法按 true 折叠进编译码，之后对字段的
        // flip/options 全部失效（-l all 中 proxyValve 五连拦的根因）。必须赶在任何
        // 组件的 vmtool 求值之前翻成 false，让折叠落在 false 上
        // 没必要做了，在connect的时候就已经设置了
//        OGNLUtils.setStrictModeClose();

        // 执行命令，执行指定参数
        try {
            String op = args[1];
            if ("-l".equals(op)) {
                if (args.length < 3) {
                    System.out.println(buildHelp());
                    return;
                }
                handleList(args[2]);
            } else if ("-u".equals(op)) {
                if (args.length < 4) {
                    System.out.println(buildHelp());
                    return;
                }
                String extra = args.length > 4 ? args[4] : null;
                handleUnload(args[2], args[3], extra);
            } else if ("-jad".equals(op)) {
                if (args.length < 3) {
                    System.out.println(buildHelp());
                    return;
                }
                handleJad(args[2]);
            } else if ("-vmtool".equals(op)) {
                if (args.length < 3) {
                    System.out.println("Usage: cli <pid> -vmtool <className> [classLoaderHash]");
                    return;
                }
                handleVmtool(args);
            } else {
                System.out.println(buildHelp());
            }
        } finally {
            ArthasWsWrapper.close();
            new ArthasController().stop();
        }
    }

    /**
     * 连接前校验参数（纯本地判断，无 IO），非法参数返回要打印的错误信息，
     * 合法返回 null。避免无效指令浪费时间 attach Arthas。
     */
    private static String validateArgs(String[] args) {
        String op = args[1];
        if ("-l".equals(op)) {
            if (args.length < 3) return buildHelp();
            String type = args[2];
            if (!"all".equals(type) && findComp(type) == null) return buildHelp();
        } else if ("-u".equals(op)) {
            if (args.length < 4) return buildHelp();
            Comp c = findComp(args[2]);
            if (c == null) return buildHelp();
            // proxyValve / thread / socket / executor 需要额外参数
            if (args.length < 5
                    && ("proxyValve".equals(args[2]) || "thread".equals(args[2])
                        || "socket".equals(args[2]) || "executor".equals(args[2]))) {
                return "Usage: cli <pid> " + c.help;
            }
        } else if ("-jad".equals(op)) {
            if (args.length < 3) return buildHelp();
        } else if ("-vmtool".equals(op)) {
            if (args.length < 3) return "Usage: cli <pid> -vmtool <className> [classLoaderHash]";
        } else {
            return buildHelp();
        }
        return null;
    }

    public static final String HELP = buildHelp();

    public static String buildHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("KMBA - Kill Memshell By Arthas [^_^]\n");
        sb.append("\n");
        sb.append("Usage:\n");
        sb.append("  java -jar KMBA.jar                         Start web console\n");
        sb.append("  java -jar KMBA.jar cli <pid> <command>     CLI mode (attach to target JVM)\n");
        sb.append("  java -jar KMBA.jar --help                  Show this help\n");
        sb.append("\n");
        sb.append("Commands:\n");
        sb.append("  -l  <component>   List components of the given type\n");
        sb.append(String.format("  -l  all           List all %s component types\n" ,COMPS.length));
        sb.append("  -u  <component> <target> [extra]\n");
        sb.append("                    Unload/remove the malicious component\n");
        sb.append("  -jad <className>  Decompile and show class source code\n");
        sb.append("  -vmtool <className> [classLoaderHash]\n");
        sb.append("                    Get instance details of the class in memory\n");
        sb.append("\n");
        sb.append("Options:\n");
        sb.append("  --log true        Enable debug logging (disabled by default in CLI mode)\n");
        sb.append("  --help            Show this help\n");
        sb.append("\n");
        sb.append("Component types and -u usage:\n");
        for (Comp c : COMPS) {
            sb.append("  ");
            sb.append(String.format("%-18s", c.name));
            sb.append(c.help);
            sb.append("\n");
        }
        sb.append("\n");
        sb.append("Examples:\n");
        sb.append("  java -jar KMBA.jar cli 54203 -l all\n");
        sb.append("  java -jar KMBA.jar cli 54203 -l servlet\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u servlet /evil\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u springMvcController /exec\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u proxyValve first com.InjectValve\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u proxyValve basic com.InjectValve\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u thread myThread com.EvilTask\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u socket /ws com.MyEndpoint\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u executor gently com.InjectExecutor\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u executor brutly com.InjectExecutor\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u agent org.apache.nZcPC.HttpClientUtil\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u agent all\n");
        sb.append("  java -jar KMBA.jar cli 54203 -jad com.InjectServlet\n");
        sb.append("  java -jar KMBA.jar cli 54203 -vmtool javax.servlet.Servlet\n");
        sb.append("  java -jar KMBA.jar cli 54203 -vmtool javax.servlet.Servlet 439f5b3d\n");
        sb.append("  java -jar KMBA.jar cli 54203 --log true -l all\n");
        return sb.toString();
    }
}
