package com.kmba.arthas;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/jad")
public class JAD {
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private String scClassName = "sc %s";
    private String jadClassName = "jad --source-only --lineNumber false %s";
    private String jadClassNameHash = "jad --source-only --lineNumber false -c %s %s";
    private String scClassLoaderHash = "sc -d %s";

    @RequestMapping("/check")
    public String check(@RequestParam String className) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            List<String> isOnly = wrapper.runCmd(String.format(scClassName, className));

            // 表示存在且唯一
            if (isOnly.size() == 2) { return "yes";}
            // 表示不存在
            else if (isOnly.size() == 1) { return "dont";}
            // 表示不唯一，需要ClassLoaderHashCode
            else { return "no";}
        } catch (Exception e){
            logger.error(e.getMessage());
            return "false";
        }
    }

    @RequestMapping("/jad")
    public String jad(@RequestParam String className){
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            // 这是为了解决初始化连接的一点错位小问题，如果动底层就有点麻烦，直接重新发一个无关痛痒的命令，重置一下连接的逻辑就OK
            wrapper.runCmd("help");

            List<String> classSourceCode = wrapper.runCmd(String.format(jadClassName, className));

            logger.info("/jad/jad: {}" ,className);
            String result = String.join("\n", classSourceCode);

            return result;
        } catch (Exception e){
            e.printStackTrace();
            logger.error(e.getMessage());
            return "false";
        }
    }

    @RequestMapping("/jadHash")
    public String jad(@RequestParam String className ,@RequestParam String classLoaderHashCode) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            wrapper.runCmd("help");

            List<String> classSourceCode = wrapper.runCmd(
                    String.format(jadClassNameHash ,classLoaderHashCode ,className));

            logger.info("/jad/jadHash: {}" , className + "/" + classLoaderHashCode);
            String result = String.join("\n", classSourceCode);

            return result;

        } catch (Exception e) {
            logger.error(e.getMessage());
            return "false";
        }
    }

    @RequestMapping("/hashcode")
    public JSONArray hashcode(@RequestParam String className) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            List<String> result0 = wrapper.runCmd(String.format(scClassLoaderHash ,className));

            String currentClassInfo = null;
            String currentClassLoaderHash = null;

            JSONObject classInfo = new JSONObject();

            JSONArray result = new JSONArray();
            for (String line : result0) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                line = line.trim();
                if (line.isEmpty()) { continue; }
                // 提取 class-info
                if (line.startsWith("class-info")) {
                    currentClassInfo = line.substring("class-info".length()).trim();
                    classInfo.put("class-info", currentClassInfo);
                }
                // 提取 classLoaderHash
                if (line.startsWith("classLoaderHash")) {
                    currentClassLoaderHash = line.substring("classLoaderHash".length())
                            .trim();
                    classInfo.put("classLoaderHash", currentClassLoaderHash);
                }
                // 只有当同时有 class-info 和 classLoaderHash 时才添加
                if (classInfo.containsKey("class-info") &&
                        classInfo.containsKey("classLoaderHash")) {
                    result.add(classInfo);
                    classInfo = new JSONObject();
                }
            }

            logger.info("/jad/hashcode: {}" , result);
            return result;
        } catch (InterruptedException e) {
            logger.error(e.getMessage());
            return new JSONArray();


        }
    }
}
