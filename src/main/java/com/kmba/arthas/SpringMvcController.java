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
 * https://party.mem.mk/ui
 * https://goodapple.top/archives/1355
 * https://jlkl.github.io/2022/05/26/Java-09/
 */
@RestController
@RequestMapping("/SpringMvcController")
public class SpringMvcController {
    /**
     * 需要适配高版本SpringMvc的注入方式
     *
     * =====
     * registerMapping
     * // 1. 从当前上下文环境中获得 RequestMappingHandlerMapping 的实例 bean
     * RequestMappingHandlerMapping r = context.getBean(RequestMappingHandlerMapping.class);
     * // 2. 通过反射获得自定义 controller 中唯一的 Method 对象
     * Method method = (Class.forName("me.landgrey.SSOLogin").getDeclaredMethods())[0];
     * // 3. 定义访问 controller 的 URL 地址
     * PatternsRequestCondition url = new PatternsRequestCondition("/hahaha");
     * // 4. 定义允许访问 controller 的 HTTP 方法（GET/POST）
     * RequestMethodsRequestCondition ms = new RequestMethodsRequestCondition();
     * // 5. 在内存中动态注册 controller
     * RequestMappingInfo info = new RequestMappingInfo(url, ms, null, null, null, null, null);
     * r.registerMapping(info, Class.forName("恶意Controller").newInstance(), method);
     * =====
     * registerHandler
     * // 1. 在当前上下文环境中注册一个名为 dynamicController 的 Webshell controller 实例 bean
     * context.getBeanFactory().registerSingleton("dynamicController", Class.forName("me.landgrey.SSOLogin").newInstance());
     * // 2. 从当前上下文环境中获得 DefaultAnnotationHandlerMapping 的实例 bean
     * org.springframework.web.servlet.mvc.annotation.DefaultAnnotationHandlerMapping  dh = context.getBean(org.springframework.web.servlet.mvc.annotation.DefaultAnnotationHandlerMapping.class);
     * // 3. 反射获得 registerHandler Method
     * java.lang.reflect.Method m1 = org.springframework.web.servlet.handler.AbstractUrlHandlerMapping.class.getDeclaredMethod("registerHandler", String.class, Object.class);
     * m1.setAccessible(true);
     * // 4. 将 dynamicController 和 URL 注册到 handlerMap 中
     * m1.invoke(dh, "/favicon", "dynamicController");
     * =====
     * detectHandlerMethods
     * context.getBeanFactory().registerSingleton("dynamicController", Class.forName("恶意Controller").newInstance());
     * org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping requestMappingHandlerMapping = context.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
     * java.lang.reflect.Method m1 = org.springframework.web.servlet.handler.AbstractHandlerMethodMapping.class.getDeclaredMethod("detectHandlerMethods", Object.class);
     * m1.setAccessible(true);
     * m1.invoke(requestMappingHandlerMapping, "dynamicController");
     * =====
     */
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private String listSMCByVmtool = "vmtool --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext -classLoader %s --express '#map=instances[%s].getBean(@org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping@class).handlerMap,#class=#map.getClass().getDeclaredField(\"m\"),#class.setAccessible(true),#class.get(#map)'";
    //@LinkedHashMap[
    //    @String[/exec]:@evalController[com.DemoSpring.evalController@25655ffa],
    //]
    private String unloadSMCByVmtool = "vmtool --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext -classLoader %s --express '#map=instances[%s].getBean(@org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping@class).handlerMap,#class=#map.getClass().getDeclaredField(\"m\"),#class.setAccessible(true),#class.get(#map).remove(\"%s\")'";

    @RequestMapping("/list")
    public JSONArray list(){
        try {
            int springCnt = Util.getSpringCnt();
            JSONArray jsonArray = new JSONArray();

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            wrapper.runCmd(Strict_Option+ENTER);

            OGNLUtils.setStrictModeClose();

            // [@LinkedHashMap[@String[/exec]:@evalController[com.DemoSpring.evalController@25655ffa]]]
            // 每个实例都需要动态获取 classLoader 后指定
            List<String> result = new ArrayList<>();
            for (int i = 0; i < springCnt; i++) {
                String classLoader = Util.getClassLoader("org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext", i, wrapper);
                if (classLoader == null) continue;
                String cmd = String.format(listSMCByVmtool, classLoader, i);
                List<String> result0 = wrapper.runCmd(cmd);
                if (result0 != null) {
                    for (String s : result0)
                        if (!(s == null || s.isEmpty()))
                            result.add(s);
                }
            }
            String resultAll = String.join("" ,result);
            String regex = "@String\\[(.*?)\\]:@(.*?)\\[([0-9a-zA-Z.$_]+)@[0-9a-f]{1,8}";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                JSONObject jsonObject = new JSONObject();
                String url = matcher.group(1);
                String className = matcher.group(2);
                jsonObject.put(url ,className);
                if (!jsonArray.contains(jsonObject)){
                    jsonArray.add(jsonObject);
                }
            }

            logger.info("/SpringMvcController/list: {}" , jsonArray);
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
                // 每个实例都需要动态获取 classLoader 后指定
                String classLoader = Util.getClassLoader("org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext", i, wrapper);
                if (classLoader == null) continue;
                String cmd = String.format(unloadSMCByVmtool, classLoader, i ,urlPath);

                List<String> result = wrapper.runCmd(cmd);
                logger.info("/SpringMvcController/unload: {}" , result);
                if (!result.isEmpty() && result.get(0).contains("Failed to execute ognl")){
                    logger.error(result.get(0));
                    return "OGNL execute failed";
                }
            }
            return "success";
        } catch (Exception e){
            logger.info(e.getMessage());
            return "failed";
        }
    }
}
