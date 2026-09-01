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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kmba.Utils.Dict.ENTER;
import static com.kmba.Utils.Dict.Strict_Option;

/**
 * https://party.mem.mk/ui
 * https://goodapple.top/archives/1355
 * https://jlkl.github.io/2022/05/26/Java-09/
 *
 * 高版本 Spring（注解式 Controller）适配：
 *   Spring 3.1 起注解式 Controller 注册于 RequestMappingHandlerMapping.getHandlerMethods()
 *   （key=RequestMappingInfo 含 URL 模式，value=HandlerMethod），此前 KMBA 只查了
 *   BeanNameUrlHandlerMapping.handlerMap（bean 名 URL 映射，现代应用通常为空），
 *   导致枚举与卸载均失效（kmbatest ENV-09 缺陷3）。
 *
 * list/unload 入口先探测 spring-boot 版本（一处 express）：
 *   vmtool getInstances org.springframework.boot.SpringApplication
 *     --express '@org.springframework.boot.SpringApplication@class.getPackage().getImplementationVersion()'
 *   → @String[2.6.13]（spring-boot 版本）
 * 再按版本只执行匹配的分支：
 *   boot >= 2.4（框架 5.3+，PathPatternParser 可用）→ _high：双条件访问器
 *     （Boot 2.6+ 默认 PathPatternParser，getPatternsCondition() 为 null，
 *       路径在 getPathPatternsCondition().getPatternValues()）
 *   boot 2.0~2.3 / 1.x（框架 3.1~5.2）→ _31：仅 PatternsRequestCondition
 *   探测失败 → 原有 BeanNameUrlHandlerMapping 逻辑
 *
 * 实测约束（vmtool express，springdemo-vuln 环境验证）：
 *   1. 匹配用模式串 toString 精确相等（兼容 Set<String> 与 Set<PathPattern>，
 *      不误伤 /exec2 之类生产路由）
 *   2. OGNL 投影（#keys.{void方法}）中 void 方法调用会被静默吞掉，
 *      必须 [0] 索引直调，多副本匹配由 Java 侧循环逐次卸载
 */
@RestController
@RequestMapping("/SpringMvcController")
public class SpringMvcController {
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    // ==================== 老 Spring：BeanNameUrlHandlerMapping ====================
    private String listSMCByVmtool = "vmtool -l -1 --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext -c %s --express '#map=instances[%s].getBean(@org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping@class).handlerMap,#class=#map.getClass().getDeclaredField(\"m\"),#class.setAccessible(true),#class.get(#map)'";
    //@LinkedHashMap[
    //    @String[/exec]:@evalController[com.DemoSpring.evalController@25655ffa],
    //]
    private String unloadSMCByVmtool = "vmtool -l -1 --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext -c %s --express '#map=instances[%s].getBean(@org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping@class).handlerMap,#class=#map.getClass().getDeclaredField(\"m\"),#class.setAccessible(true),#class.get(#map).remove(\"%s\")'";
    // 复查：getHandlerMap() 是 AbstractUrlHandlerMapping 公开 API，返回活 map
    private String checkSMCByVmtool = "vmtool -l -1 --action getInstances --className org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext -c %s --express '#map=instances[%s].getBean(@org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping@class).getHandlerMap(),\"left=\"+#map.containsKey(\"%s\")'";

    // ==================== 版本探测 ====================
    private static final String RMM_CLASS = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping";
    private String getBootVersionByVmtool = "vmtool -l -1 --action getInstances --className org.springframework.boot.SpringApplication -c %s --express '@org.springframework.boot.SpringApplication@class.getPackage().getImplementationVersion()'";

    // ==================== 高版本（框架 >= 5.3 / boot >= 2.4）：双条件访问器 ====================
    // list 输出: @ArrayList[@String[[/WhoopsunixShell]:com.example.memshell.SpringControllerMemShell#exec()], ...]
    private String listSMCByVmtool_high = "vmtool -l -1 --action getInstances --className " + RMM_CLASS + " -c %s --express '#rmm=instances[%s],#ms=#rmm.getHandlerMethods(),#ms.keys.{(#tmpkey=#this,#pc=#tmpkey.getPatternsCondition(),#ppc=#tmpkey.getPathPatternsCondition(),#pattern=(#pc!=null)?#pc.getPatterns().toString():((#ppc!=null)?#ppc.getPatternValues().toString():\"\")),#pattern+\":\"+#ms[#tmpkey]}'";
    // unload: 每次移除一个匹配路由（[0] 索引直调，投影中 void 方法调用会被吞掉），
    // 多副本由 Java 侧循环处理；模式串 toString 精确匹配，避免 /exec 误伤 /exec2
    private String unloadSMCByVmtool_high = "vmtool -l -1 --action getInstances --className " + RMM_CLASS + " -c %s --express '#rmm=instances[%s],#ms=#rmm.getHandlerMethods(),#k=#ms.keys.{? (#tmpkey=#this,#pc=#tmpkey.getPatternsCondition(),#ppc=#tmpkey.getPathPatternsCondition(),#ps=(#pc!=null)?#pc.getPatterns():((#ppc!=null)?#ppc.getPatternValues():null),#ps!=null?(#ps.{? #this.toString().equals(\"%s\")}.size>0):false)}[0],#rmm.unregisterMapping(#k),\"after=\"+#rmm.getHandlerMethods().size()'";
    // 复查: left=剩余匹配路由数
    private String checkSMCByVmtool_high = "vmtool -l -1 --action getInstances --className " + RMM_CLASS + " -c %s --express '#rmm=instances[%s],#ms=#rmm.getHandlerMethods(),#left=#ms.keys.{? (#tmpkey=#this,#pc=#tmpkey.getPatternsCondition(),#ppc=#tmpkey.getPathPatternsCondition(),#ps=(#pc!=null)?#pc.getPatterns():((#ppc!=null)?#ppc.getPatternValues():null),#ps!=null?(#ps.{? #this.toString().equals(\"%s\")}.size>0):false)},\"left=\"+#left.size'";

    // ==================== 注解式（框架 3.1~5.2 / boot 2.0~2.3、1.x）：仅 PatternsRequestCondition ====================
    private String listSMCByVmtool_31 = "vmtool -l -1 --action getInstances --className " + RMM_CLASS + " -c %s --express '#rmm=instances[%s],#ms=#rmm.getHandlerMethods(),#ms.keys.{(#tmpkey=#this,#pc=#tmpkey.getPatternsCondition(),#pattern=(#pc==null)?\"\":#pc.getPatterns().toString()),#pattern+\":\"+#ms[#tmpkey]}'";
    private String unloadSMCByVmtool_31 = "vmtool -l -1 --action getInstances --className " + RMM_CLASS + " -c %s --express '#rmm=instances[%s],#ms=#rmm.getHandlerMethods(),#k=#ms.keys.{? (#tmpkey=#this,#pc=#tmpkey.getPatternsCondition(),#pc!=null?(#pc.getPatterns().{? #this.toString().equals(\"%s\")}.size>0):false)}[0],#rmm.unregisterMapping(#k),\"after=\"+#rmm.getHandlerMethods().size()'";
    private String checkSMCByVmtool_31 = "vmtool -l -1 --action getInstances --className " + RMM_CLASS + " -c %s --express '#rmm=instances[%s],#ms=#rmm.getHandlerMethods(),#left=#ms.keys.{? (#tmpkey=#this,#pc=#tmpkey.getPatternsCondition(),#pc!=null?(#pc.getPatterns().{? #this.toString().equals(\"%s\")}.size>0):false)},\"left=\"+#left.size'";

    @RequestMapping("/list")
    public JSONArray list(){
        try {
            JSONArray jsonArray = new JSONArray();

            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            wrapper.runCmd(Strict_Option+ENTER);

            OGNLUtils.setStrictModeClose();

            // 先探测 Spring 版本，再按版本只执行匹配的分支
            int[] bootVer = detectBootVersion(wrapper);
            if (bootVer != null) {
                // ---------- 高版本 Spring：注解式 Controller（按版本选择表达式） ----------
                String lister = isBootAtLeast24(bootVer) ? listSMCByVmtool_high : listSMCByVmtool_31;
                int rmmCnt = Util.getRMMCnt();
                List<String> result = new ArrayList<>();
                for (int i = 0; i < rmmCnt; i++) {
                    String classLoader = Util.getClassLoader(RMM_CLASS, i, wrapper);
                    if (classLoader == null) continue;
                    String cmd = String.format(lister, classLoader, i);
                    List<String> result0 = wrapper.runCmd(cmd);
                    if (result0 != null) {
                        for (String s : result0)
                            if (!(s == null || s.isEmpty()))
                                result.add(s);
                    }
                }
                String resultAll = String.join("" ,result);
                // @String[[/WhoopsunixShell]:com.example.memshell.SpringControllerMemShell#exec()]
                String regex = "\\@String\\[\\[(.*?)\\]:([0-9a-zA-Z.$_]+)#(.*?)\\]";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(resultAll);

                while (matcher.find()) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(matcher.group(1), matcher.group(2));
                    if (!jsonArray.contains(jsonObject)) {
                        jsonArray.add(jsonObject);
                    }
                }
            } else {
                // ---------- 版本探测失败：原有 BeanNameUrlHandlerMapping 逻辑 ----------
                // [@LinkedHashMap[@String[/exec]:@evalController[com.DemoSpring.evalController@25655ffa]]]
                // 每个实例都需要动态获取 classLoader 后指定
                int springCnt = Util.getSpringCnt();
                List<String> result = new ArrayList<>();
                for (int i = 0; i < springCnt; i++) {
                    String classLoader = Util.getClassLoader("org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext", i, wrapper);
                    if (classLoader == null) continue;
                    String cmd = String.format(listSMCByVmtool, classLoader, i);
                    List<String> result0 = wrapper.runCmd(cmd);
                    if (result0 != null) {
                        for (String s : result0)
                            if (!(s == null || s.isEmpty()))
                                result.add(s);
                    }
                }
                String resultAll = String.join("" ,result);
                String regex = "@String\\[(.*?)\\]:@(.*?)\\[([0-9a-zA-Z.$_]+)@[0-9a-f]{1,8}";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(resultAll);

                while (matcher.find()) {
                    JSONObject jsonObject = new JSONObject();
                    String url = matcher.group(1);
                    String className = matcher.group(2);
                    jsonObject.put(url ,className);
                    if (!jsonArray.contains(jsonObject)){
                        jsonArray.add(jsonObject);
                    }
                }
            }

            logger.info("/SpringMvcController/list: {}" , jsonArray);
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

            int removedCnt = 0;
            int failCnt = 0;

            // 先探测 Spring 版本，再按版本只执行匹配的分支
            int[] bootVer = detectBootVersion(wrapper);
            if (bootVer != null) {
                // ---------- 高版本 Spring：注解式 Controller（按版本选择表达式） ----------
                boolean high = isBootAtLeast24(bootVer);
                String unloader = high ? unloadSMCByVmtool_high : unloadSMCByVmtool_31;
                String checker = high ? checkSMCByVmtool_high : checkSMCByVmtool_31;
                int rmmCnt = Util.getRMMCnt();
                for (int i = 0; i < rmmCnt; i++) {
                    String classLoader = Util.getClassLoader(RMM_CLASS, i, wrapper);
                    if (classLoader == null) {
                        failCnt++;
                        continue;
                    }
                    // 卸载前确认目标存在，避免把"本就不存在"误报为卸载成功
                    List<String> before = wrapper.runCmd(String.format(checker, classLoader, i, urlPath));
                    String beforeStr = String.join("" ,before);
                    if (beforeStr.contains("Failed to execute ognl")){
                        logger.error(beforeStr);
                        failCnt++;
                        continue;
                    }
                    if (beforeStr.contains("left=0")) {
                        continue;
                    }

                    // 每次卸载一个匹配路由（OGNL 投影无法执行 void 方法，需 [0] 直调），
                    // Java 侧循环直到 left=0、失败或超限
                    boolean removed = false;
                    for (int attempt = 0; attempt < 10; attempt++) {
                        List<String> result = wrapper.runCmd(String.format(unloader, classLoader, i, urlPath));
                        String resultStr = String.join("" ,result);
                        logger.info("/SpringMvcController/unload: {}" , resultStr);
                        if (resultStr.contains("Failed to execute ognl")) {
                            logger.error(resultStr);
                            break;
                        }
                        List<String> after = wrapper.runCmd(String.format(checker, classLoader, i, urlPath));
                        String afterStr = String.join("" ,after);
                        if (afterStr.contains("Failed to execute ognl")) {
                            logger.error(afterStr);
                            break;
                        }
                        if (afterStr.contains("left=0")) {
                            removedCnt++;
                            removed = true;
                            break;
                        }
                    }
                    if (!removed) {
                        // 失败或超限仍未移除，统一计为失败（杜绝假成功）
                        logger.error("/SpringMvcController/unload: {} 卸载后复查仍存在" ,urlPath);
                        failCnt++;
                    }
                }
            } else {
                // ---------- 版本探测失败：原有 BeanNameUrlHandlerMapping 逻辑 ----------
                int springCnt = Util.getSpringCnt();
                for (int i = 0 ; i<springCnt ;i++){
                    // 每个实例都需要动态获取 classLoader 后指定
                    String classLoader = Util.getClassLoader("org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext", i, wrapper);
                    if (classLoader == null) {
                        failCnt++;
                        continue;
                    }
                    // 卸载前确认目标存在，避免把"本就不存在"误报为卸载成功
                    List<String> before = wrapper.runCmd(String.format(checkSMCByVmtool, classLoader, i ,urlPath));
                    String beforeStr = String.join("" ,before);
                    if (beforeStr.contains("Failed to execute ognl")){
                        logger.error(beforeStr);
                        failCnt++;
                        continue;
                    }
                    if (beforeStr.contains("left=false")) {
                        continue;
                    }

                    String cmd = String.format(unloadSMCByVmtool, classLoader, i ,urlPath);

                    List<String> result = wrapper.runCmd(cmd);
                    String resultStr = String.join("" ,result);
                    logger.info("/SpringMvcController/unload: {}" , resultStr);
                    if (resultStr.contains("Failed to execute ognl")){
                        logger.error(resultStr);
                        failCnt++;
                        continue;
                    }

                    // 复查：目标是否已从 handlerMap 移除
                    List<String> after = wrapper.runCmd(String.format(checkSMCByVmtool, classLoader, i ,urlPath));
                    String afterStr = String.join("" ,after);
                    if (afterStr.contains("Failed to execute ognl")){
                        logger.error(afterStr);
                        failCnt++;
                        continue;
                    }
                    if (afterStr.contains("left=false")) {
                        removedCnt++;
                    } else {
                        logger.error("/SpringMvcController/unload: {} 卸载后复查仍存在" ,urlPath);
                        failCnt++;
                    }
                }
            }

            if (removedCnt == 0 && failCnt > 0) {
                return "OGNL execute failed";
            }
            if (removedCnt == 0) {
                return "urlPath not found";
            }
            return "success";
        } catch (Exception e){
            logger.info(e.getMessage());
            return "failed";
        }
    }

    /**
     * 探测目标 JVM 的 spring-boot 版本（如 2.7.13 -> {2,7}）。
     * 非 Boot 应用（SpringApplication 不存在）返回 null，调用方按 unknown 处理。
     */
    private int[] detectBootVersion(ArthasWsWrapper wrapper) {
        try {
            // express 的 @Class@ 解析需要 -classLoader 提示（否则报 Unable to resolve class），
            // 用 RMM 实例的 classLoader 作为探针（与注解映射同源，实测 springdemo-vuln）
            String classLoader = Util.getClassLoader(RMM_CLASS, 0, wrapper);
            if (classLoader == null) return null;
            List<String> result = wrapper.runCmd(String.format(getBootVersionByVmtool, classLoader));
            if (result == null || result.isEmpty()) return null;
            // @String[2.7.13]（版本号主次两级即可）
            String regex = "@String\\[([0-9]+)\\.([0-9]+)";
            Matcher matcher = Pattern.compile(regex).matcher(String.join("" ,result));
            if (!matcher.find()) return null;
            return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    /** boot >= 2.4 对应框架 5.3+（PathPatternsRequestCondition 可用） */
    private static boolean isBootAtLeast24(int[] bootVer) {
        return bootVer != null && (bootVer[0] > 2 || (bootVer[0] == 2 && bootVer[1] >= 4));
    }
}
