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
 * 线程很敏感，所以处理的时候必须要小心，将恶意线程interrupt即可
 * 如果是从Thread[]中给恶意线程删除掉的话，修改到原数组可能会有未知的错误
 */
@RestController
@RequestMapping("/thread")
public class Thread {
    // 线程比较敏感，所以需要双重验证和温和停止
    /**
     * 歪打正着地解决了问题，本来停止thread，原thread是不会从thread[]中删除的
     * 但是由于停止了线程，线程target就变为null，会导致vmtool执行ognl表达式失败
     * 那么就不会返回值，luckly
     */
    private String listThreadByVmtool = "vmtool --action getInstances --className java.lang.Thread --express '#thread=instances[%s],#className=#thread.target.getClass.getName(),#threadName=#thread.name,#threadName+\":\"+#className'";
    private String unloadThreadByVmtool = "vmtool --action getInstances --className java.lang.Thread --express '#thread=instances[%s],#threadName=#thread.name,#className=#thread.target.getClass().getName(),{#threadName.equals(\"%s\")&&#className.equals(\"%s\")?#thread.interrupt():null}'";
    // 线程

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    @RequestMapping("/list")
    public JSONArray list(){
        try{
            JSONArray jsonArray = new JSONArray();
            int threadCnt = Util.getThreadCnt();

            String resultAll = Util.getListResult(threadCnt ,listThreadByVmtool);

            String regex = "\\@String\\[(.*?)\\:([0-9a-zA-Z.$_]+)\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                JSONObject jsonObject = new JSONObject();

                if (matcher.group(1) != null && matcher.group(2) != null) {
                    jsonObject.put(matcher.group(1), matcher.group(2));
                }
                jsonArray.add(jsonObject);
            }
            logger.info("/thread/list: {}" ,jsonArray);

            return jsonArray;
        } catch (Exception e){
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String threadName , @RequestParam String className ){
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int threadCnt = Util.getThreadCnt();

            for (int i = 0; i < threadCnt; i++) {
                String cmd = String.format(unloadThreadByVmtool, i , threadName , className);

                logger.info("/thread/unload: "+wrapper.runCmd(cmd));
            }
            return "success";

        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }
    }

}


