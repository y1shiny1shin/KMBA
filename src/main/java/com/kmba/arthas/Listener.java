package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kmba.Utils.Dict.getTomcatSiteCnt;


/**
 * 文章参考
 * https://www.freebuf.com/articles/web/452764.html
 * https://www.cnblogs.com/erosion2020/p/18575391
 * https://su18.org/post/memory-shell/#listener-%E5%86%85%E5%AD%98%E9%A9%AC
 * https://chenlvtang.top/2022/08/03/Tomcat%E4%B9%8BListener%E5%86%85%E5%AD%98%E9%A9%AC/
 */

@RestController
@RequestMapping("/listener")
public class Listener {
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    // OGNL语法 集合过滤逻辑 ? ! 引用： https://blog.csdn.net/qin9r3y/article/details/8451977
    private String unloadListenerByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express '#list=instances[%s].applicationEventListenersList,#x=1,#list.{(#this.getClass.getName.contains(\"%s\"))?(#x=#this):null},#list.remove(#x)'";
    private String listListenerBySc = "sc javax.servlet.ServletRequestListener";
    private String listListenerByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express 'instances[%s].applicationEventListenersList'";
    @RequestMapping("/list")
    public JSONArray list() {
        JSONArray jsonArray = new JSONArray();
        try{


            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int tomcatSiteCnt = getTomcatSiteCnt();
            for (int i = 0; i < tomcatSiteCnt; i++) {
                String listListenerByVmtool0 = String.format(listListenerByVmtool, i);

                List<String> result = null;

                result = wrapper.runCmd(listListenerByVmtool0);

                /**
                 * 正则匹配 CopyOnWriteArrayList 中的恶意Listener
                 * 用sc查找出来的是属于其中的类，不够直观，
                 * 只需要给Listener的触发点给弄掉+卸载掉已存在于CopyOnWriteArrayList中的Listener，
                 * 内存马就会丧失活性，即使恶意类还存在于内存中
                 */
                if (!(result.isEmpty() || result == null)) {
                    for (String result0: result){
                        if (result0 != "@CopyOnWriteArrayList[isEmpty=true;size=0]"){
                            String regex = "@\\w+\\[([0-9a-zA-Z.$_]+)@[0-9a-f]{8}\\]";
                            Pattern pattern = Pattern.compile(regex);
                            Matcher matcher = pattern.matcher(result0);

                            while (matcher.find()) {
                                String className = matcher.group(1);
                                jsonArray.add(className);
                            }
                        }
                    }

                }
//                ArthasStringUtil.print(result);
            }
            logger.info("/listener/list: {} " , jsonArray);
            return jsonArray;
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }

        /**
         * 通过sc逻辑找，有缺陷，被卸载后没办法直观地确认是否卸载成功
         */
//        JSONArray jsonArray = new JSONArray();
//
//        ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
//        List<String> result = null;
//        try {
//            result = wrapper.runCmd(listListenerBySc);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//
//        int cnt = ArthasStringUtil.getCntBySc(result);
//
//        for (int i=0; i<cnt; i++) {
//            jsonArray.add(result.get(i));
//        }
//
//        System.out.println(jsonArray);
//
//        return jsonArray;
    }
    @RequestMapping("/unload")
    public String unload(@RequestParam String className) {
        try {

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int tomcatSiteCnt = getTomcatSiteCnt();
            for (int i=0; i<tomcatSiteCnt; i++) {
                String cmd = String.format(unloadListenerByVmtool ,i ,className);
                List<String> result = wrapper.runCmd(cmd);
                logger.info("/listener/unload: " + result);

                if (!result.isEmpty() && result.get(0).contains("Failed to execute ognl")){
                    logger.error(result.get(0));
                }
            }

        } catch (InterruptedException e) {
            logger.error(e.getMessage());
            return "failed";
        }

        return "success";
    }
}
