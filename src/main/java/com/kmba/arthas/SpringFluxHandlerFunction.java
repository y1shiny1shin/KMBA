package com.kmba.arthas;

public class SpringFluxHandlerFunction {
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
     */
}
