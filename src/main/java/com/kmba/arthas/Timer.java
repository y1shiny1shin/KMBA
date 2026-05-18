package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
import com.kmba.Utils.TomcatUtil;
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

/**
 * Arthas 操作Timer对象存在bug，故不能稳定查杀Timer内存马。
 */
@RestController
@RequestMapping("/timer")
public class Timer {
    //    private String listTimerByVmtool = "vmtool --action getInstances --className java.util.Timer --express '#timers=instances[%s].queue.queue,#existTimer=#timers.{#this!=null?#this.getClass().getName():null}.{?#this!=null}'";
    // 不从Timer#queue获取（Arthas获取Timer对象存在bug），直接找TimerTask对象
    private String listTimerByVmtool = "vmtool --action getInstances --className java.util.TimerTask --express 'instances.{#this.getClass.getName}'";
    // 温和版TimerTask#cancel方法
    private String unloadTimerByVmtoolGently = "vmtool --action getInstances --className java.util.Timer --express '#timers=instances[%s].queue.queue,#existTimer=#timers.{#this!=null?(#this.getClass().getName().equals(\"%s\"),#this.cancel()):null}'";
    // 强制删除，会有资源泄露的风险，并且stop方法已弃用，可能会执行失败
    private String unloadTimerByVmtoolForcly = "vmtool --action getInstances --className java.util.Timer --express '#timer=instances[%s],#queues=#timer.queue.queue,#evilQueue=#queues.{#this!=null&&#this.getClass().getName().equals(\"%s\")?(#timer.thread.stop(),\"success\"):null}.{?#this!=null}'";
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    @RequestMapping("/list")
    public JSONArray list() {
        try{
//            int timerCnt = TomcatUtil.getTimerCnt();

            JSONArray jsonArray = new JSONArray();
            List<String> result = new ArrayList<>();
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

//            for (int i = 0; i< timerCnt; i++){
//                String cmd = String.format(listTimerByVmtool, i);
//                List<String> result0 = wrapper.runCmd(cmd);
//
//                for (String s:result0){
//                    if (!(s.isEmpty() || s == null)) result.add(s);
//                }
//            }
            List<String> result0 = wrapper.runCmd(listTimerByVmtool);
            for (String s:result0) if (!(s.isEmpty() || s == null)) result.add(s);

            String resultAll = String.join("" , result);

            // @ArrayList[@String[com.InjectTimer$1]
            String regex = "\\@String\\[([0-9a-zA-Z.$_]+)\\]";
            Pattern pattern = Pattern.compile(regex);

            System.out.println(resultAll);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                if (matcher.group(1) != null) jsonArray.add(matcher.group(1));
            }
            logger.info("/timer/list: "+jsonArray.toString());

            return jsonArray;

        } catch (Exception e){
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String className) {
        try{
            int timerCnt = TomcatUtil.getTimerCnt();
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            for (int i=0; i<timerCnt; i++){
                String cmd = String.format(unloadTimerByVmtoolGently, i ,className);

                logger.info("/timer/list: "+wrapper.runCmd(cmd));
            }
            return "success";
        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }
    }

    @RequestMapping("/unloadForce")
    public String unloadForce(@RequestParam String className) {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            int timerCnt = TomcatUtil.getTimerCnt();

            for (int i=0; i<timerCnt; i++){
                String cmd = String.format(unloadTimerByVmtoolForcly, i ,className);

                logger.info("/timer/unloadForce: "+wrapper.runCmd(cmd));
            }
            return "success";

        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }
    }
}
