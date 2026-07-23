package com.kmba.arthas;

public class SpringFluxNettyHandler {
    // TODO
    /**
     * 卸载（初步逻辑，待优化）
     * 将恶意的doOnChannelInit，替换为Netty的默认Configurer：@reactor.netty.ReactorNetty@NOOP_CONFIGURER
     * 由于Netty的Pipeline是动态生成，故调用pipeline.remove仅能终止正在进行的连接，无法阻止后续连接
     * vmtool --action getInstances --className reactor.netty.http.server.HttpServerConfig --express 'instances.{#this.doOnChannelInit=@reactor.netty.ReactorNetty@NOOP_CONFIGURER}'
     *
     */
}
