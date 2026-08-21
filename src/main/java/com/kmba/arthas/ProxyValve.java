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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kmba.Utils.Dict.*;

/**
 * https://github.com/ReaJason/MemShellParty
 *
 * Round3 发现并修复（2026-08-20, tc24813-vuln Tomcat 9.0.97 实证）：
 * 1. 枚举 OGNL 曾用 map 字面量 + #x[0] 取值，对 map 字面量恒为 null → -l 恒空；
 *    改为直接拼接 "first=..." "basic=..." 字符串
 * 2. 卸载曾比较 Proxy 实例类名（$ProxyN）与参数类名，永不匹配 → 假成功；
 *    且 -classLoader 对多 webapp（同名 ParallelWebappClassLoader）被 vmtool 拒绝。
 *    修复（对照实验实证: InvocationHandler.rawValve 还原后 HTTP 404）：
 *    - 去掉 -classLoader，直接用 instances[%s] 索引（pvfix.as 同款）
 *    - Proxy 判定用类型级不变量 Proxy.class.isAssignableFrom（不依赖 com.sun.proxy 命名）
 *    - 命中条件 = InvocationHandler 类名【精确等于】目标
 *    - 卸载 = 把 pipeline 的 first/basic 字段还原为 handler.rawValve 中保存的原 Valve
 *    - 表达式内自查 left=false（不再为 Proxy）才算成功，杜绝假成功
 */
@RestController
@RequestMapping("/proxyValve")
public class ProxyValve {

    // list 输出: @String[first=KmbaTcProxyValve,basic=org.apache.catalina.core.StandardContextValve]
    // （Proxy → InvocationHandler 类名，非 Proxy → 自身类名）
    private String getFirstAndBasicByVmtool = "vmtool -l -1 --action getInstances --className org.apache.catalina.core.StandardContext --express '#pip=instances[%s].getPipeline,#pc=@java.lang.reflect.Proxy@class,#basicField=#pip.class.getDeclaredField(\"basic\"),#basicField.setAccessible(true),#basic=#basicField.get(#pip),#firstField=#pip.class.getDeclaredField(\"first\"),#firstField.setAccessible(true),#first=#firstField.get(#pip),\"first=\"+(#first==null?\"null\":(#pc.isAssignableFrom(#first.class)?#first.h.class.name:#first.class.name))+\",basic=\"+(#basic==null?\"null\":(#pc.isAssignableFrom(#basic.class)?#basic.h.class.name:#basic.class.name))'";

    // 卸载: Proxy 且 handler 类名精确匹配 → 还原 handler 中保存的原 Valve + 自查 left=false
    // 注意: 不硬编码保存原 Valve 的字段名（如 rawValve）——那是攻击者可控的类成员，
    // 不同工具命名不同；改为遍历 handler 全部声明字段、取第一个 Valve 类型值还原
    // （KMBA 原版设计，Round3 修复保留此通用性）
    private String restoreValveByVmtool = "vmtool -l -1 --action getInstances --className org.apache.catalina.core.StandardContext --express '#pip=instances[%s].getPipeline,#pc=@java.lang.reflect.Proxy@class,#ff=#pip.class.getDeclaredField(\"%s\"),#ff.setAccessible(true),#v=#ff.get(#pip),#v==null?\"EMPTY\":(#pc.isAssignableFrom(#v.class)?(#h=#v.h,#h.class.name.equals(\"%s\")?(#found=#h.class.getDeclaredFields().{? (#f=#this,#f.setAccessible(true),#fval=#f.get(#h),#fval!=null?#ff.getType().isAssignableFrom(#fval.class):false)},#found.size>0?(#ff.set(#pip,#found[0].get(#h)),\"left=\"+#pc.isAssignableFrom(#ff.get(#pip).class)+\",restored=\"+#ff.get(#pip).class.name):\"novalve\"):\"notfound\"):\"notproxy\")'";

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    @RequestMapping("/list")
    public JSONArray list() {
        try {
            JSONArray jsonArray = new JSONArray();

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            wrapper.runCmd(Strict_Option+ENTER);

            OGNLUtils.setStrictModeClose();

            String firstRegex = "@String\\[first\\=([0-9a-zA-Z.$_/]+)";
            Pattern firstPattern = Pattern.compile(firstRegex);

            String basicRegex = "basic\\=([0-9a-zA-Z.$_/]+)";
            Pattern basicPattern = Pattern.compile(basicRegex);

            // 每个 StandardContext 都需要动态获取 classLoader 后指定
            int tomcatSiteCnt = Dict.getTomcatSiteCnt();
            List<String> result = new ArrayList<>();
            for (int i = 0; i < tomcatSiteCnt; i++) {
                String cmd = String.format(getFirstAndBasicByVmtool, i);
                List<String> result0 = wrapper.runCmd(cmd);
                if (result0 != null) {
                    for (String s : result0)
                        if (!(s == null || s.isEmpty()))
                            result.add(s);
                }
            }
            String resultAll = String.join("" ,result);
            logger.info("/proxyValve/list raw: {}" ,resultAll);

            Matcher firstMatcher = firstPattern.matcher(resultAll);
            Matcher basicMatcher = basicPattern.matcher(resultAll);

            while (firstMatcher.find()) {
                if (firstMatcher.group(1) != null && !"null".equals(firstMatcher.group(1))) {
                    JSONObject tmp = new JSONObject();
                    tmp.put("first", firstMatcher.group(1));
                    jsonArray.add(tmp);
                }

            }

            while (basicMatcher.find()) {
                if (basicMatcher.group(1) != null && !"null".equals(basicMatcher.group(1))) {
                    JSONObject tmp = new JSONObject();
                    tmp.put("basic", basicMatcher.group(1));
                    jsonArray.add(tmp);
                }

            }

            logger.info("/proxyValve/list: {}" ,jsonArray);

            return jsonArray;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    @RequestMapping("/unloadFirst")
    public String unloadFirst(@RequestParam String className) {
        return restoreValve("first", className);
    }

    @RequestMapping("/unloadBasic")
    public String unloadBasic(@RequestParam String className) {
        return restoreValve("basic", className);
    }

    private String restoreValve(String field, String className) {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            wrapper.runCmd(Strict_Option+ENTER);
            wrapper.runCmd(Unsafe_Option+ENTER);

            OGNLUtils.setStrictModeClose();

            int tomcatSiteCnt = Dict.getTomcatSiteCnt();
            int restoredCnt = 0;
            int failCnt = 0;
            int notFoundCnt = 0;
            for (int i=0;i<tomcatSiteCnt;i++){
                // 重复注入会导致 proxy 层层嵌套（新 proxy 的 Valve 字段保存旧 proxy），
                // 每次剥一层，循环直到 left=false 或无法继续
                for (int attempt = 0; attempt < 10; attempt++) {
                    String cmd = String.format(restoreValveByVmtool, i, field, className);
                    List<String> result = wrapper.runCmd(cmd);
                    String resultStr = String.join("" ,result);
                    logger.info("/proxyValve/unload{}: {}" ,field, resultStr);
                    if (resultStr.contains("Failed to execute ognl")){
                        logger.error(resultStr);
                        failCnt++;
                        break;
                    }
                    if (resultStr.contains("left=false")) {
                        restoredCnt++;
                        break;
                    }
                    if (resultStr.contains("notfound") || resultStr.contains("notproxy") || resultStr.contains("EMPTY")) {
                        notFoundCnt++;
                        break;
                    }
                    if (resultStr.contains("novalve")) {
                        // handler 中无 Valve 类型字段可还原（无法剥到原始阀）
                        logger.error("/proxyValve/unload{}: {} 还原失败: {}" ,field, className, resultStr);
                        failCnt++;
                        break;
                    }
                    // left=true：还原的仍是一个 Proxy，继续剥下一层
                }
            }
            if (restoredCnt > 0) {
                return "success";
            }
            if (failCnt > 0) {
                return "OGNL execute failed";
            }
            return "Valve not found";
        } catch (Exception e){
            logger.error(e.getMessage());
            return "failed";
        }
    }
}
