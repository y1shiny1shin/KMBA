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
            new Comp("filter",     "-u filter <filterName>") {
                JSONArray list() {
                    return new Filter().list();
                }
                String unload(String target, String extra) {
                    return new Filter().unload(target);
                }
            },
            new Comp("weblogicFilter", "-u weblogicFilter <filterName>") {
                JSONArray list() {
                    return new WeblogicFilter().list();
                }
                String unload(String target, String extra) {
                    return new WeblogicFilter().unload(target);
                }
            },
            new Comp("weblogicServlet", "-u weblogicServlet <urlPath>") {
                JSONArray list() {
                    return new WeblogicServlet().list();
                }
                String unload(String target, String extra) {
                    return new WeblogicServlet().unload(target);
                }
            },
            new Comp("weblogicListener", "-u weblogicListener <className>") {
                JSONArray list() {
                    return new WeblogicListener().list();
                }
                String unload(String target, String extra) {
                    return new WeblogicListener().unload(target);
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
            new Comp("jettyListener", "-u jettyListener <className>") {
                JSONArray list() {
                    return new JettyListener().list();
                }
                String unload(String target, String extra) {
                    return new JettyListener().unload(target);
                }
            },
            new Comp("jettyFilter", "-u jettyFilter <filterName>") {
                JSONArray list() {
                    return new JettyFilter().list();
                }
                String unload(String target, String extra) {
                    return new JettyFilter().unload(target);
                }
            },
            new Comp("jettyServlet", "-u jettyServlet <urlPath>") {
                JSONArray list() {
                    return new JettyServlet().list();
                }
                String unload(String target, String extra) {
                    return new JettyServlet().unload(target);
                }
            },
            new Comp("struts2Action", "-u struts2Action <urlPath>") {
                JSONArray list() {
                    return new Struts2Action().list();
                }
                String unload(String target, String extra) {
                    return new Struts2Action().unload(target);
                }
            },
            new Comp("springMvcController", "-u springMvcController <urlPath>") {
                JSONArray list() {
                    return new SpringMvcController().list();
                }
                String unload(String target, String extra) {
                    return new SpringMvcController().unload(target);
                }
            },
            new Comp("springMvcInterceptor", "-u springMvcInterceptor <className>") {
                JSONArray list() {
                    return new SpringMvcInterceptor().list();
                }
                String unload(String target, String extra) {
                    return new SpringMvcInterceptor().unload(target);
                }
            },
            new Comp("springFluxWebFilter", "-u springFluxWebFilter <className>") {
                JSONArray list() {
                    return new SpringFluxWebFilter().list();
                }
                String unload(String target, String extra) {
                    return new SpringFluxWebFilter().unload(target);
                }
            },
            new Comp("springFluxNettyHandler", "-u springFluxNettyHandler <className>") {
                JSONArray list() {
                    return new SpringFluxNettyHandler().list();
                }
                String unload(String target, String extra) {
                    return new SpringFluxNettyHandler().unload(target);
                }
            },
            new Comp("springFluxHandlerMethod", "-u springFluxHandlerMethod <urlPattern>") {
                JSONArray list() {
                    return new SpringFluxHandlerMethod().list();
                }
                String unload(String target, String extra) {
                    return new SpringFluxHandlerMethod().unload(target);
                }
            },
            new Comp("springFluxHandlerFunction", "-u springFluxHandlerFunction <url>") {
                JSONArray list() {
                    return new SpringFluxHandlerFunction().list();
                }
                String unload(String target, String extra) {
                    return new SpringFluxHandlerFunction().unload(target);
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
            new Comp("executor",   "-u executor <gently|brutly> <className>") {
                JSONArray list() {
                    return new Executor().list();
                }
                String unload(String target, String extra) {
                    // 默认温柔卸载
                    if ("brutly".equals(target)) {
                        return new Executor().unloadBrutly(extra);
                    }
                    return new Executor().unloadGently(extra);
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
            new Comp("agent",      "-u agent <className>") {
                JSONArray list() {
                    return new Agent().list();
                }
                String unload(String target, String extra) {
                    return new Agent().unload(target);
                }
            },
    };

    // ──── unload ────

    /** 返回退出码: 0 卸载成功，1 失败/未找到 */
    public static int handleUnload(String type, String target, String extra) {
        Comp c = findComp(type);
        if (c == null) {
            System.out.println(buildHelp());
            return 1;
        }
        // proxyValve / thread / socket / executor 需要额外参数校验
        if (extra == null) {
            if ("proxyValve".equals(type) || "thread".equals(type) || "socket".equals(type)
                    || "executor".equals(type)) {
                System.out.println("Usage: cli <pid> " + c.help);
                return 1;
            }
        }
        String result = c.unload(target, extra);
        // 统一格式化卸载结果
        switch (result) {
            case "success":
                System.out.println("[+] unload success");
                return 0;
            case "OGNL execute failed":
                System.out.println("[!] unload failed: OGNL execute failed");
                return 1;
            case "filterName not found":
                System.out.println("[!] filterName not found");
                return 1;
            case "urlPath not found":
                System.out.println("[!] urlPath not found");
                return 1;
            case "urlName not found":
                System.out.println("[!] urlName not found");
                return 1;
            case "urlPattern not found":
                System.out.println("[!] urlPattern not found");
                return 1;
            case "Listener is empty":
                System.out.println("[!] Listener is empty");
                return 1;
            case "Listener is not exist":
                System.out.println("[!] Listener is not exist");
                return 1;
            case "Listener not found":
                System.out.println("[!] Listener not found");
                return 1;
            case "Valve not found":
                System.out.println("[!] Valve not found");
                return 1;
            case "thread not found":
                System.out.println("[!] thread not found");
                return 1;
            case "className not found":
                System.out.println("[!] className not found");
                return 1;
            case "restore failed":
                System.out.println("[!] unload failed: transformer removed but bytecode restore failed");
                return 1;
            default:
                System.out.println(result);
                return 1;
        }
    }

    // ──── jad ────

    /**
     * 完整 JAD 流程：check -> jad 或 hashcode -> jad(classInfo, hash)
     * 返回退出码: 0 成功，1 失败
     */
    public static int handleJad(String className) {
        JAD jad = new JAD();

        String checkResult = jad.check(className);

        if ("dont".equals(checkResult)) {
            System.out.println("[!] class not found: " + className);
            return 1;
        }

        if ("yes".equals(checkResult)) {
            System.out.println(jad.jad(className));
            return 0;
        }

        System.out.println("[*] class " + className + " has multiple versions, enumerating...");

        JSONArray hashes = jad.hashcode(className);
        if (hashes == null || hashes.isEmpty()) {
            System.out.println("[!] failed to get ClassLoaderHash");
            return 1;
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
        return 0;
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
    /** 返回退出码: 0 成功，1 失败 */
    public static int handleVmtool(String[] args){
        String className = args[2];
        String hash = args.length > 3 ? args[3] : null;
        if (hash != null && !hash.isEmpty()) {
            JSONObject result = VmtoolUtils.doGet(className, hash);
            printVmtoolResult(result);
            return "error".equals(result.getString("type")) ? 1 : 0;
        }

        JAD jad = new JAD();
        String check = jad.check(className);
        if ("dont".equals(check)) {
            System.out.println("[-] target class not found: " + className);
            return 1;
        }
        JSONArray hashes = jad.hashcode(className);
        if (hashes.size() == 1) {
            JSONObject result = VmtoolUtils.doGet(className, null);
            printVmtoolResult(result);
            return "error".equals(result.getString("type")) ? 1 : 0;
        }
        System.out.println("[*] class " + className + " has " + hashes.size() + " instances:\n");

        for (int i = 0; i < hashes.size(); i++) {
            JSONObject info = hashes.getJSONObject(i);
            System.out.printf("  %-5d  classLoaderHash: %s    className: %s\n",
                    i + 1,
                    info.getString("classLoaderHash"),
                    info.getString("class-info"));
        }
        System.out.println("\nPlease select a classLoaderHash and re-run:");
        System.out.println("  java -jar KMBA.jar cli <pid> -vmtool " + className + " <classLoaderHash>");
        return 0;
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

        // agent: 提示 canRetransform=true 的 transformer 需要重点关注（Agent 马注册于此）
        if ("agent".equals(c.name)) {
            System.out.println("  提示: canRetransform=true 的 transformer 需要重点关注（Agent 内存马注册于此）");
        }

        if (count == 0) {
            System.out.println("  (empty)");
            return;
        }

        // 表格标题
        String[] headers = headersOf(c);
        if (headers != null) {
            StringBuilder sb = new StringBuilder("  ");
            for (int i = 0; i < headers.length; i++) {
                if (i < headers.length - 1) {
                    sb.append(String.format("[+] %-24s", headers[i]));
                } else {
                    sb.append(String.format("[+] %s", headers[i]));
                }
            }
            System.out.println(sb.toString());
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

    /**
     * 每个组件表格的标题，按组件名返回对应的列名
     */
    private static String[] headersOf(Comp c) {
        switch (c.name) {
            case "servlet":                  return new String[]{"URLPattern", "className"};
            case "filter":                   return new String[]{"URLPattern", "filterName", "className"};
            case "weblogicFilter":           return new String[]{"filterName", "className"};
            case "weblogicServlet":          return new String[]{"URLPattern", "className"};
            case "weblogicListener":         return new String[]{"className"};
            case "listener":                 return new String[]{"className"};
            case "jettyListener":            return new String[]{"className"};
            case "jettyFilter":              return new String[]{"filterName", "className"};
            case "jettyServlet":             return new String[]{"URLPattern", "className"};
            case "springMvcController":      return new String[]{"URLPattern", "className"};
            case "struts2Action":            return new String[]{"URLPattern", "className"};
            case "springMvcInterceptor":     return new String[]{"interceptorName", "className"};
            case "springFluxWebFilter":      return new String[]{"filterName", "className"};
            case "springFluxNettyHandler":   return new String[]{"className"};
            case "springFluxHandlerMethod":  return new String[]{"URLPattern", "className"};
            case "springFluxHandlerFunction":return new String[]{"URLPattern", "className"};
            case "valve":                    return new String[]{"className"};
            case "proxyValve":               return new String[]{"type", "className"};
            case "timer":                    return new String[]{"className"};
            case "thread":                   return new String[]{"threadName", "className"};
            case "socket":                   return new String[]{"urlName", "className"};
            case "executor":                 return new String[]{"className"};
            case "upgrade":                  return new String[]{"upgradeName", "className"};
            case "agent":                    return new String[]{"className", "canRetransform"};
            default:                         return null;
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
            System.out.printf("  %-28s %s\n", "first", obj.getString("first"));
            return;
        }
        if (obj.containsKey("basic")) {
            System.out.printf("  %-28s %s\n", "basic", obj.getString("basic"));
            return;
        }
        // agent：className + canRetransform
        if (obj.containsKey("canRetransform")) {
            System.out.printf("  %-28s %s\n", obj.getString("className"), obj.getString("canRetransform"));
            return;
        }
        // 其他：单键值对 {标识: 类名}
        for (String k : obj.keySet()) {
            System.out.printf("  %-28s %s\n", k, obj.getString(k));
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
    // 按名称查找组件（包级可见，CliHandler 参数校验也会用到）
    static Comp findComp(String name) {
        for (Comp c : COMPS) {
            if (c.name.equals(name)) return c;
        }
        return null;
    }


}
