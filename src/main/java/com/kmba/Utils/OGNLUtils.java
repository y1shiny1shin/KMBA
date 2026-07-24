package com.kmba.Utils;

import com.kmba.tunnel.ArthasWsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OGNLUtils {
    private static Logger logger = LoggerFactory.getLogger(Util.class.getName());
    private static String getArthasClassLoaderHash = "classloader -l | grep taobao";
    private static String closeOGNLStrictMode = "ognl -c %s '#field = @com.taobao.arthas.core.util.reflect.FieldUtils@getDeclaredField(Class.forName(\"ognl.OgnlRuntime\"),\"_useStricterInvocation\",true), #modifiers = @com.taobao.arthas.core.util.reflect.FieldUtils@getDeclaredField(#field.getClass(),\"modifiers\",true),#modifiers.setInt(#field, #field.getModifiers() & ~@java.lang.reflect.Modifier@FINAL),#field.set(null,false)'";
    private static String closeStrict = "options strict false";

    public static boolean setStrictModeClose() {
        try{
            ArthasWsWrapper wrapper = ArthasWsWrapper.getWrapper();
            String arthasHash = null;

            List<String> hashResult = wrapper.runCmd(getArthasClassLoaderHash);
            logger.info("hashResult: {}" ,hashResult);

            if (hashResult.size() > 0) {
                String result = hashResult.get(0);

                Pattern pattern = Pattern.compile("com\\.taobao\\.arthas\\.agent\\.ArthasClassloader\\@([a-zA-Z0-9]{8})");
                Matcher matcher = pattern.matcher(result);
                if (matcher.find()) {
                    arthasHash = matcher.group(1);
                }
            }

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
