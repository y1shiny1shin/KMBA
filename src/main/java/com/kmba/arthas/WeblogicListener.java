package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
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
 * Weblogic 容器 Listener 内存马（与 weblogicFilter/weblogicServlet 配套）
 *
 * 目标结构（Weblogic 12.2.1.3 实测）：
 *   Listener 存储于 WebAppServletContext.eventsManager
 *   （weblogic.servlet.internal.EventsManager）的多个 java.util.List 字段
 *   （requestListeners / sessListeners / ctxListeners 等）。
 *   公开 API addListener 在容器初始化后被拒绝（[HTTP:101387]ServletContext has
 *   already been initialized），内存马只能直接操作这些内部列表。
 *   实测约束：
 *   1. OGNL 属性访问 #em.requestListeners 会撞上布尔 getter（返回 false），
 *      必须用 getDeclaredField 反射取值
 *   2. 注入后需同步 hasRequestListeners 标志位，否则 request 管线不派发
 *
 * 卸载思路（实测验证）：
 *   1. options strict false
 *   2. 遍历 EventsManager 全部 List 字段，removeAll(类名 contains 目标的元素)
 *   3. requestListeners 为空时还原 hasRequestListeners=false（恢复初始状态）
 *   4. 重新枚举复查目标已消失，否则报失败，杜绝假成功
 */
@RestController
@RequestMapping("/WeblogicListener")
public class WeblogicListener {

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    // list 输出: @ArrayList[@String[WebLogicListenerExec], @String[weblogic...内部Listener], ...]
    private String listWeblogicListenerByVmtool = "vmtool -l -1 --action getInstances --className weblogic.servlet.internal.WebAppServletContext --express '#ctx=instances[%s],#em=#ctx.eventsManager,#all=new java.util.ArrayList(),#fs=#em.getClass().getDeclaredFields().{? #this.getType().getName().equals(\"java.util.List\")},#fs.{(#f=#this,#f.setAccessible(true)),#all.addAll(#f.get(#em))},#all.{(#this.getClass().getName())}'";

    private String unloadWeblogicListenerByVmtool = "vmtool -l -1 --action getInstances --className weblogic.servlet.internal.WebAppServletContext --express '#ctx=instances[%s],#em=#ctx.eventsManager,#fs=#em.getClass().getDeclaredFields().{? #this.getType().getName().equals(\"java.util.List\")},#fs.{(#f=#this,#f.setAccessible(true),#l=#f.get(#em)),#l.removeAll(#l.{? #this.getClass().getName().contains(\"%s\")})},#f2=#em.getClass().getDeclaredField(\"hasRequestListeners\"),#f2.setAccessible(true),#f3=#em.getClass().getDeclaredField(\"requestListeners\"),#f3.setAccessible(true),(#f3.get(#em).size()==0)?(#f2.setBoolean(#em,false)):null,\"done\"'";

    @RequestMapping("/list")
    public JSONArray list() {
        JSONArray jsonArray = new JSONArray();
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int weblogicCnt = Util.getWeblogicCnt();
            // 私有字段访问需要
            wrapper.runCmd("options strict false");
            // @String[WebLogicListenerExec]
            String resultAll = Util.getListResult(weblogicCnt ,listWeblogicListenerByVmtool);
            String regex = "@String\\[([0-9a-zA-Z.$_/]+)\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);
            while (matcher.find()) {
                String className = matcher.group(1);
                if (!jsonArray.contains(className)) {
                    jsonArray.add(className);
                }
            }
            logger.info("/WeblogicListener/list: {} " , jsonArray);
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
            int weblogicCnt = Util.getWeblogicCnt();

            // 私有字段访问需要
            wrapper.runCmd("options strict false");

            int removedCnt = 0;
            int failCnt = 0;

            for (int i = 0; i < weblogicCnt; i++) {
                // 卸载前确认目标存在
                List<String> before = wrapper.runCmd(String.format(listWeblogicListenerByVmtool, i));
                String beforeStr = String.join("" ,before);
                if (beforeStr.contains("Failed to execute ognl")){
                    logger.error(beforeStr);
                    failCnt++;
                    continue;
                }
                if (!containsName(before, className)) {
                    continue;
                }

                List<String> result = wrapper.runCmd(String.format(unloadWeblogicListenerByVmtool, i, className));
                String resultStr = String.join("" ,result);
                logger.info("/WeblogicListener/unload: {}" ,resultStr);
                if (resultStr.contains("Failed to execute ognl")){
                    
                    failCnt++;
                    continue;
                }

                // 卸载后复查
                List<String> after = wrapper.runCmd(String.format(listWeblogicListenerByVmtool, i));
                String afterStr = String.join("" ,after);
                if (afterStr.contains("Failed to execute ognl")){
                    logger.error(afterStr);
                    failCnt++;
                    continue;
                }
                if (containsName(after, className)) {
                    logger.error("/WeblogicListener/unload: {} 卸载后复查仍存在" ,className);
                    failCnt++;
                } else {
                    removedCnt++;
                }
            }

            if (removedCnt == 0 && failCnt > 0) {
                return "OGNL execute failed";
            }
            if (removedCnt == 0) {
                return "Listener not found";
            }
            return "success";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed";
        }
    }

    /** 枚举输出中是否包含目标类名（与卸载过滤条件一致，均按 contains 匹配） */
    private boolean containsName(List<String> output, String className) {
        if (output == null || output.isEmpty()) return false;
        return String.join("", output).contains("@String[" + className);
    }
}
