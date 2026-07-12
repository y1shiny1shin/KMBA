package com.kmba.arthas;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.Utils.ArthasWsClient;
import com.kmba.Utils.Util;
import com.kmba.pojo.AgentInfo;
import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kmba.Utils.Dict.getTomcatSiteCnt;

/**
 * https://su18.org/post/memory-shell/#filter-%E5%86%85%E5%AD%98%E9%A9%AC
 * https://drun1baby.top/2022/08/22/Java%E5%86%85%E5%AD%98%E9%A9%AC%E7%B3%BB%E5%88%97-03-Tomcat-%E4%B9%8B-Filter-%E5%9E%8B%E5%86%85%E5%AD%98%E9%A9%AC/
 * https://goodapple.top/archives/1355
 */
@RestController
@RequestMapping("/filter")
public class Filter {
//    private String listFilterByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express '#tomcatContext=instances[%s].filterMaps.array'";
    private String listFilterByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express '#filterConfigs=instances[%s].filterConfigs,#filtermaps=instances[%s].filterMaps.array,#url=#filtermaps.{(#this.urlPatterns!=null)?(#a=#this.filterName,#this.urlPatterns[0]+\":\"+#this.filterName+\":\"+#filterConfigs[#a].filterClass):(null)}'";
    private String unloadFilterByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express '#ctx=instances[%s],#c=0,#ms=#ctx.findFilterMaps().{? #this.getURLPatterns()[0].equals(\"%s\")},#ms.{#ctx.removeFilterMap(#ms[#c]),#c=#c+1}'";
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    @RequestMapping("/list")
    public JSONArray list() {
        try{
            JSONArray jsonArray = new JSONArray();

//            String regex = "filterName=([^,]+),\\s*urlPattern=([^\\]]+)";
//            @ArrayList[
//            @String[/test:com.TestFilter:com.TestFilter],
//            @String[/*:TomcatUtilt WebSocket (JSR356) Filter:org.apache.tomcat.websocket.server.WsFilter],
//    @String[/evil:evilFilter:com.injectFilterDemo],
//]
            String regex = "@String\\[(.*?):(.*?):(.*?)\\]";
            Pattern pattern = Pattern.compile(regex);
            

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            List<String> result = new ArrayList<>();
            int tomcatSiteCnt = getTomcatSiteCnt();
            for (int i = 0; i < tomcatSiteCnt; i++) {
                String cmd = String.format(listFilterByVmtool, i ,i);

                List<String> result0 = wrapper.runCmd(cmd);

                for (String s: result0)
                    if (!(s.isEmpty() || s==null))
                        result.add(s);
            }
            String resultAll = String.join("" ,result);

            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
//                JSONObject jsonObject = new JSONObject();
//                jsonObject.put(matcher.group(1), matcher.group(2));
//                jsonArray.add(jsonObject);

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("urlPattern", matcher.group(1));
                jsonObject.put("filterName", matcher.group(2));
                jsonObject.put("className", matcher.group(3));
                jsonArray.add(jsonObject);
            }
            logger.info("/filter/list: {}" ,jsonArray.toJSONString());
            return jsonArray;
        }catch (Exception e){
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String URLPattern){
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int tomcatSiteCnt = getTomcatSiteCnt();
            for (int i=0;i<tomcatSiteCnt;i++){
                String cmd = String.format(unloadFilterByVmtool , i , URLPattern);
                logger.info("/filter/unload: {}" ,wrapper.runCmd(cmd));
            }
            return "success";

        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }

    }
}
