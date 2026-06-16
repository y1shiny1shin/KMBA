package com.kmba.tunnel;

import com.alibaba.fastjson.JSON;
import com.kmba.Utils.ArthasWsRequest;
import com.kmba.pojo.AgentInfo;
import com.kmba.Utils.ArthasWsClient;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import static com.kmba.Utils.Dict.*;
import static com.kmba.Utils.Dict.ENTER;

public class ArthasWsWrapper {
    public static String RESIZE_WIDTH = "{\"action\":\"resize\",\"cols\":180,\"rows\":180}";

    private static AgentInfo globalAgentInfo;
    private static ArthasWsWrapper globalWrapper;

    public boolean isStartSend = false;
    public AgentInfo agentInfo;
    public ArthasWsClient wsClient;
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    public ArthasWsWrapper(AgentInfo agentInfo) {
        this.agentInfo = agentInfo;
    }

    public ArthasWsClient createArthasWsClient () {
        // 新建ws连接
        String wsUrl = agentInfo.getConnectString();
        try {
            ArthasWsClient wsClient = new ArthasWsClient(new URI(wsUrl));
            wsClient.connectBlocking();
            Thread.sleep(500);


            // 重置窗口宽度
            wsClient.send(RESIZE_WIDTH+ENTER);

            isStartSend = true;

            this.wsClient = wsClient;
            wsClient.clear();

            return wsClient;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    public List<String> runCmd(String cmd) throws InterruptedException {
        cmd = cmd  + PLAINTEXT;
        wsClient.send(JSON.toJSONString(new ArthasWsRequest(cmd)));
        Thread.sleep(500);

        logger.info("ExecCmd: "+cmd);

        if (pollForResult(wsClient)){
            wsClient.setEnd(false);
            return clearOutput(wsClient.resultMsg ,cmd);
        } else {
            return null;
        }
    }

    public List<String> runCmd0(String cmd) throws InterruptedException {
        cmd = cmd  + PLAINTEXT;

        wsClient.send(JSON.toJSONString(new ArthasWsRequest(cmd)));


        logger.info("ExecCmd: "+cmd);

        if (pollForResult(wsClient)){
            wsClient.setEnd(false);
            return clearOutput(wsClient.resultMsg ,cmd);
        } else {
            return null;
        }
    }

    public static boolean pollForResult(ArthasWsClient client) {
        int pollCount = 0;
        boolean isEnd = false;

        while (pollCount < MAX_POLL_COUNT) {
            try {
                if (client.end) {
                    isEnd = true;
                    return isEnd;
                }

                Thread.sleep(POLL_INTERVAL_MS);
                pollCount++;

            } catch (InterruptedException e) {
                System.err.println("轮询被中断");
            }
        }
        return isEnd;
    }

    public List<String> clearOutput(List<String> output ,String cmd) {
        int cmdLength = cmd.replace(" " ,"").length();
        // 截取命令长度之外的执行结果
        try {
            return output.subList(cmdLength, output.size());
        } catch (Exception e) {
            System.err.println(cmd);
            System.err.println(output);
            return null;
        }

    }

    public static ArthasWsWrapper getWrapper(){
        if (globalWrapper == null) {
            if (globalAgentInfo == null) {
                setGlobalAgentInfo("127.0.0.1", 8563);
            }
            ArthasWsWrapper w = new ArthasWsWrapper(globalAgentInfo);
            try {
                w.createArthasWsClient();
            } catch (Exception e) {
                globalWrapper = null;
                throw e;
            }
            globalWrapper = w;
        }
        return globalWrapper;
    }

    public static void setGlobalAgentInfo(String host, int port) {
        globalAgentInfo = new AgentInfo();
        globalAgentInfo.setHost(host);
        globalAgentInfo.setWsPort(port);
        // 重置 wrapper，下次 getWrapper 时会重新创建
        globalWrapper = null;
    }

    public static void close() {
        if (globalWrapper != null && globalWrapper.wsClient != null) {
            try { globalWrapper.wsClient.close(); } catch (Exception ignored) {}
            globalWrapper = null;
        }
    }

}
