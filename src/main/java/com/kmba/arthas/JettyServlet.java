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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jetty 容器 Servlet 内存马（与 jettyListener/jettyFilter 配套）
 *
 * 依据 Jetty 各版本字节码取证（javap org.eclipse.jetty.servlet.ServletHandler，
 * 7.0.0.M0 / 8.1.3 / 9.2.28 / 9.4.46 API 一致）：
 *   ServletHolder[] getServlets()          / synchronized void setServlets(ServletHolder[])
 *   ServletMapping[] getServletMappings()  / void setServletMappings(ServletMapping[])
 *   ServletHolder.getName()/getClassName()（Holder 基类），
 *   ServletMapping.getServletName()/getPathSpecs()
 *   字节码实证（7.0.0.M0）: setServlets 内部调 updateNameMappings()，
 *   setServletMappings 内部调 updateMappings() —— 先 setServlets 再 setServletMappings
 *   即完整刷新缓存，是 addServletWithMapping 等注入路径的逆操作，不依赖私有字段。
 *
 * 卸载思路（实测验证）：
 *   1. 找出 pathSpecs【精确等于】目标的 ServletMapping，收集其 servletName
 *   2. 过滤掉这些 mapping 与对应无映射残留的 ServletHolder
 *   3. setServlets(newServlets) + setServletMappings(newMappings) 写回（公开 API）
 *   4. 重新枚举复查目标已消失，否则报失败，杜绝假成功
 */
@RestController
@RequestMapping("/JettyServlet")
public class JettyServlet {

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    // 枚举 holder: @String[name:com.X]
    private String listJettyServletHolderByVmtool = "vmtool -l -1 --action getInstances --className org.eclipse.jetty.servlet.ServletHandler --express '#sh=instances[%s],#sh.getServlets().{(#this.getName()+\":\"+#this.getClassName())}'";
    // 枚举 mapping: @String[[/*]:name]
    // （数组需 Arrays.toString 渲染，直接 toString 是身份串；OGNL 投影体
    //   不能以 @静态调用 开头，需赋值先行、静态调用放尾部——实测约束）
    private String listJettyServletMappingByVmtool = "vmtool -l -1 --action getInstances --className org.eclipse.jetty.servlet.ServletHandler --express '#sh=instances[%s],#sh.getServletMappings().{(#ps=#this.getPathSpecs(),#sn=#this.getServletName()),@java.util.Arrays@toString(#ps)+\":\"+#sn}'";

    // 注意（OGNL 实测约束）: 选择谓词必须以 #this 锚定开头（赋值先行），
    // 以其它变量开头（如 !#removed.contains(...)）会导致 #this 绑定到根对象
    private String unloadJettyServletByVmtool = "vmtool -l -1 --action getInstances --className org.eclipse.jetty.servlet.ServletHandler --express '#sh=instances[%s],#u=\"%s\",#sm=#sh.getServletMappings(),#removed=#sm.{? #this.getPathSpecs().{? #this.toString().equals(#u)}.size>0}.{#this.getServletName()},#keepM=#sm.{? !(#this.getPathSpecs().{? #this.toString().equals(#u)}.size>0)},#keepS=#sh.getServlets().{? !(#sn=#this.getName(),#removed.contains(#sn))},#ta1=@java.lang.reflect.Array@newInstance(#sm.getClass().getComponentType(),0),#ta2=@java.lang.reflect.Array@newInstance(#sh.getServlets().getClass().getComponentType(),0),#sh.setServlets(#keepS.toArray(#ta2)),#sh.setServletMappings(#keepM.toArray(#ta1)),\"after=\"+#sh.getServletMappings().length'";

    @RequestMapping("/list")
    public JSONArray list() {
        JSONArray jsonArray = new JSONArray();
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int jettyCnt = Util.getJettyCnt();

            // holder: name -> className
            Map<String, String> holders = new HashMap<String, String>();
            String holderAll = Util.getListResult(jettyCnt ,listJettyServletHolderByVmtool);
            // 注意: Jetty 经 addServletWithMapping(Class,..) 创建的 holder 名带 "-<hash>" 后缀
            String holderRegex = "@String\\[(.*?):([0-9a-zA-Z.$_/-]+)\\]";
            Matcher hm = Pattern.compile(holderRegex).matcher(holderAll);
            while (hm.find()) {
                holders.put(hm.group(1), hm.group(2));
            }

            // mapping: pathSpecs -> servletName，与 holder 合并输出 {urlPath: className}
            String mappingAll = Util.getListResult(jettyCnt ,listJettyServletMappingByVmtool);
            String mappingRegex = "@String\\[\\[(.*?)\\]:([0-9a-zA-Z.$_/-]+)\\]";
            Matcher mm = Pattern.compile(mappingRegex).matcher(mappingAll);
            while (mm.find()) {
                String pathSpecs = mm.group(1);
                String servletName = mm.group(2);
                String className = holders.containsKey(servletName) ? holders.get(servletName) : "";
                // pathSpecs 形如 [/*] 或 [/a, /b]，逐个作为 urlPath 输出
                Matcher pm = Pattern.compile("/([0-9a-zA-Z._*/-]+)").matcher(pathSpecs);
                while (pm.find()) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(pm.group(0), className);
                    if (!jsonArray.contains(jsonObject)) {
                        jsonArray.add(jsonObject);
                    }
                }
            }
            logger.info("/JettyServlet/list: {}" ,jsonArray);
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
            int jettyCnt = Util.getJettyCnt();

            int removedCnt = 0;
            int failCnt = 0;

            for (int i = 0; i < jettyCnt; i++) {
                // 卸载前确认目标存在
                List<String> before = wrapper.runCmd(String.format(listJettyServletMappingByVmtool, i));
                String beforeStr = String.join("" ,before);
                if (beforeStr.contains("Failed to execute ognl")){
                    logger.error(beforeStr);
                    failCnt++;
                    continue;
                }
                if (!beforeStr.contains(urlPath)) {
                    continue;
                }

                List<String> result = wrapper.runCmd(String.format(unloadJettyServletByVmtool, i, urlPath));
                String resultStr = String.join("" ,result);
                logger.info("/JettyServlet/unload: {}" ,resultStr);
                if (resultStr.contains("Failed to execute ognl")){
                    logger.error(resultStr);
                    failCnt++;
                    continue;
                }

                // 卸载后复查
                List<String> after = wrapper.runCmd(String.format(listJettyServletMappingByVmtool, i));
                String afterStr = String.join("" ,after);
                if (afterStr.contains("Failed to execute ognl")){
                    logger.error(afterStr);
                    failCnt++;
                    continue;
                }
                if (afterStr.contains(urlPath)) {
                    logger.error("/JettyServlet/unload: {} 卸载后复查仍存在" ,urlPath);
                    failCnt++;
                } else {
                    removedCnt++;
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
