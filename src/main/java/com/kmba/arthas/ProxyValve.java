package com.kmba.arthas;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.Utils.Dict;
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

import static com.kmba.Utils.Dict.*;

/**
 * https://github.com/ReaJason/MemShellParty
 */
@RestController
@RequestMapping("/proxyValve")
public class ProxyValve {
//    private String clearFirstByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext -classLoaderClass java.net.URLClassLoader --express '#pip=instances[%s].getPipeline,#bad=#pip.first,#good=#pip.basic,{#bad.class.name.equals(\"%s\")?(#pip.first=#good):null}'";
//    private String getFirstByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext -classLoaderClass java.net.URLClassLoader --express '#pip=instances[%s].getPipeline.first.class.name'";
//    private String checkBasicByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext -classLoaderClass java.net.URLClassLoader --express '#pip=instances[%s].getPipeline,#name=#pip.basic.class.name,#name.contains(\"Proxy\")'";

    /**
     * 必须指定classLoader，通过 Util.getClassLoader() 获取后再指定
     */
    private String clearFirstByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext -classLoader %s --express '#pip=instances[%s].getPipeline,#firstField=#pip.class.getDeclaredField(\"first\"),#firstField.setAccessible(true),#first=#firstField.get(#pip),{(#first.class.name.equals(\"%s\"))?(#evil=@java.lang.reflect.Proxy@getInvocationHandler(#first),#f=#evil.class.getDeclaredFields().{(#this.setAccessible(true),#value=#this.get(#evil),#value instanceof org.apache.catalina.Valve)?(#firstField.set(#pip,#value)):(\"false\")}):(\"failed\")}'";
    private String clearBasicByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext -classLoader %s --express '#pip=instances[%s].getPipeline,#basicField=#pip.class.getDeclaredField(\"basic\"),#basicField.setAccessible(true),#basic=#basicField.get(#pip),{(#basic.class.name.equals(\"%s\"))?(#evil=@java.lang.reflect.Proxy@getInvocationHandler(#basic),#f=#evil.class.getDeclaredFields().{(#this.setAccessible(true),#value=#this.get(#evil),#value instanceof org.apache.catalina.Valve)?(#basicField.set(#pip,#value)):(\"false\")}):(\"failed\")}'";
    /*@ArrayList[
    @String[first:org.apache.catalina.authenticator.NonLoginAuthenticator],
    @String[basic:org.apache.catalina.core.StandardContextValve],
            ]*/
    private String getFirstAndBasicByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext -classLoader %s --express '#pip=instances[%s].getPipeline,#basicField=#pip.class.getDeclaredField(\"basic\"),#basicField.setAccessible(true),#basic=#basicField.get(#pip),#firstField=#pip.class.getDeclaredField(\"first\"),#firstField.setAccessible(true),#first=#firstField.get(#pip),#x={(#first!=null)?{\"first\"+\":\"+#first.class.name,\"basic\"+\":\"+#basic.class.name}:{\"basic\"+\":\"+#basic.class.name}},#x[0]'";

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    @RequestMapping("/list")
    public JSONArray list() {
        try {
            JSONArray jsonArray = new JSONArray();

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            wrapper.runCmd(Strict_Option+ENTER);

            OGNLUtils.setStrictModeClose();

//            @ArrayList[
//                @String[first:org.apache.catalina.authenticator.NonLoginAuthenticator],
//                @String[basic:org.apache.catalina.core.StandardContextValve],
//            ]
            String firstRegex = "@String\\[first\\:([0-9a-zA-Z.$_]+)\\]";
            Pattern firstPattern = Pattern.compile(firstRegex);

            String basicRegex = "@String\\[basic\\:([0-9a-zA-Z.$_]+)\\]";
            Pattern basicPattern = Pattern.compile(basicRegex);

            // 每个 StandardContext 都需要动态获取 classLoader 后指定
            int tomcatSiteCnt = Dict.getTomcatSiteCnt();
            List<String> result = new ArrayList<>();
            for (int i = 0; i < tomcatSiteCnt; i++) {
                String classLoader = Util.getClassLoader("org.apache.catalina.core.StandardContext", i, wrapper);
                if (classLoader == null) continue;
                String cmd = String.format(getFirstAndBasicByVmtool, classLoader, i);
                List<String> result0 = wrapper.runCmd(cmd);
                if (result0 != null) {
                    for (String s : result0)
                        if (!(s == null || s.isEmpty()))
                            result.add(s);
                }
            }
            String resultAll = String.join("" ,result);

            Matcher firstMatcher = firstPattern.matcher(resultAll);
            Matcher basicMatcher = basicPattern.matcher(resultAll);

            while (firstMatcher.find()) {
                if (firstMatcher.group(1) != null) {
                    JSONObject tmp = new JSONObject();
                    tmp.put("first", firstMatcher.group(1));
                    jsonArray.add(tmp);
                }

            }

            while (basicMatcher.find()) {
                if (basicMatcher.group(1) != null) {
                    JSONObject tmp = new JSONObject();
                    tmp.put("basic", basicMatcher.group(1));
                    jsonArray.add(tmp);
                }

            }

            logger.info("/proxyValve/list: {}" ,jsonArray);

            return jsonArray;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unloadFirst")
    public String unloadFirst(@RequestParam String className) {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            wrapper.runCmd(Strict_Option+ENTER);
            wrapper.runCmd(Unsafe_Option+ENTER);

            OGNLUtils.setStrictModeClose();

            int tomcatSiteCnt = Dict.getTomcatSiteCnt();
            for (int i=0;i<tomcatSiteCnt;i++){
                String classLoader = Util.getClassLoader("org.apache.catalina.core.StandardContext", i, wrapper);
                if (classLoader == null) continue;
                String cmd = String.format(clearFirstByVmtool, classLoader, i , className);
                List<String> result = wrapper.runCmd(cmd);
                logger.info("/proxyValve/unloadFirst: {}" ,result);
                if (!result.isEmpty() && result.get(0).contains("Failed to execute ognl")){
                    logger.error(result.get(0));
                    return "OGNL execute failed";
                }
            }
            return "success";
        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }
    }

    @RequestMapping("/unloadBasic")
    public String unloadBasic(@RequestParam String className) {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            wrapper.runCmd(Strict_Option+ENTER);
            wrapper.runCmd(Unsafe_Option+ENTER);

            OGNLUtils.setStrictModeClose();
            int tomcatSiteCnt = Dict.getTomcatSiteCnt();
            for (int i=0;i<tomcatSiteCnt;i++){
                String classLoader = Util.getClassLoader("org.apache.catalina.core.StandardContext", i, wrapper);
                if (classLoader == null) continue;
                String cmd = String.format(clearBasicByVmtool, classLoader, i , className);
                List<String> result = wrapper.runCmd(cmd);
                logger.info("/proxyValve/unloadBasic: {}" ,result);
                if (!result.isEmpty() && result.get(0).contains("Failed to execute ognl")){
                    logger.error(result.get(0));
                    return "OGNL execute failed";
                }
            }
            return "success";
        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }
    }

//    @RequestMapping("/check")
//    public String check() {
//        try{
//            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
//
//            List<String> result = new ArrayList<>();
//            for (int i=0;i<Dict.getTomcatSiteCnt();i++){
//                String cmd = String.format(checkBasicByVmtool ,i);
//                result = wrapper.runCmd(cmd);
//                // 如果basic被修改，返回yes，那么前端提示立即重启
//                if (result != null && result.get(0).equals("@Boolean[true]")) {
//                    logger.info("/proxyValve/check: yes");
//                    return "yes";
//                }
//            }
//            return "no";
//        } catch (Exception e){
//            logger.error(e.getMessage());
//            return "failed";
//        }
//    }
}
