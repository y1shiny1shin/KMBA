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

        // 连接目标 JVM
        String pid = args[0];
        System.out.println("[*] 正在连接 PID: " + pid);
        ArthasController arthas = new ArthasController();
        String connectResult = arthas.connect(pid);
        if (!"success".equals(connectResult)) {
            System.err.println("[!] 连接失败: " + connectResult);
            return;
        }
        System.out.println("[+] 已连接");

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
                    System.out.println("用法: cli <pid> -vmtool <className> [classLoaderHash]");
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

    public static final String HELP = buildHelp();

    public static String buildHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("KMBA - Java 内存马查杀工具\n");
        sb.append("\n");
        sb.append("用法:\n");
        sb.append("  java -jar KMBA.jar                         启动 Web 管理端\n");
        sb.append("  java -jar KMBA.jar cli <pid> <command>     CLI 模式（附加到目标 JVM）\n");
        sb.append("  java -jar KMBA.jar --help                  显示帮助\n");
        sb.append("\n");
        sb.append("命令:\n");
        sb.append("  -l  <component>   列出指定类型的组件\n");
        sb.append("  -l  all           列出全部 14 种组件\n");
        sb.append("  -u  <组件> <目标> [额外参数]\n");
        sb.append("                    卸载/移除恶意组件\n");
        sb.append("  -jad <类名>       反编译并显示类源码\n");
        sb.append("  -vmtool <类名> [classLoaderHash]\n");
        sb.append("                    获取类在内存中的实例参数\n");
        sb.append("\n");
        sb.append("选项:\n");
        sb.append("  --log true        启用调试日志（CLI 模式默认关闭）\n");
        sb.append("  --help            显示帮助\n");
        sb.append("\n");
        sb.append("组件类型及 -u 用法:\n");
        for (Comp c : COMPS) {
            sb.append("  ");
            sb.append(String.format("%-18s", c.name));
            sb.append(c.help);
            sb.append("\n");
        }
        sb.append("\n");
        sb.append("示例:\n");
        sb.append("  java -jar KMBA.jar cli 54203 -l all\n");
        sb.append("  java -jar KMBA.jar cli 54203 -l servlet\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u servlet /evil\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u springMvcController /exec\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u proxyValve first com.InjectValve\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u proxyValve basic com.InjectValve\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u thread myThread com.EvilTask\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u socket /ws com.MyEndpoint\n");
        sb.append("  java -jar KMBA.jar cli 54203 -jad com.InjectServlet\n");
        sb.append("  java -jar KMBA.jar cli 54203 -vmtool javax.servlet.Servlet\n");
        sb.append("  java -jar KMBA.jar cli 54203 -vmtool javax.servlet.Servlet 439f5b3d\n");
        sb.append("  java -jar KMBA.jar cli 54203 --log true -l all\n");
        return sb.toString();
    }
}
