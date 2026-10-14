package com.kmba.Utils;

import java.io.File;

public class Dict {
    // 全局临时目录（启动时按系统设定：Windows 用用户临时目录，Linux 用 /tmp），后续临时文件统一存这里
    public static String TMP_DIR;
    static {
        String osName = System.getProperty("os.name" ,"").toLowerCase();
        if (osName.contains("win")) {
            TMP_DIR = System.getProperty("java.io.tmpdir") + File.separator + "kmba";
        } else {
            TMP_DIR = "/tmp" + File.separator + "kmba";
        }
    }

    public static final String READ = "read";
    // 去除ANSI颜色
    public static final String PLAINTEXT = " |plaintext";
    // 换行
    public static final String ENTER = "\n";
    // 轮询间隔
    public static final int POLL_INTERVAL_MS = 1;
    // 最大轮询次数
    public static final int MAX_POLL_COUNT = 60000;
    // 发送stop，停止Arthas
    public static final String STOP = "stop";
    // tomcat 运行站点数（懒加载，首次调用时才执行 Arthas 命令）
    private static int tomcatSiteCnt = -1;
    public static int getTomcatSiteCnt() {
        if (tomcatSiteCnt < 0) {
            tomcatSiteCnt = Util.getTomcatSiteCnt();
        }
        return tomcatSiteCnt;
    }
    // 用于设置属性，设置后才能修改内存中的参数
    public static String Strict_Option = "{\"action\":\"read\",\"data\":\"options strict false\"}";
    public static String Unsafe_Option = "{\"action\":\"read\",\"data\":\"options unsafe true\"}";



}
