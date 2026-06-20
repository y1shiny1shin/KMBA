package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kmba.Utils.Dict.tomcatSiteCnt;

/**
 * 参考文章，感恩的心感谢有你
 * https://m0d9.me/2020/09/27/Java%E5%86%85%E5%AD%98shell%EF%BC%9Aservlet/
 * https://drun1baby.top/2022/09/04/Java%E5%86%85%E5%AD%98%E9%A9%AC%E7%B3%BB%E5%88%97-05-Tomcat-%E4%B9%8B-Servlet-%E5%9E%8B%E5%86%85%E5%AD%98%E9%A9%AC/
 * https://su18.org/post/memory-shell/
 */

/**
 * TODO
 * 优化一下Servlet-list的逻辑
 */

@RestController
@RequestMapping("/servlet")
public class Servlet {
//    String listServletByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express 'instances[%s].servletMappings";
    String listServletByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express '#context=instances[%s],#maps=#context.servletMappings,#maps.entrySet().{(#value=#this.value,#key=#this.key,#tmpValue=#context.findChild(#value).getServlet().class.getName),#key+\":\"+#tmpValue}'";
    String unloadServletByVmtool = "vmtool --action getInstances --className org.apache.catalina.core.StandardContext --express 'instances[%s].removeServletMapping(\"%s\")'";
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    @RequestMapping("/list")
    public JSONArray list(){
        JSONArray jsonArray = new JSONArray();
        try {

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            for (int i = 0; i < tomcatSiteCnt; i++) {
                String listServletByVmtool0 = String.format(listServletByVmtool, i);

                List<String> result = wrapper.runCmd(listServletByVmtool0);
                if (!(result.isEmpty() || result == null)){
                    for (String result0: result) {
                        // 正则
//                        String regex = "@String\\[(.*?)\\]:@String\\[(.*?)\\]";
//                        @String[/injectListener:com.InjectListener]
                        String regex = "@String\\[(.*?):(.*?)\\]";
                        Pattern pattern = Pattern.compile(regex);
                        Matcher matcher = pattern.matcher(result0);

                        while (matcher.find()) {
                            // 第一个捕获组：URL ---pattern
                            String url = matcher.group(1);
                            // 第二个捕获组：类名
                            String className = matcher.group(2);

                            // 放入结果集
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put(url, className);
                            if (!jsonArray.contains(jsonObject)){
                                jsonArray.add(jsonObject);
                            }
                        }
                    }
                }
            }
            logger.info("/servlet/list: {}", jsonArray);
            return jsonArray;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String urlPath){
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            for (int i = 0; i < tomcatSiteCnt; i++) {
                String cmd = String.format(unloadServletByVmtool, i ,urlPath);

                List<String> result = wrapper.runCmd(cmd);
                logger.info("/servlet/unload: {}" ,result);
                if (!result.isEmpty() && result.get(0).contains("Failed to execute ognl")){
                    logger.error(result.get(0));
                }
            }

        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed";
        }
        return "success";
    }
}
