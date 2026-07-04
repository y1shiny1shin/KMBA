package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.Utils.OGNLUtils;
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

import static com.kmba.Utils.Dict.ENTER;
import static com.kmba.Utils.Dict.Strict_Option;


/**
 * https://forum.butian.net/share/2593
 * https://xz.aliyun.com/news/10778
 * https://github.com/ReaJason/MemShellParty
 */
@RestController
@RequestMapping("/SFWF")
public class SpringFluxWebFilter {
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
//    @UnmodifiableRandomAccessList[
//    @EvilFilter[com.flux.EvilFilter@1ecd5568],
//            ]
    private String listSFWFbyVmtool = "vmtool -action getInstances --className org.springframework.web.server.handler.FilteringWebHandler --express '#handler=instances[%s],#chain=#handler.chain,#chain.allFilters'";
    /**
     * 难点在于修改final属性的变量，本来打算用构造器新建DefaultWebFilterChain的来做替换的
     * 但是getInterfaces方法返回的是class[]，还需要遍历比较麻烦，干脆直接new了，正好都是一个classLoader，可以new
     * 大致逻辑和Executor差不多，都是需要替换
     */
    private String unloadSFWFbyVmtool = "vmtool -action getInstances --className org.springframework.web.server.handler.FilteringWebHandler --express '#handler=instances[%s],#chain=#handler.chain,#oldFilters=#chain.getFilters(),#newFilters=new java.util.ArrayList(),#oldFilters.{(#this.class.getName()!=\"%s\")?(#tmp=#this,#newFilters.add(#tmp)):(null)},#newChain=new org.springframework.web.server.handler.DefaultWebFilterChain(#chain.getHandler(),#newFilters),#f=#handler.getClass().getDeclaredField(\"chain\"),#f.setAccessible(true),#mod=#f.getClass.getDeclaredField(\"modifiers\"),#mod.setAccessible(true),#mod.setInt(#f,#f.getModifiers()&~@java.lang.reflect.Modifier@FINAL),#f.set(#handler,#newChain),#mod.setInt(#f,#f.getModifiers()&@java.lang.reflect.Modifier@FINAL)'";

    @RequestMapping("/list")
    public JSONArray list() {
        try {
            int springFluxCnt = Util.getSpringFluxCnt();
            JSONArray jsonArray = new JSONArray();

            String resultAll = Util.getListResult(springFluxCnt ,listSFWFbyVmtool);
            String regex = "@([0-9a-zA-Z.$_]+)\\[([0-9a-zA-Z.$_]+)@[0-9a-f]{8}\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                JSONObject jsonObject = new JSONObject();
                String filterName = matcher.group(1);
                String className = matcher.group(2);
                jsonObject.put(filterName ,className);
                if (!jsonArray.contains(jsonObject)){
                    jsonArray.add(jsonObject);
                }
            }

            logger.info("/SFWF/list: {}" , jsonArray);
            return jsonArray;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String className){
        try {
            int springFluxCnt = Util.getSpringFluxCnt();
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            for (int i = 0 ; i<springFluxCnt ;i++){
                String cmd = String.format(unloadSFWFbyVmtool ,i ,className);

                List<String> result = wrapper.runCmd(cmd);
                logger.info("/SFWF/unload: {}" , result);
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
