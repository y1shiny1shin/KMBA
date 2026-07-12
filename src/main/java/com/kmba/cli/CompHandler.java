package com.kmba.cli;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.arthas.*;

import static com.kmba.cli.CliHandler.buildHelp;

public class CompHandler {

    public static final Comp[] COMPS = {
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
            new Comp("sfwf",       "-u sfwf <className>") {
                JSONArray list() {
                    return new SpringFluxWebFilter().list();
                }
                String unload(String target, String extra) {
                    return new SpringFluxWebFilter().unload(target);
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

    // ──── unload ────

    public static void handleUnload(String type, String target, String extra) {
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
    public static void handleJad(String className) {
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

    // ──── list ────
    public static void handleList(String type) {
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

    // ──── vmtool ────
    public static void handleVmtool(String[] args){
        String className = args[2];
        String hash = args.length > 3 ? args[3] : null;
        if (hash != null && !hash.isEmpty()) {
            JSONObject result = VmtoolUtils.doGet(className, hash);
            printVmtoolResult(result);
            return;
        }

        JAD jad = new JAD();
        String check = jad.check(className);
        if ("dont".equals(check)) {
            System.out.println("[-] 目标类不存在: " + className);
            return;
        }
        JSONArray hashes = jad.hashcode(className);
        if (hashes.size() == 1) {
            JSONObject result = VmtoolUtils.doGet(className, null);
            printVmtoolResult(result);
            return;
        }
        System.out.println("[*] 类 " + className + " 存在 " + hashes.size() + " 个实例:\n");

        for (int i = 0; i < hashes.size(); i++) {
            JSONObject info = hashes.getJSONObject(i);
            System.out.printf("  %-5d  classLoaderHash: %s    className: %s\n",
                    i + 1,
                    info.getString("classLoaderHash"),
                    info.getString("class-info"));
        }
        System.out.println("\n请选择其中一个 classLoaderHash 重新执行:");
        System.out.println("  java -jar KMBA.jar cli <pid> -vmtool " + className + " <classLoaderHash>");
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

    private static void printVmtoolResult(JSONObject result) {
        switch (result.getString("type")) {
            case "single":
                System.out.println("[+] ClassLoaderHash: " + result.getString("classLoaderHash"));
                System.out.println(result.getString("data"));
                break;
            case "404":
                System.out.println("[!] " + result.getString("message"));
                break;
            case "error":
                System.err.println("[!] " + result.getString("message"));
                break;
            default:
                System.out.println(result.toJSONString());
        }
    }

    // 按名称查找组件
    private static Comp findComp(String name) {
        for (Comp c : COMPS) {
            if (c.name.equals(name)) return c;
        }
        return null;
    }


}
