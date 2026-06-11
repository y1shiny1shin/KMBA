package com.kmba.Utils;

public class Dict {
    public static final String READ = "read";
    // 去除ANSI颜色
    public static final String PLAINTEXT = " |plaintext";
    // 换行
    public static final String ENTER = "\n";
    // 轮询间隔
    public static final int POLL_INTERVAL_MS = 1;
    // 最大轮询次数
    public static final int MAX_POLL_COUNT = 60000;
    // tomcat 运行站点数
    public static int tomcatSiteCnt = Util.getTomcatSiteCnt();
    // 用于设置属性，设置后才能修改内存中的参数
    public static String Strict_Option = "{\"action\":\"read\",\"data\":\"options strict false\"}";
    public static String Unsafe_Option = "{\"action\":\"read\",\"data\":\"options unsafe true\"}";



}
