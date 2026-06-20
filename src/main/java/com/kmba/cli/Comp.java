package com.kmba.cli;

import com.alibaba.fastjson.JSONArray;

// 用抽象类实现方法，简化了一下代码
public abstract class Comp {
    final String name;
    final String help;  // 帮助信息中该组件的一行说明

    Comp(String name, String help) {
        this.name = name;
        this.help = help;
    }

    abstract JSONArray list();
    abstract String unload(String target, String extra);
}