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
import com.kmba.tunnel.ArthasWsWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * KMBA CLI 入口，解析参数并调用 arthas 包下已有控制器的方法
 */
public class CliHandler {

    // ──── 组件注册：只需在此数组中增删组件，list/unload/help 自动覆盖 ────
    // 用抽象类实现方法，简化了一下代码
    private static abstract class Comp {
        final String name;
        final String help;  // 帮助信息中该组件的一行说明

        Comp(String name, String help) {
            this.name = name;
            this.help = help;
        }

        abstract JSONArray list();
        abstract String unload(String target, String extra);
    }

    private static final Comp[] COMPS = {
            new Comp("servlet",    "-u servlet <urlPath>") {
                JSONArray list() {
                    return new Servlet().list();
                }
                String unload(String target, String extra) {
                    return new Servlet().unload(target);
                }
            },
            new Comp("filter",     "-u filter <urlPattern>") {
                JSONArray list() {
                    return new Filter().list();
                }
                String unload(String target, String extra) {
                    return new Filter().unload(target);
                }
            },
            new Comp("listener",   "-u listener <className>") {
                JSONArray list() {
                    return new Listener().list();
                }
                String unload(String target, String extra) {
                    return new Listener().unload(target);
                }
            },
            new Comp("smc",        "-u smc <urlPath>") {
                JSONArray list() {
                    return new SpringMvcController().list();
                }
                String unload(String target, String extra) {
                    return new SpringMvcController().unload(target);
                }
            },
            new Comp("smi",        "-u smi <className>") {
                JSONArray list() {
                    return new SpringMvcInterceptor().list();
                }
                String unload(String target, String extra) {
                    return new SpringMvcInterceptor().unload(target);
                }
            },
            new Comp("valve",      "-u valve <className>") {
                JSONArray list() {
                    return new Valve().list();
                }
                String unload(String target, String extra) {
                    return new Valve().unload(target);
                }
            },
            new Comp("proxyValve", "-u proxyValve <first|basic> <className>") {
                JSONArray list() {
                    return new ProxyValve().list();
                }
                String unload(String target, String extra) {
                    // 默认先处理first
                    if ("basic".equals(target)) {
                        return new ProxyValve().unloadBasic(extra);
                    }
                    return new ProxyValve().unloadFirst(extra);
                }
            },
            new Comp("timer",      "-u timer <className>") {
                JSONArray list() {
                    return new Timer().list();
                }
                String unload(String target, String extra) {
                    return new Timer().unload(target);
                }
            },
            new Comp("thread",     "-u thread <threadName> <className>") {
                JSONArray list() {
                    return new com.kmba.arthas.Thread().list();
                }
                String unload(String target, String extra) {
                    return new com.kmba.arthas.Thread().unload(target, extra);
                }
            },
            new Comp("socket",     "-u socket <urlName> <className>") {
                JSONArray list() {
                    return new Socket().list();
                }
                String unload(String target, String extra) {
                    return new Socket().unload(target, extra);
                }
            },
            new Comp("executor",   "-u executor <className>") {
                JSONArray list() {
                    return new Executor().list();
                }
                String unload(String target, String extra) {
                    return new Executor().unloadGently(target);
                }
            },
            new Comp("upgrade",    "-u upgrade <upgradeName>") {
                JSONArray list() {
                    return new Upgrade().list();
                }
                String unload(String target, String extra) {
                    return new Upgrade().unload(target);
                }
            },
    };

    // 按名称查找组件
    private static Comp findComp(String name) {
        for (Comp c : COMPS) {
            if (c.name.equals(name)) return c;
        }
        return null;
    }

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

        // 预热 WebSocket
        Exception lastEx = null;
        for (int retry = 0; retry < 3; retry++) {
            try {
                ArthasWsWrapper.getWrapper().runCmd("help");
                break;
            } catch (Exception e) {
                lastEx = e;
                ArthasWsWrapper.setGlobalAgentInfo("127.0.0.1", 8563);
                java.lang.Thread.sleep(1000);
            }
        }
        if (lastEx != null) {
            System.err.println("[!] 预热失败: " + lastEx.getMessage());
            return;
        }

        // 执行命令
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
            } else {
                System.out.println(buildHelp());
            }
        } finally {
            ArthasWsWrapper.close();
            new ArthasController().stop();
        }
    }

    // ──── list ────
    private static void handleList(String type) {
        if ("all".equals(type)) {
            for (Comp c : COMPS) {
                listAndPrint(c);
            }
            return;
        }
        Comp c = findComp(type);
        if (c == null) {
            System.out.println(buildHelp());
            return;
        }
        listAndPrint(c);
    }

    private static void listAndPrint(Comp c) {
        JSONArray arr = c.list();
        int count = 0;
        if (arr != null && !arr.isEmpty()) {
            count = arr.size();
        }

        System.out.println();
        System.out.println("  " + c.name + " (" + count + ")");
        System.out.println("  ------------------------------------------------------------");

        if (count == 0) {
            System.out.println("  (empty)");
            return;
        }

        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (item instanceof JSONObject) {
                printJsonItem((JSONObject) item);
            } else {
                System.out.println("  " + item);
            }
        }
    }

    private static void printJsonItem(JSONObject obj) {
        // filter：三列
        if (obj.containsKey("urlPattern")) {
            System.out.printf("  %-28s %-28s %s\n",
                    obj.getString("urlPattern"),
                    obj.getString("filterName"),
                    obj.getString("className"));
            return;
        }
        // proxyValve：first / basic
        if (obj.containsKey("first")) {
            System.out.println("  first  -> " + obj.getString("first"));
            return;
        }
        if (obj.containsKey("basic")) {
            System.out.println("  basic  -> " + obj.getString("basic"));
            return;
        }
        // 其他：单键值对 {标识: 类名}
        for (String k : obj.keySet()) {
            System.out.printf("  %-50s %s\n", k, obj.getString(k));
        }
    }

    // ──── unload ────

    private static void handleUnload(String type, String target, String extra) {
        Comp c = findComp(type);
        if (c == null) {
            System.out.println(buildHelp());
            return;
        }
        // proxyValve / thread / socket 需要额外参数校验
        if (extra == null) {
            if ("proxyValve".equals(type) || "thread".equals(type) || "socket".equals(type)) {
                System.out.println("用法: cli <pid> " + c.help);
                return;
            }
        }
        String result = c.unload(target, extra);
        System.out.println(result);
    }

    // ──── jad ────

    /**
     * 完整 JAD 流程：check -> jad 或 hashcode -> jad(classInfo, hash)
     */
    private static void handleJad(String className) {
        JAD jad = new JAD();

        String checkResult = jad.check(className);

        if ("dont".equals(checkResult)) {
            System.out.println("[!] 类不存在: " + className);
            return;
        }

        if ("yes".equals(checkResult)) {
            System.out.println(jad.jad(className));
            return;
        }

        System.out.println("[*] 类 " + className + " 存在多个版本，正在枚举...");

        JSONArray hashes = jad.hashcode(className);
        if (hashes == null || hashes.isEmpty()) {
            System.out.println("[!] 未能获取 ClassLoaderHash");
            return;
        }

        for (int i = 0; i < hashes.size(); i++) {
            JSONObject info = hashes.getJSONObject(i);
            String classInfo = info.getString("class-info");
            String hash = info.getString("classLoaderHash");

            System.out.println();
            System.out.println("  ---- ClassLoaderHash: " + hash + " (" + classInfo + ") ----");
            System.out.println();
            System.out.println(jad.jad(classInfo, hash));
        }
    }

    // ──── 帮助 ────

    public static final String HELP = buildHelp();

    private static String buildHelp() {
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
        sb.append("  -l  all           列出全部 12 种组件\n");
        sb.append("  -u  <组件> <目标> [额外参数]\n");
        sb.append("                    卸载/移除恶意组件\n");
        sb.append("  -jad <类名>       反编译并显示类源码\n");
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
        sb.append("  java -jar KMBA.jar cli 54203 -u smc /exec\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u proxyValve first com.InjectValve\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u proxyValve basic com.InjectValve\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u thread myThread com.EvilTask\n");
        sb.append("  java -jar KMBA.jar cli 54203 -u socket /ws com.MyEndpoint\n");
        sb.append("  java -jar KMBA.jar cli 54203 -jad com.InjectServlet\n");
        sb.append("  java -jar KMBA.jar cli 54203 --log true -l all\n");
        return sb.toString();
    }
}
