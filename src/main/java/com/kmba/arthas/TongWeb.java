package com.kmba.arthas;

import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TongWeb（东方通）容器适配
 *
 * TongWeb 与 Tomcat 的 API 结构几乎一致、仅包名不同，映射规则
 * （TongWeb 7.0.4.9 实测取证，启动类 com.tongweb.web.thor.startup.ThorBootstrap、
 *  其父类 com.tongweb.catalina.startup.Bootstrap 实锤 catalina 包保留）:
 *   org.apache  → com.tongweb
 *   tomcat      → web（仅包路径段，如 org.apache.tomcat.util → com.tongweb.web.util）
 * 例: org.apache.catalina.core.StandardContext → com.tongweb.catalina.core.StandardContext
 *
 * 接入方式（命令级翻译钩子）:
 *   ArthasWsWrapper.runCmd() 发送前调用 translate()，所有 Tomcat 系组件
 *   （servlet/filter/listener/valve/proxyValve/socket/executor/upgrade）
 *   零改动自动适配；Spring/WebLogic/Jetty 组件命令不含 org.apache 类名，天然不受影响。
 *
 * 关键设计（勿退回全命令通用替换）:
 *   translate 只替换【KMBA 源码中硬编码的 Tomcat 类名集合】（完整类名匹配）。
 *   内存马常伪装成 org.apache.* 包名（如 org.apache.http.web.handlers.X.AuthListener），
 *   通用替换会把这些伪装类名（-u 目标、-jad/-vmtool 参数）也翻译掉，导致匹配失败。
 *   完整类名替换下，集合外的任何 org.apache.* 类名都不受影响。
 *
 * 注意:
 *   1. 探测必须走 runCmd0（不经过翻译层的原始方法），避免与 runCmd 的钩子形成递归
 *   2. 探测结果连接级缓存，换目标 JVM 时由 setGlobalAgentInfo/close 重置
 */
public class TongWeb {
    private static final Logger logger = LoggerFactory.getLogger(TongWeb.class.getName());

    /** TongWeb 独有启动类（实测存在），作为探测标志 */
    private static final String PROBE_CLASS = "com.tongweb.web.thor.startup.ThorBootstrap";
    private static final String PROBE_CMD = "vmtool -l -1 --action getInstances --className " + PROBE_CLASS;

    /** 探测结果缓存（连接级），null 表示未探测 */
    private static volatile Boolean isTongWeb;

    /** KMBA 源码中硬编码的 Tomcat 类名（grep src/main/java 提取），映射表由规则生成 */
    private static final String[] TOMCAT_CLASSES = {
            "org.apache.catalina.core.StandardContext",
            "org.apache.catalina.authenticator.NonLoginAuthenticator",
            "org.apache.catalina.core.StandardContextValve",
            "org.apache.catalina.Valve",
            "org.apache.tomcat.util.net.NioEndpoint",
            "org.apache.tomcat.util.threads.ThreadPoolExecutor",
            "org.apache.tomcat.websocket.server.WsServerContainer",
            "org.apache.tomcat.websocket.server.WsFilter",
            "org.apache.coyote.AbstractProtocol$AsyncTimeout",
            "org.apache.coyote.http11.AbstractHttp11Protocol",
    };

    /** 完整类名 -> TongWeb 类名（规则生成，初始化一次） */
    private static final Map<String, String> CLASS_MAP = new HashMap<String, String>();

    static {
        for (String c : TOMCAT_CLASSES) {
            CLASS_MAP.put(c, map(c));
        }
    }

    /**
     * 按规则映射单个类名: org.apache → com.tongweb；包路径段 tomcat → web。
     * 例: org.apache.catalina.core.StandardContext → com.tongweb.catalina.core.StandardContext
     *     org.apache.tomcat.util.net.NioEndpoint  → com.tongweb.web.util.net.NioEndpoint
     */
    private static String map(String className) {
        if (!className.startsWith("org.apache.")) {
            return className;
        }
        String rest = className.substring("org.apache.".length());
        if (rest.startsWith("tomcat.")) {
            rest = "web." + rest.substring("tomcat.".length());
        }
        return "com.tongweb." + rest;
    }

    /**
     * 探测目标 JVM 是否为 TongWeb（结果缓存，仅首次真实探测）。
     * 探测走 runCmd0（不经过翻译层），避免递归。
     */
    public static boolean isTongWeb(ArthasWsWrapper wrapper) {
        if (isTongWeb != null) {
            return isTongWeb;
        }
        try {
            List<String> result = wrapper.runCmd0(PROBE_CMD);

            if (result == null || result.isEmpty()) {
                logger.info("TongWeb probe: retry-next (empty output)");
                return false;
            }
            String out = String.join("", result);
            if (out.contains("@ThorBootstrap[")) {
                isTongWeb = true;
            } else if (out.contains("Can not find class") || out.contains("Failed to execute")) {
                isTongWeb = false;
            } else {
                logger.info("TongWeb probe: retry-next (unexpected output: {})", out);
                return false;
            }

        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
        logger.info("TongWeb probe: {}" , isTongWeb);
        return isTongWeb;
    }

    /**
     * 命令翻译: 仅替换 KMBA 硬编码的 Tomcat 类名（完整类名匹配）。
     * 内存马伪装类名（org.apache.http.* 等）不在集合内，不会被误翻译。
     */
    public static String translate(String cmd) {
        String out = cmd;
        for (Map.Entry<String, String> e : CLASS_MAP.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    /** 换目标 JVM / 断开重连时重置缓存，避免上一次探测结果污染新目标 */
    public static void reset() {
        isTongWeb = null;
    }
}
