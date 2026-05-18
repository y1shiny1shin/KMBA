package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.Utils.TomcatUtil;
import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



@RestController
@RequestMapping("/socket")
public class Socket {
    private String listSocketByVmtool = "vmtool --action getInstances --className org.apache.tomcat.websocket.server.WsServerContainer --express '#container=instances[%s],#name=#container.configExactMatchMap.keySet().toArray().{#name=#this,#this+\":\"+#container.configExactMatchMap[#name].config.endpointClass.name}'";
    //    @ArrayList[
//    @String[/ws2:com.MyWebSocketEndpoint],
//    @String[/ws1:com.MyWebSocketEndpoint],
//    @String[/ws3:com.MyWebSocketEndpoint],
//            ]
    private String unloadSocketByVmtool = "vmtool --action getInstances --className org.apache.tomcat.websocket.server.WsServerContainer --express '#container=instances[%s],#className=#container.configExactMatchMap[\"%s\"].config.endpointClass.name,#className,{#className.equals(\"%s\")?#container.configExactMatchMap.remove(\"%s\"):\"failed\"}'";
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    @RequestMapping("/list")
    public JSONArray list() {
        try{
            int socketCnt = TomcatUtil.getSocketCnt();
            JSONArray jsonArray = new JSONArray();

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            List<String> result = new ArrayList<>();
            for(int i = 0; i < socketCnt; i++) {
                String cmd = String.format(listSocketByVmtool, i);
                List<String> result0 = wrapper.runCmd(cmd);

                for (String s : result0) {
                    if (!(s.isEmpty() || s==null)) result.add(s);
                }
            }
            String resultAll = String.join("" ,result);


            String regex = "\\@String\\[(.*?):([0-9a-zA-Z.$_]+)\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            System.out.println(resultAll);
            System.out.println(matcher);
            while (matcher.find()) {
                JSONObject jsonObject = new JSONObject();
                if (!(matcher.group(1).isEmpty()&&matcher.group(2).isEmpty()))
                    jsonObject.put(matcher.group(1), matcher.group(2));
                jsonArray.add(jsonObject);
            }

            logger.info("/socket/list: " + jsonArray.toJSONString());

            return jsonArray;
        } catch (Exception e){
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    /**
     * 已经建立的socket连接，需要在系统层面进行切断，Java层面做到切断连接有点困难
     */
    @RequestMapping("/unload")
    public String unload(@RequestParam String urlName , @RequestParam String className) {
        try{
            int socketCnt = TomcatUtil.getSocketCnt();

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            for (int i = 0; i < socketCnt; i++) {
                String cmd = String.format(unloadSocketByVmtool, i, urlName , className ,urlName);

                logger.info("/socket/unload: "+wrapper.runCmd(cmd));
            }
            return "success";
        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }


    }


}
