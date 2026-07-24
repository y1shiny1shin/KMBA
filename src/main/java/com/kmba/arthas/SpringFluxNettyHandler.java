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
@RequestMapping("/SpringFluxNettyHandler")
public class SpringFluxNettyHandler {
    // TODO
    /**
     * 卸载（初步逻辑，待优化）
     * 将恶意的doOnChannelInit，替换为Netty的默认Configurer：@reactor.netty.ReactorNetty@NOOP_CONFIGURER
     * 由于Netty的Pipeline是动态生成，故调用pipeline.remove仅能终止正在进行的连接，无法阻止后续连接
     * vmtool --action getInstances --className reactor.netty.http.server.HttpServerConfig --express 'instances.{(#this.doOnChannelInit.getClass.getName=="com.flux.evilNettyHandler")?(#this.doOnChannelInit=@reactor.netty.ReactorNetty@NOOP_CONFIGURER):(null)},instances.{#this.doOnChannelInit}'
     * 获取通过
     * vmtool --action getInstances --className reactor.netty.http.server.HttpServerConfig --express 'instances.{#this.doOnChannelInit.getClass.getName}'
     */
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private String listSFNHByVmtool = "vmtool --action getInstances --className reactor.netty.http.server.HttpServerConfig --express 'instances.{#this.doOnChannelInit.getClass.getName}'";
    /**
     * @ArrayList[
     *     @String[reactor.netty.ReactorNetty$$Lambda$340/1681094402],
     *     @String[reactor.netty.ReactorNetty$$Lambda$340/1681094402],
     *     @String[com.flux.evilNettyHandler],
     * ]
     */
    private String unloadSFNHByVmtool = "vmtool --action getInstances --className reactor.netty.http.server.HttpServerConfig --express 'instances.{(#this.doOnChannelInit.getClass.getName==\"%s\")?(#this.doOnChannelInit=@reactor.netty.ReactorNetty@NOOP_CONFIGURER):(null)},instances.{#this.doOnChannelInit}'";

    @RequestMapping("/list")
    public JSONArray list() {
        try {
//            int nettyHttpCnt = Util.getNettyHttpConfigCnt();
            JSONArray jsonArray = new JSONArray();

            String resultAll = Util.getListResult(1 ,listSFNHByVmtool);
            String regex = "@String\\[([0-9a-zA-Z.$_]+)\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                String className = matcher.group(1);
                if (!jsonArray.contains(className)){
                    jsonArray.add(className);
                }
            }

            logger.info("/SpringFluxNettyHandler/list: {}" , jsonArray);
            // ["com.flux.evilNettyHandler"]
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

            OGNLUtils.setStrictModeClose();

            String cmd = String.format(unloadSFNHByVmtool ,className);

            List<String> result = wrapper.runCmd(cmd);

            logger.info("/SpringFluxNettyHandler/unload: {}" , result);

            if (!result.isEmpty() && result.get(0).contains("Failed to execute ognl")){
                logger.error(result.get(0));
                return "failed";
            }

            return "success";
        } catch (Exception e){
            logger.info(e.getMessage());
            return "failed";
        }
    }

}
