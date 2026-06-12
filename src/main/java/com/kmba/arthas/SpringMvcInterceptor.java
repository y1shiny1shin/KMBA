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
 * https://liaoxuefeng.com/books/java/spring/web/interceptor/index.html
 * https://party.mem.mk/ui
 * https://jlkl.github.io/2022/05/26/Java-09/
 * https://goodapple.top/archives/1355
 */
@RestController
@RequestMapping("/SMI")
public class SpringMvcInterceptor {
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private String listSMIByVmtool = "vmtool --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext --express 'instances[%s].getBean(\"requestMappingHandlerMapping\").adaptedInterceptors'";
    //@HandlerInterceptor[][
    //    @ConversionServiceExposingInterceptor[org.springframework.web.servlet.handler.ConversionServiceExposingInterceptor@bc4fa53],
    //    @ResourceUrlProviderExposingInterceptor[org.springframework.web.servlet.resource.ResourceUrlProviderExposingInterceptor@3b2cfeff],
    //    @evilInterceptor[com.DemoSpring.evilInterceptor@34b9fcef],
    //]
    private String unloadSMIByVmtool = "vmtool --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext --express '#rmhm=instances[%s].getBean(\"requestMappingHandlerMapping\"),#oldais=#rmhm.adaptedInterceptors,#newais=new java.util.ArrayList(),#oldais.{(#this.class.getName()!=\"%s\")?(#a=#this,#newais.add(#a)):null},#ahmClass=#rmhm.getClass().getSuperclass().getSuperclass().getSuperclass(),#f=#ahmClass.getDeclaredField(\"adaptedInterceptors\"),#f.setAccessible(true),#f.set(#rmhm,#newais)'";


    @RequestMapping("/list")
    public JSONArray list() {
        try {
            int springCnt = Util.getSpringCnt();

            JSONArray jsonArray = new JSONArray();

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            List<String> result = new ArrayList<>();
            wrapper.runCmd(Strict_Option+ENTER);

            OGNLUtils.setStrictModeClose();

            String resultAll = Util.getListResult(springCnt ,listSMIByVmtool);
            System.out.println(resultAll);

            String regex = "\\@([0-9a-zA-Z]+)\\[([0-9a-zA-Z.$_]+)\\@";

            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                JSONObject jsonObject = new JSONObject();
                String SMIName = matcher.group(1);
                String className = matcher.group(2);
                jsonObject.put(SMIName ,className);
                System.out.println(jsonObject);
                if (!jsonArray.contains(jsonObject)){
                    jsonArray.add(jsonObject);
                }
            }

            logger.info("/SMI/list: {}" ,jsonArray);

            return jsonArray;

        } catch (Exception e) {
            logger.info(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String className) {
        try {
            int springCnt = Util.getSpringCnt();
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            for (int i = 0 ; i<springCnt ;i++){
                String cmd = String.format(unloadSMIByVmtool ,i ,className);

                List<String> result = wrapper.runCmd(cmd);
                logger.info("/SMI/unload: {}" , result);
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
