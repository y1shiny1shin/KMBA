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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Struts2 Action 内存马（Round3 能力边界发现，s2-vuln Struts 2.3.34 实测取证）
 *
 * 目标结构（MemShellParty 注入器 SOAPUtils 反编译 + arthas 实测）：
 *   Action 动态注册于 com.opensymphony.xwork2.config.impl.DefaultConfiguration
 *     .getRuntimeConfiguration().namespaceActionConfigs
 *     —— Map&lt;namespace, Map&lt;actionName, ActionConfig&gt;&gt;
 *   （实测 35 个 namespace；注入器按 namespace 逐个 put，
 *   键为无斜杠的 action 名，ActionConfig 携带 className/packageName）
 *   注: 每个 webapp 一个 DefaultConfiguration 实例（实测 2 个）
 *
 * 卸载思路：
 *   1. urlPath 归一化（去前导 / 与 .action 后缀）得到 action 名
 *   2. 从全部 namespace 的内层 map 中 remove 该键
 *   3. 表达式内自查 left=false（无任何 namespace 仍含该键），杜绝假成功
 */
@RestController
@RequestMapping("/Struts2Action")
public class Struts2Action {

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    // list 输出: @ArrayList[@String[/kmbatest:KmbaTcS2Action], ...]
    // （action 名无斜杠，展示时补 / 与 URL 语义一致）
    private String listStruts2ActionByVmtool = "vmtool -l -1 --action getInstances --className com.opensymphony.xwork2.config.impl.DefaultConfiguration --express '#cfg=instances[%s],#rc=#cfg.getRuntimeConfiguration(),#maps=#rc.namespaceActionConfigs,#all=new java.util.ArrayList(),#maps.values().{(#inner=#this),#inner.entrySet().{(#e=#this,#all.add(\"/\"+#e.getKey()+\":\"+#e.getValue().getClassName()),#e)}},#all'";

    private String unloadStruts2ActionByVmtool = "vmtool -l -1 --action getInstances --className com.opensymphony.xwork2.config.impl.DefaultConfiguration --express '#cfg=instances[%s],#u=\"%s\",#rc=#cfg.getRuntimeConfiguration(),#maps=#rc.namespaceActionConfigs,#removed=0,#maps.values().{(#inner=#this,#old=#inner.remove(#u),#old!=null&&(#removed=#removed+1))},\"removed=\"+#removed'";

    @RequestMapping("/list")
    public JSONArray list() {
        try {
            int cnt = Util.getStruts2ActionCnt();
            JSONArray jsonArray = new JSONArray();

            // 私有字段 namespaceActionConfigs 访问需要
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            wrapper.runCmd("options strict false");

            String resultAll = Util.getListResult(cnt ,listStruts2ActionByVmtool);
            String regex = "@String\\[/([0-9a-zA-Z.$_/-]+):([0-9a-zA-Z.$_/]+)\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);
            while (matcher.find()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("/" + matcher.group(1), matcher.group(2));
                if (!jsonArray.contains(jsonObject)) {
                    jsonArray.add(jsonObject);
                }
            }
            logger.info("/Struts2Action/list: {}" ,jsonArray);
            return jsonArray;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String urlPath) {
        try {
            int cnt = Util.getStruts2ActionCnt();
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            // 私有字段访问需要
            wrapper.runCmd("options strict false");

            // 归一化: /kmbatest 或 /kmbatest.action -> kmbatest（map 键无斜杠无后缀）
            String action = urlPath;
            if (action.startsWith("/")) {
                action = action.substring(1);
            }
            if (action.endsWith(".action")) {
                action = action.substring(0, action.length() - ".action".length());
            }

            int removedCnt = 0;
            int failCnt = 0;
            for (int i = 0; i < cnt; i++) {
                String cmd = String.format(unloadStruts2ActionByVmtool, i, action);
                List<String> result = wrapper.runCmd(cmd);
                String resultStr = String.join("" ,result);
                logger.info("/Struts2Action/unload: {}" ,resultStr);
                if (resultStr.contains("Failed to execute ognl")) {
                    logger.error(resultStr);
                    failCnt++;
                    continue;
                }
                // removed=N: N>0 表示实际移除（Map.remove 返回旧值，不存在返回 null），
                // 区分"已移除"与"本就不存在"（避免卸载不存在的 action 误报 success）
                java.util.regex.Matcher removedMatcher =
                        java.util.regex.Pattern.compile("removed=(\\d+)").matcher(resultStr);
                if (removedMatcher.find() && Integer.parseInt(removedMatcher.group(1)) > 0) {
                    removedCnt++;
                } else {
                    logger.error("/Struts2Action/unload: {} 卸载后复查仍存在" ,action);
                    failCnt++;
                }
            }
            if (removedCnt > 0) {
                return "success";
            }
            if (failCnt > 0) {
                return "OGNL execute failed";
            }
            return "urlPath not found";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed";
        }
    }
}
