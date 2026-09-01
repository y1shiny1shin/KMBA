package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.Utils.Util;
import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
 *   2. dump 容器类全量（org.* / com.* / javax.*，逐类独立 retransform）
 *      —— JVM 缓存类加载时的原始字节，retransform 自动还原被改字节；
 *        逐类容错（单类失败跳过），规避 retransformClasses 批量调用时
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
    private String unloadAgentByVmtool = "vmtool --action getInstances --className sun.instrument.InstrumentationImpl --express '#ins=instances[%s],#tm1=#ins.mTransformerManager,#tm2=#ins.mRetransfomableTransformerManager,#lst1=#tm1==null?new java.util.ArrayList():@java.util.Arrays@asList(#tm1.mTransformerList),#lst2=#tm2==null?new java.util.ArrayList():@java.util.Arrays@asList(#tm2.mTransformerList),#m1=#lst1.{? #this.mTransformer.getClass().getName().equals(\"%s\")},#m2=#lst2.{? #this.mTransformer.getClass().getName().equals(\"%s\")},#c=#m1.size()+#m2.size(),#c>0&&(#m1.size()>0&&(#ins.removeTransformer(#m1[0].mTransformer)),#m2.size()>0&&(#ins.removeTransformer(#m2[0].mTransformer))),#c>0?\"removed\":\"not-found\"'";

    // JDK 9+ 版（Unsafe 绕模块）: sun.instrument 私有字段反射被模块系统拒绝
    // （InaccessibleObjectException），改用 Unsafe.objectFieldOffset/getObject 按偏移读字段；
    // removeTransformer 通过 java.lang.instrument.Instrumentation 接口的 Method 反射调用
    // （接口 exports 可访问，绕开实现类模块限制）；需先 options strict false（ognl 拦 Unsafe 方法）
    private String listAgentByVmtoolJdk9 = "vmtool -l -1 --action getInstances --className sun.instrument.InstrumentationImpl --express '#ins=instances[%1$s],#uc=@java.lang.Class@forName(\"sun.misc.Unsafe\"),#uf=#uc.getDeclaredField(\"theUnsafe\"),#uf.setAccessible(true),#u=#uf.get(null),#f1=#ins.getClass().getDeclaredField(\"mTransformerManager\"),#tm1=#u.getObject(#ins,#u.objectFieldOffset(#f1)),#f2=#ins.getClass().getDeclaredField(\"mRetransfomableTransformerManager\"),#tm2=#u.getObject(#ins,#u.objectFieldOffset(#f2)),#r1=#tm1==null?new java.util.ArrayList():@java.util.Arrays@asList(#u.getObject(#tm1,#u.objectFieldOffset(#tm1.getClass().getDeclaredField(\"mTransformerList\")))).{(#t=#this,#tf=#t.getClass().getDeclaredField(\"mTransformer\"),#u.getObject(#t,#u.objectFieldOffset(#tf)).getClass().getName())},#r2=#tm2==null?new java.util.ArrayList():@java.util.Arrays@asList(#u.getObject(#tm2,#u.objectFieldOffset(#tm2.getClass().getDeclaredField(\"mTransformerList\")))).{(#t=#this,#tf=#t.getClass().getDeclaredField(\"mTransformer\"),#u.getObject(#t,#u.objectFieldOffset(#tf)).getClass().getName())},\"normal:\"+#r1+\"|retransformable:\"+#r2'";

    private String unloadAgentByVmtoolJdk9 = "vmtool --action getInstances --className sun.instrument.InstrumentationImpl --express '#ins=instances[%s],#uc=@java.lang.Class@forName(\"sun.misc.Unsafe\"),#uf=#uc.getDeclaredField(\"theUnsafe\"),#uf.setAccessible(true),#u=#uf.get(null),#f1=#ins.getClass().getDeclaredField(\"mTransformerManager\"),#tm1=#u.getObject(#ins,#u.objectFieldOffset(#f1)),#f2=#ins.getClass().getDeclaredField(\"mRetransfomableTransformerManager\"),#tm2=#u.getObject(#ins,#u.objectFieldOffset(#f2)),#lst1=#tm1==null?new java.util.ArrayList():@java.util.Arrays@asList(#u.getObject(#tm1,#u.objectFieldOffset(#tm1.getClass().getDeclaredField(\"mTransformerList\")))),#lst2=#tm2==null?new java.util.ArrayList():@java.util.Arrays@asList(#u.getObject(#tm2,#u.objectFieldOffset(#tm2.getClass().getDeclaredField(\"mTransformerList\")))),#m1=#lst1.{? (#t=#this,#u.getObject(#t,#u.objectFieldOffset(#t.getClass().getDeclaredField(\"mTransformer\"))).getClass().getName().equals(\"%s\"))},#m2=#lst2.{? (#t=#this,#u.getObject(#t,#u.objectFieldOffset(#t.getClass().getDeclaredField(\"mTransformer\"))).getClass().getName().equals(\"%s\"))},#c=#m1.size()+#m2.size(),#ic=@java.lang.Class@forName(\"java.lang.instrument.Instrumentation\"),#c>0&&(#m1.size()>0&&(#t2=#u.getObject(#m1[0],#u.objectFieldOffset(#m1[0].getClass().getDeclaredField(\"mTransformer\"))),#rm=#ic.getMethods().{? #this.getName().equals(\"removeTransformer\")}[0],#r1=#rm.invoke(#ins,#t2)),#m2.size()>0&&(#t3=#u.getObject(#m2[0],#u.objectFieldOffset(#m2[0].getClass().getDeclaredField(\"mTransformer\"))),#rm2=#ic.getMethods().{? #this.getName().equals(\"removeTransformer\")}[0],#r2=#rm2.invoke(#ins,#t3))),#c>0?\"removed\":\"not-found\"'";

    // Agent 马常见目标类（dump 逐个还原字节；类不存在时 dump 0 类快速跳过）
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

    // dump 输出目录：不传 -d，用 arthas 默认输出目录（~/logs/arthas/classdump），
    // 跨平台（Windows 上 /tmp/xxx 不可用且目录检查不过会导致 dump 整体失败、还原失效）

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
                // 移除成功 → dump 目标类清单还原被改字节
                // JDK 9+ 也走还原：2026-08-30 重测推翻"retransform 卡死"结论——
                // Tomcat 10.1/JDK17 上 dump 单类 0.5s 完成并成功还原字节（命令失效）；
                // 此前卡死的是 arthas stop 的 reset（全量增强类还原），非 dump 单类路径。
                // 实测：JDK9+ 不还原时，advisor 类不随 removeTransformer 卸载 +
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
     * dump Agent 马常见目标类还原被改字节（transformer 已移除时，逐类 retransform
     * 触发 JVM 用缓存原始字节重变换）。返回 false 表示 dump 命令执行异常。
     */
    private boolean restoreBytecode(ArthasWsWrapper wrapper) {
        try {
            // Agent 马常见目标类逐个 dump（逐类 retransform 触发 JVM 用缓存原始字节还原；
            // 类不存在时 "No class found" 也视为通过）。不依赖马内部任何字段名。
            // 注: 不做全量 dump——WebLogic 类量巨大（LaunchClassLoader 几千类，
            //     org.* 单次 dump 4 分钟+），全量还原不现实；清单覆盖 MSP/JMG 全部
            //     Agent 注入器目标类，清单外目标类可手动 jad/redefine 兜底
            for (String cls : dumpTargetClasses) {
                // "No class found"（类不存在）视为通过
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
     * dump 单个宿主类还原被改字节（restoreBytecode 清单循环与 restore 按传入
     * 类名单类还原共用）。返回 "success" / "className not found"（类未加载）/
     * "restore failed"（dump 命令执行异常）。
     */
    private String restoreBytecode(ArthasWsWrapper wrapper, String className) {
        try {
            // 不传 -d：用 arthas 默认输出目录，跨平台（Windows 上 /tmp 不可用）
            String cmd = String.format("dump %s" ,className);
            List<String> result = wrapper.runCmd(cmd);
            String resultStr = result == null ? null : String.join("" ,result);
            logger.info("/agent/unload: {}" ,resultStr);
            // 显式失败才中断：目录/执行错误、超时返回 null
            if (resultStr == null || resultStr.contains("is not a directory")
                    || resultStr.contains("fail to dump") || resultStr.contains("processing error")) {
                logger.error("/agent/unload: dump {} failed" ,className);
                return "restore failed";
            }
            // "No class found"（类未加载）
            if (resultStr.contains("No class found")) {
                return "className not found";
            }
            return "success";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "restore failed";
        }
    }
}
