package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
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

@RestController
@RequestMapping("/executor")
public class Executor {
    private String listExecutorByVmtool = "vmtool --action getInstances --className org.apache.tomcat.util.net.NioEndpoint --express 'instances.{#e=#this.getExecutor}'";
//    @ArrayList[
//    @evilExecutor[com.InjectExecutor$evilExecutor@5af54470[Running, pool size = 10, active threads = 0, queued tasks = 0, completed tasks = 0]],
//            ]

    /**
     * 杀，有风险，因为
     * java.util.concurrent.ThreadPoolExecutor和
     * org.apache.tomcat.util.threads.ThreadPoolExecutor不是一个类型，
     * 直接设置不会有问题，但是会影响后来者（利弊参半），可能会触发java.lang.ClassCastException的异常，对可能存在的业务有影响
     */
    private String unloadExecutorByVmtoolBrutly = "vmtool --action getInstances --className org.apache.tomcat.util.net.NioEndpoint --express '#endpoint=instances[%s],#org=#endpoint.executor,{#org.class.getName.equals(\"%s\")?(#constructor=@java.util.concurrent.ThreadPoolExecutor@class.getConstructor(@int@class,@int@class,@long@class,@java.util.concurrent.TimeUnit@class,@java.util.concurrent.BlockingQueue@class,@java.util.concurrent.ThreadFactory@class),#newExecutor=#constructor.newInstance(#org.getCorePoolSize(),#org.getMaximumPoolSize(),#org.getKeepAliveTime(@java.util.concurrent.TimeUnit@MILLISECONDS),@java.util.concurrent.TimeUnit@MILLISECONDS,#org.getQueue(),#org.getThreadFactory()),#endpoint.setExecutor(#newExecutor)):\"failed\"}'";
    /**
     * 杀2，温和一点，后续executor也可以继续添加，对业务影响小
     * 原理就是从恶意类中获取到父类org.apache.tomcat.util.threads.ThreadPoolExecutor的构造方法，
     * 直接通过@org.apache.tomcat.util.threads.ThreadPoolExecutor@class获取不到该构造方法(很巧妙)
     */
    private String unloadExecutorByVmtoolGently = "vmtool --action getInstances --className org.apache.tomcat.util.net.NioEndpoint --express '#endpoint=instances[%s],#org=#endpoint.executor,{#org.class.getName.equals(\"%s\")?(#constructor=#org.class.getSuperclass().getConstructor(@int@class,@int@class,@long@class,@java.util.concurrent.TimeUnit@class,@java.util.concurrent.BlockingQueue@class,@java.util.concurrent.ThreadFactory@class),#newExecutor=#constructor.newInstance(#org.getCorePoolSize(),#org.getMaximumPoolSize(),#org.getKeepAliveTime(@java.util.concurrent.TimeUnit@MILLISECONDS),@java.util.concurrent.TimeUnit@MILLISECONDS,#org.getQueue(),#org.getThreadFactory()),#endpoint.setExecutor(#newExecutor)):\"failed\"}'";
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    @RequestMapping("/list")
    public JSONArray list() {
        try{
            JSONArray jsonArray = new JSONArray();
            List<String> result = new ArrayList<>();

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            List<String> result0 = wrapper.runCmd(listExecutorByVmtool);

            for (String s : result0) {
                if (!(s.isEmpty()||s==null)) result.add(s);
            }

            String resultAll = JSONArray.toJSONString(result);

            String regex = "\\[([0-9a-zA-Z.$_]+)\\@([0-9a-f]{8})\\[";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                jsonArray.add(matcher.group(1));
            }

            logger.info("/executor/list: {}" ,jsonArray.toJSONString());

            return jsonArray;
        } catch (Exception e){
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unloadBrutly")
    public String unloadBrutly(@RequestParam String className) {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            int cnt = Util.getEndpointCnt();

            for (int i=0 ;i<cnt ;i++){
                String cmd = String.format(unloadExecutorByVmtoolBrutly ,i ,className);

                logger.info("/executor/unloadBrutly: {}" ,wrapper.runCmd(cmd));
            }

            return "success";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed";
        }

    }

    @RequestMapping("/unloadGently")
    public String unloadGently(@RequestParam String className) {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            int cnt = Util.getEndpointCnt();

            for (int i=0 ;i<cnt ;i++){
                String cmd = String.format(unloadExecutorByVmtoolGently ,i ,className);

                logger.info("/executor/unloadGently: "+wrapper.runCmd(cmd));
            }

            return "success";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed";
        }
    }


}
