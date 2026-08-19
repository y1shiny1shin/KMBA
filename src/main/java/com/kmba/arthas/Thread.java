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
     *
     * ====
     * 上面的做法是不对的，既然停止了现场，那target变为null，只需要再list的时候，指定target为null的就不输出就可以
     */
    private String listThreadByVmtool = "vmtool -l -1 --action getInstances --className java.lang.Thread --express '#ts=instances.{?(#this.target!=null)},#ts.{#ts0=#this,#tName=#ts0.name,#tClassName,{(#ts0.target==null)?(null):(#tClassName=#ts0.target.getClass().getName())},#tName+\":\"+#tClassName}'";
    private String unloadThreadByVmtool = "vmtool -l -1 --action getInstances --className java.lang.Thread --express '#ts=instances.{?#this.target!=null},#ts.{#thread=#this,#className=#thread.target.getClass().getName(),#threadName=#thread.name,{#threadName.equals(\"%s\")&&#className.equals(\"%s\")?#thread.interrupt():null}}'";
    // 线程

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    @RequestMapping("/list")
    public JSONArray list(){
        try{
            JSONArray jsonArray = new JSONArray();

            String resultAll = Util.getListResult(1 ,listThreadByVmtool);

            /**
             * @String[mcp-keep-alive-scheduler:java.util.concurrent.ThreadPoolExecutor$Worker@3bb99711[State = 0, empty queue]],
             */
            String regex = "\\@String\\[(.*?)\\:([0-9a-zA-Z.$_/]+)\\]";
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

            int failCnt = 0;
            for (int i = 0; i < threadCnt; i++) {
                String cmd = String.format(unloadThreadByVmtool , threadName , className);

                List<String> result = wrapper.runCmd(cmd);
                String resultStr = String.join("" ,result);
                logger.info("/thread/unload: {}" ,resultStr);
                if (resultStr.contains("Failed to execute ognl")){
                    logger.error(resultStr);
                    failCnt++;
                }
            }
            if (failCnt == threadCnt) {
                return "OGNL execute failed";
            }
            return "success";

        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }
    }

}


