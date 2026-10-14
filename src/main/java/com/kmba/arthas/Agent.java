package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.Utils.Dict;
import com.kmba.Utils.Util;
import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 内存马（transformer 型，MemShellParty TomcatFilterChainAgentInjector 全流程实测取证）
 *
 * 原理（JDK8 sun.instrument.InstrumentationImpl 实测）：
 *   Agent 加载即创建独立的 InstrumentationImpl 实例（干净 JVM + arthas = 1，
 *   每次 loadAgent +1），马通过 addTransformer(t, true) 注册：
 *     canRetransform=true  → mRetransfomableTransformerManager（Agent 马所在，必须查）
 *     canRetransform=false → mTransformerManager
 *   两个 manager 均为懒初始化（可能为 null），vmtool 实例顺序随机（须按类名匹配，
 *   不能硬编码索引）。
 *
 * 检测：枚举全部 InstrumentationImpl 实例 → 双 manager 的 mTransformerList
 *       → 输出 transformer 类名。全程纯属性访问（DefaultMemberAccess），
 *       无需 options strict false。
 *
 * 卸载（不依赖马内部任何字段名，最稳定）：
 *   1. removeTransformer（双 manager 按类名匹配移除，马失活）
 *   2. 还原目标类清单（org.* / com.* / javax.*）：sc -d 确认类存在 → 取
 *      code-source（宿主 jar 磁盘路径）→ 读磁盘原始字节落盘 → redefine 回灌。
 *      磁盘 jar 里的字节即该类加载时的原始字节，版本必然匹配；逐类容错
 *      （单类失败跳过），规避 redefineClasses 批量调用时
 *        UnmodifiableClassException / VerifyError / JDK 核心类崩溃（实测
 *        全量 retransformClasses 曾致 JVM 崩溃）。
 *
 * 注意（OGNL 实测约束）:
 *   1. 投影体/选择体内引用外层变量会劫持 #this 为 InstancesWrapper，
 *      表达式只在投影体/选择体内引用 #this
 *   2. dump 命令匹配超过 50 类需 --limit 9999
 */
@RestController
@RequestMapping("/agent")
public class Agent {

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    // list 输出: @String[normal:[com.xxx]|retransformable:[com.yyy]]
    // （两个 manager 独立输出，normal 为 canRetransform=false 注册，retransformable 为 true）
    // 注意: Util.getListResult 只传实例索引一个参数，模板用 %1$s 复用同一参数
    private String listAgentByVmtool = "vmtool -l -1 --action getInstances --className sun.instrument.InstrumentationImpl --express '#ins=instances[%1$s],#tm1=#ins.mTransformerManager,#tm2=#ins.mRetransfomableTransformerManager,#r1=#tm1==null?new java.util.ArrayList():@java.util.Arrays@asList(#tm1.mTransformerList).{(#this.mTransformer.getClass().getName())},#r2=#tm2==null?new java.util.ArrayList():@java.util.Arrays@asList(#tm2.mTransformerList).{(#this.mTransformer.getClass().getName())},\"normal:\"+#r1+\"|retransformable:\"+#r2'";

    // unload 移除输出: removed / not-found（#c>0 表示命中）
    // 必须与计数命令同带 -l -1：vmtool 默认 limit 10，实例数>10 时 instances[10+] 越界
    // （AIOOBE → unload failed），且马注册在后段实例上时恒 not-found
    private String unloadAgentByVmtool = "vmtool -l -1 --action getInstances --className sun.instrument.InstrumentationImpl --express '#ins=instances[%s],#tm1=#ins.mTransformerManager,#tm2=#ins.mRetransfomableTransformerManager,#lst1=#tm1==null?new java.util.ArrayList():@java.util.Arrays@asList(#tm1.mTransformerList),#lst2=#tm2==null?new java.util.ArrayList():@java.util.Arrays@asList(#tm2.mTransformerList),#m1=#lst1.{? #this.mTransformer.getClass().getName().equals(\"%s\")},#m2=#lst2.{? #this.mTransformer.getClass().getName().equals(\"%s\")},#c=#m1.size()+#m2.size(),#c>0&&(#m1.size()>0&&(#ins.removeTransformer(#m1[0].mTransformer)),#m2.size()>0&&(#ins.removeTransformer(#m2[0].mTransformer))),#c>0?\"removed\":\"not-found\"'";

    // JDK 9+ 版（Unsafe 绕模块）: sun.instrument 私有字段反射被模块系统拒绝
    // （InaccessibleObjectException），改用 Unsafe.objectFieldOffset/getObject 按偏移读字段；
    // removeTransformer 通过 java.lang.instrument.Instrumentation 接口的 Method 反射调用
    // （接口 exports 可访问，绕开实现类模块限制）；需先 options strict false（ognl 拦 Unsafe 方法）
    private String listAgentByVmtoolJdk9 = "vmtool -l -1 --action getInstances --className sun.instrument.InstrumentationImpl --express '#ins=instances[%1$s],#uc=@java.lang.Class@forName(\"sun.misc.Unsafe\"),#uf=#uc.getDeclaredField(\"theUnsafe\"),#uf.setAccessible(true),#u=#uf.get(null),#f1=#ins.getClass().getDeclaredField(\"mTransformerManager\"),#tm1=#u.getObject(#ins,#u.objectFieldOffset(#f1)),#f2=#ins.getClass().getDeclaredField(\"mRetransfomableTransformerManager\"),#tm2=#u.getObject(#ins,#u.objectFieldOffset(#f2)),#r1=#tm1==null?new java.util.ArrayList():@java.util.Arrays@asList(#u.getObject(#tm1,#u.objectFieldOffset(#tm1.getClass().getDeclaredField(\"mTransformerList\")))).{(#t=#this,#tf=#t.getClass().getDeclaredField(\"mTransformer\"),#u.getObject(#t,#u.objectFieldOffset(#tf)).getClass().getName())},#r2=#tm2==null?new java.util.ArrayList():@java.util.Arrays@asList(#u.getObject(#tm2,#u.objectFieldOffset(#tm2.getClass().getDeclaredField(\"mTransformerList\")))).{(#t=#this,#tf=#t.getClass().getDeclaredField(\"mTransformer\"),#u.getObject(#t,#u.objectFieldOffset(#tf)).getClass().getName())},\"normal:\"+#r1+\"|retransformable:\"+#r2'";

    private String unloadAgentByVmtoolJdk9 = "vmtool -l -1 --action getInstances --className sun.instrument.InstrumentationImpl --express '#ins=instances[%s],#uc=@java.lang.Class@forName(\"sun.misc.Unsafe\"),#uf=#uc.getDeclaredField(\"theUnsafe\"),#uf.setAccessible(true),#u=#uf.get(null),#f1=#ins.getClass().getDeclaredField(\"mTransformerManager\"),#tm1=#u.getObject(#ins,#u.objectFieldOffset(#f1)),#f2=#ins.getClass().getDeclaredField(\"mRetransfomableTransformerManager\"),#tm2=#u.getObject(#ins,#u.objectFieldOffset(#f2)),#lst1=#tm1==null?new java.util.ArrayList():@java.util.Arrays@asList(#u.getObject(#tm1,#u.objectFieldOffset(#tm1.getClass().getDeclaredField(\"mTransformerList\")))),#lst2=#tm2==null?new java.util.ArrayList():@java.util.Arrays@asList(#u.getObject(#tm2,#u.objectFieldOffset(#tm2.getClass().getDeclaredField(\"mTransformerList\")))),#m1=#lst1.{? (#t=#this,#u.getObject(#t,#u.objectFieldOffset(#t.getClass().getDeclaredField(\"mTransformer\"))).getClass().getName().equals(\"%s\"))},#m2=#lst2.{? (#t=#this,#u.getObject(#t,#u.objectFieldOffset(#t.getClass().getDeclaredField(\"mTransformer\"))).getClass().getName().equals(\"%s\"))},#c=#m1.size()+#m2.size(),#ic=@java.lang.Class@forName(\"java.lang.instrument.Instrumentation\"),#c>0&&(#m1.size()>0&&(#t2=#u.getObject(#m1[0],#u.objectFieldOffset(#m1[0].getClass().getDeclaredField(\"mTransformer\"))),#rm=#ic.getMethods().{? #this.getName().equals(\"removeTransformer\")}[0],#r1=#rm.invoke(#ins,#t2)),#m2.size()>0&&(#t3=#u.getObject(#m2[0],#u.objectFieldOffset(#m2[0].getClass().getDeclaredField(\"mTransformer\"))),#rm2=#ic.getMethods().{? #this.getName().equals(\"removeTransformer\")}[0],#r2=#rm2.invoke(#ins,#t3))),#c>0?\"removed\":\"not-found\"'";

    // Agent 马常见目标类（sc -d 确认存在后读磁盘原始字节回灌；类不存在时如实跳过）
    // 来源覆盖:
    //   工具: MSP 13 个 Agent 注入器（Tomcat/Jetty 6 变体含 ee8-ee11/WebLogic/SpringMVC/
    //     TongWeb 3 变体/Undertow/BES/Apusic/Resin/WebSphere，TARGET_CLASS 逐字提取）、
    //     JavaChains(vulhub/java-chains v2.0.0) 打包的增强版 JMG 12 个 Agent 注入器
    //     （jar 常量池 javap 取证，目标类与 MSP 一致）及原版 JMG Tomcat/SpringMVC
    //     AgentTransformer、AgentInjectTool(SummerSec)、
    //     ysoserial 系 EX-Agent(HeyDorvin/957204459)、KpLi0rn/AgentMemShell、
    //     flowerwind/Weblogic404Memshell
    //   文章: 先知《无文件落地注入Agent内存马实操》《反序列化打内存马知一二》、
    //     InfoQ/CTF导航/CSDN《Agent内存马的自动分析与查杀》（黑名单四类）、
    //     《浅谈 Java Agent 内存马》（javax/jakarta 双命名空间）、
    //     FreeBuf《内存马深度剖析与实战演练》
    private String[] dumpTargetClasses = {
            // Tomcat（MSP FilterChain / ContextValve + JMG）
            "org.apache.catalina.core.ApplicationFilterChain",
            "org.apache.catalina.core.StandardContextValve",
            // Jetty（MSP JettyHandler，含 ee8-ee11 变体）
            "org.eclipse.jetty.servlet.ServletHandler",
            "org.eclipse.jetty.ee8.servlet.ServletHandler",
            "org.eclipse.jetty.ee9.servlet.ServletHandler",
            "org.eclipse.jetty.ee10.servlet.ServletHandler$Chain",
            "org.eclipse.jetty.ee11.servlet.ServletHandler$Chain",
            "org.mortbay.jetty.servlet.ServletHandler",
            // WebLogic（MSP WebAppServletContext#securedExecute；veo/vagent 用
            //   ServletStubImpl#execute——WebLogic 另一种宿主，用户 star 项目实测）
            "weblogic.servlet.internal.WebAppServletContext",
            "weblogic.servlet.internal.ServletStubImpl",
            // Spring WebMvc
            "org.springframework.web.servlet.FrameworkServlet",
            // TongWeb（MSP TongWeb*AgentInjector TARGET_CLASSES 三变体:
            //   thor 旧版 / catalina / server，JavaChains 增强版 JMG 同）
            "com.tongweb.catalina.core.ApplicationFilterChain",
            "com.tongweb.catalina.core.StandardContextValve",
            "com.tongweb.server.core.ApplicationFilterChain",
            "com.tongweb.server.core.StandardContextValve",
            "com.tongweb.web.thor.core.ApplicationFilterChain",
            "com.tongweb.web.thor.core.StandardContextValve",
            // Undertow（MSP UndertowServletHandlerAgentInjector
            //   TARGET_CLASS=ServletInitialHandler；ServletHandler 兼容保留）
            "io.undertow.servlet.handlers.ServletInitialHandler",
            "io.undertow.servlet.handlers.ServletHandler",
            // BES
            "com.bes.enterprise.webtier.core.DefaultContextValve",
            "com.bes.enterprise.webtier.core.ApplicationFilterChain",
            // Apusic
            "com.apusic.web.container.FilterChainImpl",
            // Resin
            "com.caucho.server.http.FilterChainFilter",
            "com.caucho.server.dispatch.FilterFilterChain",
            // WebSphere
            "com.ibm.ws.webcontainer.filter.WebAppFilterManager",
            // Servlet 型（ysoserial EX-Agent-*-Servlet、flowerwind/Weblogic404Memshell
            //   改 HttpServlet.service；先知/InfoQ《Agent内存马的自动分析与查杀》黑名单亦列）
            "javax.servlet.http.HttpServlet",
            // jakarta 命名空间（Tomcat 10+ / Spring Boot 3，文章《浅谈 Java Agent 内存马》
            //   《内存马进阶》均提及需适配 javax/jakarta 双命名空间）
            "jakarta.servlet.http.HttpServlet",
            // Spring 底层 DispatcherServlet.doService（文章《Agent内存马的自动分析与查杀》
            //   黑名单：javax/servlet/http/HttpServlet#service、
            //   org/apache/catalina/core/ApplicationFilterChain#doFilter、
            //   org/springframework/web/servlet/DispatcherServlet#doService、
            //   org/apache/tomcat/websocket/server/WsFilter#doFilter）
            "org.springframework.web.servlet.DispatcherServlet",
            // Tomcat 自带 Filter 宿主 WsFilter.doFilter（先知《无文件落地注入Agent内存马实操》
            //   首选宿主类——无 lambda、长度短，Javassist 修改不易出错）
            "org.apache.tomcat.websocket.server.WsFilter",
    };

    @RequestMapping("/list")
    public JSONArray list() {
        try {
            int cnt = Util.getAgentCnt();
            JSONArray jsonArray = new JSONArray();

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            String resultAll = Util.getListResult(cnt ,listAgentByVmtool);
            // JDK 9+ 模块系统拒绝反射 sun.instrument 私有字段 → Unsafe 版重试
            if (resultAll.contains("InaccessibleObjectException")) {
                wrapper.runCmd("options strict false");
                resultAll = Util.getListResult(cnt ,listAgentByVmtoolJdk9);
            }
            String regex = "@String\\[normal:\\[([^\\]]*)\\]\\|retransformable:\\[([^\\]]*)\\]\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);
            while (matcher.find()) {
                // 非 retransformable 注册（canRetransform=false，一般非 Agent 马）
                for (String name : matcher.group(1).split(",")) {
                    name = name.trim();
                    if (name.isEmpty()) continue;
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("className", name);
                    jsonObject.put("canRetransform", false);
                    if (!jsonArray.contains(jsonObject)) {
                        jsonArray.add(jsonObject);
                    }
                }
                // retransformable 注册（Agent 马所在，canRetransform=true，重点关注）
                for (String name : matcher.group(2).split(",")) {
                    name = name.trim();
                    if (name.isEmpty()) continue;
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("className", name);
                    jsonObject.put("canRetransform", true);
                    if (!jsonArray.contains(jsonObject)) {
                        jsonArray.add(jsonObject);
                    }
                }
            }
            logger.info("/agent/list: {}" ,jsonArray);
            return jsonArray;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String className) {
        try {
            int cnt = Util.getAgentCnt();
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            int removedCnt = 0;
            int failCnt = 0;
            boolean jdk9 = false;
            for (int i = 0; i < cnt; i++) {
                if (jdk9) {
                    // JDK 9+ 需关闭 OGNL strict（ognl 拦 Unsafe 方法调用）
                    wrapper.runCmd("options strict false");
                }
                String cmd = String.format(jdk9 ? unloadAgentByVmtoolJdk9 : unloadAgentByVmtool, i, className, className);
                List<String> result = wrapper.runCmd(cmd);
                String resultStr = String.join("" ,result);
                logger.info("/agent/unload: {}" ,resultStr);
                if (resultStr.contains("Failed to execute ognl")) {
                    logger.error(resultStr);
                    // JDK 9+ 模块拒绝反射 → 切换 Unsafe 版重试当前实例
                    if (resultStr.contains("InaccessibleObjectException") && !jdk9) {
                        jdk9 = true;
                        i--;
                        continue;
                    }
                    failCnt++;
                    continue;
                }
                if (resultStr.contains("removed")) {
                    removedCnt++;
                }
            }
            if (removedCnt > 0) {
                // 移除成功 → 目标类清单逐类还原被改字节
                // （sc -d 确认存在 → 读磁盘原始字节 → redefine 回灌，不依赖 retransform 缓存）
                // JDK 9+ 同样适用：2026-08-30 重测推翻"retransform 卡死"结论；
                // 实测 JDK9+ 不还原时，advisor 类不随 removeTransformer 卸载 +
                // 嵌入字节残留 → 后门仍可执行（<4> 失败）；还原后命令失效。
                if (!restoreBytecode(wrapper)) {
                    return "restore failed";
                }
                return "success";
            }
            if (failCnt > 0) {
                return "OGNL execute failed";
            }
            return "className not found";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed";
        }
    }

    /**
     * 强制恢复字节码（前端强制恢复按钮与 CLI -r 共用）。classNames 为空时遍历
     * 全部宿主类清单 dumpTargetClasses。返回 "success"（有类还原成功）/
     * "className not found"（传入类均未加载）/ "restore failed"（dump 执行异常）。
     */
    @RequestMapping("/restore")
    public String restore(@RequestParam(value = "className", required = false) String[] classNames) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            // 未指定类名 → 遍历全部宿主类清单
            String[] targets = (classNames == null || classNames.length == 0) ? dumpTargetClasses : classNames;
            int okCnt = 0;
            for (String className : targets) {
                String result = restoreBytecode(wrapper, className);
                if ("restore failed".equals(result)) {
                    return "restore failed";
                }
                if ("success".equals(result)) {
                    okCnt++;
                }
            }
            return okCnt > 0 ? "success" : "className not found";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed";
        }
    }

    /**
     * 还原 Agent 马常见目标类被改字节（transformer 已移除时逐类处理）。不依赖
     * 马内部任何字段名。马插桩的宿主类通常只有一个，其余类回灌的是与加载时
     * 一致的磁盘原始字节，等价无副作用（unload 全清单循环实际修复的就是被改
     * 的那个类）。
     * 注: 不做全量 dump——WebLogic 类量巨大（LaunchClassLoader 几千类，
     *     org.* 单次 dump 4 分钟+），全量还原不现实；清单覆盖 MSP/JMG 全部
     *     Agent 注入器目标类，清单外目标类可手动 jad/redefine 兜底。
     * 返回 false 表示单类还原流程执行异常（redefine 失败）。
     */
    private boolean restoreBytecode(ArthasWsWrapper wrapper) {
        try {
            for (String cls : dumpTargetClasses) {
                // "className not found"（类未加载）视为通过
                if ("restore failed".equals(restoreBytecode(wrapper, cls))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
    }

    /**
     * 还原单个宿主类被改字节（restoreBytecode 清单循环与 restore 按传入类名
     * 单类还原共用）。返回 "success" / "className not found"（类未加载）/
     * "restore failed"（磁盘原始字节取不到或 redefine 执行异常）。
     *
     * 流程：sc -d 确认类存在并取 code-source（还原前先确认这个类是否存在）→
     * KMBA 本进程从磁盘读出该类原始字节 → 落盘临时文件（Dict.TMP_DIR）→
     * arthas redefine 回灌。不做 dump 比对——retransform 缓存过的字节 dump
     * 出来与磁盘原始字节存在序列化差异（2026-10-13 实测 7863B 同长 1931 字节
     * 不同），比对不成立；磁盘 jar 里的字节即该类加载时的原始字节，版本必然
     * 匹配（目标须与 KMBA 同机）。
     */
    private String restoreBytecode(ArthasWsWrapper wrapper, String className) {
        try {
            // 确认类存在 + 取 code-source（宿主 jar 磁盘路径）
            List<String> scResult = wrapper.runCmd(String.format("sc -d %s" ,className));
            String scStr = scResult == null ? null : String.join("" ,scResult);
            if (scStr == null || !scStr.contains("class-info")) {
                logger.info("/agent/restore: {} not loaded" ,className);
                return "className not found";
            }

            // 读磁盘原始字节（宿主 jar / exploded 目录 / 嵌套 jar）
            List<byte[]> originals = readOriginalBytes(extractCodeSource(scResult, className), className);
            if (originals == null || originals.isEmpty()) {
                logger.error("/agent/restore: {} code-source 字节不可读" ,className);
                return "restore failed";
            }

            // 原始字节落盘临时文件 → redefine 回灌
            String redefinePath = writeTempClass(className, originals.get(0));
            String redefineStr = String.join("" ,wrapper.runCmd(String.format("redefine %s" ,redefinePath)));
            logger.info("/agent/restore: redefine {}" ,redefineStr);
            if (redefineStr.toLowerCase().contains("error")) {
                logger.error("/agent/restore: redefine {} failed" ,className);
                return "restore failed";
            }
            return "success";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "restore failed";
        }
    }

    /**
     * 从 sc -d 输出提取 code-source 值（多 classloader 时多份）。同 JAD.hashcode
     * 的逐行提取模式。sc 默认追加子类匹配，先按 class-info 分块，只保留完整类名
     * 等于目标名的块，避免取到子类的宿主 jar。
     */
    private List<String> extractCodeSource(List<String> lines, String className) {
        List<String> codeSources = new ArrayList<>();
        boolean blockMatch = false;
        for (String line : lines) {
            if (line == null) { continue; }
            for (String sub : line.split("\r?\n")) {
                sub = sub.trim();
                // class-info 行是每块起点，行值即该块的完整类名
                if (sub.startsWith("class-info")) {
                    blockMatch = className.equals(sub.substring("class-info".length()).trim());
                } else if (blockMatch && sub.startsWith("code-source")) {
                    String value = sub.substring("code-source".length()).trim();
                    if (!value.isEmpty() && !"null".equals(value)) {
                        codeSources.add(value);
                    }
                }
            }
        }
        return codeSources;
    }

    /**
     * 从 code-source 读宿主类磁盘原始字节（KMBA 本进程读文件，目标须与 KMBA
     * 同机）。多 classloader 多个 jar 各读一份。
     */
    private List<byte[]> readOriginalBytes(List<String> codeSources, String className) {
        if (codeSources == null || codeSources.isEmpty()) {
            return null;
        }
        String entryPath = className.replace('.' ,'/') + ".class";
        List<byte[]> result = new ArrayList<>();
        for (String codeSource : codeSources) {
            try {
                byte[] bytes = readClassFromCodeSource(codeSource, entryPath);
                if (bytes != null) {
                    result.add(bytes);
                }
            } catch (Exception e) {
                logger.warn("/agent/restore: read {} from {} failed: {}" ,entryPath ,codeSource ,e.getMessage());
            }
        }
        return result;
    }

    /**
     * code-source 形态（先判断后分派）：
     *   jar:file:/app.jar!/BOOT-INF/lib/x.jar!/ → Spring Boot 嵌套 jar（外层逐层入内）
     *   file:/path/classes/                     → exploded 目录 + 包路径直读
     *   file:/path/x.jar 或 /path/x.jar         → 普通 jar（arthas 输出的裸路径无 file: 前缀）
     */
    private byte[] readClassFromCodeSource(String codeSource, String entryPath) throws Exception {
        if (codeSource.startsWith("jar:file:")) {
            String url = codeSource.substring("jar:file:".length());
            // 尾部 !/ 是嵌套 jar URL 的收尾符
            if (url.endsWith("!/")) {
                url = url.substring(0 ,url.length() - 2);
            }
            String[] segments = url.split("!/");
            byte[] jarBytes = readLocalBytes(urlDecode(segments[0]));
            for (int i = 1; jarBytes != null && i < segments.length; i++) {
                jarBytes = readJarEntry(jarBytes, urlDecode(segments[i]));
            }
            return jarBytes == null ? null : readJarEntry(jarBytes, entryPath);
        }
        String path = urlDecode(codeSource.startsWith("file:")
                ? codeSource.substring("file:".length()) : codeSource);
        // 目录形态以 / 收尾
        if (path.endsWith("/")) {
            return readLocalBytes(path + entryPath);
        }
        return readJarEntry(readLocalBytes(path), entryPath);
    }

    // 路径含 %20 等编码时解码；无 % 原样返回（避免 + 号被误转空格）
    private String urlDecode(String path) {
        if (!path.contains("%")) {
            return path;
        }
        try {
            return URLDecoder.decode(path ,"UTF-8");
        } catch (Exception e) {
            return path;
        }
    }

    // 从 jar 字节流读一个条目（嵌套 jar 同路径；Boot 嵌套 jar 条目 STORED 未压缩，流式可读）
    private byte[] readJarEntry(byte[] jarBytes, String entryName) {
        if (jarBytes == null) {
            return null;
        }
        JarInputStream jarInputStream = null;
        try {
            jarInputStream = new JarInputStream(new ByteArrayInputStream(jarBytes));
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                if (jarEntry.getName().equals(entryName)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = jarInputStream.read(buf)) > 0) {
                        out.write(buf ,0 ,len);
                    }
                    return out.toByteArray();
                }
            }
        } catch (Exception e) {
            logger.warn("/agent/restore: read jar entry {} failed: {}" ,entryName ,e.getMessage());
        } finally {
            if (jarInputStream != null) {
                try { jarInputStream.close(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    // 读本机文件（宿主 jar / exploded class 都在目标 JVM 同机磁盘上）
    private byte[] readLocalBytes(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (Exception e) {
            logger.warn("/agent/restore: read {} failed: {}" ,path ,e.getMessage());
            return null;
        }
    }

    // 原始字节落盘临时文件供 redefine（全局临时目录 Dict.TMP_DIR，启动时按系统设定）
    private String writeTempClass(String className, byte[] bytes) throws Exception {
        File dirFile = new File(Dict.TMP_DIR);
        if (!dirFile.exists() && !dirFile.mkdirs()) {
            throw new Exception("mkdirs failed: " + Dict.TMP_DIR);
        }
        String path = Dict.TMP_DIR + File.separator + className.replace('$' ,'_') + ".class";
        Files.write(Paths.get(path), bytes);
        return path;
    }
}
