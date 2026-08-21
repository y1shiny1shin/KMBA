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
@RequestMapping("/SpringFluxWebFilter")
public class SpringFluxWebFilter {
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
//    @UnmodifiableRandomAccessList[
//    @EvilFilter[com.flux.EvilFilter@1ecd5568],
//            ]
    /**
     * unload 需要指定classloader（已做）
     */
    private String listSFWFbyVmtool = "vmtool -l -1 --action getInstances --className org.springframework.web.server.handler.FilteringWebHandler --express '#handler=instances[%s],#chain=#handler.chain,#chain.allFilters'";
    /**
     * 难点在于修改final属性的变量，本来打算用构造器新建DefaultWebFilterChain的来做替换的
     * 但是getInterfaces方法返回的是class[]，还需要遍历比较麻烦，干脆直接new了，正好都是一个classLoader，可以new
     * 大致逻辑和Executor差不多，都是需要替换
     * unload 需要指定 classLoader，通过 Util.getClassLoader() 获取后再指定
     */
    private String unloadSFWFbyVmtool = "vmtool -l -1 --action getInstances --className org.springframework.web.server.handler.FilteringWebHandler -c %s --express '#handler=instances[%s],#chain=#handler.chain,#oldFilters=#chain.getFilters(),#newFilters=new java.util.ArrayList(),#oldFilters.{(#this.class.getName()!=\"%s\")?(#tmp=#this,#newFilters.add(#tmp)):(null)},#newChain=new org.springframework.web.server.handler.DefaultWebFilterChain(#chain.getHandler(),#newFilters),#f=#handler.getClass().getDeclaredField(\"chain\"),#f.setAccessible(true),#mod=#f.getClass.getDeclaredField(\"modifiers\"),#mod.setAccessible(true),#mod.setInt(#f,#f.getModifiers()&~@java.lang.reflect.Modifier@FINAL),#f.set(#handler,#newChain),#mod.setInt(#f,#f.getModifiers()&@java.lang.reflect.Modifier@FINAL)'";

    @RequestMapping("/list")
    public JSONArray list() {
        try {
            int springFluxCnt = Util.getSpringFluxCnt();
            JSONArray jsonArray = new JSONArray();

            String resultAll = Util.getListResult(springFluxCnt ,listSFWFbyVmtool);
            String regex = "@([0-9a-zA-Z.$_]+)\\[([0-9a-zA-Z.$_]+)@([0-9a-f]+)\\]";
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

            logger.info("/SpringFluxWebFilter/list: {}" , jsonArray);
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

            OGNLUtils.setStrictModeClose();

            int failCnt = 0;
            for (int i = 0 ; i<springFluxCnt ;i++){
                // 每个实例都需要动态获取 classLoader 后指定
                String classLoader = Util.getClassLoader("org.springframework.web.server.handler.FilteringWebHandler", i, wrapper);
                if (classLoader == null) {
                    failCnt++;
                    continue;
                }
                String cmd = String.format(unloadSFWFbyVmtool, classLoader, i ,className);

                List<String> result = wrapper.runCmd(cmd);
                String resultStr = String.join("" ,result);
                logger.info("/SpringFluxWebFilter/unload: {}" , resultStr);
                if (resultStr.contains("Failed to execute ognl")){
                    
                    failCnt++;
                }
            }
            if (failCnt == springFluxCnt) {
                return "OGNL execute failed";
            }
            return "success";
        } catch (Exception e){
            logger.info(e.getMessage());
            return "failed";
        }
    }
}
