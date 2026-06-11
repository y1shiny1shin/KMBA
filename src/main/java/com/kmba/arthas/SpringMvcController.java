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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * https://party.mem.mk/ui
 * https://goodapple.top/archives/1355
 * https://jlkl.github.io/2022/05/26/Java-09/
 */
@RestController
@RequestMapping("/SMC")
public class SpringMvcController {
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private String listSMCByVmtool = "vmtool --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext --express '#map=instances[%s].getBean(@org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping@class).handlerMap,#class=#map.getClass().getDeclaredField(\"m\"),#class.setAccessible(true),#class.get(#map)'";
    //@LinkedHashMap[
    //    @String[/exec]:@evalController[com.DemoSpring.evalController@25655ffa],
    //]
    private String unloadSMCByVmtool = "vmtool --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext --express '#map=instances[%s].getBean(@org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping@class).handlerMap,#class=#map.getClass().getDeclaredField(\"m\"),#class.setAccessible(true),#class.get(#map).remove(\"%s\")'";

    @RequestMapping("/list")
    public JSONArray list(){
        try {
            int springCnt = Util.getSpringCnt();
            JSONArray jsonArray = new JSONArray();

            // [@LinkedHashMap[@String[/exec]:@evalController[com.DemoSpring.evalController@25655ffa]]]
            String resultAll = Util.getListResult(springCnt ,listSMCByVmtool);
            String regex = "@String\\[(.*?)\\]:@evalController\\[([0-9a-zA-Z.$_]+)@[0-9a-f]{8}";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

//            System.out.println(resultAll);
//            System.out.println(matcher);

            while (matcher.find()) {
                JSONObject jsonObject = new JSONObject();
                String url = matcher.group(1);
                String className = matcher.group(2);
                jsonObject.put(url ,className);
                if (!jsonArray.contains(jsonObject)){
                    jsonArray.add(jsonObject);
                }
            }

            logger.info("/SMC/list: {}" , jsonArray);
            return jsonArray;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String urlPath) {
        try {
            int springCnt = Util.getSpringCnt();
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            for (int i = 0 ; i<springCnt ;i++){
                String cmd = String.format(unloadSMCByVmtool,i ,urlPath);

                List<String> result = wrapper.runCmd(cmd);
                logger.info("/SMC/unload: {}" , result);
                if (!result.isEmpty() && result.get(0).contains("Failed to execute ognl")){
                    logger.error(result.get(0));
                }
            }
            return "success";
        } catch (Exception e){
            logger.info(e.getMessage());
            return "failed";
        }
    }
}
