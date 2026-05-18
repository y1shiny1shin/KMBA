package com.kmba.Utils;

import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TomcatUtil {
    // 日志
    private static Logger logger = LoggerFactory.getLogger(TomcatUtil.class.getName());
    // 获取tomcat运行了几个站点
    public static String getTomcatSiteCntExpress = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express 'instances.length'";
    public static String getTimerCntExpress = "vmtool --action getInstances --className java.util.Timer --express 'instances.length'";
    public static String getThreadCntExpress = "vmtool --action getInstances --className java.lang.Thread --express 'instances.length'";
    public static String getSocketCntExpress = "vmtool --action getInstances --className org.apache.tomcat.websocket.server.WsServerContainer --express 'instances.length'";
    public static String getEndpointCntExpress = "vmtool --action getInstances --className org.apache.tomcat.util.net.NioEndpoint --express 'instances.length'";
    public static int getTomcatSiteCnt() {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            List<String> result = wrapper.runCmd(getTomcatSiteCntExpress);
            System.out.println(result);
            if (result.size() > 0) {
                String num = result.get(0);
                logger.info("getTomcatSiteCnt: " + num);
                // 正则提取数字
                Pattern pattern = Pattern.compile("@Integer\\[(\\d+)\\]");
                Matcher matcher = pattern.matcher(num);

                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        }catch (Exception e){
            logger.error(e.getMessage());
            return 0;
        }
        return 0;
    }

    public static int getTimerCnt() {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            List<String> result = wrapper.runCmd(getTimerCntExpress);
            if (result.size() > 0) {
                String num = result.get(0);
                logger.info("getTimerCnt: " + num);
                // 正则提取数字
                Pattern pattern = Pattern.compile("@Integer\\[(\\d+)\\]");
                Matcher matcher = pattern.matcher(num);

                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            return 0;
        }catch (Exception e){
            logger.error(e.getMessage());
            return 0;
        }
    }

    public static int getThreadCnt() {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            List<String> result = wrapper.runCmd(getThreadCntExpress);
            if (result.size() > 0) {
                String num = result.get(0);
                logger.info("getThreadCnt: " + num);
                // 正则提取数字
                Pattern pattern = Pattern.compile("@Integer\\[(\\d+)\\]");
                Matcher matcher = pattern.matcher(num);

                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            return 0;
        }catch (Exception e){
            logger.error(e.getMessage());
            return 0;
        }
    }


    public static int getSocketCnt() {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            List<String> result = wrapper.runCmd(getSocketCntExpress);
            if (result.size() > 0) {
                String num = result.get(0);
                logger.info("getSocketCnt: " + num);

                Pattern pattern = Pattern.compile("@Integer\\[(\\d+)\\]");
                Matcher matcher = pattern.matcher(num);

                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            return 0;
        } catch (Exception e){
            logger.error(e.getMessage());
            return 0;
        }
    }

    public static int getEndpointCnt() {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            List<String> result = wrapper.runCmd(getEndpointCntExpress);
            if (result.size() > 0) {
                String num = result.get(0);
                logger.info("getEndpointCnt: " + num);

                Pattern pattern = Pattern.compile("@Integer\\[(\\d+)\\]");
                Matcher matcher = pattern.matcher(num);

                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            return 0;
        } catch (Exception e){
            logger.error(e.getMessage());
            return 0;
        }
    }
}
