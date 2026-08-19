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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/SpringFluxHandlerMethod")
public class SpringFluxHandlerMethod {
    /**
     * HandlerMethod：https://party.mem.mk/ui -> SpringWebFlux -> HandlerMethod
     * 这个内存马，有一点点像Tomcat-Servlet，获取到RequestMappingHandlerMapping后，在里面写入恶意的路由，再指定其中执行的method，如下：
     * 本质是Netty匹配到这个路由后，就执行Map中，key对应的方法，
     * 例如/exec就执行com.flux.LogHandlerMethod#invoke(ServerWebExchange)方法
     *
     * 写入的思路是：
     * 1. 通过反射获取到 org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping
     * 2. 构造一个包含能获取到Request的类，作为执行命令的主要逻辑
     * 3. 调用反射调用 RequestMappingHandlerMapping.registerHandlerMethod(Object handler, Method method, T mapping)，将构造的类和路由写入其中
     *
     * @UnmodifiableMap[
     *     @RequestMappingInfo[{ /webFilter}]:@HandlerMethod[com.flux.WebFilter#webFilter()],
     *     @RequestMappingInfo[{ /handlerMethod}]:@HandlerMethod[com.flux.evilHandlerMethod#inject()],
     *     @RequestMappingInfo[{ /nettyHandler}]:@HandlerMethod[com.flux.NettyHandler#nettyHandler()],
     *     @RequestMappingInfo[{ /exec}]:@HandlerMethod[com.flux.LogHandlerMethod#invoke(ServerWebExchange)],
     * ]
     * ========
     * org.springframework.web.reactive.result.method.AbstractHandlerMethodMapping$MappingRegistry#unregister
     * 自带一个unregister，正好可以直接调用卸载，稍微难点的是这里的逻辑要匹配好
     * unload：
     * vmtool -l -1 --action getInstances --className 'org.springframework.boot.web.embedded.netty.NettyWebServer$1' --express '#maps,#registry=instances[0].this$0.handler.httpHandler.delegate.delegate.delegate.delegate.getHandlerMappings().{(#this.class.getName.contains("RequestMappingHandlerMapping"))?(#maps=#this):(null)},#routes=#maps.getHandlerMethods(),#routes.keys.{(#tmpkey=#this,#routes.get(#tmpkey).toString().contains("com.flux.LogHandlerMethod"))?(#maps.mappingRegistry.unregister(#tmpkey)):(null)}'
     * list：
     * vmtool -l -1 --action getInstances --className 'org.springframework.boot.web.embedded.netty.NettyWebServer$1' --express '#maps,#registry=instances[0].this$0.handler.httpHandler.delegate.delegate.delegate.delegate.getHandlerMappings().{(#this.class.getName.contains("RequestMappingHandlerMapping"))?(#maps=#this):(null)},#maps.getHandlerMethods'
     *
     *
     * 需要优化（已做）
     * [@UnmodifiableMap[, @RequestMappingInfo[{ /, produces [application/json;charset=UTF-8]}]:@HandlerMethod[lab.VulnController#index(String)],, @RequestMappingInfo[{[GET, POST] /shell-flux-method2}]:@HandlerMethod[MemshellFluxHandlerMethod#simple()],, @RequestMappingInfo[{[GET, POST] /shell-flux-method}]:@HandlerMethod[MemshellFluxHandlerMethod#handle(ServerWebExchange)],, ]]
     */

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private String listSFHMByVmtool = "vmtool -l -1 --action getInstances --className 'org.springframework.boot.web.embedded.netty.NettyWebServer$1' --express '#maps,#registry=instances[%s].this$0.handler.httpHandler.delegate.delegate.delegate.delegate.getHandlerMappings().{(#this.class.getName.contains(\"RequestMappingHandlerMapping\"))?(#maps=#this):(null)},#ms=#maps.getHandlerMethods,#ms.keys.{#tmpkey=#this,#pattern=#tmpkey.patternsCondition.patterns.toString(),#pattern+\":\"+#ms[#tmpkey]}'";
    private String unloadSFHMByVmtool = "vmtool -l -1 --action getInstances --className 'org.springframework.boot.web.embedded.netty.NettyWebServer$1' --express " +
            "'#maps,#registry=instances[%s].this$0.handler.httpHandler.delegate.delegate.delegate.delegate.getHandlerMappings()" +
            ".{(#this.class.getName.contains(\"RequestMappingHandlerMapping\"))?(#maps=#this):(null)}," +
            "#routes=#maps.getHandlerMethods()," +
            "#routes.keys.{(#tmpkey=#this,#tmpkey.patternsCondition.patterns.toString().equals(\"[%s]\"))?(#maps.mappingRegistry.unregister(#tmpkey)):(null)}'";
    @RequestMapping("/list")
    public JSONArray list() {
        try {
            JSONArray jsonArray = new JSONArray();
            int nettyServerCnt = Util.getNettyServerCnt();

            /**
             * @ArrayList[
             *     @String[[/]:lab.VulnController#index(String)],
             *     @String[[/shell-flux-method2]:MemshellFluxHandlerMethod#simple()],
             *     @String[[/shell-flux-method]:MemshellFluxHandlerMethod#handle(ServerWebExchange)],
             * ]
             */
            String resultAll = Util.getListResult(1 ,listSFHMByVmtool);
            String regex = "\\@String\\[\\[(.*?)\\]:([0-9a-zA-Z.$_]+)#(.*?)\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                String patterns = matcher.group(1);
                String className = matcher.group(2);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put(patterns ,className);

                if (!jsonArray.contains(jsonObject)){
                    jsonArray.add(jsonObject);
                }
            }

            logger.info("/SpringFluxHandlerMethod/list: {}" ,jsonArray);
            // [{"/webFilter":"com.flux.WebFilter"},{"/handlerMethod":"com.flux.evilHandlerMethod"},{"/nettyHandler":"com.flux.NettyHandler"},{"/exec":"com.flux.LogHandlerMethod"}]
            return jsonArray;

        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String className) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int nettyServerCnt = Util.getNettyServerCnt();

            OGNLUtils.setStrictModeClose();

            int failCnt = 0;
            for (int i = 0 ;i<nettyServerCnt ;i++) {
                String cmd = String.format(unloadSFHMByVmtool,i ,className);

                List<String> result = wrapper.runCmd(cmd);
                String resultStr = String.join("" ,result);
                logger.info("/SpringFluxHandlerMethod/unload: {}" , resultStr);
                if (resultStr.contains("Failed to execute ognl")){
                    logger.error(resultStr);
                    failCnt++;
                }
            }
            if (failCnt == nettyServerCnt) {
                return "OGNL execute failed";
            }

            return "success";
        } catch (Exception e){
            logger.info(e.getMessage());
            return "failed";
        }
    }
}
