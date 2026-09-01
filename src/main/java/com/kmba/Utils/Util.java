package com.kmba.Utils;

import com.kmba.arthas.TongWeb;
import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {
    // 日志
    private static Logger logger = LoggerFactory.getLogger(Util.class.getName());
    // 获取tomcat运行了几个站点
    public static String getTomcatSiteCntExpress = "vmtool -l -1 --action getInstances --className org.apache.catalina.core.StandardContext --express 'instances.length'";
    public static String getTimerCntExpress = "vmtool -l -1 --action getInstances --className java.util.Timer --express 'instances.length'";
    public static String getThreadCntExpress = "vmtool -l -1 --action getInstances --className java.lang.Thread --express 'instances.length'";
    public static String getSocketCntExpress = "vmtool -l -1 --action getInstances --className org.apache.tomcat.websocket.server.WsServerContainer --express 'instances.length'";
    public static String getEndpointCntExpress = "vmtool -l -1 --action getInstances --className org.apache.tomcat.util.net.NioEndpoint --express 'instances.length'";
    public static String getSpringCntExpress = "vmtool -l -1 --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext --express 'instances.length'";
    public static String getSpringFluxCntExpress = "vmtool -action getInstances --className org.springframework.web.server.handler.FilteringWebHandler --express '#handler=instances.length'";
    public static String getNettyServerCntExpress = "vmtool -l -1 --action getInstances --className 'org.springframework.boot.web.embedded.netty.NettyWebServer$1' --express 'instances.length'";
    public static String getHttpServerConfigCntExpress = "vmtool -l -1 --action getInstances --className reactor.netty.http.server.HttpServerConfig --express 'instances.length'";
    public static String getRMMCntExpress = "vmtool -l -1 --action getInstances --className org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping --express 'instances.length'";
    public static String getWeblogicCntExpress = "vmtool -l -1 --action getInstances --className weblogic.servlet.internal.WebAppServletContext --express 'instances.length'";
    public static String getJettyCntExpress = "vmtool -l -1 --action getInstances --className org.eclipse.jetty.servlet.ServletHandler --express 'instances.length'";
    public static String getUpgradeCntExpress = "vmtool -l -1 --action getInstances --className org.apache.coyote.http11.AbstractHttp11Protocol --express 'instances.length'";
    public static String getAgentCntExpress = "vmtool -l -1 --action getInstances --className sun.instrument.InstrumentationImpl --express 'instances.length'";
    public static String getStruts2ActionCntExpress = "vmtool -l -1 --action getInstances --className com.opensymphony.xwork2.config.impl.DefaultConfiguration --express 'instances.length'";

    private static final Pattern INTEGER_PATTERN =
            Pattern.compile("@Integer\\[(\\d+)]");
    // 获取实例的 classLoader 类名，获取后再通过 -classLoader 指定
//    private static final String getClassLoaderByVmtool = "vmtool -l -1 --action getInstances --className %s --express 'instances[%s].getClass.getClassLoader.getClass.name'";
    private static final String getClassLoaderByVmtool = "vmtool --action getInstances --className %s --express '@java.lang.Integer@toHexString(@java.lang.System@identityHashCode(instances[%s].getClass().getClassLoader()))";

    private static int executeCountCommand(String command, String metricName) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            List<String> result = wrapper.runCmd(command);

            if (result.isEmpty()) {
                return 0;
            }

            String output = result.get(0);
            logger.info("{}: {}", metricName, output);
            Matcher matcher = INTEGER_PATTERN.matcher(output);

            return matcher.find()
                    ? Integer.parseInt(matcher.group(1))
                    : 0;

        } catch (Exception e) {
            logger.error("Get {} failed", metricName, e);
            return 0;
        }
    }

    public static int getTomcatSiteCnt() {
        return executeCountCommand(getTomcatSiteCntExpress, "TomcatSiteCnt");
    }

    public static int getTimerCnt() {
        return executeCountCommand(getTimerCntExpress, "TimerCnt");
    }

    public static int getThreadCnt() {
        return executeCountCommand(getThreadCntExpress, "ThreadCnt");
    }

    public static int getSocketCnt() {
        return executeCountCommand(getSocketCntExpress, "SocketCnt");
    }

    public static int getEndpointCnt() {
        return executeCountCommand(getEndpointCntExpress, "EndpointCnt");
    }

    public static int getSpringCnt() {
        return executeCountCommand(getSpringCntExpress, "SpringCnt");
    }

    public static int getSpringFluxCnt() {
        return executeCountCommand(getSpringFluxCntExpress ,"SpringFluxCnt");
    }

    public static int getNettyServerCnt() {
        return executeCountCommand(getNettyServerCntExpress ,"NettyServerCnt");
    }

    public static int getHttpServerConfigCnt() {
        return executeCountCommand(getHttpServerConfigCntExpress ,"HttpServerConfigCnt");
    }

    public static int getRMMCnt() {
        return executeCountCommand(getRMMCntExpress ,"RMMCnt");
    }

    public static int getWeblogicCnt() {
        return executeCountCommand(getWeblogicCntExpress ,"WeblogicCnt");
    }

    public static int getJettyCnt() {
        return executeCountCommand(getJettyCntExpress ,"JettyCnt");
    }

    public static int getUpgradeCnt() {
        return executeCountCommand(getUpgradeCntExpress ,"UpgradeCnt");
    }

    public static int getAgentCnt() {
        return executeCountCommand(getAgentCntExpress ,"AgentCnt");
    }

    public static int getStruts2ActionCnt() {
        return executeCountCommand(getStruts2ActionCntExpress ,"Struts2ActionCnt");
    }


    /**
     * 预热验证：建立 WebSocket 连接并发送一条命令，确认 Arthas 真正可用。
     * 失败时自动重置连接并重试，最多重试 maxRetry 次。
     *
     * @return true 连接可用，false 所有重试均失败
     */
    public static boolean checkConnect(String ip, int port, int maxRetry) {
        for (int retry = 0; retry < maxRetry; retry++) {
            try {
                ArthasWsWrapper.getWrapper().runCmd("help");
                logger.info("checkConnect success {}:{}" ,ip ,port);

                TongWeb.reset();
                TongWeb.isTongWeb(ArthasWsWrapper.getWrapper());

                return true;
            } catch (Exception e) {
                logger.warn("checkConnect retry {}/{}: {}", retry + 1, maxRetry, e.getMessage());
                ArthasWsWrapper.setGlobalAgentInfo(ip, port);
                try {
                    java.lang.Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    java.lang.Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    public static String getListResult(int cnt ,String lister) throws Exception {
        ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

        List<String> result = new ArrayList<>();

        for (int i = 0; i < cnt; i++) {
            String cmd = String.format(lister, i);

            List<String> result0 = wrapper.runCmd(cmd);

            for (String s: result0)
                if (!(s==null || s.isEmpty()))
                    result.add(s);
        }
        logger.info("Util.getListResult: {}" ,result);
        return String.join("" ,result);
    }

    /**
     * 动态获取指定类实例的 classLoader 类名
     * 不同的实例可能由不同的 classLoader 加载，为了稳定性，
     * 每次执行命令前都要先获取，再通过 -classLoader 参数指定
     *
     * @param className 目标类全限定名（vmtool 的 --className）
     * @param index     实例索引
     * @param wrapper   Arthas WebSocket 封装
     * @return classLoader 类名，获取失败返回 null
     */
    public static String getClassLoader(String className, int index, ArthasWsWrapper wrapper) {
        try {
            String cmd = String.format(getClassLoaderByVmtool, className, index);
            List<String> result = wrapper.runCmd(cmd);
            if (result == null || result.isEmpty()) return null;

//            @String[java.net.URLClassLoader@71dac704]
//             [@String[ThanosTomEEWebappClassLoader\r\n  context: ROOT\r\n  delegate: false\r\n----------> Parent Classloader:\r\ncom.tongweb.tongejb.core.ConnectorClassLoader@775466b6\r\n]]
//            @String[71dac704]
            String regex = "@String\\[(.*?)\\]";
            Matcher matcher = Pattern.compile(regex).matcher(result.get(0));
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
    }
}
