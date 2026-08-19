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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HandlerFunction: https://party.mem.mk/ui -> SpringWebFlux -> HandlerFunction
 *
 * 目标结构（Spring 5.3.x, spring-webflux）：
 *   RouterFunctionMapping.routerFunction 是一棵 RouterFunction 树：
 *   - SameComposedRouterFunction   (.and() 产生)     字段 first/second —— 组合节点
 *   - DifferentComposedRouterFunction (.andOther() 产生) 字段 first/second —— 组合节点
 *   - DefaultRouterFunction        (route() 产生)    字段 predicate/handlerFunction —— 叶子，无 first/second
 *   - DefaultNestedRouterFunction / FilteredRouterFunction  子字段 routerFunction —— 单子包装
 *   - AttributesRouterFunction     子字段 delegate —— 单子包装
 *
 * 加载器（memshell-kit）每次注入执行 ours.and(existing)，即新路由挂在 first、
 * 旧树沉到 second，所以多次注入的副本沿 .second 链分布，树形无固定深度。
 *
 * 卸载思路（响应驱动的递归遍历）：
 *   客户端维护路径表达式栈做 DFS，每一步探测都解析 vmtool 响应：
 *     1. 节点探测：一次 express 同时取 class 名 + 叶子 URL 匹配（LEAF_MATCH / LEAF_NOMATCH / NULL / 类名）
 *     2. 组合节点压入 first/second 子节点；单子包装压入其唯一子节点
 *     3. 任何 OGNL 错误（ognl.NoSuchPropertyException 等）都意味着该路径已到头/为空，
 *        不再下潜 —— 这是关键信号：叶子 DefaultRouterFunction 没有 first/second 属性，
 *        对其访问子字段必然抛 NoSuchPropertyException
 *     4. 命中叶子后（两级切除）：叶子 L 挂在组合父节点 P 的 first/second 上，
 *        不能只改 P.first（会把 P 变成 (X,X) 重复子树），而是把 P 整体在其父节点
 *        （根为 RouterFunctionMapping.routerFunction）上替换为 P 的另一个子节点；
 *        若 P 是单子包装或整棵树就是该叶子，则替换为 null（字段本身 null-safe，
 *        见 RouterFunctionMapping.getHandlerInternal 的 ifnull 保护）。
 *        反射写入时先清除字段 FINAL 位（first/second 是 final 字段），
 *        且只能用目标 classpath 的 Spring ReflectionUtils（vmtool express 无法解析 arthas 类）。
 *   外层 while(containsUrl) 循环，逐个移除同名多副本。
 */
@RestController
@RequestMapping("/SpringFluxHandlerFunction")
public class SpringFluxHandlerFunction {

    Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    // ==================== 导航前缀 ====================
    private static final String NAV =
            "#mappings=instances[%s]"
                    + ".this$0.handler.httpHandler.delegate.delegate.delegate.delegate"
                    + ".getHandlerMappings(),"
                    + "#rfm=#mappings.{? #this.class.name.contains(\"RouterFunctionMapping\")}[0],";

    // vmtool 命令前缀（-l -1 = 不限制结果条数）
    private static final String VMT_PREFIX =
            "vmtool -l -1 --action getInstances "
                    + "--className org.springframework.boot.web.embedded.netty.NettyWebServer$1 "
                    + "--express '";

    // ==================== Spring 5.3.x 路由节点类型 ====================
    private static final String PKG = "org.springframework.web.reactive.function.server.RouterFunctions$";
    private static final String LEAF_CLASS = PKG + "DefaultRouterFunction";
    private static final String SAME_COMPOSED = PKG + "SameComposedRouterFunction";
    private static final String DIFF_COMPOSED = PKG + "DifferentComposedRouterFunction";
    private static final String NESTED = PKG + "DefaultNestedRouterFunction";
    private static final String FILTERED = PKG + "FilteredRouterFunction";
    private static final String ATTRIBUTES = PKG + "AttributesRouterFunction";

    /** 探测响应解析的统一错误标记 */
    static final String ERR = "__ERROR__";

    // ==================== list：拉取所有路由 ====================
    private String listSFHFByVmtool = "vmtool -l -1 --action getInstances "
            + "--className org.springframework.boot.web.embedded.netty.NettyWebServer$1 "
            + "--express '" + NAV
            // routerFunction 可能为 null（卸载干净后的初始状态），toString 前需判空
            + "#rf=#rfm.routerFunction,#rf==null?\"\":#rf.toString()'";

    /**
     * toString() 输出示例（ToStringVisitor 以换行拼接各叶子）：
     *   /shell-flux-function -> MemshellFluxHandlerFunction$$Lambda$1209/1204667376@626495ac
     *   /shell-flux-function -> MemshellFluxHandlerFunction$$Lambda$1062/241709365@5a09c87b
     */
    @RequestMapping("/list")
    public JSONArray list() {
        try {
            int nettyServerCnt = Util.getNettyServerCnt();
            JSONArray jsonArray = new JSONArray();

            String resultAll = Util.getListResult(nettyServerCnt, listSFHFByVmtool);
            // 类名支持 JDK8 lambda 命名：Outer$$Lambda$1209/1204667376（含 /hash 后缀）
            String regex = "(/[^ ]+) -> ([0-9a-zA-Z.$_/]+)@[0-9a-f]+";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(resultAll);

            while (matcher.find()) {
                String path = matcher.group(1);
                String className = matcher.group(2);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put(path, className);
                if (!jsonArray.contains(jsonObject)) {
                    jsonArray.add(jsonObject);
                }
            }

            logger.info("/SpringFluxHandlerFunction/list: {}", jsonArray);
            return jsonArray;

        } catch (Exception e) {
            logger.error(e.getMessage());
            return new JSONArray();
        }
    }

    // ==================== unload：按 URL 移除路由 ====================

    /**
     * DFS 栈节点（携带两级引用信息）。
     * path                    = 节点自身的 OGNL 路径表达式
     * refTarget / refField    = 本节点被引用的位置（父对象表达式 + 字段名），
     *                           根节点为 RouterFunctionMapping（#rfm.routerFunction）
     * cutTarget / cutField / replacement = 若本节点是命中叶子，切除动作是：
     *                           把本节点的父节点整体在其父处替换掉 —— 写 cutTarget.cutField = replacement
     *                           （组合父节点 → 兄弟子树；包装父 / 根 → null）
     */
    static class Node {
        final String path;
        final String refTarget;
        final String refField;
        final String cutTarget;
        final String cutField;
        final String replacement;

        Node(String path, String refTarget, String refField,
             String cutTarget, String cutField, String replacement) {
            this.path = path;
            this.refTarget = refTarget;
            this.refField = refField;
            this.cutTarget = cutTarget;
            this.cutField = cutField;
            this.replacement = replacement;
        }
    }

    /**
     * "空路由"直接写 null。
     * Spring 5.3.13 RouterFunctionMapping.getHandlerInternal 对 null routerFunction
     * 有 ifnull 保护（返回 Mono.empty()），且注入前该字段本来就是 null——
     * 写 null 是还原初始状态，还避免了在 vmtool express 里反射调用
     * RouterFunctions.empty()（varargs 方法在 OGNL 中无法安全调用，实测
     * getMethod/invoke 均抛 ArrayIndexOutOfBoundsException / ArrayStoreException）。
     */
    private static final String NULL_FUNC = "null";

    /** OGNL 字符串字面量转义（url 来自外部请求参数） */
    static String escapeOgnl(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 构建节点探测 express：一次调用同时返回节点类型与叶子匹配结果。
     * 返回值为以下之一：
     *   LEAF_MATCH    —— 叶子且 predicate 精确等于目标 URL
     *   LEAF_NOMATCH  —— 叶子但不是目标
     *   NULL          —— 该路径为空（节点为 null）
     *   类全名        —— 非叶子节点（组合 / 包装 / 未知类型）
     */
    static String buildProbe(int serverIdx, String nodePath, String url) {
        String urlPrefix = escapeOgnl(url) + " ->";
        return VMT_PREFIX
                + String.format(NAV, serverIdx)
                + "#rf=#rfm.routerFunction,"
                + "#n=" + nodePath + ","
                + "#c=(#n == null) ? \"NULL\" : #n.class.name,"
                + "#c.equals(\"" + LEAF_CLASS + "\")"
                + " ? (#n.toString().startsWith(\"" + urlPrefix + "\") ? \"LEAF_MATCH\" : \"LEAF_NOMATCH\")"
                + " : #c'";
    }

    /**
     * 解析探测响应。
     * 关键信号：ognl.NoSuchPropertyException（如 DefaultRouterFunction 没有 first/second 属性）
     * 或任何 "Failed to execute ognl" 错误 => 该路径已到头/为空，返回 ERR。
     */
    static String parseProbe(String output) {
        if (output == null || output.isEmpty()) return ERR;
        if (output.contains("Failed to execute")
                || output.contains("NoSuchPropertyException")
                || output.contains("MethodNotFoundException")
                || output.contains("NullPointerException")) {
            return ERR;
        }
        Matcher m = Pattern.compile("@String\\[([^]]*)]").matcher(output);
        if (m.find()) return m.group(1);
        return ERR;
    }

    private String runProbe(int serverIdx, String nodePath, String url, ArthasWsWrapper wrapper)
            throws Exception {
        String cmd = buildProbe(serverIdx, nodePath, url);
        List<String> result = wrapper.runCmd(cmd);
        return result != null ? String.join("\n", result) : "";
    }

    /**
     * 探测未知类型节点是否拥有某个子字段（first/second）。
     * NoSuchPropertyException => 该类型没有此属性 => 该方向无子节点（分支为空）。
     */
    private boolean hasChild(int serverIdx, String nodePath, String field, ArthasWsWrapper wrapper)
            throws Exception {
        String cmd = VMT_PREFIX
                + String.format(NAV, serverIdx)
                + "#rf=#rfm.routerFunction,"
                + "#n=" + nodePath + ","
                + "#n." + field + " != null'";
        List<String> result = wrapper.runCmd(cmd);
        String out = result != null ? String.join("\n", result) : "";
        if (out.contains("NoSuchPropertyException") || out.contains("Failed to execute")) {
            return false;
        }
        return out.contains("@Boolean[true]");
    }

    /**
     * 构建切除 express：
     *   清 FINAL 位后把 cutTarget 的 cutField 字段写为 replacement（兄弟子树 / null）。
     * 实测约束（vmtool 的 express 类解析器只能解析 JDK 类，arthas 类与目标应用类
     * 均报 Unable to resolve class），所以全部走 JDK 反射 + 对象方法调用：
     *   1. Class.getDeclaredField 拿到目标字段与 Field.modifiers 字段
     *   2. 清除目标字段的 FINAL 位（first/second 是 final；Field.set 的 accessor
     *      惰性创建并缓存 isFinal，必须在其创建前清位，这正是 memshell 加载器的做法）
     *   3. Field.set 写回
     *   （getDeclaredField 只查声明类：routerFunction/first/second/delegate 均为
     *     各节点类自身声明，见 javap 结果）
     */
    static String buildCut(int serverIdx, Node n) {
        return VMT_PREFIX
                + String.format(NAV, serverIdx)
                + "#rf=#rfm.routerFunction,"
                + "#f=" + n.cutTarget + ".class.getDeclaredField(\"" + n.cutField + "\"),"
                + "#m=#f.getClass().getDeclaredField(\"modifiers\"),"
                + "#m.setAccessible(true),"
                + "#m.setInt(#f,#f.getModifiers() & ~@java.lang.reflect.Modifier@FINAL),"
                + "#f.setAccessible(true),"
                + "#f.set(" + n.cutTarget + "," + n.replacement + "),"
                + "\"cut done\"'";
    }

    private boolean cutNode(int serverIdx, Node n, ArthasWsWrapper wrapper) throws Exception {
        logger.info("cut: target={} field={} replacement={}", n.cutTarget, n.cutField, n.replacement);
        List<String> result = wrapper.runCmd(buildCut(serverIdx, n));
        String out = result != null ? String.join("\n", result) : "";
        if (out.contains("Failed to execute") || out.contains("Exception")) {
            logger.error("cut failed: {}", out);
            return false;
        }
        return true;
    }

    /**
     * 一次 DFS：遍历实际树形，找到并切除一个匹配叶子。
     * 切除 = 把叶子所在的组合父节点整体替换为其另一个子节点（两级切除），
     * 避免只改 first/second 造成 (X,X) 重复子树、后续切除空转。
     * @return 是否成功切除了一份副本
     */
    private boolean removeOneCopy(int serverIdx, String url, ArthasWsWrapper wrapper)
            throws Exception {
        Deque<Node> stack = new ArrayDeque<Node>();
        // 根节点：若整棵树就是目标叶子，则把 RouterFunctionMapping.routerFunction 整体写为 null
        // （getHandlerInternal 对 null 有 ifnull 保护；注入前该字段本来就是 null）
        stack.push(new Node("#rf", "#rfm", "routerFunction", "#rfm", "routerFunction", NULL_FUNC));

        int budget = 300; // 防止异常树形导致无限探测
        while (!stack.isEmpty() && budget-- > 0) {
            Node n = stack.pop();
            String resp = parseProbe(runProbe(serverIdx, n.path, url, wrapper));
            logger.info("probe {} -> {}", n.path, resp);

            if ("LEAF_MATCH".equals(resp)) {
                logger.info("leaf matched at path={} (cut target={} field={} replacement={})",
                        n.path, n.cutTarget, n.cutField, n.replacement);
                return cutNode(serverIdx, n, wrapper);
            }
            if ("LEAF_NOMATCH".equals(resp) || "NULL".equals(resp)) {
                continue; // 分支到头
            }
            if (ERR.equals(resp)) {
                // NoSuchPropertyException 等错误信号：路径已为空，不再下潜
                logger.warn("probe error at {} (branch empty/ended), skip descend", n.path);
                continue;
            }
            if (SAME_COMPOSED.equals(resp) || DIFF_COMPOSED.equals(resp)) {
                // 组合节点：命中子节点时把本节点整体（在其父处）替换为另一个子节点
                // （兄弟为 null 时写 null，等效 empty）
                stack.push(new Node(n.path + ".second", n.path, "second",
                        n.refTarget, n.refField, n.path + ".first"));
                stack.push(new Node(n.path + ".first", n.path, "first",
                        n.refTarget, n.refField, n.path + ".second"));
                continue;
            }
            if (NESTED.equals(resp) || FILTERED.equals(resp)) {
                // 单子包装：命中子节点时把包装整体替换为 null
                stack.push(new Node(n.path + ".routerFunction", n.path, "routerFunction",
                        n.refTarget, n.refField, NULL_FUNC));
                continue;
            }
            if (ATTRIBUTES.equals(resp)) {
                stack.push(new Node(n.path + ".delegate", n.path, "delegate",
                        n.refTarget, n.refField, NULL_FUNC));
                continue;
            }
            // 未知类型：用 first/second 探测子字段（NoSuchPropertyException = 无此属性 = 无子节点）
            logger.info("unknown router node at {}: {}, probing first/second children", n.path, resp);
            if (hasChild(serverIdx, n.path, "first", wrapper)) {
                stack.push(new Node(n.path + ".first", n.path, "first",
                        n.refTarget, n.refField, n.path + ".second"));
            }
            if (hasChild(serverIdx, n.path, "second", wrapper)) {
                stack.push(new Node(n.path + ".second", n.path, "second",
                        n.refTarget, n.refField, n.path + ".first"));
            }
        }
        return false;
    }

    /**
     * 精确检查 routerFunction 链中是否包含目标 URL 路径。
     * 用正则匹配完整的 URL path（如 /execf），避免子串误判（/exec 误匹配 /execf）。
     */
    private boolean containsUrl(int serverIdx, String url, ArthasWsWrapper wrapper)
            throws Exception {
        String cmd = String.format(listSFHFByVmtool, serverIdx);
        List<String> result = wrapper.runCmd(cmd);
        String output = result != null ? String.join("", result) : "";

        // 精确匹配: url 后面必须跟空格（与 handler 之间）或行尾
        String escaped = Pattern.quote(url);
        return Pattern.compile(escaped + "( |->|$)", Pattern.MULTILINE)
                .matcher(output)
                .find();
    }

    @RequestMapping("/unload")
    public String unload(@RequestParam String url) {
        try {
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            int nettyServerCnt = Util.getNettyServerCnt();

            OGNLUtils.setStrictModeClose();

            for (int i = 0; i < nettyServerCnt; i++) {
                // 精确确认目标 URL 存在
                if (!containsUrl(i, url, wrapper)) {
                    logger.info("url {} not found in NettyServer[{}], skip", url, i);
                    continue;
                }

                // 记录切除前的链状态（用于出问题时排查）
                String beforeState = "";
                try {
                    List<String> before = wrapper.runCmd(String.format(listSFHFByVmtool, i));
                    beforeState = before != null ? String.join("", before) : "";
                } catch (Exception ignored) {}

                // 同名多副本逐个移除：每移除一份后重扫，直到链中不再含目标 URL
                int maxPass = 50;
                int pass = 0;
                while (pass++ < maxPass && containsUrl(i, url, wrapper)) {
                    boolean ok = removeOneCopy(i, url, wrapper);
                    if (ok) {
                        logger.info("removed one copy of {} from NettyServer[{}] (pass {})", url, i, pass);
                    } else {
                        logger.error("failed to remove url {} from NettyServer[{}], before state: {}, pass: {}",
                                url, i, beforeState, pass);
                        return "failed: cannot remove " + url + " (tree walk ended without finding it)";
                    }
                }

                if (!containsUrl(i, url, wrapper)) {
                    logger.info("url {} fully removed from NettyServer[{}], before state: {}", url, i, beforeState);
                    continue;
                }
                logger.error("url {} still present in NettyServer[{}] after {} passes, before state: {}",
                        url, i, pass - 1, beforeState);
                return "failed: cannot remove " + url + " (still present after max passes)";
            }

            return "success";
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return "failed: " + e.getMessage();
        }
    }

}
