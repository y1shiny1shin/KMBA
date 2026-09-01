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

import static com.kmba.Utils.Dict.Strict_Option;
import static com.kmba.Utils.Dict.Unsafe_Option;

/**
 * 感谢前辈
 * https://myzxcg.com/2021/11/Weblogic-%E5%86%85%E5%AD%98%E9%A9%AC%E5%88%86%E6%9E%90%E4%B8%8E%E5%AE%9E%E7%8E%B0/
 */

/**
 * Weblogic 容器 Filter 内存马（kmbatest ENV-03 缺陷2：KMBA 仅支持 Tomcat StandardContext）
 *
 * 目标结构（Weblogic 12.2.1.3 实测）：
 *   weblogic.servlet.internal.WebAppServletContext 每个应用一个实例
 *   （bea_wls_internal / bea_wls_console 等），Filter 注册于其内部的 FilterManager
 *   （私有字段 filters:Map + filterPatternList:List<FilterInfo>，FilterInfo.filterName 为名）。
 *   Servlet 3.0 API getFilterRegistrations() 每次返回临时 HashMap，仅可读：
 *   对其 remove 不影响注册表（kmbatest ENV-03 的"对照实验"实为假成功）。
 *
 * 卸载思路（实测验证，2026-08-19 weblogic-vuln 环境）：
 *   1. options strict false（反射 setAccessible 在严格模式下被 OGNL 拒绝；
 *      注意必须以纯文本命令发送，ArthasWsRequest 会二次包装成 read action，
 *      预序列化的 Dict.Strict_Option JSON 会被当作命令文本执行而失效）
 *   2. 反射移除 FilterManager.filters 中的注册项
 *   3. 重建 filterPatternList（去掉 filterName 命中的 FilterInfo），
 *      避免残留的 FilterInfo 影响后续请求链（不能破坏生产环境）
 *   4. 以 getFilterRegistrations().containsKey 复查，目标仍在则报失败，杜绝假成功
 *   （destroyFilter 实测仅触发 destroy 生命周期、不注销注册，不可用）
 */
@RestController
@RequestMapping("/WeblogicFilter")
public class WeblogicFilter {

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    // list 输出: @ArrayList[@String[test=>InjectWeblogicGodzilla4Filter], ...]
    private String listWeblogicFilterByVmtool = "vmtool -l -1 --action getInstances --className weblogic.servlet.internal.WebAppServletContext --express '#regs=instances[%s].getFilterRegistrations(),#regs.entrySet().{#this.getKey()+\"=>\"+#this.getValue().getClassName()}'";

    private String unloadWeblogicFilterByVmtool = "vmtool -l -1 --action getInstances --className weblogic.servlet.internal.WebAppServletContext --express '#ctx=instances[%s],#f=\"%s\",#fm=#ctx.filterManager,#f1=#fm.getClass().getDeclaredField(\"filters\"),#f1.setAccessible(true),#map=#f1.get(#fm),#had=#map.containsKey(#f),#map.remove(#f),#f2=#fm.getClass().getDeclaredField(\"filterPatternList\"),#f2.setAccessible(true),#list=#f2.get(#fm),#keep=#list.{? !(#fi=#this,#fn=#fi.getClass().getDeclaredField(\"filterName\"),#fn.setAccessible(true),#fn.get(#fi).equals(#f))},#list.clear(),#list.addAll(#keep),\"had=\"+#had'";

    private String checkWeblogicFilterByVmtool = "vmtool -l -1 --action getInstances --className weblogic.servlet.internal.WebAppServletContext --express '#ctx=instances[%s],\"left=\"+#ctx.getFilterRegistrations().containsKey(\"%s\")'";

    @RequestMapping("/list")
    public JSONArray list() {
        JSONArray jsonArray = new JSONArray();
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int weblogicCnt = Util.getWeblogicCnt();
            // @String[test=>InjectWeblogicGodzilla4Filter]
            String resultAll = Util.getListResult(weblogicCnt ,listWeblogicFilterByVmtool);
            String regex = "@String\\[(.*?)=>([0-9a-zA-Z.$_/]+)\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);
            while (matcher.find()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put(matcher.group(1), matcher.group(2));
                if (!jsonArray.contains(jsonObject)) {
                    jsonArray.add(jsonObject);
                }
            }
            logger.info("/WeblogicFilter/list: {}" ,jsonArray);
            return jsonArray;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String filterName) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int weblogicCnt = Util.getWeblogicCnt();

            // options strict false：反射 setAccessible 在严格模式下被 OGNL 拒绝
            wrapper.runCmd(Strict_Option);
            wrapper.runCmd(Unsafe_Option);

            OGNLUtils.setStrictModeClose();

            int removedCnt = 0;
            int failCnt = 0;

            for (int i = 0; i < weblogicCnt; i++) {
                // 卸载前确认目标存在，避免把"本就不存在"误报为卸载成功
                List<String> before = wrapper.runCmd(String.format(checkWeblogicFilterByVmtool, i, filterName));
                String beforeStr = String.join("" ,before);
                if (beforeStr.contains("Failed to execute ognl")){
                    logger.error(beforeStr);
                    failCnt++;
                    continue;
                }
                if (beforeStr.contains("left=false")) {
                    continue;
                }

                // 反射移除 filters 注册项 + 重建 filterPatternList（实测验证有效）
                List<String> result = wrapper.runCmd(String.format(unloadWeblogicFilterByVmtool, i, filterName));
                String resultStr = String.join("" ,result);
                logger.info("/WeblogicFilter/unload: {}" ,resultStr);
                if (resultStr.contains("Failed to execute ognl")){
                    logger.error(resultStr);
                    failCnt++;
                    continue;
                }

                // 卸载后复查
                List<String> after = wrapper.runCmd(String.format(checkWeblogicFilterByVmtool, i, filterName));
                String afterStr = String.join("" ,after);
                if (afterStr.contains("Failed to execute ognl")){
                    logger.error(afterStr);
                    failCnt++;
                    continue;
                }
                if (afterStr.contains("left=false")) {
                    removedCnt++;
                } else {
                    logger.error("/WeblogicFilter/unload: {} 卸载后复查仍存在" ,filterName);
                    failCnt++;
                }
            }

            if (removedCnt == 0 && failCnt > 0) {
                return "OGNL execute failed";
            }
            if (removedCnt == 0) {
                return "filterName not found";
            }
            return "success";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed";
        }
    }
}
