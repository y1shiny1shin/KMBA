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
 * Tomcat WebSocket 端点枚举与卸载
 *
 * Round3 发现并修复（2026-08-20, tc24813-vuln Tomcat 9.0.97 实证）：
 *   WsServerContainer.configExactMatchMap 的 value 类型随 Tomcat 版本变化：
 *   - Tomcat 8（javap 8.5.31）: Map<String, ServerEndpointConfig>，
 *     端点类经 value.getEndpointClass()
 *   - Tomcat 9/10（javap 9.0.76 / 10.1.39）: Map<String, ExactPathMatch>，
 *     ExactPathMatch 无 getEndpointClass()，需经 getConfig().getEndpointClass()
 *   修复: 运行时探测 value 类是否有 getConfig() 方法（9+ 有、8 没有），
 *   按探测结果分派取值路径；卸载为 configExactMatchMap.remove(urlName)
 *   （Round3 对照实验实证: 移除后 WS 握手 400）+ 表达式内自查 left=false
 */
@RestController
@RequestMapping("/socket")
public class Socket {

    // list 输出: @String[/ws-shell:class com.X]（与既有渲染格式兼容）
    private String listSocketByVmtool = "vmtool -l -1 --action getInstances --className org.apache.tomcat.websocket.server.WsServerContainer --express '#container=instances[%s],#keys=#container.configExactMatchMap.keySet().toArray(),#keys.length>0?(#v=#container.configExactMatchMap.get(#keys[0]),#hasCfg=#v.getClass().getMethods().{? #this.getName().equals(\"getConfig\")}.size>0,#keys.{(#k=#this,#m=#container.configExactMatchMap.get(#k),#cls=#hasCfg?#m.getConfig().getEndpointClass():#m.getEndpointClass(),#k+\":\"+#cls)}):\"EMPTY\"'";

    private String unloadSocketByVmtool = "vmtool -l -1 --action getInstances --className org.apache.tomcat.websocket.server.WsServerContainer --express '#container=instances[%s],#u=\"%s\",#c=\"%s\",#m=#container.configExactMatchMap.get(#u),#m==null?\"notfound\":(#hasCfg=#m.getClass().getMethods().{? #this.getName().equals(\"getConfig\")}.size>0,#cls=#hasCfg?#m.getConfig().getEndpointClass():#m.getEndpointClass(),#cls.getName().equals(#c)?(#container.configExactMatchMap.remove(#u),\"left=\"+#container.configExactMatchMap.containsKey(#u)):\"mismatch\")'";

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    @RequestMapping("/list")
    public JSONArray list() {
        try{
            int socketCnt = Util.getSocketCnt();
            JSONArray jsonArray = new JSONArray();

            String resultAll = Util.getListResult(socketCnt ,listSocketByVmtool);

            // 注意: 路径段用 [^...] 排除方括号，避免 .*? 跨越多个 @String token 产生乱串匹配
            String regex = "\\@String\\[([^\\[\\]:]*):class ([0-9a-zA-Z.$_/]+)\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                JSONObject jsonObject = new JSONObject();
                if (!(matcher.group(1).isEmpty()&&matcher.group(2).isEmpty()))
                    jsonObject.put(matcher.group(1), matcher.group(2));
                jsonArray.add(jsonObject);
            }

            logger.info("/socket/list: {}" ,jsonArray.toJSONString());

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
            int socketCnt = Util.getSocketCnt();

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int removedCnt = 0;
            int failCnt = 0;
            int notFoundCnt = 0;
            // 注入端可能延迟注册（实测第 5 份在首次 list 之后才落地），
            // 移除一轮后复查，若目标再现则再移除一轮
            for (int pass = 0; pass < 3; pass++) {
                boolean found = false;
                for (int i = 0; i < socketCnt; i++) {
                    String cmd = String.format(unloadSocketByVmtool, i, urlName , className);

                    List<String> result = wrapper.runCmd(cmd);
                    String resultStr = String.join("" ,result);
                    logger.info("/socket/unload: {}" ,resultStr);
                    if (resultStr.contains("Failed to execute ognl")){
                        logger.error(resultStr);
                        failCnt++;
                        continue;
                    }
                    if (resultStr.contains("left=false")) {
                        removedCnt++;
                        found = true;
                    } else if (resultStr.contains("notfound") || resultStr.contains("mismatch") || resultStr.contains("EMPTY")) {
                        notFoundCnt++;
                    } else {
                        // left=true：移除后复查仍存在
                        logger.error("/socket/unload: {} 移除后复查仍存在" ,urlName);
                        failCnt++;
                    }
                }
                if (!found && pass > 0) {
                    break;
                }
            }
            if (removedCnt > 0) {
                return "success";
            }
            if (failCnt > 0) {
                return "OGNL execute failed";
            }
            return "urlName not found";
        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }
    }
}
