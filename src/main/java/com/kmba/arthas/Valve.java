package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.Utils.Util;
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

import static com.kmba.Utils.Dict.*;


/**
 * 参考文章
 * https://drun1baby.top/2022/09/07/Java%E5%86%85%E5%AD%98%E9%A9%AC%E7%B3%BB%E5%88%97-06-Tomcat-%E4%B9%8B-Valve-%E5%9E%8B%E5%86%85%E5%AD%98%E9%A9%AC/
 * https://su18.org/post/memory-shell/#tomcat-valve-%E5%86%85%E5%AD%98%E9%A9%AC
 */
@RestController
@RequestMapping("/valve")
public class Valve {
    private String listValveByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express 'instances[%s].pipeline.getValves()'";
    private String unloadValveByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express '#ctx=instances[%s],#valves=#ctx.pipeline.getValves(),#valves.{#this.getClass().getName().equals(\"%s\")?(#evil=#this):null},#ctx.pipeline.removeValve(#evil)'";
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    /**
     * 只返回类名的JSONArray
     * @return
     */
    @RequestMapping("/list")
    public JSONArray list() {
        try {
            JSONArray jsonArray = new JSONArray();

            String resultAll = Util.getListResult(tomcatSiteCnt ,listValveByVmtool);

            String regex = "\\[([0-9a-zA-Z.$_]+)\\[";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                if (matcher.group(1) != null)
                    jsonArray.add(matcher.group(1));
            }
            logger.info("/valve/list: {}" ,jsonArray);

            return jsonArray;
        } catch (Exception e){
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    /**
     * 通过类名卸载valve
     * @return
     */
    @RequestMapping("/unload")
    public String unload(@RequestParam String className) {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            for (int i=0;i<tomcatSiteCnt;i++){
                String cmd = String.format(unloadValveByVmtool, i , className);
                logger.info("/valve/unload: "+wrapper.runCmd(cmd));
            }
            return "success";
        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }
    }
}
