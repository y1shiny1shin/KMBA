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

@RestController
@RequestMapping("/vmtool")
public class VmtoolUtils {
    /**
     * 问题：由于jad是直接反编译的class，故存在一个问题，直接反编译出的参数的值可能和内存中的参数的值不一致
     * 这个方法是直接读取类的内存，故而可以解决此问题。若是使用sc，watch等动态工具，那么就会和此工具目前的目标背道而驰，故而采用静态读取的办法
     */
    static Logger logger = LoggerFactory.getLogger(VmtoolUtils.class.getName());
    // CLH -> ClassLoaderHash
//    public static String vmtoolNoCLH = "vmtool --action getInstances --className %s";
    public static String vmtoolWithCLH = "vmtool --action getInstances --className %s -c %s --express 'instances[0]'";
    @RequestMapping("/get")
    public JSONObject get(@RequestParam String className , @RequestParam(required = false) String classLoaderHash) {
        logger.info("/vmtool/get: className={}, hash={}", className, classLoaderHash);
        return doGet(className, classLoaderHash);
    }

    public static JSONObject doGet(String className, String classLoaderHash) {
        JAD jad = new JAD();
        JSONObject result = new JSONObject();
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();

            if (classLoaderHash != null && !classLoaderHash.isEmpty()) {
                List<String> output =
                        wrapper.runCmd(String.format(vmtoolWithCLH, className, classLoaderHash));
                result.put("type", "single");
                result.put("classLoaderHash", classLoaderHash);
                result.put("data", String.join("\n", output));
                return result;
            }
            // 不存在该class
            String check = jad.check(className);
            if ("dont".equals(check)) {
                result.put("type", "404");
                result.put("message", "Class not found: " + className);
                return result;
            }

            JSONArray hashes = jad.hashcode(className);
            if (hashes.isEmpty()) {
                result.put("type", "404");
                result.put("message", "Class not found: " + className);
                return result;
            }
            if (hashes.size() == 1) {
                JSONObject info = hashes.getJSONObject(0);
                String hash = info.getString("classLoaderHash");
                className = info.getString("class-info");
                List<String> output = wrapper.runCmd(
                        String.format(vmtoolWithCLH, className, hash));
                result.put("type", "single");
                result.put("classLoaderHash", hash);
                result.put("classInfo", className);
                result.put("data", String.join("\n", output));
                return result;
            }

            JSONArray multi = new JSONArray();
            for (int i = 0; i < hashes.size(); i++) {
                JSONObject info = hashes.getJSONObject(i);
                String hash = info.getString("classLoaderHash");
                className = info.getString("class-info");
                List<String> output = wrapper.runCmd(
                        String.format(vmtoolWithCLH, className, hash));
                JSONObject item = new JSONObject();
                item.put("classLoaderHash", hash);
                item.put("classInfo", className);
                item.put("data", String.join("\n", output));
                multi.add(item);
            }
            result.put("type", "multi");
            result.put("count", multi.size());
            result.put("results", multi);
        } catch (Exception e){
            logger.error("vmtool error", e);
            result.put("type", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }
}
