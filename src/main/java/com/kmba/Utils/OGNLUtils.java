package com.kmba.Utils;

import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OGNLUtils {
    private static Logger logger = LoggerFactory.getLogger(Util.class.getName());
    private static String getArthasClassLoaderHash = "classloader -l | grep com.taobao.arthas.agent.ArthasClassloader";
    private static String closeOGNLStrictMode = "ognl -c %s '#field = @com.taobao.arthas.core.util.reflect.FieldUtils@getDeclaredField(Class.forName(\"ognl.OgnlRuntime\"),\"_useStricterInvocation\",true), #modifiers = @com.taobao.arthas.core.util.reflect.FieldUtils@getDeclaredField(#field.getClass(),\"modifiers\",true),#modifiers.setInt(#field, #field.getModifiers() & ~@java.lang.reflect.Modifier@FINAL),#field.set(null,false)'";
    private static String closeStrict = "options strict false";

    public static boolean setStrictModeClose() {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            String arthasHash = null;

            List<String> hashResult = wrapper.runCmd(getArthasClassLoaderHash);

            // 不能只看第一行：grep taobao 会把父加载器列含 taobao 的行也带进来，
            // 行首可能是 fastjson2 DynamicClassLoader 等，排最前时 get(0) 上正则不中 → 静默漏翻。
            // 必须在整个输出里找 ArthasClassloader；翻转落点由表达式内 Class.forName 的
            // caller 语义决定（必为当前会话自己的 OgnlRuntime 副本），-c 只用于解析
            // FieldUtils，各 ArthasClassloader 均可，取第一个匹配即可
            Pattern pattern = Pattern.compile("com\\.taobao\\.arthas\\.agent\\.ArthasClassloader\\@([a-zA-Z0-9]{1,8})");
            Matcher matcher = pattern.matcher(String.join(" ", hashResult));
            if (matcher.find()) {
                arthasHash = matcher.group(1);
            }
            logger.info("hashResult: {}" ,arthasHash);

            if (arthasHash==null) {
                return false;
            } else {
                String cmd = String.format(closeOGNLStrictMode, arthasHash);
                logger.info("setStrictModeClose: {}" ,wrapper.runCmd(cmd));
                logger.info("closeStrict: {}" ,wrapper.runCmd(closeStrict));

                return true;
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
    }
}
