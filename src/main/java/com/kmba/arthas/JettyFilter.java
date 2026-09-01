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
 * Jetty 容器 Filter 内存马（与 jettyListener/jettyServlet 配套）
 *
 * 依据 Jetty 各版本字节码取证（javap org.eclipse.jetty.servlet.ServletHandler，
 * 7.0.0.M0 / 8.1.3 / 9.2.28 / 9.4.46 API 一致）：
 *   FilterHolder[] getFilters()          / synchronized void setFilters(FilterHolder[])
 *   FilterMapping[] getFilterMappings()  / void setFilterMappings(FilterMapping[])
 *   FilterHolder.getName()/getClassName()（Holder 基类），
 *   FilterMapping.getFilterName()/getPathSpecs()
 *   字节码实证（7.0.0.M0）: setFilters 内部调 updateNameMappings()，
 *   setFilterMappings 内部调 updateMappings() —— 先 setFilters 再 setFilterMappings
 *   即可完整刷新 _filterNameMap/_filterPathMappings/_filterNameMappings 缓存，
 *   这正是 jMG/Whoopsunix 等工具 addFilterWithMapping/prependFilterMapping
 *   注入路径的逆操作，不依赖任何私有字段。
 *
 * 卸载思路（实测验证）：
 *   1. 过滤出 filterName【精确等于】目标的 FilterHolder[] 与 FilterMapping[]
 *   2. setFilters(newFilters) + setFilterMappings(newMappings) 写回（公开 API，
 *      缓存由 setter 内部刷新）
 *   3. 重新枚举复查目标已消失，否则报失败，杜绝假成功
 */
@RestController
@RequestMapping("/JettyFilter")
public class JettyFilter {

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    // 枚举 holder: @String[name:com.X]
    private String listJettyFilterHolderByVmtool = "vmtool -l -1 --action getInstances --className org.eclipse.jetty.servlet.ServletHandler --express '#sh=instances[%s],#sh.getFilters().{(#this.getName()+\":\"+#this.getClassName())}'";
    // 枚举 mapping: @String[[/*]:name]
    // （数组需 Arrays.toString 渲染，直接 toString 是身份串；OGNL 投影体
    //   不能以 @静态调用 开头，需赋值先行、静态调用放尾部——实测约束）
    private String listJettyFilterMappingByVmtool = "vmtool -l -1 --action getInstances --className org.eclipse.jetty.servlet.ServletHandler --express '#sh=instances[%s],#sh.getFilterMappings().{(#ps=#this.getPathSpecs(),#fn=#this.getFilterName()),@java.util.Arrays@toString(#ps)+\":\"+#fn}'";

    private String unloadJettyFilterByVmtool = "vmtool -l -1 --action getInstances --className org.eclipse.jetty.servlet.ServletHandler --express '#sh=instances[%s],#f=\"%s\",#fh=#sh.getFilters(),#keepF=#fh.{? !#this.getName().equals(#f)},#ta1=@java.lang.reflect.Array@newInstance(#fh.getClass().getComponentType(),0),#sh.setFilters(#keepF.toArray(#ta1)),#fm=#sh.getFilterMappings(),#keepM=#fm.{? !#this.getFilterName().equals(#f)},#ta2=@java.lang.reflect.Array@newInstance(#fm.getClass().getComponentType(),0),#sh.setFilterMappings(#keepM.toArray(#ta2)),\"after=\"+#sh.getFilters().length'";

    @RequestMapping("/list")
    public JSONArray list() {
        JSONArray jsonArray = new JSONArray();
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int jettyCnt = Util.getJettyCnt();

            // holder: name -> className
            Map<String, String> holders = new HashMap<String, String>();
            String holderAll = Util.getListResult(jettyCnt ,listJettyFilterHolderByVmtool);
            // 注意: Jetty 经 addFilterWithMapping(Class,..) 创建的 holder 名带 "-<hash>" 后缀
            String holderRegex = "@String\\[(.*?):([0-9a-zA-Z.$_/-]+)\\]";
            Matcher hm = Pattern.compile(holderRegex).matcher(holderAll);
            while (hm.find()) {
                holders.put(hm.group(1), hm.group(2));
            }

            // mapping: pathSpecs -> filterName，与 holder 合并输出 {filterName: className}
            String mappingAll = Util.getListResult(jettyCnt ,listJettyFilterMappingByVmtool);
            String mappingRegex = "@String\\[\\[(.*?)\\]:([0-9a-zA-Z.$_/-]+)\\]";
            Matcher mm = Pattern.compile(mappingRegex).matcher(mappingAll);
            while (mm.find()) {
                String filterName = mm.group(2);
                String className = holders.containsKey(filterName) ? holders.get(filterName) : "";
                JSONObject jsonObject = new JSONObject();
                jsonObject.put(filterName, className);
                if (!jsonArray.contains(jsonObject)) {
                    jsonArray.add(jsonObject);
                }
            }
            logger.info("/JettyFilter/list: {}" ,jsonArray);
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
            int jettyCnt = Util.getJettyCnt();

            int removedCnt = 0;
            int failCnt = 0;

            for (int i = 0; i < jettyCnt; i++) {
                // 卸载前确认目标存在
                List<String> before = wrapper.runCmd(String.format(listJettyFilterHolderByVmtool, i));
                String beforeStr = String.join("" ,before);
                if (beforeStr.contains("Failed to execute ognl")){
                    logger.error(beforeStr);
                    failCnt++;
                    continue;
                }
                if (!beforeStr.contains(filterName + ":")) {
                    continue;
                }

                List<String> result = wrapper.runCmd(String.format(unloadJettyFilterByVmtool, i, filterName));
                String resultStr = String.join("" ,result);
                logger.info("/JettyFilter/unload: {}" ,resultStr);
                if (resultStr.contains("Failed to execute ognl")){
                    logger.error(resultStr);
                    failCnt++;
                    continue;
                }

                // 卸载后复查
                List<String> after = wrapper.runCmd(String.format(listJettyFilterHolderByVmtool, i));
                String afterStr = String.join("" ,after);
                if (afterStr.contains("Failed to execute ognl")){
                    logger.error(afterStr);
                    failCnt++;
                    continue;
                }
                if (afterStr.contains(filterName + ":")) {
                    logger.error("/JettyFilter/unload: {} 卸载后复查仍存在" ,filterName);
                    failCnt++;
                } else {
                    removedCnt++;
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
