package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
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
 * Jetty 容器 Listener 内存马（kmbatest ENV-08 缺陷4：KMBA 仅支持 Tomcat StandardContext）
 *
 * 依据 Jetty 各版本字节码取证（javap org.eclipse.jetty.server.handler.ContextHandler）：
 *   Jetty 7.0.0.M0 / 8.1.3 / 9.2.28 / 9.4.46 均有公开 API
 *     EventListener[] getEventListeners()
 *     void setEventListeners(EventListener[])
 *   （listener 注册在 ContextHandler 基类上，addEventListener 内部即
 *     setEventListeners(ArrayUtil.addToArray(...))，因此锚定基类 ContextHandler
 *     而非 ServletContextHandler）
 *   Jetty 12.0.14 移除了 get/setEventListeners（仅剩 add/removeEventListener），
 *     无公开枚举途径，探测到该形态时跳过并记日志（NOAPI）
 *
 * 内存马形态（JavaRce JavaxJettyDemo 实测）：
 *   Whoopsunix 的 Jetty listener 内存马经 ContextHandler.addEventListener 注册，
 *   元素是 java.lang.reflect.Proxy（InvocationHandler 才是真正的恶意类），
 *   故枚举时对 Proxy 解析 InvocationHandler 类名（经 Proxy.h 字段，
 *   实测 getInvocationHandler 在长生命周期 JVM 中会因 proxyClassCache 未命中
 *   抛 "not a proxy instance"，h 字段访问更可靠）
 *
 * 卸载思路（实测验证）：
 *   1. 枚举出每个 listener 的有效类名（Proxy -> InvocationHandler 类名）
 *   2. 过滤出有效类名【精确等于】目标的元素，重建 EventListener[] 数组
 *      （精确匹配，避免 contains 子串误伤 ListenerExec2 之类）
 *   3. 公开 API setEventListeners(EventListener[]) 写回
 *   4. 写回后重新枚举复查，目标仍在则报失败，杜绝假成功
 */
@RestController
@RequestMapping("/JettyListener")
public class JettyListener {

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    /**
     * 枚举有效类名：
     *   getEventListeners() 方法不存在（Jetty 12 起移除，监听器移入 EE 模块）时
     *   输出 NOAPI，调用方跳过（公开枚举途径不存在，不做猜测性反射）；
     *   #ls==null 时输出 EMPTY（7/8 数组型存储在未注册过 listener 时为 null）；
     *   代理判定用规范级不变量 Proxy.class.isAssignableFrom(clazz)
     *   （JDK 代理类必然继承 java.lang.reflect.Proxy，不依赖 com.sun.proxy/jdk.proxy
     *   这类内部命名格式——命名是 JDK 实现细节，JDK9 起不保证稳定）；
     *   代理的 InvocationHandler 经 Proxy.h 字段解析（Proxy 自 1.3 起的成员；
     *   公开 API getInvocationHandler 因内部 proxyClassCache 弱缓存失效，
     *   在长生命周期 JVM 中会误抛 "not a proxy instance"，实测不可靠）
     */
    private String listJettyListenerByVmtool = "vmtool -l -1 --action getInstances --className org.eclipse.jetty.server.handler.ContextHandler --express '#h=instances[%s],#pc=@java.lang.reflect.Proxy@class,#has=#h.getClass().getMethods().{? #this.getName().equals(\"getEventListeners\")}.size>0,#has?(#ls=#h.getEventListeners(),#ls==null?\"EMPTY\":(#ls.{(#o=#this),#pc.isAssignableFrom(#o.class)?#o.h.class.name:#o.class.name})):\"NOAPI\"'";

    /**
     * 卸载：过滤重建数组 + setEventListeners 写回（公开 API，7~9.4 通用——
     * 9.2+ 的 List 型存储由 setter 内部同步重建各类型化列表）。
     * 与 list 对齐的守卫：
     *   getEventListeners 方法不存在（Jetty 12 等）→ NOAPI，调用方跳过（不硬报 OGNL 失败）；
     *   #ls==null（7/8 从未注册过 listener 的 context）→ EMPTY，调用方按未命中处理；
     * 有效类名【精确等于】目标类名才视为命中（代理判定同 list，类型级 isAssignableFrom），
     * 表达式内直接输出卸载后目标是否已消失（left=），无需二次往返自查。
     */
    private String unloadJettyListenerByVmtool = "vmtool -l -1 --action getInstances --className org.eclipse.jetty.server.handler.ContextHandler --express '#h=instances[%s],#u=\"%s\",#pc=@java.lang.reflect.Proxy@class,#has=#h.getClass().getMethods().{? #this.getName().equals(\"getEventListeners\")}.size>0,#has?(#ls=#h.getEventListeners(),#ls==null?\"EMPTY\":(#keep=#ls.{? !((#o=#this),#pc.isAssignableFrom(#o.class)?#o.h.class.name:#o.class.name).equals(#u)},#newArr=#keep.toArray(@java.lang.reflect.Array@newInstance(#ls.getClass().getComponentType(),0)),#h.setEventListeners(#newArr),#after=#h.getEventListeners(),#after==null?\"left=true\":\"left=\"+(#after.{? ((#o=#this),#pc.isAssignableFrom(#o.class)?#o.h.class.name:#o.class.name).equals(#u)}.size>0))):\"NOAPI\"'";

    @RequestMapping("/list")
    public JSONArray list() {
        JSONArray jsonArray = new JSONArray();
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int jettyCnt = Util.getJettyCnt();
            // @ArrayList[@String[org.example.jetty.memshell.ListenerExec], @String[...], ...]
            String resultAll = Util.getListResult(jettyCnt ,listJettyListenerByVmtool);
            String regex = "@String\\[([0-9a-zA-Z.$_/]+)\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);
            while (matcher.find()) {
                String className = matcher.group(1);
                // 守卫标记（EMPTY/NOAPI）不是类名，不进入结果
                if ("EMPTY".equals(className) || "NOAPI".equals(className)) {
                    continue;
                }
                if (!jsonArray.contains(className)) {
                    jsonArray.add(className);
                }
            }
            if (resultAll.contains("NOAPI")) {
                logger.info("/JettyListener/list: 该 Jetty 版本无 getEventListeners 公开 API（如 Jetty 12），跳过枚举");
            }
            logger.info("/JettyListener/list: {} " , jsonArray);
            return jsonArray;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String className) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int jettyCnt = Util.getJettyCnt();

            int removedCnt = 0;
            int failCnt = 0;

            for (int i = 0; i < jettyCnt; i++) {
                // 卸载前确认目标存在（NOAPI 的版本跳过）
                List<String> before = wrapper.runCmd(String.format(listJettyListenerByVmtool, i));
                String beforeStr = String.join("" ,before);
                if (beforeStr.contains("Failed to execute ognl")){
                    logger.error(beforeStr);
                    failCnt++;
                    continue;
                }
                if (!containsName(before, className)) {
                    continue;
                }

                List<String> result = wrapper.runCmd(String.format(unloadJettyListenerByVmtool, i, className));
                String resultStr = String.join("" ,result);
                logger.info("/JettyListener/unload: {}" ,resultStr);
                if (resultStr.contains("Failed to execute ognl")){
                    logger.error(resultStr);
                    failCnt++;
                    continue;
                }
                if (resultStr.contains("NOAPI")) {
                    // 该版本无 getEventListeners（如 Jetty 12），与 list 一致优雅跳过
                    logger.info("/JettyListener/unload: 该 Jetty 版本无 getEventListeners 公开 API，跳过实例 {}", i);
                    continue;
                }
                if (resultStr.contains("EMPTY")) {
                    // 卸载前确认存在，卸载时却为空——不一致，计为失败
                    logger.error("/JettyListener/unload: {} 实例 {} 枚举为空（与卸载前检查不一致）" ,className, i);
                    failCnt++;
                    continue;
                }
                if (resultStr.contains("left=false")) {
                    removedCnt++;
                } else {
                    // 表达式内自查目标仍存在
                    logger.error("/JettyListener/unload: {} 卸载后复查仍存在" ,className);
                    failCnt++;
                }
            }

            if (removedCnt == 0 && failCnt > 0) {
                return "OGNL execute failed";
            }
            if (removedCnt == 0) {
                return "Listener not found";
            }
            return "success";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed";
        }
    }

    /** 枚举输出中是否包含目标有效类名（与卸载过滤条件一致，精确匹配） */
    private boolean containsName(List<String> output, String className) {
        if (output == null || output.isEmpty()) return false;
        return String.join("", output).contains("@String[" + className + "]");
    }
}
