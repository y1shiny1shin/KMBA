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
 * https://www.freebuf.com/articles/vuls/345119.html
 * https://y4er.com/posts/tomcat-upgrade-memshell/
 */
@RestController
@RequestMapping("/upgrade")
public class Upgrade {
    private String listUpgradeByVmtool = "vmtool --action getInstances --className org.apache.coyote.http11.AbstractHttp11Protocol --express 'instances[%s].httpUpgradeProtocols'";
    private String unloadUpgradeByVmtool = "vmtool --action getInstances --className org.apache.coyote.http11.AbstractHttp11Protocol --express 'instances[%s].httpUpgradeProtocols.remove(\"%s\")'";
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    @RequestMapping("/list")
    public JSONArray list(){
        try{
            JSONArray jsonArray = new JSONArray();

            String resultAll = Util.getListResult(tomcatSiteCnt ,listUpgradeByVmtool);

            String regex = "\\@String\\[(\\w+)\\]\\:\\@(\\w+)\\[([0-9a-zA-Z.$_]+)\\@";

            Pattern pattern = Pattern.compile(regex);

            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                if (matcher.group(1) != null && matcher.group(3) != null) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(matcher.group(1), matcher.group(3));
                    jsonArray.add(jsonObject);
                }
            }
            logger.info("/upgrade/list: {}"  ,jsonArray.toJSONString());

            return jsonArray;
        } catch (Exception e){
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }


    @RequestMapping("/unload")
    public String unload(@RequestParam String upgradeName){
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            System.out.println(upgradeName);
            for (int i=0;i<tomcatSiteCnt;i++){
                String cmd = String.format(unloadUpgradeByVmtool, i ,upgradeName);

                logger.info("/upgrade/unload: " +wrapper.runCmd(cmd));
            }
            return "success";
        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }
    }
}
