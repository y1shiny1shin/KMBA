package com.kmba.Utils;

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
    public static String getTomcatSiteCntExpress = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express 'instances.length'";
    public static String getTimerCntExpress = "vmtool --action getInstances --className java.util.Timer --express 'instances.length'";
    public static String getThreadCntExpress = "vmtool --action getInstances --className java.lang.Thread --express 'instances.length'";
    public static String getSocketCntExpress = "vmtool --action getInstances --className org.apache.tomcat.websocket.server.WsServerContainer --express 'instances.length'";
    public static String getEndpointCntExpress = "vmtool --action getInstances --className org.apache.tomcat.util.net.NioEndpoint --express 'instances.length'";
    public static String getSpringCntExpress = "vmtool --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext --express 'instances.length'";
    public static String getSpringFluxCntExpress = "vmtool -action getInstances --className org.springframework.web.server.handler.FilteringWebHandler --express '#handler=instances.length'";

    private static final Pattern INTEGER_PATTERN =
            Pattern.compile("@Integer\\[(\\d+)]");

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

        return String.join("" ,result);
    }
}
