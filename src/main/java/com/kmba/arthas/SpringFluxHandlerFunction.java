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

/**
 * HandlerFunction: https://party.mem.mk/ui -> SpringWebFlux -> HandlerFunctin
 * 这类个内存马写入到了org.springframework.web.reactive.function.server.support.RouterFunctionMapping#routerFunction
 * @DifferentComposedRouterFunction[
 *     first=@DifferentComposedRouterFunction[/execf -> com.flux.ErrorHandlerFunction@4a1fb764
 * /hello0 -> com.flux.evilHandlerFunction$HelloHandlerFunction@2d0db7b9
 * /hello1 -> com.flux.evilHandlerFunction$HelloHandlerFunction@305226c4],
 *     second=@DefaultRouterFunction[/hello2 -> com.flux.evilHandlerFunction$HelloHandlerFunction@17c9aa40],
 * ]
 * 写入到first和second没有规律可言，且无直接的remove方法，
 * 只能使用递归遍历，写入到一个新的DifferentComposedRouterFunction对象中，再使用反射替换原本的
 * 但是ONGL表达式不支持递归，故而有点麻烦，这里先搁置一下
 *
 * 考虑到目前仅有memshellparty对这个木马做了武器化，所以这里也只对这个木马做处理
 * 但是也不是特别好做替换，因为要遍历
 *
 *   routerFunction = DifferentComposed(
 *     first = DifferentComposed(              ← allHelloRoutes2
 *       first = DifferentComposed(            ← allHelloRoutes1
 *         first = DifferentComposed(          ← allHelloRoutes0
 *           first  = /execf,                  ← 最内层 first
 *           second = /hello0                  ← 最内层 second
 *         ),
 *         second = /hello1
 *       ),
 *       second = /hello2
 *     ),
 *     second = <原始路由 或 上次注入的链>
 *   )
 *
 * 卸载思路：
 *   沿 .first 链逐层遍历，对每个层级 c 尝试两种切除策略：
 *     FIRST  策略：setFinalField(#rf.F(c-1), "first", #rf.F(c).second)
 *              切除位于 .first 位置的叶子（如 /execf）
 *     SECOND 策略：setFinalField(#rf.F(c-1), "first", #rf.F(c).first)
 *              切除位于 .second 位置的叶子（如 /hello0/1/2）
 *   每次尝试后用 toString() 验证目标 URL 是否已从链中移除
 *
 * 本来不会这么复杂，但是都用AI了，肯定要考虑一下代码通用性了
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

    // ==================== list：拉取所有路由 ====================
    private String listSFHFByVmtool = "vmtool --action getInstances "
            + "--className org.springframework.boot.web.embedded.netty.NettyWebServer$1 "
            + "--express '" + NAV
            + "#rfm.routerFunction.toString()'";

    /**
     * toString() 输出示例：
     *   /execf -> com.flux.ErrorHandlerFunction@4a1fb764
     *   /hello0 -> com.flux.evilHandlerFunction$HelloHandlerFunction@2d0db7b9
     */
    @RequestMapping("/list")
    public JSONArray list() {
        try {
            int nettyServerCnt = Util.getNettyServerCnt();
            JSONArray jsonArray = new JSONArray();

            String resultAll = Util.getListResult(nettyServerCnt, listSFHFByVmtool);
            String regex = "(/[^ ]+) -> ([0-9a-zA-Z.$_]+)@[0-9a-f]+";
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
     * 构建 .first 链表达式（如 "#rf.first.first"）。
     * @param root   根引用，如 "#rf" 或 "#rf.second"
     * @param count  .first 的重复次数
     */
    private static String firstChain(String root, int count) {
        StringBuilder sb = new StringBuilder(root);
        for (int i = 0; i < count; i++) {
            sb.append(".first");
        }
        return sb.toString();
    }

    /**
     * 预检：root.F(c).first 叶子节点是否匹配目标 URL（FIRST 策略专用）。
     */
    private boolean leafAtFirstMatches(int serverIdx, int secDepth, int c,
                                       String url, ArthasWsWrapper wrapper)
            throws Exception {
        StringBuilder root = new StringBuilder("#rf");
        for (int i = 0; i < secDepth; i++) { root.append(".second"); }
        String nodeExpr = firstChain(root.toString(), c) + ".first";  // root.F(c).first

        return checkLeafMatches(serverIdx, nodeExpr, url, wrapper);
    }

    /**
     * 预检：root.F(c).second 叶子节点是否匹配目标 URL（SECOND 策略专用）。
     */
    private boolean leafAtSecondMatches(int serverIdx, int secDepth, int c,
                                        String url, ArthasWsWrapper wrapper)
            throws Exception {
        StringBuilder root = new StringBuilder("#rf");
        for (int i = 0; i < secDepth; i++) { root.append(".second"); }
        String nodeExpr = firstChain(root.toString(), c) + ".second"; // root.F(c).second

        return checkLeafMatches(serverIdx, nodeExpr, url, wrapper);
    }

    private static final String LEAF_CLASS =
            "org.springframework.web.reactive.function.server.RouterFunctions$DefaultRouterFunction";

    /** OGNL 检查一个节点是否为叶子且 predicate.pattern.patternString 精确等于目标 URL */
    private boolean checkLeafMatches(int serverIdx, String nodeExpr,
                                     String url, ArthasWsWrapper wrapper)
            throws Exception {
        String cmd = String.format(
                "vmtool --action getInstances "
                        + "--className org.springframework.boot.web.embedded.netty.NettyWebServer$1 "
                        + "--express '" + NAV
                        + "#rf=#rfm.routerFunction,"
                        + "%s != null"
                        + " && %s.class.name.equals(\"%s\")"
                        + " && %s.predicate.pattern.patternString.equals(\"%s\")'",
                serverIdx,
                nodeExpr, nodeExpr, LEAF_CLASS,
                nodeExpr, url);

        List<String> result = wrapper.runCmd(cmd);
        if (result == null || result.isEmpty()) return false;
        return result.get(0).contains("@Boolean[true]");
    }

    /**
     * 在指定 secondary 深度和 composed 深度的位置尝试切除。
     * 仅当预检确认该位置包含目标 URL 时才执行。
     *
     * @param serverIdx  NettyServer 索引
     * @param secDepth   沿 .second 下沉的深度（0=最外层，1=.second，2=.second.second ...）
     * @param c          composed node depth（沿 .first 的层级）
     * @param strategy   "FIRST" 或 "SECOND"
     */
    private String tryRemoveAtDepth(int serverIdx, int secDepth, int c,
                                    String strategy, ArthasWsWrapper wrapper)
            throws Exception {
        // 预检：该位置是否真的包含目标 URL
        // 注意：这里我们无法访问 url 参数，预检在 unload 循环中调用
        StringBuilder root = new StringBuilder("#rf");
        for (int i = 0; i < secDepth; i++) {
            root.append(".second");
        }
        String rootRef = root.toString();

        // c=1 时 c-1=0，target 就是 rootRef 本身
        String target = c == 1 ? rootRef : firstChain(rootRef, c - 1);
        String replacement;
        if ("FIRST".equals(strategy)) {
            replacement = firstChain(rootRef, c) + ".second";       // root.F(c).second
        } else {
            replacement = firstChain(rootRef, c) + ".first";        // root.F(c).first
        }

        String cmd = String.format(
                "vmtool --action getInstances "
                        + "--className org.springframework.boot.web.embedded.netty.NettyWebServer$1 "
                        + "--express '" + NAV
                        + "#rf=#rfm.routerFunction,"
                        + "@com.flux.Utils@setFinalField(%s,\"first\",%s),"
                        + "\"done secDepth=%d depth=%d strategy=%s\"'",
                serverIdx,
                target, replacement,
                secDepth, c, strategy);

        logger.info("tryRemove: serverIdx={} secDepth={} depth={} strategy={}",
                serverIdx, secDepth, c, strategy);
        List<String> result = wrapper.runCmd(cmd);
        return result != null && !result.isEmpty() ? result.get(0) : "";
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
                    List<String> before = wrapper.runCmd(
                            String.format(listSFHFByVmtool, i));
                    beforeState = before != null ? String.join("", before) : "";
                } catch (Exception ignored) {}

                // 两级遍历：
                //   secDepth: 沿 .second 下沉（处理多次注入的旧链累积）
                //   c:        沿 .first 定位（在给定链中精确切除叶子）
                // 每次注入 = prepend 一层到 .first，旧链沉到 .second
                // 注入 N 次后，目标 URL 可能出现在任意 secDepth 的 .first 链中
                boolean removed = false;
                int maxSecDepth = 10;
                int maxDepth = 10;
                int minDepth = 2;

                outerLoop:
                for (int secDepth = 0; secDepth < maxSecDepth; secDepth++) {
                    if (!containsUrl(i, url, wrapper)) {
                        removed = true;
                        break;
                    }

                    for (int c = maxDepth; c >= minDepth; c--) {
                        // === FIRST 策略：root.F(c).first 是叶子 && predicate 匹配 ===
                        if (leafAtFirstMatches(i, secDepth, c, url, wrapper)) {
                            String r1 = tryRemoveAtDepth(i, secDepth, c, "FIRST", wrapper);
                            if (r1 != null && !r1.contains("Failed to execute ognl")) {
                                if (!containsUrl(i, url, wrapper)) {
                                    logger.info("removed by FIRST secDepth={} depth={}",
                                            secDepth, c);
                                    removed = true;
                                    break outerLoop;
                                }
                                logger.warn("FIRST secDepth={} depth={} ok but still present,"
                                        + " multiple copies in chain?", secDepth, c);
                            }
                        }

                        // === SECOND 策略：root.F(c).second 是叶子 && predicate 匹配 ===
                        if (leafAtSecondMatches(i, secDepth, c, url, wrapper)) {
                            String r2 = tryRemoveAtDepth(i, secDepth, c, "SECOND", wrapper);
                            if (r2 != null && !r2.contains("Failed to execute ognl")) {
                                if (!containsUrl(i, url, wrapper)) {
                                    logger.info("removed by SECOND secDepth={} depth={}",
                                            secDepth, c);
                                    removed = true;
                                    break outerLoop;
                                }
                                logger.warn("SECOND secDepth={} depth={} ok but still present,"
                                        + " multiple copies in chain?", secDepth, c);
                            }
                        }
                    }
                }

                if (!removed) {
                    logger.error("failed to remove url {} from NettyServer[{}], before state: {}",
                            url, i, beforeState);
                    return "failed: cannot remove " + url + " (not found at any safe depth)";
                }
            }

            return "success";
        } catch (Exception e) {
            logger.error(e.getMessage());
            return "failed: " + e.getMessage();
        }
    }

}