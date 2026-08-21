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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weblogic 容器 Servlet 内存马（与 weblogicFilter 配套，覆盖 filter/servlet/listener 三类）
 *
 * 目标结构（Weblogic 12.2.1.3 实测，WeblogicTool Godzilla4_Servlet 注入取证）：
 *   动态 Servlet 注册于 WebAppServletContext 私有方法 getServletMapping() 返回的
 *   weblogic.servlet.utils.ServletMapping（Map-like：keys() 为 URL 模式数组，
 *   value 为 URLMatchHelper，其私有字段 servletStub 为 ServletStubImpl）。
 *   ServletStubImpl.getClassName() 对动态 Servlet 返回缓存键名（如 cmdServlet），
 *   真实类名需经 WebAppServletContext.classLoader.cachedClasses 解析。
 *   注意 Servlet 3.0 API getServletRegistrations() 不包含动态注册的 ServletStubImpl，
 *   枚举与卸载只能走 ServletMapping。
 *
 * 卸载思路（实测验证）：
 *   1. options strict false（私有方法 getServletMapping / 私有字段访问需要）
 *   2. ServletMapping.removePattern(urlPath) 移除映射（实测 removePattern 后 get 为 null）
 *   3. 同一 express 内复查 get(urlPath)==null，目标仍在则报失败，杜绝假成功
 */
@RestController
@RequestMapping("/WeblogicServlet")
public class WeblogicServlet {

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    // list 输出: @ArrayList[@String[/kmba-servlet:InjectWeblogicGodzilla4Servlet], ...]
    private String listWeblogicServletByVmtool = "vmtool -l -1 --action getInstances --className weblogic.servlet.internal.WebAppServletContext --express '#ctx=instances[%s],#m=#ctx.getServletMapping(),#m.keys().{(#k=#this,#umh=#m.get(#k),#f=#umh.getClass().getDeclaredField(\"servletStub\"),#f.setAccessible(true),#stub=#f.get(#umh),#cn=#stub.getClassName(),#ccf=#ctx.classLoader.getClass().getDeclaredField(\"cachedClasses\"),#ccf.setAccessible(true),#cc=#ccf.get(#ctx.classLoader),#cls=#cc.get(#cn),#out=(#cls!=null)?#cls.getName():#cn),#k+\":\"+#out}'";

    // had=卸载前是否存在 left=卸载后是否仍存在（removePattern 后 get 为 null 即移除成功）
    private String unloadWeblogicServletByVmtool = "vmtool -l -1 --action getInstances --className weblogic.servlet.internal.WebAppServletContext --express '#ctx=instances[%s],#u=\"%s\",#m=#ctx.getServletMapping(),#had=(#m.get(#u)!=null),#m.removePattern(#u),\"had=\"+#had+\",left=\"+(#m.get(#u)!=null)'";

    @RequestMapping("/list")
    public JSONArray list() {
        JSONArray jsonArray = new JSONArray();
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int weblogicCnt = Util.getWeblogicCnt();
            // 私有方法 getServletMapping / 私有字段访问需要
            wrapper.runCmd("options strict false");
            // @String[/kmba-servlet:InjectWeblogicGodzilla4Servlet]
            String resultAll = Util.getListResult(weblogicCnt ,listWeblogicServletByVmtool);
            String regex = "@String\\[(.*?):([0-9a-zA-Z.$_/]+)\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);
            while (matcher.find()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put(matcher.group(1), matcher.group(2));
                if (!jsonArray.contains(jsonObject)) {
                    jsonArray.add(jsonObject);
                }
            }
            logger.info("/WeblogicServlet/list: {}" ,jsonArray);
            return jsonArray;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String urlPath) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int weblogicCnt = Util.getWeblogicCnt();

            // 私有方法 getServletMapping / 私有字段访问需要
            wrapper.runCmd(Dict.Strict_Option);


            int removedCnt = 0;
            int failCnt = 0;

            for (int i = 0; i < weblogicCnt; i++) {
                List<String> result = wrapper.runCmd(String.format(unloadWeblogicServletByVmtool, i, urlPath));
                String resultStr = String.join("" ,result);
                logger.info("/WeblogicServlet/unload: {}" ,resultStr);
                if (resultStr.contains("Failed to execute ognl")){
                    
                    failCnt++;
                    continue;
                }
                if (resultStr.contains("left=false")) {
                    removedCnt++;
                } else if (resultStr.contains("had=true")) {
                    // 目标存在但移除后复查仍存在
                    logger.error("/WeblogicServlet/unload: {} 卸载后复查仍存在" ,urlPath);
                    failCnt++;
                }
            }

            if (removedCnt == 0 && failCnt > 0) {
                return "OGNL execute failed";
            }
            if (removedCnt == 0) {
                return "urlPath not found";
            }
            return "success";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed";
        }
    }
}
