package com.kmba;

/**
 * KMBA 启动 Logo，Web / CLI 模式统一输出
 */
public class Logo {

    public static final String VERSION = "0.7";

    public static void print() {
        System.out.println(
                " _  __ __  __ ____     _    \n" +
                "| |/ /|  \\/  || __ )   / \\   \n" +
                "| ' / | |\\/| ||  _ \\  / _ \\  \n" +
                "| . \\ | |  | || |_) |/ ___ \\ \n" +
                "|_|\\_\\|_|  |_||____//_/   \\_\\\n" +
                "\n" +
                "  KMBA v" + VERSION + " — Kill Memshell By Arthas\n" +
                "  https://github.com/y1shiny1shin/KMBA\n" +
                "\n");
    }
}
