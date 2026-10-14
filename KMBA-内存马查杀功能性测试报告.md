# KMBA 内存马检测与卸载工具 · 功能性测试报告

> **被测对象**: KMBA v0.8（Kill Memshell By Arthas），`KMBA-0.0.1-SNAPSHOT.jar`，MD5 `01501f79ce33c1d25539cbc020b563d7`（39,087,753 B）
> **报告日期**: 2026-09-01（v0.8 增补 2026-10-14；覆盖全部累计测试；开发者五次交付：`769f37da…`（v1 dump 回灌）→ `e4d7cb1f…`（v2 CodeSource jar 原始字节回灌）→ `a25cc5bf…`（v3，开发者称修复 #31）→ `d22aad21…`（v4，2026-09-01 交付，开发者说明"把 Agent 内存马 redefine 相关部分删了，功能也改了一点点"）→ **`01501f79…`（v0.8，2026-10-13 交付：redefine 处置链重建为"sc -d 确认存在 → KMBA 本进程读磁盘原始字节 → arthas redefine 回灌"，新增 `-u agent all` 与 Web「Agent类强杀」、`-l all` 卡死修复、sc -d 子类污染分块过滤；功能实测在 KMBA0 同源构建 `1193cb3a…` 上完成，功能文件与本发布构建逐字节一致，见 3.3.11）**——v0.8 为当前被测物，此前各中间版（含 `1fec05c7…`）均已被覆盖，本报告历史迭代结论以各版实测小节为准（redefine 处置迭代史完整保留于 3.3.7–3.3.10 作为能力演变依据链，v0.8 实测见 3.3.11）；CLI banner 版本字符串见观察项 #30（v0.8 起已同步））
> **测试性质**: 对该工具的功能性测试（检出 / 反编译确认 / 卸载 / 复查 / 失效确认），非漏洞复现测试——漏洞入口仅为把内存马送进 JVM 的投递手段
> **测试方式**: 全部环境部署在远端授权测试服务器的 Docker 中；逐环境注入公开武器化工具与公开技术文章手法的内存马，每个内存马严格执行五步验证协议，CLI 与 Web 控制台双通道；KMBA 全程单实例运行（同一时刻仅一个环境在测）
> **脱敏声明**: 本报告不含测试服务器地址、端口、账号口令及任何可定位基础设施的信息；所有命令均在服务器本地（127.0.0.1）或容器内执行，输出中的 Docker 内网地址（172.17.0.0/16，RFC 1918 保留网段）无外部指向性

---

## 一、结论速览

| 维度                                         | 结果                                                         |
| -------------------------------------------- | ------------------------------------------------------------ |
| 环境实测                                     | 累计 25+ 个真实环境实例（去重约 17 种环境类型：Tomcat 8.5/9/10.1、Spring Boot 内嵌 Tomcat、Jetty 7/9.4、WebLogic 12.2.1.3、JBoss AS 6.1、Spring Cloud Gateway 3.1.0、Struts2 2.3.34、Jenkins 2.138、Solr 8.11.0、OFBiz 18.12.09 等） |
| 组件类型                                     | v0.7 交付 jar 内置 **25 类组件**；五步验证通过 **24 类**（2026-08-31 复测确认：thread 子类型缺口已修复、socket 输出瑕疵已修复、listener 卸载消息已修复、filter 枚举去重已修复，见 3.7 与 3.8）；agent 的 redefine 型检出不可见（机制性盲区，维持）。redefine 处置路径迭代史：v1 假 success（3.3.7）→ v2 在样本⑥真实容器（8u151）CLI+Web 双通道五步终验通过、8u432 暴露主路径 OGNL 兼容性缺陷 #31（3.3.8）→ v3（称修复 #31）复测证实未修复，8u432 双通道假 success（3.3.9）→ **v4 开发者删除 redefine 处置链：卸载如实报失败（假 success 消除）但 8u151 既有处置能力一并消失，另新增 /agent/restore（dump-only）报 success 而不生效——新假 success 面（#32，见 3.3.10）**；**v0.8：处置链重建（sc -d → 磁盘原始字节 → redefine 回灌）+ `-u agent all`/Web 强杀，8u432 双马五步实测 redefine 马强杀闭环、#32 假 success 消除，另 `-l all` 卡死修复与 sc -d 子类污染分块过滤（3.3.11）** |
| **Agent 内存马专项（重点）**                 | 4 个环境实例 × 6 个样本（含 MemShellParty、GitHub 开源项目、公开文章自建、redefine-only 特殊机制）× CLI+Web 双通道：**5 个 transformer 型样本五步闭环——当前 jar（v4）复测 4 环境（Tomcat 9/8u432、Tomcat 10/JDK17、Solr-Jetty/JRE8、WebLogic 容器）transformer 型五步全过、能力无回归；1 个 redefine 型样本检出不可见（原理性，维持）：处置路径迭代 v1 假 success → v2 在 8u151 双通道五步终验通过、8u432 假 success（#31）→ v3 复测 #31 未修复 → **v4 删除 redefine 处置链：卸载如实报失败（假 success 消除）但 8u151 既有处置能力随之消失，v4 新增 /agent/restore（dump-only）对 redefine 污染报 success 而不生效——新假 success 面**（迭代证据链见 3.3.7 / 3.3.8 / 3.3.9 / 3.3.10）；**v0.8 处置链重建：强杀通道五步闭环、假 success 消除（3.3.11）** |
| Agent 专项新发现                             | 4+1 项功能问题（redefine 盲点：v1 回灌假 success（3.3.7）→ v2 主路径 8u151 通过、8u432 主路径 OGNL API 失败落 dump 回退（#31，3.3.8）→ v3 复测 #31 未修复，8u432 双通道假 success（3.3.9）→ v4 删除处置链：诚实拒绝但能力清零 + /agent/restore 新假 success（#32）+ vmtool bootstrap 边界（#33）+ 失败消息漂移（#34）（3.3.10） / Web 卸载失败窗口与列表残留（#10，v4 上 CLI/Web 双通道 1/1 复现） / 同名多实例半删（#15） / transformer 型列表残留需二次卸载）+ 4 项正向确认（JDK17 Unsafe 链路可用、transformer 型 restoreBytecode 有效、检测不依赖样本实现、jad 在 8u151 可见 redefine 后恶意代码——但 jad 字节来源与 JVM 版本强相关且可与运行时行为不一致，不可作回灌 ground truth，3.3.8）；**v0.8：#32 修复（restore 重构为真实回灌，8u432 五步实测马死亡）+ `-u agent all` 强杀通道 + sc -d 子类污染分块过滤实战验证（HttpServlet 5 块）；#10/#33/#34 维持（3.3.11）** |
| **组件能力闭环（2026-08-31 交付 jar 复测）** | 上一轮记录的问题项在交付 jar 上逐项复测：**thread 子类型（#12）、socket 输出（#18）、listener 卸载消息（#14）、filter 枚举去重（#22）四项修复实测确认带入**；agent redefine 回灌（#9）：v2 在 8u151 实测通过、v3 复测 8u432 双通道仍假 success（主路径 #31 未修复 + dump 回退字节会话相关，3.3.9），**v4 将处置链整体删除（#9 转为能力清零；#31 随代码删除失效——非修复）**；proxyValve 枚举面（#11）、valve 假阳性（#16）、CLI 退出码（#21）维持未修；round3 P0（listener 卸载残留）复测未复现；v4 新增 /agent/restore、-vmtool、/vmtool/get 功能并实测（#32 假 success / #33 bootstrap 边界，3.3.10）；**v0.8 复测：#32 修复（restore 真实回灌）、`-u agent all` 落地、`-l all` 卡死修复（全量 47.7s 实测），#10/#11/#16/#21 维持（3.3.11）** |
| **多马共存与协议马**                         | Tomcat 9 六马共存、Gateway 3.1.0 四马共存逐步精准卸载（"只清目标、其余存活"逐步复核）；哥斯拉协议马完整五步（pass/key/md5 三要素校验） |
| **对业务的影响（重点）**                     | 全部卸载操作**不停机、不重启目标 JVM、零业务中断**：24 类通过组件的每个五步均以"合法组件完好（<3> 复查）+ 生产页面/接口正常响应（<4> 死亡确认）"双验证收口；共存场景逐步卸载全程"只清目标、其余存活"；卸载附带**原地恢复**——Agent 组件（transformer 型）经 restoreBytecode 即时还原被插桩宿主类（ApplicationFilterChain / HttpServlet / ServletHandler，无需重启进程）、proxyValve 卸载后管线槽位还原原始 valve、executor 卸载后恢复原始 executor；全程未出现 attach 或卸载操作导致的业务异常、误伤合法组件或服务重启。影响业务的场景是 redefine 型样本未被清除（内存马持续存活、可执行命令）：v1 报 success 而实际未还原（假 success，3.3.7），业务侧对"卸载已完成"产生错误确认；v2 在 8u151 场景闭环、8u432 双通道假 success（#31，3.3.8/3.3.9）；**v4 删除 redefine 处置链后该场景不再报假 success（卸载如实报失败、内存马存活——从"误导"变为"无能"），但 v4 新增的 /agent/restore 对 redefine 污染重新引入假 success**（dump-only 报 success，8u432 清态与 8u151 双环境实测内存马均存活，#32，3.3.10）——业务侧若以 restore 输出为确认依据将再次产生错误确认。该场景对业务无破坏性操作，属查杀能力未闭环而非工具对业务的破坏；**v0.8：restore 重构为真实回灌后该错误确认面消除——8u432 双马五步实测 redefine 马 restore 强杀后命令失效、探针 +0（3.3.11）** |
| 高版本 JDK                                   | JDK 17（jakarta 命名空间）filter 与 agent 两类均 CLI+Web 全链通过（含模块系统下 Unsafe 自动降级链）；v4 复测 agent 样本④ CLI 五步全过（Agent 代码已随 redefine 处置链删除而变更，3.3.10） |
| 缺陷修复史                                   | 测试过程中发现 7 个缺陷 → 均经修复后复验通过（PASS=45 FAIL=0 及后续回归）；round3 修复项中 proxyValve / socket / struts2Action 新增已在交付 jar 实测确认；**2026-08-31 交付 jar 复测确认 4 项修复带入（thread 子类型 / socket 输出 / listener 消息 / filter 去重）**，agent redefine 回灌修复 v2 在 8u151 实测通过（3.3.8）、8u432 假 success 遗留（新发现 #31；v3 复测证实 #31 未修复，3.3.9）；**v4 将 redefine 处置链整体删除（#31 随之失效——属功能删除非修复；#9 转为能力清零），新增 /agent/restore 与 vmtool 功能并实测出新假 success（#32）与 bootstrap 边界（#33），3.3.10**，round3 P0（listener 卸载残留）复测未复现；**v0.8：#32 修复（restore 真实回灌 + `-u agent all`）、`-l all` 卡死修复实测带入，redefine 型处置能力恢复（3.3.11）** |
| 存留问题                                     | **v0.7 时 14 项 → v0.8 后 13 项**（1 项 P0 + 3 项 P1 + 9 项 P2：#32 于 v0.8 修复转入已修复清单；#31 随 v4 功能删除失效非修复；#9 处置通道 v0.8 恢复、检出盲区维持）+ 7 项观察记录，详见第八节问题清单，均有复现输出依据 |

**总体结论**: 对 Filter / Servlet / Listener / Valve / 定时与线程类 / Spring MVC / Spring WebFlux / Jetty / WebLogic / Struts2 Action / WebSocket 等主流内存马类型，KMBA 的 **检出 → 反编译确认 → 卸载 → 复查 → 失效确认** 全部闭环；Web 控制台与 CLI 能力一致；上一轮存留的 4 项组件级问题（thread 子类型枚举 / socket 输出 / listener 消息 / filter 去重）在 2026-08-31 交付 jar 上复测确认修复。**Agent 内存马**：对"注册 ClassFileTransformer"这一主流实现（含 ASM / Javassist 两类字节码库、javax / jakarta 双命名空间、Tomcat / Jetty / 多插桩目标）检出卸载可靠——当前 jar（v4，删除 redefine 处置链后的交付物）复测 4 环境（Tomcat 9/8u432、Tomcat 10/JDK17、Solr-Jetty/JRE8、WebLogic 容器）transformer 型五步全过，能力无回归。对**仅 redefineClasses、不注册 transformer** 的特殊实现：检出不可见（transformer 枚举原理性盲区，维持）；处置路径历经 v1 假 success（3.3.7）→ v2 在样本⑥真实容器（8u151）CLI+Web 双通道五步终验通过、8u432 暴露主路径兼容性缺陷 **#31**（OGNL `sun.misc.IOUtils.readFully(-1)` 被该 JDK 构建拒绝 → 落 dump 回退，回退字节来源会话相关）（3.3.8）→ v3 复测证实 #31 未修复、8u432 双通道假 success（3.3.9）→ **v4 开发者删除 redefine 处置链（`AgentUtils` 整类移除）：卸载不再假 success（CLI/Web 如实报失败、内存马存活），但 v2/v3 在 8u151 上真实可用的回灌能力随代码删除一并消失——redefine 型从"部分误导"变为"完全无处置"，8u432 假 success 的消除以删除 8u151 真实能力为代价**；v4 新增的 `/agent/restore`（web-only、仅 dump）对 redefine 污染报 success 而不生效（8u432 清态与 8u151 双环境实锤，dump 取到被改写字节 `2b7731e2…`）——**新假 success 面（#32）**；新增 vmtool 双通道功能可用，但 bootstrap 加载类不可用且 Web 端把错误文本包进 `type=single`（#33）。Web 端 Agent 卸载"实例顺序漂移"导致的失败窗口与列表残留（#10）在 v4 上 CLI/Web 双通道 1/1 复现；同 JVM 同目标失败消息随会话/通道漂移（not found / OGNL execute failed，#34）。**对业务的影响**：卸载动作本身对业务无损——不停机、不重启目标 JVM，被插桩宿主类字节码与管线/组件状态原地还原，全部测试中合法组件与生产功能零中断、零误伤；唯一例外仍是 redefine 型样本未被清除的场景——内存马实际持续存活：v4 卸载路径已如实报失败（不再误导"卸载已完成"的确认），但 /agent/restore 报 success 而内存马存活，业务侧若以 restore 输出为确认依据将再次产生错误确认（属查杀能力未闭环，非工具对业务的破坏性操作）。**v0.8 更新（2026-10-13）**：redefine 处置链重建为 sc -d → 磁盘原始字节 → redefine 回灌（不经 OGNL 提取，v2/v3 的 #31 模板问题与 dump 回退通道一并退出），强杀通道（`-u agent all` / Web「Agent类强杀」）落地；8u432 双马五步实测 redefine 马强杀闭环（restore success 16.6s 后命令失效、探针 +0）、#32 假 success 消除；`-l all` 卡死修复（全量 47.7s）与 sc -d 子类污染分块过滤（HttpServlet 5 块实战样本）同批带入（3.3.11）。

---

## 二、测试方法与约束

### 五步验证协议（每个内存马严格执行，缺一即记录为未闭环）

```
<0> 注入后确认内存马可执行命令（如 ?cmd=id 回显 uid=...）
<1> KMBA list 检出内存马（区分合法组件与内存马）
    ↳ 必要时 KMBA jad 反编译确认恶意（getParameter → Runtime.exec 等证据）
<2> KMBA unload 卸载目标内存马
<3> list 复查：目标已消失，合法组件完好
<4> 死亡确认：原通道请求不再执行命令（无回显、业务正常响应）
```

- CLI 通道：`java -jar KMBA.jar cli <pid> -l/-jad/-u <组件> <目标>`
- Web 通道：`java -jar KMBA.jar` 绑定 9099 → `GET /arthas/connect?pid=<pid>`（**前置必需**）→ 组件端点 list / unload

### 纪律与原则

- **一环境一容器，测完即停**；每个样本开始时容器处于"无残留内存马 + 业务正常"状态
- **不制造漏洞**：镜像无可利用入口的环境如实记录并弃用；对注入侧的限制优先采用"启动时关闭限制配置 / 换用低版本"等环境侧解法，不在漏洞复现上消耗篇幅
- **有依据原则**：所有内存马注册路径基于公开 API / 字节码反编译（javap / jad）取证，不做无依据假设
- **同环境同注入方式只测一次**：主体逻辑相同的重复注入不重复测，覆盖优先
- **修复验证以最终产物为准**：所有"已修复"结论均在最终发布 jar（v0.7）上实测复验，不以开发中间版结论替代
- **互联网取样**：Agent 样本按"公开技术文章 + 开源项目 + 与已知生成器产物实现不同"的要求从互联网收集与自建（详见 3.3）

---

## 三、查杀能力实证

### 3.1 组件类型 × 查杀能力总矩阵

> 25 类组件的查杀能力按五步协议逐类验证。"证据来源"列指向对应测试日志（见附录）。

| 组件                        | 查杀能力（CLI + Web）                                        | 验证环境                                                     | 状态与边界                                                   |
| --------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| filter                      | 检出/卸载/复查/死亡全链                                      | Shiro、Solr、WebLogic、fastjson、Tomcat 系                   | ✅                                                            |
| servlet                     | 全链                                                         | 类型矩阵、WebLogic                                           | ✅                                                            |
| listener                    | 全链                                                         | 类型矩阵（5 副本场景）、JettyDemo；交付 jar 复测（3/12 副本） | ✅（卸载消息修复在交付 jar 复测确认，见 3.8）                 |
| valve                       | 全链                                                         | 类型矩阵、proxyValve 兜底清除；交付 jar 复测                 | ✅（不存在目标时报假阳性 success 维持未修，见 3.8）           |
| upgrade                     | 全链                                                         | 类型矩阵                                                     | ✅                                                            |
| timer                       | 全链                                                         | 类型矩阵                                                     | ✅                                                            |
| thread                      | 全链                                                         | 类型矩阵；交付 jar 复测（Thread 子类型）                     | ✅（"继承 Thread 覆写 run"型在交付 jar 复测确认可检出可卸载，见 3.8） |
| executor                    | list/unload/恢复原始                                         | 类型矩阵                                                     | ✅（替换型占位特性与公开模板同型）                            |
| springMvcController         | 全链                                                         | SpringDemo、JavaRce                                          | ✅                                                            |
| springMvcInterceptor        | 全链                                                         | SpringDemo                                                   | ✅                                                            |
| springFluxHandlerMethod     | 全链                                                         | Gateway 3.1.0、四马共存                                      | ✅                                                            |
| springFluxWebFilter         | 全链                                                         | Gateway 3.1.0                                                | ✅                                                            |
| springFluxNettyHandler      | 全链                                                         | Gateway 3.1.0                                                | ✅                                                            |
| springFluxHandlerFunction   | 全链                                                         | Gateway 3.1.0（四马共存）                                    | ✅（生成器侧不兼容问题见边界清单）                            |
| jettyFilter                 | 全链                                                         | Solr 8.11.0、Jenkins 2.138                                   | ✅                                                            |
| jettyServlet                | 全链                                                         | Solr 8.11.0                                                  | ✅                                                            |
| jettyListener               | 全链                                                         | JettyDemo                                                    | ✅                                                            |
| weblogicFilter              | 全链                                                         | WebLogic 12.2.1.3                                            | ✅                                                            |
| weblogicServlet             | 全链                                                         | WebLogic 12.2.1.3                                            | ✅                                                            |
| weblogicListener            | 全链                                                         | WebLogic 12.2.1.3                                            | ✅                                                            |
| **agent**（transformer 型） | 全链                                                         | 5 样本 × Tomcat9/Tomcat10-JDK17/Solr                         | ✅（详见 3.3 专项）                                           |
| **agent**（redefine 型）    | 检出不可见（原理性，维持）；处置迭代：v1 假 success → v2 8u151 CLI+Web 五步通过 / 8u432 **Web 假 success（#31）** → v3 复测 #31 未修复、8u432 双通道假 success → **v4 删除处置链：CLI/Web 如实报失败、内存马存活（8u151 既有能力消失）；新增 /agent/restore（dump-only）报 success 不生效** | WebLogic 12.2.1.3（样本⑥真实容器）；复测 Tomcat 9 · JDK 8u432 双通道 | ⚠️ 检出盲区维持；v4 redefine 型处置能力清零（诚实拒绝）+ /agent/restore 新假 success（3.3.7–3.3.10）；**v0.8 处置链重建：restore 强杀通道五步闭环（8u432 redefine 马实测马死亡）、假 success 消除（3.3.11）** |
| **struts2Action**           | 全链                                                         | Struts 2.3.34 showcase                                       | ✅（本轮闭环 round3 能力边界，见 3.6）                        |
| **proxyValve**              | first/basic 槽位形状全链                                     | Tomcat 9.0.97                                                | ⚠️ 五步全过，但枚举面覆盖不全（engine/host 管线与中间槽位代理不可见，见 3.7） |
| **socket**（WebSocket）     | 全链                                                         | Tomcat 9.0.97；交付 jar 复测                                 | ✅（首条目输出乱码已修复，见 3.8）                            |
| tongWeb                     | —                                                            | —                                                            | ⏸ 按测试范围约定不测                                         |

### 3.2 环境覆盖总表

| 环境                                                | 容器 / JDK                                     | 入口（公开 CVE / 公开途径）            | 内存马类型 / 投递                                            | CLI 五步                    | Web 五步                          |
| --------------------------------------------------- | ---------------------------------------------- | -------------------------------------- | ------------------------------------------------------------ | --------------------------- | --------------------------------- |
| Tomcat 10.1.34                                      | tomcat:10.1.34-jdk17-temurin · OpenJDK 17.0.14 | Manager 弱口令 war 部署                | Filter (jakarta) · JSP 反射注入                              | ✅                           | ✅                                 |
| Spring Boot 2.x 内嵌 Tomcat（fastjson 1.2.24）      | vulhub/fastjson:1.2.24 · JDK 8u102 JRE         | CVE-2017-18349 JNDI                    | Filter · JNDIExploit 远程类加载                              | ✅                           | —（同环境类型按覆盖原则不重复测） |
| Spring Boot 2.2.2 内嵌 Tomcat 9.0.29（Shiro 1.2.4） | shiro demo · JDK 8u102 JRE                     | CVE-2016-4437 Shiro-550                | Filter · web-chains 反序列化 defineClass                     | ✅                           | ✅                                 |
| Solr 8.11.0（Jetty 9.4.44）                         | vulhub/solr:8.11.0 · JDK 8u102 JRE             | CVE-2021-44228 log4j2                  | Jetty Filter · web-chains JNDI 字节码；Agent 样本            | ✅                           | ✅                                 |
| WebLogic 12.2.1.3                                   | vulhub/weblogic:12.2.1.3-2018 · JDK 8u151      | CVE-2020-14882/14883；T3 ECHO          | weblogic Filter/Servlet/Listener；Agent redefine 样本        | ✅                           | ✅                                 |
| Shiro 1.2.4 ×3 注入工具                             | 同上环境类型                                   | Shiro-550                              | 哥斯拉 Filter、reGeorg Filter、冰蝎 Servlet、独立家族 Filter ×2（ShiroAttack2 / Hyacinth / Pyke-Shiro） | ✅（jad 确认各家族源码）     | —                                 |
| Spring Cloud Gateway 3.1.0                          | spring-cloud-gateway · JDK 8                   | 公开路由注入                           | WebFlux 四类（四马共存）                                     | ✅（修复后）                 | ✅（修复后）                       |
| log4j-lab / JavaRce SpringDemo / JettyDemo          | Boot 2.6.1 / Spring MVC / Jetty 7              | EL / 公开演示入口                      | DynamicFilterTemplate（ELProcessor）、SpringController、Jetty Listener | ✅（修复后）                 | ✅（修复后）                       |
| Struts2 2.3.34（Tomcat 8.5）                        | vulhub/struts2:2.3.34-showcase                 | S2-057 OGNL                            | ①Tomcat Filter ②Struts2 Action（MemShellParty 注入器）       | ①✅ ②✅（struts2Action 组件） | ②✅                                |
| Jenkins 2.138（Jetty 9.4）                          | 公开漏洞镜像                                   | CVE-2018-1000861 Groovy                | Jetty Filter · MemShellParty GroovyClassDefiner              | ✅                           | —                                 |
| OFBiz 18.12.09                                      | 公开漏洞镜像                                   | CVE-2023-49070 XML-RPC                 | Tomcat Filter（EvilFilters）· OFBiz-Attack 解包脚本化        | ✅                           | —                                 |
| Tomcat 8.5.19 / 8.5.33                              | 公开镜像                                       | CVE-2017-12615 PUT JSP / showcase      | 类型矩阵：listener/servlet/valve/proxyValve/upgrade/websocket/timer/thread/executor | ✅（见 3.1 状态列）          | 部分                              |
| Tomcat 9.0.97                                       | 公开镜像                                       | CVE-2025-24813 会话反序列化 / JSP 注入 | Filter、类型矩阵、六马共存、哥斯拉协议马、Agent 样本、修复延续性复验 | ✅                           | ✅                                 |
| Tomcat 10.1.34 Agent                                | 同上 · JDK17                                   | Manager 部署                           | Agent jakarta 样本                                           | ✅                           | ⚠️（失败窗口，见 3.3）             |
| JBoss AS 6.1.0                                      | 公开镜像 · JDK7                                | CVE-2017-12149                         | Tomcat(6 fork) Filter                                        | ⛔ JDK7 不支持（边界）       | —                                 |
| XWiki / Confluence                                  | —                                              | —                                      | XWiki 无公开内存马方案；Confluence license 环境卡死（工具侧不可复现） | ⏸ 记录                      | ⏸ 记录                            |

---

### 3.3 【重点专项】Agent 内存马查杀测试

#### 3.3.1 测试设计与样本矩阵

KMBA 的 `agent` 组件与其它组件模型不同：它不感知业务组件（Filter/Servlet 等），而是**枚举目标 JVM 中各 `sun.instrument.InstrumentationImpl` 实例上已注册的 `ClassFileTransformer`**（按 transformer 实现类的名字列出，附 canRetransform 标志），卸载即按类名匹配后调用 `Instrumentation.removeTransformer()`。因此 Agent 内存马样本的选择以"注入机制、字节码库、插桩目标类、实现来源"的最大差异化为原则：

| #    | 样本                                                         | 来源（互联网收集/自建）                                      | 字节码库         | 注入机制                                                     | 插桩目标                                                     | 运行环境（容器 / JDK）                                       |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------ | ---------------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| ①    | MSP AgentFilterChain（javax）                                | MemShellParty 2.8.0 开源生成器（API 生成，packer=AgentJar）  | ASM（shade）     | `addTransformer(t, true)` + retransform                      | `ApplicationFilterChain#internalDoFilter`                    | `agent-t9a` vulhub/tomcat:9.0.97 · OpenJDK 1.8.0_432         |
| ②    | MyAgent（`org.chabug.MyTransformer`）                        | GitHub 公开仓库 **Y4er/javaagent-tomcat-memshell**（仓库自带预编译成品，未经修改直接使用） | Javassist 3.22   | `addTransformer(t, true)` + retransform                      | 同 `ApplicationFilterChain`（不同实现）                      | `agent-t9a`（复用，起始无残留）                              |
| ③    | 自建 HttpServlet#service 钩子（`self.HttpServletTransformer`） | 按公开技术文章的通用手法自建（Java Agent 改字节码注入内存马系列，源码存档 `poc/agent/http-servlet-hook/`） | Javassist 3.30.2 | 同类名**双实例**（javax + jakarta 两个变体）`addTransformer(t, true)` | `javax.servlet.http.HttpServlet#service`                     | `agent-t9a`（复用，起始无残留）                              |
| ④    | MSP AgentFilterChain（jakarta）                              | MemShellParty 2.8.0（jakarta=true）                          | ASM（shade）     | 同①                                                          | `ApplicationFilterChain`（jakarta 命名空间）                 | `agent-t10` tomcat:10.1.34-jdk17-temurin · OpenJDK 17.0.14   |
| ⑤    | MSP Jetty AgentHandler                                       | MemShellParty 2.8.0（injector=jetty.JettyHandlerAgentInjector） | ASM（shade）     | `addTransformer(t, true)` + retransform                      | Jetty `ServletHandler#handle`（另定义伪装类 `org.eclipse.jetty.servlet.handlers.dnvVM.OAuthAgentHandler`） | `agent-solr` vulhub/solr:8.11.0（Jetty 9.4.44）· OpenJDK 8u102 JRE |
| ⑥    | Weblogic404Memshell                                          | GitHub 公开项目 Weblogic404Memshell（源码本地 javac 构建，jar 797,103 B，manifest `Agent-Class: c`） | Javassist        | **仅 `redefineClasses()`，不注册任何 ClassFileTransformer**  | WebLogic `HttpConnectionHandler#resolveServletContext`（原地改写为命令执行） | `agent-wl` vulhub/weblogic:12.2.1.3-2018 · Oracle JDK 8u151  |

样本⑥的设计目的：KMBA 的检出模型建立在 transformer 枚举之上，"仅 redefine、不注册 transformer"的样本理论上不可见——本项即为对该盲点的定向验证。

注入方式统一为容器内 `com.sun.tools.attach`（tools.jar + AttachMain）向目标 JVM `loadAgent`；`<0>` 生命确认均以"请求命令参数/命令头 → 回显 uid"为准。

#### 3.3.2 环境一：Tomcat 9.0.97 · JDK 8 —— 样本 ① ② ③

**样本① MemShellParty AgentFilterChain（基线）**

注入后 `GET /?cmd=id` → `uid=0(root) gid=0(root) groups=0(root)`。

| 步骤       | CLI                                                          | Web                                       |
| ---------- | ------------------------------------------------------------ | ----------------------------------------- |
| <1> 检出   | `agent (3)` 列出 `com.reajason.javaweb.memshell.injector.tomcat.TomcatFilterChainAgentInjector (canRetransform=true)` + 2 条 arthas 自噪音 | `/agent/list` 同（JSON 三条，恶意项在内） |
| jad        | 反编译确认：`TARGET_CLASS = org/apache/catalina/core/ApplicationFilterChain`、`TARGET_METHOD_NAME = "doFilter"`、`launch()` 内 `addTransformer(..., true)` + `retransformClasses`、`transform()` 内 ClassReader/ClassWriter 重写与 base64+gzip 内嵌 shell 类完整可见 | —                                         |
| <2> unload | `[+] unload success`                                         | `success`                                 |
| <3> 复查   | `agent (2)`，仅剩自噪音                                      | 同                                        |
| <4> 死亡   | `?cmd=id` / `?cmd=whoami` 无回显，`GET /` 200                | 同                                        |

**五步全部通过（CLI+Web）**。已卸载后同请求失活说明 KMBA 不止移除 transformer，还恢复了被 retransform 的 `ApplicationFilterChain` 字节码（restoreBytecode 生效，样本③环境再次印证）。

附带可用性记录：Web 模式**不会自动 attach**，直接 `GET /agent/list` 返回 `[]`（HTTP 200）而非报错——必须先 `GET /arthas/connect?pid=1` 建立会话。此行为易被误读为"未检出"，已在问题清单中记为观察项。

**样本② Y4er MyAgent（开源项目、Javassist、不同类名/实现）**

注入后 `?cmd=id` → `uid=0(root)`。

| 步骤       | CLI                                                          | Web              |
| ---------- | ------------------------------------------------------------ | ---------------- |
| <1> 检出   | `agent (3)` 列出 `org.chabug.MyTransformer (true)`           | `/agent/list` 同 |
| jad        | 反编译确认：`public static String ClassName = "org.apache.catalina.core.ApplicationFilterChain";` 与 `m.insertBefore(" ... String cmd = req.getParameter(\"cmd\"); if (cmd != null) { Process process = Runtime.getRuntime().exec(cmd); ... }")` —— Javassist 注入源码与命令执行逻辑直接可见 | —                |
| <2> unload | `[+] unload success`                                         | `success`        |
| <3> 复查   | `agent (0)`（该次无常驻会话，连自噪音也不存在）              | 仅剩自噪音       |
| <4> 死亡   | `?cmd=id` 无回显，业务 200                                   | 同               |

**五步全部通过（CLI+Web）**。与样本①结论合并：KMBA 的 Agent 检出模型**不依赖样本的字节码库（ASM/Javassist）、transformer 类名与实现代码**，凡注册了 transformer 即可检出。

**样本③ 自建 HttpServlet#service 钩子（公开文章手法、javax+jakarta 双实例）**

自建说明：按公开技术文章的 canonical 手法实现 `agentmain` 注册 retransformable transformer，Javassist `insertBefore` 挂 `HttpServlet#service`，请求参数 `cmd` 触发 `Runtime.exec` 并回显；agentmain 一次性注册 javax 与 jakarta 两个同名类实例（jar 792,957 B，含 javassist 3.30.2 共 443 类）。注入后容器内出现 `/tmp/hooked-javax-HttpServlet.class`（transformer 改写后的字节码落盘，注入证据），`?cmd=id` → `uid=0(root)`（HttpServlet 层钩子对 ROOT 应用全部请求生效）。

| 步骤       | CLI                                                          | Web              |
| ---------- | ------------------------------------------------------------ | ---------------- |
| <1> 检出   | `agent (3)` 列出 `self.HttpServletTransformer (true)`（双实例同名显示为一条） | `/agent/list` 同 |
| jad        | 反编译确认 `SHELL_SRC`（`$1.getParameter("cmd")` → `Runtime.exec` → `$2.getOutputStream().write(...)`）与 `getDeclaredMethod("service", ...)` + `insertBefore` 明确插桩 HttpServlet | —                |
| <2> unload | `[+] unload success`                                         | `success`        |
| <3> 复查   | **仍显示 `self.HttpServletTransformer true`** —— 单次 unload 按 OGNL 匹配后仅移除第一个匹配实例（javax），jakarta 实例残留 | 同               |
| <4> 死亡   | `?cmd=id` → **空（死亡）**，业务 200 —— 被移除的第一个实例即活跃钩子 | 同               |

二次 `unload` → `success`，复查后列表仅剩自噪音（残留清零）。**结论**：按类名卸载每次只移除一个匹配实例；同名多实例场景需执行多次才能清空列表。死亡判定不受影响，但对运维语义有影响（"列表未清零"既可能意味着没杀干净，也可能只是注册残留），已在问题清单中记录。

#### 3.3.3 环境二：Tomcat 10.1.34 · JDK 17（jakarta 命名空间）—— 样本④

本环境验证 KMBA 在 **JDK 9+ 模块系统**（反射受限）下的 Agent 代码路径。样本为 MemShellParty jakarta 变体（jar 94,242 B），容器内手工创建最小 ROOT 应用（`webapps/ROOT/index.jsp`）以承载请求。

**CLI 五步全部通过**：

| 步骤 | 结果                                                         |
| ---- | ------------------------------------------------------------ |
| <0>  | `GET /?cmd=id` → `uid=0(root) ...`                           |
| <1>  | `cli 1 -l agent` 检出 `TomcatFilterChainAgentInjector (true)` + 自噪音 2 条；jad 反编译成功（ApplicationFilterChain 插桩逻辑可见） |
| <2>  | `[+] unload success`（一次成功）                             |
| <3>  | `agent (0)`                                                  |
| <4>  | `?cmd=id` 无回显，页面返回正常业务内容（200）                |

**技术路径记录**：JDK17 下直接反射 `InstrumentationImpl` 私有字段触发 `InaccessibleObjectException`（`module java.instrument does not "opens sun.instrument"`）；KMBA 捕获后自动执行 `options strict false`、`options unsafe true`、OGNL stricter-invocation 反射补丁，并切换 sun.misc.Unsafe + 接口反射调用的 JDK9 模板重试，最终成功读取 transformer 列表并完成卸载（KMBA web 日志全程可查）。**JDK17 的 Unsafe 代码路径完整可用。**

**Web 五步——发现"卸载失败窗口"（重要缺陷）**：

- `GET /arthas/connect?pid=1` → `success`；重注入后 `?cmd=id` → `uid=0(root)`
- `<1>` `/agent/list` 检出恶意 transformer ✅
- `<2>` `GET /agent/unload?className=...TomcatFilterChainAgentInjector` → HTTP 200 **`className not found`**
    - 同一时刻：`/agent/list` 仍能看到该 transformer，且 `?cmd=id` 仍回显 `uid=0(root)` —— **"列表可检出 + shell 存活 + 卸载报不存在"** 的失败窗口
    - web 日志证据：unload 循环逐 index 探测 `instances[i]` 共 8 次全部 `not-found`；此前 list 调用刚经历 JDK8 模板 7 个 `InaccessibleObjectException` → 自动 strict/unsafe/OGNL 补丁 → JDK9 模板重试成功
- 第二次调用 → `success`；`<3>` 复查仅剩自噪音；`<4>` 死亡确认通过（restoreBytecode 的 30 条 dumpTargetClasses 查询行出现在日志中）

**根因分析（实测诊断脚本逐 index 举证）**：KMBA 的 list/unload 采用"先查 `instances.length`（实测 7→8），再对 `instances[i]` 逐 index 发起**独立** vmtool 查询"的模式，而 arthas vmtool 的堆扫描**不保证多次调用间实例顺序一致**，且每次外部 loadAgent（arthas connect、注入）都会在目标 JVM 新建一个 `InstrumentationImpl`（实测实例数 7→8）。诊断脚本同模板逐 index 对比显示：list 模板在 idx5 探到内存马 transformer，数秒后 unload 模板在 idx0 探到匹配、idx5 反而为空——两次调用间顺序已漂移。结果即"时隐时现"：探测窗口错位时 unload `not-found`（shell 仍存活），重试后窗口对齐即成功。**该项为模型级问题**（下一环境在 JRE 8 上复现同样现象，见 3.3.4）。

#### 3.3.4 环境三：Solr 8.11.0 · Jetty 9.4.44 · JRE 8 —— 样本⑤

样本为 MemShellParty Jetty AgentHandler（jar 94,400 B），插桩目标 Jetty `ServletHandler#handle`（与 Tomcat 环境不同的中间件实现），另定义伪装类 `org.eclipse.jetty.servlet.handlers.dnvVM.OAuthAgentHandler`。Solr 的 JRE 容器缺 tools.jar，测试时补入 JDK8 tools.jar 供 attach 使用。

**CLI 五步全部通过**：`<0>` `GET /solr/?cmd=id` → `uid=0(root)`；`<1>` 检出 `JettyHandlerAgentInjector (true)` + 自噪音，jad 确认 TARGET_CLASSES 为 ServletHandler（ee8/9/10/11 多版本）+ `handle` 插桩；`<2>` `[+] unload success` 一次成功；`<3>` 仅剩 TransformerManager 自噪音；`<4>` `?cmd=id` 无回显、`/solr/admin/cores?action=STATUS` 200 正常。

**Web 五步——失败窗口在 JRE 8 复现**：

- `<1>` `/agent/list` 检出 ✅
- `<2>` 首次 `GET /agent/unload?className=...JettyHandlerAgentInjector` → **`className not found`**（同一时刻 list 可见、`?cmd=id` 回显 `uid=0(root)`）；第二次调用 → `success`
- `<3>` `<4>` 复查干净、死亡确认通过

**结论**：环境二观察到的"list 可检出 + shell 存活 + unload not-found"窗口**不是 JDK17 特有**——JDK8/JRE 8 同样复现，确认为模型级问题，与目标 JDK 版本无关。

#### 3.3.5 环境四：WebLogic 12.2.1.3 · JDK 8u151 —— 样本⑥（redefine-only 盲点验证）

本样本（Weblogic404Memshell，开源项目源码本地构建）的 agentmain **不注册任何 ClassFileTransformer**，而是直接 `Instrumentation.redefineClasses()` 把 `weblogic.servlet.internal.HttpConnectionHandler#resolveServletContext(String)` 原地改写：请求 URI 等于 `/hello` 且带 `huahua` 请求头时走 `ProcessBuilder`（`/bin/bash -c` 或 `cmd.exe /c`）执行命令并回显。注入前 javap 确认 12.2.1.3 中目标方法存在。

**`<0>` 注入确认**：`GET /hello` + `huahua: id` → **`uid=1000(oracle) gid=1000(oracle) groups=1000(oracle)`**；不带头时该路径连接被重置，其余路径 `/notexist` 404、console 200（业务不受影响）。内存马真实可用。

**CLI 五步——检出失败、卸载失败、内存马存活**：

| 步骤         | 结果                                                         |
| ------------ | ------------------------------------------------------------ |
| <1> 检出     | `cli 214 -l agent` 仅 2 条 arthas 自噪音（`TransformerManager$2 false` / `$1 true`），**无任何 payload transformer** |
| jad 辅助     | `cli 214 -jad weblogic.servlet.internal.HttpConnectionHandler`：反编译的是**已加载（被 redefine 后）的字节码**，`resolveServletContext` 中完整可见注入代码（`getRequestURI().equals("/hello")` → `getHeader("huahua")` → `ProcessBuilder`）——jad 能"看到并证明"恶意改写，但该能力未接入检出/处置链路；`jad c` 亦能反编译 payload 自身类 |
| <2> 卸载     | `cli 214 -u agent c` → `[!] className not found`；`cli 214 -u agent weblogic.servlet.internal.HttpConnectionHandler` → `[!] className not found` |
| <3> 复查     | `agent (0)`（仍无 payload transformer）                      |
| <4> 死亡确认 | `GET /hello` + `huahua: id` → **仍回显 `uid=1000(oracle)...` —— 内存马存活，查杀失败** |

**Web 五步——与 CLI 完全一致**：`connect?pid=214` → `success`；`/agent/list` 仅自噪音；`/agent/unload?className=c` 与 `?className=weblogic.servlet.internal.HttpConnectionHandler` 均 `className not found`；复查无变化；死亡确认**存活**。

**结论**：KMBA 的检出模型完全建立在"枚举已注册的 ClassFileTransformer"之上，对**仅 redefineClasses 的 Agent 内存马**检不出、卸不掉，五步走完后内存马仍可执行命令——这是本次 Agent 专项最重要的功能性缺口，证据链完整（命令回显、两次 CLI unload、两次 Web unload、存活复验）。→ 针对该盲点的卸载回灌修复（transformer 未命中时按宿主类名 dump + redefine 回灌）**在 2026-08-31 交付 jar 上复测未通过：报 success 而内存马存活（假 success），详见 3.3.7**；修复 v2 终验通过见 3.3.8；**v4（2026-09-01）开发者删除 redefine 处置链：卸载如实报失败、内存马存活，v2/v3 的 8u151 能力消失，另新增 /agent/restore 假 success 面，见 3.3.10**；**v0.8 处置链重建后，同机制复刻装置（8u432 redefine 马）经 restore 强杀五步闭环（3.3.11）**。

#### 3.3.6 Agent 专项小结与发现

**六样本 × 双通道结果总表**：

| 样本                               | 环境                | 机制                         | <0>  | <1> 检出                         | <2> unload                                                   | <3> 复查   | <4> 死亡确认                           |
| ---------------------------------- | ------------------- | ---------------------------- | ---- | -------------------------------- | ------------------------------------------------------------ | ---------- | -------------------------------------- |
| ① MSP AgentFilterChain (javax/ASM) | Tomcat9 · JDK8      | addTransformer               | ✅    | ✅ CLI+Web                        | ✅ CLI+Web 一次成功                                           | ✅          | ✅                                      |
| ② Y4er MyAgent (Javassist)         | Tomcat9 · JDK8      | addTransformer               | ✅    | ✅ CLI+Web                        | ✅ CLI+Web 一次成功                                           | ✅          | ✅                                      |
| ③ 自建 HttpServlet 钩子            | Tomcat9 · JDK8      | addTransformer（同名双实例） | ✅    | ✅ CLI+Web                        | ✅（单次删一实例）                                            | ⚠️ 残留一个 | ✅                                      |
| ④ MSP jakarta                      | Tomcat10 · JDK17    | addTransformer               | ✅    | ✅ CLI+Web                        | ✅ CLI 一次 / ⚠️ Web 首次 not-found 后重试成功                 | ✅          | ✅                                      |
| ⑤ MSP Jetty Handler                | Solr · JRE8         | addTransformer               | ✅    | ✅ CLI+Web                        | ✅ CLI 一次 / ⚠️ Web 首次 not-found 后重试成功                 | ✅          | ✅                                      |
| ⑥ Weblogic404Memshell              | WebLogic · JDK8u151 | **仅 redefineClasses**       | ✅    | ❌ CLI+Web 均不可见（原理性盲区） | 迭代：v0.7 not found / v1 假 success / 修复 v2 双通道五步终验（CodeSource jar 回灌，3.3.8；8u432 上 Web 假 success，#31）/ v3 维持 → **v4 删除处置链：卸载如实报失败（CLI "OGNL execute failed" / Web "className not found"）、马存活；/agent/restore 报 success 马仍存活（3.3.10）** | ✅          | ⑥ v2 终验后 ✅（404）；v4 下 ❌ 存活；**v0.8 同机制复刻装置（8u432）restore 强杀后 ✅（3.3.11）** |

**发现清单**：

1. **❌ 功能盲点（缺陷）**：仅 `redefineClasses`、不注册 transformer 的 Agent 内存马完全绕过 KMBA 的检出与卸载模型（样本⑥，CLI+Web 双通道证据）。jad 能力本可见到 redefine 后的恶意字节码，但未参与检出判定。→ 针对该盲点的卸载回灌修复（按宿主类名 dump + redefine 还原）在 2026-08-31 交付 jar 上复测未通过：报 success 而内存马存活（假 success）（3.3.7）；修复 v2（CodeSource jar 原始字节回灌）在样本⑥真实容器（8u151）CLI+Web 双通道五步终验通过（3.3.8）；但 8u432 复测发现 v2 主路径 OGNL 兼容性缺陷（#31），第三交付 v3（称修复 #31）复测证实模板未改、未修复——**8u432 CLI 与 Web 双通道均假 success**（3.3.9）；检出侧维持沉默。**v4（`d22aad21…`）开发者删除 redefine 处置链（`AgentUtils` 整类移除）：卸载如实报失败（假 success 消除）但 v2/v3 的 8u151 处置能力随之消失，并新增 /agent/restore 假 success 面（3.3.10）**。**v0.8（`01501f79…`，2026-10-13）重建处置链：sc -d 确认类存在并取 code-source → KMBA 本进程读宿主磁盘 jar 原始字节（不经 OGNL 提取，#31 模板路线整体退出；不经 dump，3.3.8 结论沿用）→ 落盘临时目录 → arthas redefine 回灌；redefine 型经 `-u agent all`/Web「Agent类强杀」强杀五步闭环（8u432 实测），假 success 消除（3.3.11）。**
2. **⚠️ 失败窗口 / 列表残留（缺陷）**：Web 端 `/agent/unload` 的"逐命令独立枚举 `instances[i]`"模型存在顺序漂移——上一轮表现为"list 可检出 + shell 存活 + unload `className not found`"偶发失败窗口（样本④ JDK17 与样本⑤ JRE8 双重复现）；2026-08-31 交付 jar 复测以第二张面孔复现：**单次 unload 报 success、钩子已死亡，但列表残留 1 条 transformer 条目，需第二次 unload 才清零**（同机制样本③复跑 5/5 复现，web 日志地面真值：unload 循环内 `instances[3]` 返回 removed、随后 `/agent/list` 在 `instances[6]` 才探到该条目——漂移发生在同一次卸载调用的多条命令之间）。根因同一：`instances.length` 计数 + 逐 index 独立 vmtool 探测的模式与 arthas 堆扫描顺序漂移相错位；每次 loadAgent/attach 使 InstrumentationImpl 实例数增长加剧漂移。建议 unload 循环内重新查询实例（或按对象标识定位）消除跨命令错位。
3. **⚠️ 卸载语义（观察）**：同名多实例场景的清除语义两组件不同步——listener 组件在交付 jar 上已改进为单次最多删 10 份（12 副本实测恰删 10、余 2 需二次调用，消息不披露剩余数）；agent transformer 组件单实例场景仍存在列表残留（见上一条）。死亡判定不受影响。
4. **✅ 正向**：检出模型不依赖样本实现——ASM/Javassist、不同类名/包名/伪装类、javax/jakarta、Tomcat/Jetty 不同插桩目标，凡注册 transformer 均可检出并卸载（2026-08-31 交付 jar 以样本③复跑 6 轮 + 冒烟确认全链）。
5. **✅ 正向**：JDK17 模块系统下的 Unsafe + 接口反射代码路径完整可用，且对 `InaccessibleObjectException` 有自动重试链（strict false / unsafe true / OGNL 补丁 → JDK9 模板）；Util/ArthasController 与上一轮字节码一致，结论合并。
6. **✅ 正向**：restoreBytecode 生效——unload 后被 retransform 的宿主类（ApplicationFilterChain / HttpServlet / ServletHandler）命令执行能力随之消失，无需重启进程（样本③复跑中 `<4>` uid 回显 1→0 逐轮验证）。
7. **ℹ️ 可用性**：Web 模式必须先 `/arthas/connect?pid=<pid>`，否则 `/agent/list` 返回 `[]`（200），易误判为"未检出"；`-l agent` 中的 arthas 自噪音（TransformerManager$1/$2）条目可解释但需使用者自行辨认；**另见 #23——WS 会话断开后所有端点静默返回 `[]`，与"未 connect"同样表现为空列表**。

#### 3.3.7 redefine 型卸载回灌修复——交付 jar 复测未通过（假 success）（2026-08-31 两次实测）

针对 3.3.5 确认的 P0 盲点（问题清单 #9），开发者交付了带卸载回灌路径的 jar（`769f37da…`）。本节记录该修复在**交付 jar** 上的复测全过程与判定：**CLI 与 Web 双通道均报 success，但行为探针持续触发——内存马未被清除（假 success），复测不通过**。

**修复设计**（`Agent.java`，CLI 与 Web 同一实现，单点生效；字节码核对确认存在于交付 jar）：

- `unload(className)` 在 transformer 路径未命中（双 manager 均无记录）时转入 `unloadRedefineTarget`：仅当 className 为内置宿主类清单成员时，`dump <宿主类>`（从输出 LOCATION 列解析文件路径）→ `redefine <dump 文件>` 逐文件回灌，用取回的字节覆盖被改写的运行时类
- `dumpTargetClasses` 增补 `weblogic.servlet.internal.HttpConnectionHandler`（样本⑥宿主，原清单无此类）
- 清单门控原因：对任意传入类盲还原会把"实例顺序漂移漏检的 transformer 型 not-found"（问题 #10）误报成 success（宿主未还原、马仍在）

**复测环境**：全新容器（Tomcat 9.0.97 · OpenJDK 1.8.0_432），等机制探针样本（agentmain 仅 `redefineClasses` 改写 `ApplicationFilterChain#internalDoFilter` 插入行为探针，模拟样本⑥"仅 redefine、不注册 transformer"机制；探针源码存档见附录）。

**复测一：CLI 通道** —— `<1>` `-l agent` 仅 2 条 arthas 自噪音（检出沉默，与 3.3.5 一致）→ `<2>` `cli 1 -u agent org.apache.catalina.core.ApplicationFilterChain`（白名单内宿主类）→ **`[+] unload success`** → `<4>` 行为探针计数 **8 → 14**（卸载后探针仍持续触发，内存马存活）。

**复测二：Web 通道** —— `/arthas/connect?pid=1` → `/agent/unload?className=org.apache.catalina.core.ApplicationFilterChain` → **`success`** → 探针计数 **16 → 22**（同样存活）。

**根因取证（dump 字节比对）**：

| #    | 事实                                                         | 证据                                                         |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 1    | arthas `dump` 在 8u432 上返回 JVM **当前运行态字节**（类被 redefine 后即被改写后的字节），而非类加载基准字节 | 宿主类被探针改写后 `dump` 输出 md5 `5435656e…`（8431 B），`javap` 可见 `__probe__` ×1；磁盘原始类 md5 `1d914752…`（7863 B）、`javap` `__probe__` ×0 |
| 2    | 因此修复的回灌链 `dump → redefine <dump 文件>` 实际是"取恶意字节 → 回灌恶意字节" | HotSpot 对等值字节跳过实质变更，arthas 仍报 `redefine success`——整条链无害化空转，KMBA 输出 success |
| 3    | jar 条目替换不影响 dump 输出（dump ≠ CodeSource jar 读取）   | catalina.jar 内 `ApplicationFilterChain.class` 换为恶意字节后 dump 仍输出 JVM 内字节（上一轮实验，结论保持） |
| 4    | redefineClasses 不更新 retransform 基准（8u432）             | redefine 恶意字节后，retransformClasses 交给 canRetransform transformer 的仍是原始 7863 字节（上一轮实验，结论保持） |
| 5    | 原修复机制（restoreBytecode 逐类 dump）对 redefine 型无还原效果 | 恶意字节存活状态下执行 dump，探针继续触发（上一轮实验，结论保持） |

**对照实验（机制方向可行性验证）**：用**磁盘原始类字节**（`1d914752…`，7863 B）对运行态宿主类手工执行 `redefine <原始 class 文件>` → arthas 报 `redefine success, size: 1` → 探针计数**恒定不再增长（死亡）**。即：**若在卸载时能取回类加载基准字节（例如从 CodeSource jar 读取类文件、或缓存类加载原始字节），回灌机制本身有效**——当前实现取字节的位置（unload 时刻的 dump）在"类已被改写"的前提下必然取到改写后字节，前提不成立。

**对上一轮受控实验结论的修订**：上一轮"redefine 回灌有效（多次复现）"结论在"dump 发生在被改写之前 / dump 返回原始字节"的前提下成立；交付 jar 的实现路径（unload 时刻 dump）不满足该前提，故复测失败。其余 4 行结论（dump 非 jar 读取、retransform 基准不更新、retransform 回灌无效、restoreBytecode 对 redefine 型无效）维持。

**回归确认**：非清单类名（`com.example.Nope`）→ `[!] className not found`；清单内但 JVM 未加载的宿主类（`weblogic.servlet.internal.HttpConnectionHandler`）→ `[!] className not found`——负语义如实；transformer 型 `removed` 分支与 restoreBytecode 路径不受影响（transformer 型全链复跑见 3.3.6 与测试日志）。

**边界与决策记录（更新）**：

- **检出侧不增加 dump-vs-jar 比对信号**（经评估决策维持）：dump/jad 对 redefine 的可见性是 JVM 版本相关行为——8u151 类 JVM 缓存字节被 redefine 更新（3.3.5 中 jad 可见恶意代码）、8u432 实测 dump 返回改写后字节（本节取证 1）；比对信号在后者恒沉默（比对结果恒等值），且 jar 热替换（类未重载）场景会误报。8u151 类 JVM 上可用 `jad <宿主类名>` 手工确认。（2026-08-31 v2 复测再修订：8u151 上回灌生效后 jad 仍显示注入代码，与运行时行为不一致——jad 字节来源与 JVM 版本强相关、双向不可靠，**jad 不可作回灌 ground truth**，判定以行为探针 + 提取物 md5 为准，见 3.3.8。）
- **修复方向可行、实现取字节位置错误**：应改为从宿主类 CodeSource jar 读取类文件字节，或在类加载早期缓存原始字节；清单门控设计维持。
- 若目标类同时被合法 APM agent 插桩，回灌会打掉其插桩（至其下次 retransform）；清单外宿主类不回灌，需扩清单支持。

#### 3.3.8 redefine 型卸载回灌修复 v2（CodeSource jar 原始字节）——双环境实测：8u151 双通道通过、8u432 暴露主路径兼容性缺陷 #31（2026-08-31）

按 3.3.7 边界记录的修复方向重写取字节位置后，开发者交付 v2 jar（`e4d7cb1f…`，39,085,396 B）。本节为该 jar 的实测记录（完整日志 `logs/agent-0831-v2-verify.md`），覆盖两个环境 × 双通道：

- **环境一**：WebLogic 12.2.1.3（Oracle JDK 1.8.0_151）容器，样本⑥原始 jar 经 AttachMain attach 注入——即 v0.7 首测失败（3.3.5）的同一镜像同一注入方式
- **环境二**：Tomcat 9.0.97（OpenJDK 1.8.0_432）容器，等机制探针样本（agentmain 仅 `redefineClasses` 改写 `ApplicationFilterChain#internalDoFilter` 插入行为探针，模拟样本⑥"仅 redefine、不注册 transformer"机制；首次 attach 因目标类未加载报 `target class not loaded` 整轮作废，加业务预热后重做——装置教训见测试日志）

**v2 实现**（新 jar `Agent.class` 字节码核验）：`unloadRedefineTarget` 主路径 = 白名单校验（30 项宿主类清单，含 `weblogic.servlet.internal.HttpConnectionHandler`）→ `sc -d <宿主类>` 解析 `code-source`（8u151 实测定位 `/u01/oracle/wlserver/modules/com.oracle.weblogic.servlet.jar`）→ vmtool OGNL 从宿主 jar 提取原始 .class 落盘 `kmba_orig_<SimpleName>.class`（`JarFile.getJarEntry` → `@sun.misc.IOUtils@readFully(is,-1,true)` → `FileOutputStream`）→ `redefine <提取文件>`；提取失败 → 回退 dump 基准路径（`dump` → redefine classdump 文件）。

**（A）WebLogic 8u151 —— CLI+Web 双通道五步终验通过**

| 步骤         | CLI 通道实测                                                 | Web 通道实测                                                 |
| ------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| <0> 注入确认 | `huahua: id` → `uid=1000(oracle)`                            | 重新注入后同                                                 |
| <1> 枚举     | `-l agent` 仅 2 条 arthas 自噪声（检出盲区符合预期，3.3.5）  | `/agent/list` 同                                             |
| <2> 卸载     | `[+] unload success`                                         | 返回 `success`                                               |
| 主路径证据   | `/tmp/kmba_orig_HttpConnectionHandler.class` 生成（24,652 B），**md5 `57a661e0…` == 从 `com.oracle.weblogic.servlet.jar` 解包的原始类 md5，逐字节一致** | web 日志全链留痕：OGNL 提取命令 → `extracted=/tmp/kmba_orig_HttpConnectionHandler.class` → `redefine /tmp/kmba_orig_…` → `redefine success, size: 1, classes:weblogic.servlet.internal.HttpConnectionHandler` |
| <3> 复查     | 同前置，无残留                                               | 同                                                           |
| <4> 死亡确认 | `huahua` 请求 404 ×2、uid 行 0                               | probe1/probe2 均 404、uid 行 0                               |
| 负例/业务    | `com.example.Nope` → `[!] className not found`；控制台 200   | 同                                                           |

**（B）Tomcat 8u432 —— 主路径被 JDK API 兼容性打断；CLI 经回退侥幸通过、Web 假 success（新发现 #31）**

CLI：探针注入存活（计数 0→2）→ `-u agent org.apache.catalina.core.ApplicationFilterChain` → `[+] unload success` → 探针 2→2→2（死亡）→ 业务 200 → 负例如实。但 `/tmp/kmba_orig_*.class` **未生成**——主路径未走通，实际执行 dump 回退；该会话回退取到的 classdump 为基线字节（7863 B、md5 `d0a5140a…`、`__probe__`×0）→ redefine 后探针死亡。**行为通过属"回退恰取基线字节"的侥幸，非主路径生效。**

Web：容器重启清态重注入（探针计数 4 存活）→ `/arthas/connect` success → `/agent/unload` 返回 `success` → **探针计数 4→6→8 持续触发（假 success，内存马存活）**。

根因链（web 日志取证）：

1. `sc -d` 输出含 `code-source /usr/local/tomcat/lib/catalina.jar`——CodeSource 定位正常；
2. OGNL 提取失败：`ognl.MethodFailedException: Method "readFully" failed for object class sun.misc.IOUtils [java.io.IOException: length cannot be negative: -1]`——**8u432 构建的 `sun.misc.IOUtils.readFully` 拒绝 -1 读全部哨兵值，OGNL 主路径在该 JDK 恒失败**（新问题 #31）；
3. 落入 dump 回退并报 `redefine success, size: 1`；但该次 dump 的 classdump 实测 **8431 B、md5 `2b7731e2…`、`__probe__`×1**——**Web 会话的 dump 取到被改写后的字节**，redefine 等于原样写回（no-op），工具报 success。

**dump 回退字节来源会话相关（对 3.3.7 取证 1 的修订）**：同一容器同一 JVM，CLI 会话 dump = 7863 B 基线（`d0a5140a…`）、Web 会话 dump = 8431 B 被改写字节（`2b7731e2…`）——3.3.7 "dump 返回 JVM 当前运行态字节"的表述应修订为"**dump 字节来源会话相关：有的会话取基线、有的取被改写字节**"（机制与 arthas retransform 捕获缓存相关，实证不可依赖）。

**汇总**：

| 环境  | 通道 | 主路径（CodeSource 提取） | 回灌实际来源               | 探针死亡 | 判定                     |
| ----- | ---- | ------------------------- | -------------------------- | -------- | ------------------------ |
| 8u151 | CLI  | 成功                      | 主路径（md5 逐字节一致）   | 是       | ✅ 通过                   |
| 8u151 | Web  | 成功（web 日志全链）      | 主路径                     | 是       | ✅ 通过                   |
| 8u432 | CLI  | 失败（readFully -1）      | dump 回退·基线字节（侥幸） | 是       | ◐ 通过（依赖不可靠回退） |
| 8u432 | Web  | 失败（同上）              | dump 回退·**被改写字节**   | **否**   | ❌ 假 success             |

**（C）判定与遗留**：

- v2 在样本⑥真实容器（8u151）上 redefine 型卸载回灌**双通道五步闭环**，回灌字节与磁盘原始逐字节一致——#9 的 8u151 场景闭环；
- 8u432 类 JDK 上 v2 主路径不可用（**#31**），且 dump 回退字节来源会话相关 → **8u432 Web 通道假 success 未闭环**（#9 状态：部分修复）；v3 轮复测证实 CLI 会话同样会取到被改写字节（假 success），并证实 #31 未修复（3.3.9）；
- **jad 不可作回灌 ground truth**：8u151 上 unload 后 `-jad` 仍显示注入代码（`huahua`/`ProcessBuilder`），而行为探针已死——jad 与运行时行为不一致；8u432 上 jad 恒显示基线。两个 JDK 方向相反，回灌判定以**行为探针 + 提取物 md5** 为准（3.3.7 边界记录"8u151 可用 jad 手工确认"据此修订）；
- transformer 型路径与负语义回归不受影响（双环境负例均如实 `className not found`）；#10 列表残留 1/1 复现未修复。

**修复方向建议（#31）**：OGNL 改用固定长度读取（`#je.getSize()`）或逐块循环读取，兼容拒绝 -1 哨兵的 JDK 构建；回灌后增加验证步骤（重取运行态字节与提取文件比对，不一致时报 "unverified" 而非 success）——宁可暴露不确定性，也不报假 success。

#### 3.3.9 redefine 型回灌 v3 复测——#31 未修复，8u432 双通道假 success（2026-08-31 第三次交付）

开发者交付第三版 jar（`a25cc5bf…`，39,087,533 B，称修复 #31），本节为该 jar 的实测记录（完整日志 `logs/agent-0831-v3-verify.md`）。环境与探针装置同 3.3.8（全新容器重建：Tomcat 9.0.97 / 8u432 `tc0831v3`，WebLogic 12.2.1.3 / 8u151 `agent-wl3`，样本⑥原始 jar）。

**静态核验**（`javap -p -c`）：v3 将 OGNL 模板外迁至 `AgentUtils`、`Agent.java` 瘦身（313 行）并新增 `restore()` 入口，但 **`extractOriginalFromCodeSource` 的提取模板与 v2 逐字节相同——`@sun.misc.IOUtils@readFully(#is,-1,true)` 原样保留**，无 `#je.getSize()` 固定长度、无逐块读取、无回灌后验证步骤。另有 transformer 卸载模板变更（`unloadAgentByVmtool` 去掉 `-l -1`，#10 方向）与 Windows 路径转义加固。

**实测结果**：

| 环境              | JDK   | 通道       | 主路径(CodeSource)                                           | 实际回灌来源                                                 | 探针死亡          | 判定             |
| ----------------- | ----- | ---------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ----------------- | ---------------- |
| Tomcat 9.0.97     | 8u432 | CLI（T1b） | 失败（零产物，`kmba_orig_*` 未生成）                         | dump 回退·**被改写字节**（8431 B，md5 `2b7731e2…`，`__probe__`×1） | 否（2→4→6）       | **❌ 假 success** |
| Tomcat 9.0.97     | 8u432 | Web（T2）  | 失败（web 日志 L53 提取命令含 readFully(-1)、L54 `ognl.MethodFailedException … length cannot be negative: -1`——与 v2 轮同一异常） | dump 回退·**被改写字节**                                     | 否（8→10→12）     | **❌ 假 success** |
| WebLogic 12.2.1.3 | 8u151 | CLI（WL1） | 成功（`kmba_orig_HttpConnectionHandler.class` 24,652 B，md5 `57a661e0…` == wlserver jar 解包原始类，逐字节一致） | 主路径                                                       | 是（404 / uid 0） | ✅ 通过           |
| WebLogic 12.2.1.3 | 8u151 | Web（WL2） | 成功（web 日志 L72-L76 全链：`code-source` → 提取 → `extracted=` → `redefine success`） | 主路径                                                       | 是（404 / uid 0） | ✅ 通过           |

**判定：#31 未修复**——静态（模板逐字节未变）、行为（8u432 双通道探针持续增长）、日志（同异常复现）三重证据一致。同时本轮新增一项实证：**dump 回退的不可靠性对 CLI 通道同样成立**——v2 轮 CLI 会话 dump 取到基线字节（侥幸通过，3.3.8 表中"◐"行），v3 轮 CLI 会话 dump 取到被改写字节（`2b7731e2…`，与 v2 轮 Web 会话 dump 的 md5 相同）→ 假 success。"回退字节来源会话相关"由此有了跨轮、跨通道的四点对照（v2 CLI=基线 / v2 Web=被改写 / v3 CLI=被改写 / v3 Web=被改写），任一会话均不可预测、不可依赖。

**回归确认**：8u151 双通道主路径完整走通（回灌字节与磁盘原始逐字节一致），v3 未破坏 8u151 既有能力；transformer 型路径正常（v3 模板变更无行为回归，T2r：检出 → unload success → uid 1→0），列表残留 1/1 复现（#10 维持）；负例如实拒绝；jad 不可作 ground truth 的结论维持（8u151 上回灌已生效仍显示注入代码）。另观察到 v3 CLI 各命令输出新增一行 `vmtool … ThorBootstrap |plaintext` 与 `[]` 噪音行（输出质量问题，不影响功能）。

#### 3.3.10 v4 实测——删除 redefine 处置链后的 Agent 功能全景（2026-09-01 第四次交付）

开发者交付第四版 jar（`d22aad21793306a9f60fef97647c7d84`，39,083,904 B），说明"把 Agent 内存马 redefine 相关部分删了，功能也改了一点点"。本节为该 jar 的 Agent 功能实测记录（完整日志 `logs/agent-0901-v4-verify.md`），覆盖 4 环境（Tomcat 9.0.97/8u432 `tc0831v4`、Tomcat 10.1.34/JDK17、Solr 8.11.0/Jetty 9.4.44/JRE 8u102、WebLogic 12.2.1.3/8u151）× CLI+Web 双通道 + redefine 等机制探针对照装置（磁盘原始 AFC `1d914752…` 7863 B 与历轮一致）。

**静态核验**（javap 全量核对）：

1. **redefine 处置链整体删除**：v2/v3 的 `com/kmba/Utils/AgentUtils.class`（`unloadRedefineTarget` / `extractOriginalFromCodeSource` / `redefineDumpedBaseline`）整类移除；`Agent.class` 常量池无 `kmba_orig`、无 `JarFile`、无 redefine 字符串——#31 指向的 `readFully(-1)` 模板随类删除而失效（属功能删除，非修复）。
2. **`Agent.unload(className)` 新流程**：逐实例 OGNL removeTransformer 循环 → `removedCnt>0` → restoreBytecode（29 类 dump 循环）→ "success"；`failCnt>0` → "OGNL execute failed"；否则 "className not found"。
3. **新增 `Agent.restore(String[])`**：`restoreBytecode` 仅执行 **`dump <类>`**（**无 redefine**）；web 端点 `/agent/restore`（参数 `classNames`，空参 = 29 类全量清单，v3 的 30 项去掉 `weblogic.servlet.internal.HttpConnectionHandler`）；**全 jar 无任何 `Agent.restore` 调用方——restore 仅 web 可达**（实测 `cli -restore` 打印 usage）。
4. **新增 `VmtoolUtils`**（`@RestController`，`GET /vmtool/get?className=X&hash=Y`）与 CLI 命令 `-vmtool <className> [classLoaderHash]`：单实例 / 多 classloader 消歧 / 404 三态。

**（A）transformer 型能力保持（4 环境五步全过）**

| 环境                       | JDK       | 样本 / 通道                             | 结果                                                         |
| -------------------------- | --------- | --------------------------------------- | ------------------------------------------------------------ |
| Tomcat 9.0.97              | 8u432     | ① MSP AgentFilterChain · CLI            | ✅ 检出（jad：`TARGET_CLASS=ApplicationFilterChain`、`addTransformer(…, true)`）→ `[+] unload success` → 复查无残留 → 死亡 0；负例 `[!] className not found` |
| Tomcat 9.0.97              | 8u432     | ③ self HttpServletTransformer · CLI+Web | ✅ 双通道五步过（jad `SHELL_SRC` 完整可见）；**#10 残留 1/1 双通道复现**——CLI 二次卸载 `[!] unload failed: OGNL execute failed`、Web 二次卸载 success 清零（同 JVM 同目标，两通道消息不一致） |
| Tomcat 10.1.34             | JDK17     | ④ MSP jakarta · CLI                     | ✅ 五步全过，复查 `agent (0)`，业务页面 200                   |
| Solr 8.11.0 / Jetty 9.4.44 | JRE 8u102 | ⑤ MSP Jetty Handler · CLI               | ✅ 五步全过（jad：6 项目标类清单、伪装类 `OAuthAgentHandler`、方法 `handle`），复查无 injector 残留，Solr 接口 200 |

**（B）redefine 型：假 success 消除、诚实拒绝——但处置能力清零**

等机制探针（redefine-only，改写 `ApplicationFilterChain#internalDoFilter` 插行为探针）CLI+Web 双通道：`-l agent` / `/agent/list` 检出沉默（盲区维持）→ unload 双通道**如实报失败**（CLI `[!] unload failed: OGNL execute failed` / Web `OGNL execute failed`）→ 探针计数 2→4→6 持续触发（马存活）→ `kmba_orig_*` 零产物。**对照历史：v1/v2/v3 在同场景均报 success 而马存活**——假 success 类缺陷在该场景消除。

但样本⑥（WebLogic 8u151 真实容器）实测：Web `/agent/unload` → `className not found`、CLI → `[!] unload failed: OGNL execute failed`，huahua 请求仍回显 uid（马存活）——**v3 在该环境双通道真实闭环的 CodeSource 主路径能力随代码删除消失，redefine 型从"8u151 可杀、8u432 误导"退化为"全环境无处置"（净效果：从误导变为无能）**。同 JVM 同目标 CLI 与 Web 失败消息不一致，且失败消息随会话状态漂移（`className not found` 与 `OGNL execute failed` 并存，#34；OGNL 失败根因为 arthas 侧 `java.lang.ArrayIndexOutOfBoundsException`）。

**（C）新增 `/agent/restore`：dump-only，对 redefine 污染报 success 不生效——新假 success 面（#32）**

| 场景                                                         | 实测                                                         | 判定                                               |
| ------------------------------------------------------------ | ------------------------------------------------------------ | -------------------------------------------------- |
| Tomcat 8u432 **清态对照轮**（容器重启清态 → 仅注入探针（计数 10 存活）→ connect → **首次** POST `/agent/restore`） | 返回 `success`；探针 10→12→14 持续触发；dump 文件 8431 B、md5 `2b7731e2a40144f2fb1bb922bc225db8`、`__probe__`×1（被改写字节，与 v2 轮 Web / v3 轮 CLI/Web 三处历史污染 dump 完全相同） | **❌ 假 success（清态必然形态）**                   |
| 同容器**卸载历史会话**（T1/T2 卸载的 29 类 dump 循环之后）   | `/agent/restore` → `success`；探针停止触发（死）；dump 7863 B、md5 `6430d8b2…`、`__probe__`×0 | ◐ 探针死亡，但为前序 dump 会话效应的偶然，不可依赖 |
| WebLogic 8u151（样本⑥宿主类）                                | `/agent/restore?classNames=…HttpConnectionHandler` → `success` → huahua 仍回显 uid（马存活） | **❌ 假 success**                                   |
| 负例                                                         | `com.example.Nope` / 未加载的 `jakarta.servlet.http.HttpServlet` → `className not found` | ✅ 诚实                                             |

判定：`/agent/restore` 仅把**当前运行态字节** dump 落盘（dump-only 设计决定其无法主动恢复任何字节），"success" 仅表示 dump 完成；对 redefine 污染**清态下必为假 success**，历史会话下"生效"纯属前序操作的会话效应。命名（restore）与语义（dump 落盘）不符，且 web-only 无 CLI 命令。

**（D）新增 vmtool 双通道：功能可用，bootstrap 加载类为边界（#33）**

| 用例                | CLI `-vmtool`                                                | Web `/vmtool/get`                                            |
| ------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 多 classloader 消歧 | `javax.servlet.Servlet` → 9 实例逐条 classLoaderHash 枚举 + 重跑提示 | `{"type":"multi","count":9,"results":[…]}` ✅                 |
| 单实例 / 带 hash    | `org.apache.jsp.index_jsp` → 实例字段转储 ✅                  | 同 ✅                                                         |
| bootstrap 加载类    | `java.lang.String` → `ClassLoaderHash: null` → `Can not find classloader with hashCode: null.` | `sun.instrument.InstrumentationImpl` → **`{"type":"single","classLoaderHash":"null","data":"Can not find…"}`——错误文本被 type=single 包裹，程序化消费无法识别失败** |
| 负例                | `com.example.Nope` → `target class not found`（如实）        | `{"type":"404","message":"Class not found: …"}`（如实）      |
| 退出码              | 错误场景 exit code 恒 0（#21 同族）                          | —                                                            |

**（E）汇总判定**

1. transformer 型五步在 4 环境全部通过——删除 redefine 代码未破坏既有 transformer 能力；
2. redefine 型：假 success 消除（诚实拒绝）但处置能力清零，8u151 真实能力为删除代价——redefine 型整体未闭环；
3. `/agent/restore` 为新假 success 面（#32）：dump-only 无法恢复 redefine 污染却报 success，清态 8u432 与 8u151 双环境实锤；
4. vmtool 双通道可用（多实例消歧 / 单实例 / 负例），bootstrap 加载类不可用且 Web 端错误包裹为 type=single（#33）；
5. 历史问题：#10 残留 1/1×2 通道维持；失败消息漂移为新观察（#34）；#31 随 `AgentUtils` 删除而失效（非修复）；#23/#21 未触及场景未复测。

#### 3.3.11 v0.8 实测——redefine 处置链重建 + 强杀通道落地（2026-10-13 第五次交付）

**背景**：v4 删除处置链后 redefine 型陷入"查出即无解"（3.3.10）。本版以"不依赖 OGNL 提取、不依赖 dump"的磁盘字节回灌路线重建处置链，新增全清单强杀通道，并同批带入 `-l all` 卡死修复。完整日志：`logs/agent-1013-codesource-redefine-restore.md`（处置链调试）、`logs/agent-1013-agentcrack-redefine-memshell.md`（双马五步终验）。

**1. 交付物与实测物一致性**

- 发布构建 `KMBA-0.0.1-SNAPSHOT.jar`：MD5 `01501f79ce33c1d25539cbc020b563d7`（39,087,753 B），banner 0.8。
- 功能实测在 KMBA0 开发树同源构建 `1193cb3a…`（39,090,567 B）上完成；对两构建逐一比对 Agent.class / Dict.class / CliHandler.class / CompHandler.class / config.js / index.html / index.js **逐字节一致**，差异仅两处且不影响功能结论：① 开发树独有的 TongWeb.java（#26 暂缓，未被任何代码引用，不参与行为）② banner 版本字符串（实测构建为 0.7，发布构建为 0.8）。

**2. 处置链重构（Agent.java `restoreBytecode`）**

```
sc -d <类> 确认存在（无 class-info 即如实失败）
  → extractCodeSource：按 class-info 块过滤、className 精确匹配，取 code-source（宿主 jar 磁盘路径）
  → readOriginalBytes → readClassFromCodeSource：裸 jar 路径 / file: / 解压目录 / Spring Boot 嵌套 jar:file:…!/…!/（%20 解码；readJarEntry 以 JarInputStream 流式读取）
  → writeTempClass：落盘 Dict.TMP_DIR（Linux /tmp/kmba，Windows %TMP%\kmba）
  → arthas redefine 回灌 → 输出含 error 即如实失败
```

与 v2/v3 路线的关系：**不经 OGNL**（v2/v3 的 #31 `readFully(-1)` 模板问题整体退出）、**不经 dump**（dump 字节来源会话相关、不可作回灌依据的结论沿用 3.3.8）。

**3. sc -d 子类污染分块过滤**

`sc -d` 默认附带已加载子类，结果含目标类 + 全部子类块。`extractCodeSource` 按 class-info 块的 className 精确匹配取目标的 code-source。实战样本：`sc -d javax.servlet.http.HttpServlet` 返回 5 块（HttpServlet / DefaultServlet / HttpJspBase / JspServlet / org.apache.jsp.index_jsp），过滤仅取 servlet-api.jar 的 HttpServlet——8u432 实测通过。

**4. 强杀通道与行为矩阵（8u432 双马装置：transformer 马 MSP `TomcatFilterChainAgentInjector` + agentcrack 式 redefine 马）**

| 入口 | transformer 马 | redefine 马 |
| --- | --- | --- |
| `-u agent <className>` / Web 卸载 | ✅ 卸载（23.6s） | ❌ 如实报 className not found |
| `-u agent all`（CLI） / Web「Agent类强杀」（`/agent/restore`） | ——（应先用上行卸载） | ✅ 唯一处置路径（restore 强杀） |

`-l agent` 对 redefine 马维持不可见（transformer 枚举原理性盲区，原则性维持，见四）；`-l all` 可见 InstrumentationImpl 实例计数。

**5. 五步法实测（两马各验）**

| 步骤 | transformer 马 | redefine 马 |
| --- | --- | --- |
| <0> 注入后命令执行 | ✅ | ✅ |
| <1> list 发现 | ✅ | ❌（检出盲区，维持） |
| <2> unload 卸载 | ✅（23.6s） | ❌（诚实拒绝）→ 补强杀：restore success 16.6s |
| <3> list 确认卸载 | ✅ | ——（同 <1>；强杀后 InstrumentationImpl 计数不变属正常） |
| <4> 命令失效 | ✅ | ✅（强杀后 `?cmd=id` 无回显、探针 +0、Web 恢复欢迎页） |

- 探针计数口径：`[REDEFINED-PROBE] uid=probe`（`?__probe__=1`）与 `[REDEFINED-PROBE] cmd executed:`（`?cmd=`）两类行并存，判断钩子生死必须只数 `uid=probe` 或按 `docker logs -t` 时间归属——本轮曾因混数出现 8→10 假异常，复盘记录于测试日志 §5。
- 注入后立即 list 可能仅见 TransformerManager 噪声（attach→list 注册竞态），1–2s 后复查正常，属测试装置注意项而非工具缺陷。

**6. `-l all` 卡死修复（首次进入发布树）**

根因：`Util.getClassLoaderByVmtool` 模板缺收尾单引号（模板替换时丢失），arthas 终端把未闭合引号当续行输入 → 无输出 → KMBA 60s 轮询超时级联（后续全部命令各烧 60s）。修复后 8u432 双马装置全量 `-l all` 实测 **47.7s** 正常返回。

**7. 失败文案中性化（#34 部分）**

restore 失败提示 "restore failed" → "bytecode restore failed"；CLI 提示行补处置口径（`能查出的马先卸载(-u agent <className>)再强杀一次(-u agent all)，查不出的马直接强杀(-u agent all)`）。同 JVM 同目标消息随会话/通道漂移本身维持观察（#34）。

**8. 装置备注与开放观察**

- 注入装置：`RedefineMakerCmd.java`（KMBA0 开发树 kmbatest/poc，-agent redeliver 前置），替代 v2/v3 的 OGNL 注入面。
- 开放观察（未修复）：① agent shutdown 副作用——kill agent 进程后宿主类字节可能回退为运行态污染字节（8u151 观察），故处置完成后不要重启 agent 进程；② `sendStopToAgent` / `forceStop` 停止路径不对称。

**9. 小结**

1. redefine 型处置闭环恢复：restore 真实回灌（非 dump、非 OGNL），8u432 双马五步实测强杀后命令失效，#32 假 success 消除（修复确认）；
2. 检出盲区维持（机制性，#9 检出面）；transformer 马卸载路径无回归；
3. #31 路线（OGNL 提取）随重构整体弃用；`-l all` 卡死修复实测带入；
4. #34 文案中性化带入（漂移本身维持）；#10/#11/#16/#21 未触及场景未复测、维持。

---

### 3.4 非 Agent 组件代表性证据实录

**（1）Tomcat 10.1.34 + JDK 17（高版本 JDK 适配）** —— Manager 弱口令部署 war → JSP 反射注入 jakarta Filter：

```
$ java -jar KMBA.jar cli 1 -l filter
  filter (2)
  /*  kmbaTestFilter        org.apache.jsp.inject_jsp$1
  /*  Tomcat WebSocket (JSR356) Filter  org.apache.tomcat.websocket.server.WsFilter
$ java -jar KMBA.jar cli 1 -jad org.apache.jsp.inject_jsp$1
  （CFR 源码）String cmd = req.getParameter("cmd"); Runtime.getRuntime().exec(cmd)...
$ ... -u filter kmbaTestFilter   → [+] unload success
$ ... -l filter                  → filter (1)，仅剩 WsFilter
$ GET /kmbaapp/anything?cmd=id   → HTTP 404，无回显；GET /kmbaapp/ 正常业务
```

Web 端 `/filter/list` → jad（jakarta 头声明反编译正常）→ unload `success` → 复查干净 → `/arthas/stop`，五步全过。

**（2）Shiro-550 → Filter（含 insertPoint 陷阱发现）** —— RememberMe 默认 AES key → web-chains 反序列化 defineClass。反编译 tomcat-embed-core-9.0.29 证实 `StandardContext$ContextFilterMaps.addBefore()` 并非插到位置 0 而是插到 `insertPoint` 所指位置（本环境 4 个 Spring Boot 程序化 filter → `insertPoint=4`，内存马被 shiroFilter 挡在后面）；反射置 0 后生效：

```
$ java -jar KMBA.jar cli 1 -l filter
  filter (9): kmbaShiroFilter（4 条同名 map，活跃条目列首位）+ characterEncoding/formContent/requestContext/shiro/WsFilter
$ ... -jad org.apache.shiro.coyote.ser.std.StdKeySerializers2b0fb...
  （源码）getParameter("cmd") → Runtime.getRuntime().exec(...) → 逐行回显
$ ... -u filter kmbaShiroFilter → [+] unload success
$ ... -l filter                 → filter (5)，4 条同名 map 全部清除
$ GET /kmba-probe?cmd=id        → HTTP 302（回到 Shiro 认证重定向），回显消失
```

**（3）Solr log4j2 → Jetty Filter（含异步日志线程陷阱发现）** —— `${jndi:}` 替换发生在 log4j2 AsyncLoggerConfig + LMAX Disruptor 的**后台线程**而非 qtp 请求线程，ThreadLocal 型取请求上下文恒为 null（容器日志完整调用栈：`JndiLookup.lookup ← StrSubstitutor.replace ← ... ← BatchEventProcessor.run`）。改用静态对象图注册（遍历 `Thread.getThreads()` 取 CCL 为 WebAppClassLoader 的线程 → `getContext().getServletHandler().addFilterWithMapping(...)`）后注册成功（`[JShell] registered via webapp-classloader-walk ctx=/solr ×2`）。随后：

```
$ cli 1 -l jettyFilter → jettyFilter (2): SolrRequestFilter(合法) + kmbaJettyFilter(CreatorProperty4f88...)
$ cli 1 -jad ... → 源码含 getParameter("cmd") → Runtime.exec 回显 + addFilterWithMapping("/*")
$ cli 1 -u jettyFilter kmbaJettyFilter → [+] unload success
$ cli 1 -l jettyFilter → jettyFilter (1)
$ GET /solr/?cmd=id    → 200 正常 Solr 管理页（无执行输出），[JShell]F 计数 2 → 2（零新增）
```

Web 端正确组件端点 `/JettyFilter/*` 五步全过（list 含两条、unload success、复查仅剩 SolrRequestFilter、死亡确认通过）。

**（4）WebLogic CVE-2020-14882/14883 → weblogic Filter** —— MVEL + URLClassLoader 注册路径经字节码逆向取证（`FilterManager.registerFilter` 失败静默返回 → 绕开为直构 package-private `FilterWrapper` 5 参构造 + `addMappingForUrlPatterns("/*")`；`FilterWrapper.setFilter` 三选一语义要求 className 传 null；请求线程定位链 `ExecuteThread.getCurrentWork()` → `WlsRequestExecutor` → `connectionHandler` → `getServletRequest().getContext()`）：

```
容器日志: [WShell] loadFilter=true / reg=true / patternListSize=14→15→16 / ctx=/console
$ cli 214 -l weblogicFilter
  weblogicFilter (3): ParamFilter(合法) / kmbaWlFilter → WShell / HTMLContentFilter(合法)
$ cli 214 -jad WShell   → （registerSelf 全文：FilterWrapper 直构 + addMappingForUrlPatterns("/*") + Runtime.exec 回显）
$ cli 214 -u weblogicFilter kmbaWlFilter → [+] unload success
$ cli 214 -l weblogicFilter → weblogicFilter (2)，仅剩两个合法 filter
$ curl 'http://127.0.0.1:.../console/css/aftercheck1.css?cmd=id' ×3 → 全部 404，无回显，[WShell]F 计数不变
```

Web 端 `/WeblogicFilter/*` 五步全过；KMBA 卸载 OGNL 操作的 `filters` Map + `filterPatternList` 与本环境逆向出的 FilterManager 内部结构互证。

**（5）fastjson 1.2.24 JNDI → Filter（低版本 JDK 8u102 JRE）** —— 顶层 `{"@type":...}` 报 `type not match`（JavaBeanDeserializer 期望类型校验），改嵌套对象绕过；LDAP 查询须带 `/basic/` 前缀（反编译 LdapServer 证实）：

```
JNDIExploit 日志: Receive ClassRequest: TomcatMemshellTemplate1.class → Response Code: 200
$ cli 1 -l filter → filter (6)：5 条 Spring Boot 正常 filter + TomcatDynamicFilter-1 / com.feihong.ldap.template.DynamicFilterTemplate
$ cli 1 -jad com.feihong.ldap.template.DynamicFilterTemplate
  （源码）private String basicCmdShellPwd = "pass"; Runtime.getRuntime().exec(cmds)
$ cli 1 -u filter TomcatDynamicFilter-1 → [+] unload success
$ cli 1 -l filter → filter (5)
$ POST /anything type=basic&pass=id → HTTP 404（命令不再执行）
```

**（6）Tomcat 9.0.97 组件类型矩阵** —— listener / servlet / valve / websocket(upgrade) / executor / timer / thread 七类五步通过（类型边界见 3.1 状态列）；另完成**共存测试**与**哥斯拉协议马**（见 3.5）。

---

### 3.5 多马共存与协议马（真实环境复杂度验证）

1. **Tomcat 9.0.97 六马共存** ✅: Filter(命令) + Filter(哥斯拉) + Listener + Servlet + Valve + Timer
   同时注入 → KMBA 全量可见 → 逐个精准卸载（每步验证"只清目标、其余存活"）→ 最终全清、生产存活。
2. **Gateway 3.1.0 四马共存** ✅: GMemShell(HandlerMethod) + WebFilter + NettyHandler + HandlerFunction
   → 4→3→2→1→0 逐步复核 → ALL-MEMSHELLS-REMOVED、生产存活。
3. **哥斯拉协议马完整五步** ✅: MemShellParty Godzilla Filter（pass/key/md5(pass+key)[0:16]/自定义头校验）
   ——MiniG3 客户端适配三要素后命令执行 uid=0、list/unload/复查/失效全过
   （踩坑: 生成器随机化 key 忽略配置值，需从马类 ConstantValue 提取）。
4. **共存干扰观察**: Listener/Valve 型命令马响应所有带 cmd 参数的请求（全路径拦截），
   多马共存时对其它马的 HTTP 层验证产生干扰——真实环境复杂度实证；KMBA 层枚举/卸载不受影响。

### 3.6 struts2Action 组件（round3 能力边界闭环）

round3 曾记录"Struts2 Action 内存马无检测组件"（Action 马注册于 `runtimeConfiguration.namespaceActionConfigs`，当时 22 类组件均不可见，只能 jad 反编译确认）。v0.7 新增 struts2Action 组件，本次在 Struts 2.3.34 showcase（Tomcat 8.5.33，S2-057 入口，MemShellParty struts2-action 注入器经 OGNL 请求线程注册，马类 `KmbaS2Action`，urlPath `/kmbaaction`，参数 `cmd`）完成五步验证：

**CLI 五步 ✅（全部通过）**

| 步骤 | 操作                                    | 结果                                                         |
| ---- | --------------------------------------- | ------------------------------------------------------------ |
| <0>  | 注入后 `curl /kmbaaction.action?cmd=id` | ✓ `uid=0(root)`                                              |
| <1>  | `cli 1 -l struts2Action`                | ✓ 188 条（187 条 showcase 合法 ActionConfig + 1 条恶意）：`204:  /kmbaaction    KmbaS2Action` |
| <1>  | `cli 1 -jad KmbaS2Action`               | ✓ 反编译出恶意源码：`private static String paramName = "cmd"` + `public String execute()`（RuntimeExec 链） |
| <2>  | `cli 1 -u struts2Action /kmbaaction`    | ✓ `[+] unload success`                                       |
| <3>  | `cli 1 -l struts2Action` 复查           | ✓ Kmba 计数 0，条目 188→187                                  |
| <4>  | HTTP 死亡确认                           | ✓ `/kmbaaction.action?cmd=id` 无 uid 输出；`/index.action` 200 生产存活 |
| 附加 | 重复 unload                             | ✓ 如实报失败（无假 success），但消息为 `[!] unload failed: OGNL execute failed` 而非 "not found"（消息质量问题，见 3.7） |

**Web 五步 ✅（全部通过）**

| 步骤 | 操作                                                    | 结果                                                         |
| ---- | ------------------------------------------------------- | ------------------------------------------------------------ |
| <1>  | `GET /arthas/connect?pid=1` → `GET /Struts2Action/list` | ✓ connect success；list 返回 namespace→className JSON，含 /kmbaaction→KmbaS2Action |
| <2>  | `GET /Struts2Action/unload?urlPath=/kmbaaction`         | ✓ `success`                                                  |
| <3>  | 复查 `/Struts2Action/list`                              | ✓ kmbaaction/KmbaS2Action 计数 0（187 条合法条目仍在）       |
| <4>  | HTTP 死亡确认                                           | ✓ uid 计数 0；生产页面 200                                   |

**本组件发现**（详见 3.7 与问题清单）：

1. **CLI 卸载参数语义 = urlPath（/kmbaaction），不是 className**。传 `KmbaS2Action` 报 `[!] unload failed: OGNL execute failed`（马不死）；换 `/kmbaaction` 成功。Web 端点参数名就叫 `urlPath`，两端语义一致，仅 CLI 未做提示；且失败消息具有误导性。
2. 重复卸载（目标不存在）的失败消息同为 "OGNL execute failed"，与其他组件 "not found" 风格不一致。

### 3.7 round3 修复项在 v0.7 最终产物上的延续性复验

> round3 曾基于开发中间版 jar 验证三项修复并记录 P0（listener 卸载残留）。该中间版 jar 已遗失（被 v0.7 覆盖，MD5 序列无法比对），按"不符合本次标准的就要再测一遍"纪律，全部转为**在 v0.7 最终产物上的行为级复验**；git 提交历史仅作旁证不作证据。2026-08-31 开发者重新交付 jar（`769f37da…`）后，本节全部项目**又在交付 jar 上重做一遍**（容器重建，见 3.8 与测试日志 `logs/agent-0831-repack-verify.md`）。

复验环境：Tomcat 9.0.97 + JDK8 容器，自写最小内存马复刻 round3 各组件测试形状（多副本 listener、Proxy 包装 valve 三级管线、WebSocket 端点），五步协议不变。

**（1）proxyValve——修复延续 ✅，并实测出新枚举面边界（P1）**

五步（round3 形状：代理占据 context 管线 first 槽位、包装合法 valve、恶意逻辑在 InvocationHandler）：

| 步骤 | 操作                                                         | 结果                                                         |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| <0>  | first 槽位代理接管（探针确认 `first=com.sun.proxy.$Proxy51 wraps=NonLoginAuthenticator`） | ✓ 马执行：目标 webapp 每请求 5 行 uid 输出                   |
| <1>  | `cli 1 -l proxyValve`                                        | ✓ 12 条（6 context × first/basic），恶意条目 **`first KmbaVHandler2`**（处理器类名，非 $ProxyN——round3 修复签名复现） |
| <2>  | `cli 1 -u proxyValve first KmbaVHandler2`                    | ✓ `[+] unload success`                                       |
| <3>  | 复查                                                         | ✓ first 槽位恢复为 `NonLoginAuthenticator`（探针地面真值复核），代理条目消失 |
| <4>  | 死亡确认                                                     | ✓ uid 行数 5→4，生产页面 200                                 |

**新发现：枚举面缺陷（P1，反编译 ProxyValve.java 归因 + 行为实证一致）**。组件的枚举规则为 vmtool 逐个 StandardContext 反射读取管线的 `first` 和 `basic` **两个字段**——仅这两个槽位进入枚举。实测漏检场景：

- 在 **engine 管线、host 管线、ROOT context 管线中间槽位**（addValve 追加在 first 之后、basic 之前）各投放 1 条代理 valve，3 条全部活体执行；其中 engine/host 级代理**对全站任意 webapp 任意路径请求生效**（每请求多行 uid 输出），影响面更大；
- `-l proxyValve`：**0/3 检出**（列表 10 条 = 5 个 context 的 first/basic 逐条对应，全是合法 valve）；
- `-l valve`（普通 valve 组件，遍历范围为 `StandardContext.pipeline.getValves()`）：**1/3 检出**（仅 context 管线中间槽那条，显示为 `com.sun.proxy.$Proxy8`，不解析处理器类名）；engine/host 管线完全不可见。

**兜底清除路径（可用）**：`-u valve com.sun.proxy.$Proxy51`（按类名 removeValve）成功移除 context 级中间槽代理 ×2（uid 4→2），其他位置代理未被误伤（按类名精确匹配）。但 **engine/host 级代理在全部组件中无可达清除路径**（两个 valve 组件都不遍历非 context 管线）。

**2026-08-31 交付 jar 复测**：first 槽位形状五步全过（检出 `first KmbaVHandler2` → unload → 探针确认槽位还原 → uid 3→2）；三级管线枚举面维持 0/3 与 1/3、engine/host 无清除入口（#11 维持）；`-u valve com.example.DoesNotExist` → `[+] unload success`（#16 维持）；上述失败场景 CLI 退出码均 0（#21 维持）。详见测试日志 §7。

**（2）socket（WebSocket）——修复延续 ✅**

| 步骤 | 操作                                                         | 结果                                                         |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| <0>  | `@ServerEndpoint("/kmbaws")` 端点注册                        | ✓ WS 握手 HTTP/1.1 101                                       |
| <1>  | `cli 1 -l socket`                                            | ✓ 7 条：`/kmbaws KmbaWs` + 6 条 Tomcat examples 合法端点（round3 的 `getEndpointClass` NoSuchMethodException 未再出现） |
| <2>  | `cli 1 -u socket /kmbaws KmbaWs`（参数 = urlName + className 双参数，反编译确认） | ✓ `[+] unload success`                                       |
| <3>  | 复查                                                         | ✓ socket (6)，kmbaws 条目消失                                |
| <4>  | WS 握手死亡确认                                              | ✓ 握手返回 **HTTP/1.1 404**（端点已从 `WsServerContainer.configExactMatchMap` 移除）；examples 生产页面 200 |

小瑕疵（P2）：`-l socket` 首条目输出含未剥离的集合 toString 片段（`EMPTY]@ArrayList[@String[/kmbaws KmbaWs` 等），检出内容仍可辨认，属输出格式问题。——**2026-08-31 交付 jar 复测：已修复**（Socket.java list 正则重写），CLI `-l socket` 首条目 `"/kmbaws  KmbaWs"` 输出干净、Web `/socket/list` 同样无乱码（见 3.8）。

**（3）listener——卸载消息误报回归 ❌，P0 残留未复现 ✅**

测试装置自纠（如实记录，不影响被测对象结论）：初版自写 listener（KmbaLsn）注册成功但从未执行——其反射取 Response 的路径在 RequestFacade 上不存在、异常被吞，成为哑弹；此前观察到的 uid 输出实为代理 valve 的输出（对照实验：注册新副本后 uid 行数不变）。修正版 listener（KmbaLsn2，兼容 RequestFacade 反射路径）注册 3 份后 uid 行数 2→5，马确认真实执行，后续判定全部基于修正版。

五步（kmba3 context，3 副本）：

| 步骤 | 操作                             | 结果                                                         |
| ---- | -------------------------------- | ------------------------------------------------------------ |
| <0>  | 3 副本注册（探针确认 `total=3`） | ✓ 每请求 uid 行数 2→5，三副本全部执行                        |
| <1>  | `cli 1 -l listener`              | ✓ 检出 KmbaLsn2 ×3（+2 条合法 listener），与地面真值一致     |
| <2>  | `cli 1 -u listener KmbaLsn2`     | 删除成功但**消息误报** `[!] Listener is empty`               |
| <3>  | 复查                             | relist 2 条；uid 5→4 —— **枚举计数与实际存活严格对应**       |
| <4>  | 循环卸载至 list=0                | relist 1→0，uid 4→3→2；终点 uid=2 = 代理 valve 基线（非 listener）；探针确认目标 context `listeners=[]` |

**P0 复测结论：未复现**。round3 P0（"OGNL 遍历 instances[0..3]，实例位于 instances[4] 时漏掉，list=0 但请求仍有 uid"）以更严苛流程复测：多副本（ROOT 5 副本 + 另一 context 3 副本，跨 2 个 context、覆盖 instances 数组首尾位置）、逐份卸载、每轮以 uid 行数 + 探针地面真值双重复核——**每一轮 KMBA list 计数与实际存活副本数严格一致，卸载终点与基线完全吻合，无任何残留**。

**消息修复在交付 jar 上实测确认 ✅（2026-08-31 复测）**：3 副本一次 unload → `[+] unload success`（不再误报 "Listener is empty"）；负样本 → `[!] Listener not found`；Web 通道同（`success` + 复查仅剩合法条目 + 对应 webapp 请求 uid 计数 4→1）。反编译确认交付 jar 的 Listener.java 已改：单 context 内最多 10 次删除循环 + `listContains` 预检 + 消息按"doneCnt>0 → success → OGNL execute failed → empty → not found"优先级返回。上一轮在中间版 jar 上观察到的误报未再出现。

**单删语义改进为"单次最多删 10"（2026-08-31 实测）**：12 副本场景单次 unload 恰移除 10 份（relist 余 2、消息仍 `success`），第二次 unload → 0。较上一轮"一次仅删 1 个"大幅改善；>10 副本仍需多次调用且消息不披露剩余实例数（并入 #15 记录）。

### 3.8 2026-08-31 交付 jar（`769f37da…`）问题清单复测汇总

> 开发者 2026-08-31 重新交付 jar 后，对问题清单全部存留项逐项复测（全新容器，五步协议不变，CLI+Web 双通道；原始输出见测试日志 `logs/agent-0831-repack-verify.md`）。jar 逐类比对：Agent / Listener / Thread / Socket / Filter 为行为级变更，Valve / Servlet / Upgrade / SpringMvcInterceptor / Timer 仅日志级，ArthasController / CliHandler / Util 字节码一致（jps 依赖、jad hashcode、退出码逻辑未变）。

**（1）thread 组件子类型缺口（#12）——修复确认 ✅**

`extends Thread` 覆写 run、target 为空的常驻线程马（KmbaThreadSub，daemon 轮询命令文件执行命令）：

| 步骤         | 结果                                                         |
| ------------ | ------------------------------------------------------------ |
| <1> 检出     | ✓ `cli 1 -l thread`（34 条）列出 `kmba-thread-sub  KmbaThreadSub`——子类型进入枚举（过滤式改为 `target!=null || class!=java.lang.Thread`） |
| <2> 卸载     | ✓ `cli 1 -u thread kmba-thread-sub KmbaThreadSub` → `[+] unload success` |
| <4> 死亡确认 | ✓ 输出文件行数不再增长；命令文件不再被消费                   |
| 负样本       | ✓ `-u thread nope Nope` → `[!] thread not found`（消息如实） |

**（2）filter 枚举去重（#22）——修复确认 ✅**

同名 filter 注册进 ROOT 与另一 webapp（双 context 双 FilterDef/FilterMap）：CLI `-l filter` 仅列 1 条（旧版逐 context 重复列出）；一次 unload 清除两个 context（双侧 `FILTER-UID` 归零、业务页面双侧 200）；Web `/filter/list` 同样按名去重并保留合法过滤器条目供甄别。

**（3）transformer 型 Agent 全链复跑 ✅（同机制样本③，6 轮 + 冒烟）**

容器重建后冒烟五步全过（检出 → Web unload success → relist 清零 → 钩子回显死亡）；正式 5 轮 load→list→unload→death→relist：检出/卸载 success/钩子死亡（uid 1→0）逐轮全过，唯列表残留 1 条需二次卸载（B 探针：第二次 unload → 0），即 **#10 的"列表残留"新面孔**——完整数据与 web 日志逐实例漂移证据见 3.3.6 发现 2 与测试日志 §6。

**（4）其余存留项复测结果**

| 项                    | 交付 jar 复测结果                                            |
| --------------------- | ------------------------------------------------------------ |
| #9 redefine 回灌      | ❌ **假 success 双通道**（探针 8→14 / 16→22 持续触发；根因 dump=改写后字节；对照实验证明机制方向可行）——退回存留，见 3.3.7；**后续 v2 jar（`e4d7cb1f…`）复测：8u151 双通道通过、8u432 Web 通道仍假 success（主路径 #31 连锁），部分修复**（3.3.8）；**v3 jar（`a25cc5bf…`，称修复 #31）复测：8u432 CLI 也取到被改写字节 → 双通道假 success，#31 未修复**（3.3.9）；**v4 jar（`d22aad21…`）开发者删除处置链：卸载如实报失败、马存活（8u151 能力消失），另 /agent/restore 假 success（#32）**（3.3.10）；**v0.8 jar（`01501f79…`）处置链重建（sc -d → 磁盘原始字节 → redefine 回灌，不经 OGNL/dump）：8u432 双马装置 restore 强杀五步闭环（success 16.6s 后命令失效、探针 +0），#32 假 success 消除；检出盲区维持**（3.3.11） |
| #10 agent 卸载窗口    | ❌ "列表残留需二次卸载"面孔 5/5 复现（同根因：逐命令独立枚举漂移），见 3.3.6 发现 2 |
| #11 proxyValve 枚举面 | ❌ 维持（3.7 复测段）                                         |
| #13 / #17 / #19 / #20 | —（对应类字节码一致，维持上一轮结论）                        |
| #15 同名多实例        | ◐ listener 侧改进为单次最多删 10（12 副本余 2）；agent 侧残留并入 #10 |
| #16 valve 假阳性      | ❌ 维持（`-u valve DoesNotExist` → success）                  |
| #21 CLI 退出码        | ❌ 维持（失败场景 exit 0）                                    |
| #23 Web 会话静默失效  | ❌ **新发现**：WS 会话断开后（`WebsocketNotConnectedException`）所有组件端点静默返回 `[]`（HTTP 200），无自动重连、无错误提示，与"未 connect"状态不可区分——本轮首批 5 轮 Agent 试验即因此作废重做（web 日志异常栈与 `Util.getListResult: []` 为证） |

**（5）环境侧教训记录（非 KMBA 问题）**：该 Tomcat 镜像无 `catalina.out`，探针类计数必须走 `docker logs`（上一轮以 `catalina.out` 计数的探针归零校验无效，已全部重取）；agent jar 重复 loadAgent 不重载已加载类（更新样本后须重启目标 JVM）。

---

## 四、查杀能力边界清单

> 以下为本次测试实测出的全部能力边界（查杀不到或查杀受限的场景），供能力评估与后续修复排序参考。

| #    | 边界                                                         | 实测证据                                                     |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 1    | **Agent redefine 型内存马**（仅 `redefineClasses`、不注册 transformer）检出不可见（transformer 枚举原理性盲区，维持）；处置路径迭代：v1 dump 回灌报 success 不生效（8u432 类 JVM 上 dump 取到被改写后的字节，回灌等于重放恶意字节）；v2 CodeSource 主路径 8u151 闭环、8u432 上主路径 OGNL 被拒绝（#31）且 dump 回退字节来源会话相关；v3 复测证实 #31 未修复——8u432 CLI 与 Web 双通道假 success；**v4 删除处置链：CLI/Web 如实报失败（假 success 消除）但 8u151 既有处置能力一并消失，全环境无处置；新增 /agent/restore（dump-only）报 success 不生效——新假 success 面（#32）** | 样本⑥ WebLogic 复现（3.3.5）；v1 双通道假 success + dump 字节比对 + 原始字节 redefine 对照实验（3.3.7）；v2 双环境双通道复测：8u151 提取物 md5 逐字节一致 + Web 日志全链、8u432 readFully(-1) 失败与 CLI/Web dump 字节 md5 对照（3.3.8）；v3 复测：8u432 双通道假 success + 8u151 主路径回归通过（3.3.9）；v4 四环境双通道 + redefine 探针诚实拒绝 + 清态/历史会话 restore 对照（dump `2b7731e2…` 污染字节 vs `6430d8b2…` 干净字节）+ 8u151 restore 假 success（3.3.10）；v0.8 处置链重建（sc -d → 磁盘原始字节 → redefine 回灌）+ `-u agent all`/Web 强杀：8u432 双马五步实测强杀闭环、#32 假 success 消除，HttpServlet 5 块子类过滤实战样本（3.3.11） |
| 2    | **proxyValve 枚举面仅覆盖 StandardContext 管线的 first/basic 槽位**：engine/host 管线代理完全不可见（两 valve 组件均不遍历非 context 管线）、中间槽位代理对 proxyValve 不可见 | 三级管线 3 条活体代理 0/3 检出（2026-08-31 交付 jar 复测维持）；反编译归因一致（3.7） |
| 3    | **engine/host 管线代理无可达清除路径**：valve 组件可兜底清除 context 级中间槽代理（按类名精确匹配无误伤），但 engine/host 级代理在全部组件中无入口 | 兜底清除成功（context 级）+ 两组件遍历范围反编译证据（3.7）  |
| 4    | **JDK7 目标不支持**：内置 arthas 4.3.x 为 JDK8 字节码（major 52 实证），attach 报 UnsupportedClassVersionError；KMBA 本体亦需 JDK8 | JBoss AS 6.1.0 环境                                          |
| 5    | **注入侧非 KMBA 范围的限制**（记录）：WebFlux HandlerFunction 型内存马受 MemShellParty 生成器与 spring-webflux 5.3.16 二进制不兼容影响（VerifyError，多实现类复现）——非 KMBA 缺陷，修复生成器后可补测；Weblogic JNDI 入口要求 ObjectFactory 载荷（注入侧待改造） | 类型矩阵与注入侧记录                                         |

---

## 五、组件类型覆盖（v0.7 内置 25 类）

| 状态                                                         | 类型                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| ✅ 五步通过（24 类）                                          | filter（含交付 jar 去重复测）、servlet、listener（含交付 jar 消息复测）、valve、upgrade、timer、thread（含 Thread 子类型，交付 jar 复测确认）、executor、springMvcController、springMvcInterceptor、springFluxHandlerMethod、springFluxWebFilter、springFluxNettyHandler、springFluxHandlerFunction、jettyFilter、jettyServlet、jettyListener、weblogicFilter、weblogicServlet、weblogicListener、**struts2Action**、**socket**、**proxyValve（first/basic 槽位形状）**、**agent（transformer 型，5/6 样本全链通过；transformer 型路径 2026-08-31 交付 jar 复跑 6 轮 + 冒烟确认，v4 复测 4 环境五步全过无回归，3.3.10）** |
| ⚠️ 检出盲区（检出维持不可见；v0.8 处置经强杀通道闭环） | **agent（redefine 型）**——检出不可见（见 3.3.5 与第四节边界 1）；迭代史：v1 dump 回灌假 success（3.3.7）→ v2 CodeSource 主路径在样本⑥真实容器（8u151）CLI+Web 五步终验通过，8u432 上主路径被 JDK API 兼容性打断（#31）、Web 通道经不可靠 dump 回退假 success（3.3.8）→ v3（称修复 #31）复测证实未修复，8u432 双通道假 success（3.3.9）→ **v4 开发者删除处置链：卸载如实报失败、内存马存活，8u151 能力消失；新增 /agent/restore（dump-only）报 success 不生效（3.3.10）**；**v0.8 处置链重建（sc -d → 磁盘原始字节 → redefine 回灌）+ `-u agent all`/Web「Agent类强杀」：五步实测强杀闭环（8u432 redefine 马死亡），#32 假 success 消除；检出不可见维持（3.3.11）** |
| ⏸ 范围外                                                     | tongWeb（按测试范围约定不测）                                |

---

## 六、清理记录

- Agent 专项 4 个容器（agent-t9a / agent-t10 / agent-solr / agent-wl）测试完成后全部 `docker stop`（已核实 Exited）；struts2Action 专项容器、修复延续性复验容器 `docker rm -f` 销毁（附着其上的内存马随容器销毁）
- 容器内注入用 JSP / 马类文件 / 编译脚本 / arthas 工作目录逐项删除并复核 0 残留
- 服务器 /tmp 测试文件（java/sh/jsp/py/class）全部删除并复核 0 残留；服务器上 KMBA.jar 与各历史备份未动
- 此前各环境测试容器同样遵循"一环境一容器，测完即停"纪律，均已停止
- redefine 型修复复验容器（kmba-fix16-20，Tomcat 9 · JDK 8u432）测试完成后删除注入探针 jar/class 文件、javassist 与编译产物、classdump 目录并复核，随后 `docker stop`
- **2026-08-31 交付 jar 复测容器**（tc0831）收尾：redefine 探针经容器重启自然还原（复验 `__probe__` 请求探针输出 0）；残留 transformer 两次 unload 后 relist 0；web 进程 kill（进程数复核 0）；容器内注入 JSP、马类、kmba3 webapp、arthas classdump、全部 /tmp 测试文件（jar/jad/脚本/输出文件/工作目录）逐项删除并 `ls` 复验；随后 `docker rm -f` 销毁（`docker ps -a` 复核 0）；服务器 /tmp 本轮上传物（jar/tgz/脚本/源文件）删除并复验不存在，历史轮次文件未动
- **2026-08-31 v2 jar 复测容器**（tc0831v2 · Tomcat 9/8u432；agent-wl2 · WebLogic/8u151）收尾：测试完成即 `docker rm -f` 销毁（`docker ps -a` 复核两容器 0 残留）；服务器 `/tmp/v2test` 上传目录（v2 jar、五步法脚本、AttachMain）删除并复验不存在；容器内 KMBA web 进程与注入样本随容器销毁；本地暂存（/tmp/v2stage、/tmp/v2jar、javap 反编译文本）删除；历史轮次文件未动（`ls` 清单确认在位）
- **2026-08-31 v3 jar 复测容器**（tc0831v3 · Tomcat 9/8u432；agent-wl3 · WebLogic/8u151）收尾：测试完成即 `docker rm -f` 销毁（`docker ps -a` 复核两容器 0 残留）；服务器 `/tmp/v3test` 上传目录（v3 jar、探针装置源码与五步法脚本、WL 样本 jar、AttachMain）删除并复验不存在；本地暂存（/tmp/v3jar 与 javap 反编译文本）删除；历史轮次文件未动（`ls` 清单确认在位）
- **2026-09-01 v4 jar 复测容器**（tc0831v4 · Tomcat 9/8u432；agent-t10 · Tomcat 10/JDK17；agent-solr · Solr/JRE8u102；agent-wl4 · WebLogic/8u151）收尾：测试完成即 `docker rm -f` 销毁（`docker ps -a | grep -cE` 复核 4 容器 0 残留）；服务器 `/tmp/v4test` 上传目录（v4 jar、探针装置源码与五步法脚本、WL 样本 jar、AttachMain）删除并复验不存在；历史 MSP 样本 jar 与 javassist（服务器 /tmp 下 4 文件）`ls | wc -l` 确认在位未动；本地暂存（/tmp/v4jar 与 4 份 javap 反编译文本）删除；服务器常驻容器（业务/平台类）未做任何操作
- 服务器上与本次测试无关的常驻容器（业务/平台类）未做任何操作

---

## 七、遗留与建议（面向 KMBA 后续版本）

1. **Agent redefine 型处置（#9，v0.8 已重建）**：处置链历经 v1 dump 假 success（3.3.7）→ v2 CodeSource 真回灌 8u151 闭环、8u432 #31 中断（3.3.8）→ v3 未修复（3.3.9）→ v4 整体删除（3.3.10）→ **v0.8 重建为"sc -d 确认存在 → KMBA 本进程读宿主磁盘 jar 原始字节 → 落盘临时目录 → arthas redefine 回灌"，并新增全清单强杀通道（CLI `-u agent all` / Web「Agent类强杀」）**，8u432 双马五步实测强杀闭环（3.3.11）。三点教训的处置状态：① **#31**（OGNL 提取模板 `readFully(-1)` 被 8u432 类 JDK 拒绝）——v0.8 路线不经 OGNL，该问题整体退出触达面；② **dump 回退字节来源会话相关**（跨轮四点实证，3.3.8）——v0.8 已无 dump 回退通道，不再触达；③ **回灌后验证步骤**（重取运行态字节与提取文件比对，不一致时报 unverified 而非 success）——**仍开放**，建议在 v0.8 链路 redefine 之后追加。遗留关注：**检出侧盲区维持**（transformer 枚举原理性，redefine 马可靠 `-l all` 的 InstrumentationImpl 计数间接感知）；**agent shutdown 副作用**——kill agent 进程后宿主类字节可能回退为运行态污染字节（8u151 观察），处置完成后不要重启 agent 进程（v0.8 新记录）。历史参考：v2 曾做 Windows 路径加固（`file:` URL URI 化、OGNL 反斜杠转义、`extracted=` 懒惰匹配，重建 jar `5075606d…`）——v0.8 重建链已等效覆盖路径处理（URL 化 + %20 解码 + Spring Boot 嵌套 jar 支持）；检出侧经评估暂不加 dump-vs-jar 比对信号（维持 3.3.7 决策）；"jad 手工确认"不可靠（jad 字节来源与 JVM 版本强相关且可与运行时行为不一致，3.3.8）。
2. **Agent unload 顺序漂移（#10）**：逐命令独立枚举 `instances[i]` 的模型在单次 unload 调用内部即可漂移（实测：unload 循环内 `instances[3]` 报 removed、随后的 list 在 `instances[6]` 才探到该条目），产生"列表残留需二次卸载"；跨调用漂移产生"not-found 但 list 可检出、shell 存活"。建议循环内重新查询实例计数、或按对象标识定位后逐对象操作；"已卸载但列表残留"与"未找到"两种语义应可区分。
3. **proxyValve 枚举面**：建议将枚举范围从"first/basic 两槽位"扩展为完整 valve 链（`getValves()`），并覆盖 engine/host 管线；处理器类名解析逻辑可复用现有实现。
4. **卸载消息一致性**：listener 消息已修复（success / not found 如实）；仍并存的不一致语义：valve 对不存在目标报 success、struts2Action 失败报 "OGNL execute failed"、listener >10 副本时"删 10 余 N"不披露剩余数——应统一为如实的 "not found" / "removed N instances (M remaining)" 风格；**v4 新增 agent 失败消息漂移（#34）**：同 JVM 同目标 CLI 报 "OGNL execute failed"、Web 报 "className not found"，干净 JVM 上对无马宿主类 unload 在 v4 报 OGNL execute failed 而 v3 报 not found（OGNL 失败根因为 arthas 侧 `ArrayIndexOutOfBoundsException`）——"没找到"与"执行失败"不可区分，建议失败原因排序固定（not found 优先）且双通道一致。
5. **同名多实例 / 列表残留**：agent transformer 的"列表残留需二次卸载"与 listener 的"单次上限 10"都应在返回消息中明示剩余实例数，避免"列表未清零 = 没杀干净"与"报 success = 已清零"的双向误判。
6. **Web 模式可靠性（#23）**：WS 会话断开后所有组件端点静默返回 `[]`（HTTP 200），与"未 connect"不可区分——建议 wrapper 检测 `WebsocketNotConnectedException` 并自动重连，重连失败时显式返回错误而非空列表。
7. **Web 模式易用性**：未 connect 时 `/agent/list` 返回 `[]` 建议改为显式提示"请先 connect"；`-l agent` 的 arthas 自噪音条目建议内置白名单标记。
8. **CLI 退出码**：失败场景 exit code 恒为 0（交付 jar 复测维持），建议失败时返回非零码（脚本化使用无法感知失败）。
9. **版本标识**：交付 jar 的 CLI banner 显示 "KMBA v0.6"，与交付版本号 v0.7 不一致，建议同步。**v0.8 起已同步：banner 随 `Logo.VERSION` 交付更新（0.8）**。
10. **vmtool bootstrap 边界与错误结构（#33）**：classloader hash=null（bootstrap 加载类）双通道不可用；Web 端把错误文本包进 `{"type":"single","classLoaderHash":"null","data":"Can not find classloader with hashCode: null."}`，程序化消费无法识别失败——建议错误走独立 type（与 404 一致化），并在 CLI/Web 显式声明"bootstrap 加载类不支持"；CLI `-vmtool` 错误场景 exit code 恒 0（#21 同族）。

---

## 八、问题清单（累计，按 v0.8 现状）

### 已修复（修复效果在对应交付 jar 上实测确认）

| #    | 问题                                                         | 修复与复验证据                                               |
| ---- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 1    | springFluxHandlerMethod 卸载假成功（过滤条件写在 value.toString()，URL 模式在 key 中，条件永不命中；不校验结果误报 success） | 按 key pattern 匹配 + 卸载前后 diff 校验；复验 PASS=7 FAIL=0 |
| 2    | WebLogic 容器不支持枚举/卸载                                 | 新增 weblogicFilter/Servlet/Listener 三组件；复验 PASS=16，后续回归五步全过 |
| 3    | springMvcController 高版本 Spring 查错 HandlerMapping        | 版本探测分派（≥2.4 双条件访问器 / 2.0~2.3 / 1.x / 兜底）；复验 PASS=7 |
| 4    | Jetty 容器不支持枚举/卸载                                    | 新增 jettyListener/Filter/Servlet；复验 PASS=15，后续回归五步全过 |
| 5    | proxyValve 组件枚举/卸载双缺陷（round3：枚举 OGNL 恒空 + 卸载假成功） | **v0.7 实测修复**：first/basic 槽位代理检出（处理器类名）、卸载恢复原始 valve、死亡确认全链通过（3.7） |
| 6    | socket 组件对 Tomcat 9 不兼容（round3：ExactPathMatch 无 getEndpointClass） | **v0.7 实测修复**：枚举/卸载/404 死亡确认全链通过（3.7）     |
| 7    | Struts2 Action 内存马无检测组件（round3：22 类组件均不可见） | **v0.7 新增 struts2Action 组件**：CLI+Web 五步全过，jad 证据链完整（3.6） |
| 8    | listener 卸载后残留（round3 P0：副本位于 instances[4] 时漏删，list=0 但命令仍执行） | **v0.7 实测未复现**：多副本跨 context 逐份卸载，每轮枚举计数与实际存活严格一致，探针地面真值复核无残留（3.7） |
| 12   | thread 组件枚举缺口：仅枚举 target!=null（Runnable 型），"继承 Thread 覆写 run"型不可见 | **v0.7 交付 jar 实测修复**：枚举过滤式改为 `target!=null \|\| class!=java.lang.Thread`；Thread 子类型（KmbaThreadSub，daemon 常驻、target 为空）检出（thread(34) 列表可见）/卸载 `[+] unload success`/死亡确认（输出文件行数冻结、命令文件不再被消费）/负样本 `[!] thread not found` 四点全过（3.8，测试日志 §3） |
| 14   | listener 卸载消息误报：实际卸载成功但报 "[!] Listener is empty"（round3 中间版曾修复，一度回归） | **v0.7 交付 jar 实测修复**：消息优先级重写（doneCnt>0 → success → OGNL failed → empty → not found）+ listContains 预检 + 单 context 最多 10 次删除循环；3 副本一次 unload → `[+] unload success`、负样本 → `[!] Listener not found`，CLI+Web 双通道（3.7/3.8，测试日志 §2） |
| 18   | socket 枚举首条目输出含集合 toString 乱码（`EMPTY]@ArrayList[@String[...`） | **v0.7 交付 jar 实测修复**：Socket.java list 正则重写 + unload 复查；CLI `-l socket` 首条目 `"/kmbaws  KmbaWs"` 输出干净、Web `/socket/list` 同样无乱码（3.8，测试日志 §4） |
| 22   | filter 枚举同条目重复（多 context 层级重复遍历），同一 filter 多 context 场景重复列出 | **v0.7 交付 jar 实测修复**：JSONArray.contains 去重；双 context 同名 filter 仅列 1 条，一次 unload 清除两个 context（双侧 FILTER-UID 归零、业务页面双侧 200），合法过滤器条目保留供甄别（3.8，测试日志 §5） |
| 32   | /agent/restore 假 success：restore 实现为 dump-only（无 redefine），对 redefine 污染把被改写字节落盘后报 "success"，内存马不受影响；web-only、命名（restore）与语义（dump 落盘）不符 | **v0.8（`01501f79…`）实测修复**：restore 重构为真实回灌（sc -d 确认存在 → KMBA 本进程读宿主磁盘原始字节 → 落盘临时目录 → arthas redefine），新增 CLI `-u agent all`（全清单强杀）与 Web「Agent类强杀」按钮（`/agent/restore`）；8u432 双马装置实测：redefine 马 restore success 16.6s 后 `?cmd=id` 无回显、探针 +0、Web 恢复欢迎页；失败场景如实报 "bytecode restore failed"（3.3.11） |

### 已失效（v4 功能删除——代码路径移除，非修复；v0.8 重建路线不经 OGNL，该路线彻底退出）

| #    | 原级别 | 问题                                                         | 失效说明                                                     |
| ---- | ------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 31   | P0     | **v2/v3 回灌主路径对 8u432 类 JDK 构建不可用（2026-08-31 发现）**：OGNL 模板 `@sun.misc.IOUtils@readFully(#is,-1,true)` 的 -1 读全部哨兵被该 JDK 构建的 `sun.misc.IOUtils.readFully` 拒绝（`java.io.IOException: length cannot be negative: -1`），CodeSource 提取恒失败 → 恒落 dump 回退；回退字节来源会话相关，形成"回灌 no-op 仍报 success"。**v3（`a25cc5bf…`）交付称已修复，复测证实提取模板逐字节未变、缺陷仍在** | **v4 将承载该模板的 `AgentUtils` 类整体删除**，CodeSource 主路径不复存在，缺陷随之不可触达——属功能删除而非修复（代价是 8u151 上真实可用的处置能力一并消失，见 #9）；其核心教训（回灌字节必须验证、dump 回退不可依赖）已转入 #32（/agent/restore 假 success）。取证存档：web 日志 OGNL 失败原文（v2/v3 两轮复现）、dump md5 对照（`d0a5140a…` 基线 / `2b7731e2…` 被改写）、javap 模板逐字节比对（3.3.8 / 3.3.9，测试日志 §2/§3）；**v0.8 重建路线不经 OGNL（sc -d → 磁盘原始字节 → redefine），该模板问题彻底退出**（3.3.11） |

### 存留（有复现依据，待修）

| #    | 级别                                    | 问题                                                         | 依据摘录                                                     |
| ---- | --------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| 9    | **P0（检出盲区维持；v0.8 处置通道已恢复）** | Agent redefine 型内存马（仅 `redefineClasses`、不注册 transformer）检出不可见（transformer 枚举原理性盲区）；处置路径迭代：v1 报 success 不生效（unload 时刻 `dump` 取到被改写字节）→ v2 CodeSource 主路径在 8u151 双通道闭环、**8u432 双通道假 success**（主路径 #31 失败 → dump 回退字节来源会话相关；v3 称修复 #31，复测证实未修复）→ **v4（`d22aad21…`）开发者删除 redefine 处置链（`AgentUtils` 整类移除）：CLI/Web 卸载如实报失败、内存马存活，v2/v3 在 8u151 上真实可用的回灌能力一并消失——redefine 型全环境无处置（诚实拒绝），检出侧维持沉默** → **v0.8（`01501f79…`）处置链重建（sc -d → 磁盘原始字节 → redefine 回灌，不经 OGNL/dump）+ 强杀通道（`-u agent all`/Web「Agent类强杀」）落地，8u432 双马实测强杀闭环（3.3.11）** | 3.3.5 样本⑥五步走完内存马存活；3.3.7 v1 复测：探针 8→14 / 16→22，dump md5 `5435656e…`（8431 B 含探针）vs 磁盘原始 `1d914752…`（7863 B）；3.3.8 v2 复测：8u151 CLI（提取物 md5 `57a661e0…` == jar 解包原始）+ Web（日志 `extracted= → redefine success` 全链）探针均死亡；8u432 Web 探针 4→6→8 假 success；3.3.9 v3 复测：8u432 CLI 探针 2→4→6、Web 探针 8→10→12 假 success（dump md5 均 `2b7731e2…` 含探针字节）；3.3.10 v4 复测：8u432 探针 2→4→6（CLI/Web 均诚实报失败）、8u151 样本⑥ huahua 存活（Web `className not found` / CLI `[!] unload failed: OGNL execute failed`）、`kmba_orig_*` 零产物；3.3.11 v0.8 复测：8u432 双马装置 restore 强杀五步闭环（success 16.6s → 命令失效、探针 +0）、HttpServlet 5 块子类过滤实战样本、`-l all` 全量 47.7s（修复带入） |
| 10   | **P1**                                  | Web `/agent/unload` 实例顺序漂移，**双面孔**：① `className not found` 但 list 可检出、shell 存活（失败窗口）；② 单次 unload 报 success、钩子已死亡，但列表残留 1 条 transformer 条目需二次卸载。根因同一：逐命令独立枚举 `instances[i]`，漂移可发生在**同一次卸载调用的多条命令之间** | 面孔① 3.3.3/3.3.4：JDK17 与 JRE8 双重复现，逐 index 诊断脚本举证；面孔② 2026-08-31 交付 jar 复测 5/5 复现，web 日志地面真值——unload 循环内 `instances[3]` 返回 removed、随后 `/agent/list` 在 `instances[6]` 才探到该条目；v3 复测（T2r）1/1 复现；**v4 复测 CLI 与 Web 双通道 1/1 复现，且二次卸载消息两通道不一致**（CLI `[!] unload failed: OGNL execute failed` / Web success 清零）（3.3.6、3.8、3.3.9、3.3.10，测试日志 §6/§3/§2/§4） |
| 11   | **P1**                                  | proxyValve 枚举面覆盖不全：engine/host 管线代理完全不可见（两 valve 组件均不遍历非 context 管线）、context 管线中间槽位代理对 proxyValve 不可见；engine/host 级代理无任何可达清除路径 | 3.7：三级管线 3 条活体代理 0/3 检出（engine/host 级对全站请求生效），交付 jar 复测维持；反编译归因一致 |
| 13   | P1                                      | JDK7 目标不支持（内置 arthas 4.3.x 为 JDK8 字节码 major 52，attach 报 UnsupportedClassVersionError） | JBoss AS 6.1.0 环境记录                                      |
| 15   | P2                                      | 同名多实例/批量删除语义：listener 单 context 单次最多删 10（12 副本实测删 10 余 2，消息不披露剩余数）；agent transformer 列表残留需二次卸载（并入 #10 面孔②）——两种场景的消息均不披露剩余实例数 | 3.7/3.8：12 副本实测恰删 10、第二次调用清零；3.3.2 样本③ / 3.3.6 发现 2 |
| 16   | P2                                      | valve 组件卸载假阳性：不存在的 className 也报 `[+] unload success`（与 proxyValve 的 "Valve not found"、struts2Action 的 "OGNL execute failed" 三种语义并存） | 3.7：`-u valve com.example.DoesNotExist` → success；交付 jar 复测维持（3.8） |
| 17   | P2                                      | struts2Action CLI 卸载参数语义 = urlPath 而非 className，误传报 "OGNL execute failed"（误导性失败消息）；重复卸载同消息 | 3.6：className 传参失败、urlPath 成功                        |
| 19   | P2                                      | Web `/arthas/processes` 依赖 `jps`，JRE 容器返回空列表（后端日志 `Cannot run program "jps": error=2`） | 缓解：`/arthas/connect?pid=N` 直连不受影响，全流程可用       |
| 20   | P2                                      | Web jad 同名类多 classloader 无法消歧（`Found more than one class ... Please use jad -c <hashcode>`，Web 端传 hashcode 无效；CLI 正常逐一枚举） | Shiro/Solr 两处复现                                          |
| 21   | P2                                      | CLI 失败场景 exit code 恒为 0（脚本化使用无法感知失败）      | 各失败场景实测；交付 jar 复测维持（3.8）                     |
| 23   | P2                                      | **Web WS 会话静默失效（2026-08-31 新发现）**：会话断开（`org.java_websocket.exceptions.WebsocketNotConnectedException`，ArthasWsWrapper.runCmd:79）后**无自动重连、无错误提示**，所有组件端点静默返回 `[]`（HTTP 200）——与"未 connect"状态不可区分，"没有马"与"会话已死"无从甄别 | 交付 jar 实测：web 日志异常栈 + `Util.getListResult: []`；本轮首批 5 轮 Agent 试验即因此整体作废重做；重连 `/arthas/connect?pid=N` 后恢复（3.8，测试日志 §8） |
| 33   | P2（2026-09-01 新发现，v4 引入）        | vmtool 对 bootstrap 加载类（classloader hash=null）双通道不可用，且 Web 端把错误文本包进 `{"type":"single","classLoaderHash":"null","data":"Can not find classloader with hashCode: null."}`——type=single 包裹错误文本，程序化消费无法识别失败；CLI `-vmtool` 错误场景 exit code 恒 0（#21 同族） | 3.3.10（D）：CLI `java.lang.String` → `Can not find classloader with hashCode: null.`；Web `sun.instrument.InstrumentationImpl` → type=single 包裹错误文本；多实例消歧（`javax.servlet.Servlet` 9 实例）/ 单实例 / 负例（`type:404`）均正常 |
| 34   | P2（2026-09-01 新观察）                 | agent 失败消息语义漂移：同 JVM 同目标，CLI 报 `[!] unload failed: OGNL execute failed` 而 Web 报 `className not found`（8u151 样本⑥）；干净 JVM 上对无马宿主类 unload，v3 报 `[!] className not found`（诚实）而 v4 报 `[!] unload failed: OGNL execute failed`（OGNL 失败根因为 arthas 侧 `java.lang.ArrayIndexOutOfBoundsException`）——失败原因排序随会话状态/通道变化，"没找到"与"执行失败"不可区分 | 3.3.10（B）：T2/T2r2/T6/T6web 消息对照；web 日志 OGNL AIOOBE 取证（测试日志 §2/§3/§7）；**v0.8 注**：restore 失败文案中性化（"bytecode restore failed"）+ CLI 提示补处置口径已带入（3.3.11）；漂移本身维持 |

### 观察项（行为差异记录，非缺陷）

| #    | 观察                                                         |
| ---- | ------------------------------------------------------------ |
| 24   | CLI `-l` 按 filterMaps 逐条列出同名多条 vs Web `/filter/list` 按名去重——两端粒度不一致 |
| 25   | 多次注入多版本类：WebLogic filters Map 同名覆盖（chain 生效 1 次）；Jetty filterMaps 累积 + 多 classloader 类共存（CLI jad 提示 multiple versions 并逐一反编译，行为合理） |
| 26   | Listener/Valve 型命令马响应所有带 cmd 的请求，多马共存时对其它马的 HTTP 层验证有干扰（KMBA 枚举/卸载不受影响） |
| 27   | Web 模式不自动 attach：未 connect 时 `/agent/list` 返回 `[]`（200），易误判"未检出"；`-l agent` 存在 arthas 自噪音条目（TransformerManager$1/$2）；叠加 #23 后空列表有三种可能含义（未检出 / 未 connect / 会话已死） |
| 28   | Agent 检出列表条目数随 attach 次数波动（每次 arthas connect/外部 loadAgent 使 InstrumentationImpl 实例 +1，实测 7→8），加剧 #10 的漂移概率 |
| 29   | executor 类型特性：命令执行受 execute()-time currentProcessor 未绑定限制（公开 su18/JYso 模板同为替换型占位）；KMBA executor 组件 list/unload/恢复原始三能力验证全通过 |
| 30   | 交付 jar 的 CLI banner 实测显示 "KMBA v0.6"，与交付版本号 v0.7 不一致（版本字符串未随交付更新；2026-08-31 前两轮交付 jar CLI 实测观察，本轮未复验）。**v0.8 起已同步：`Logo.VERSION` 随交付更新为 0.8，banner 与版本号一致** |

### 测试过程记录（非 KMBA 问题）

- Web 组件路由误测更正：Solr/WebLogic 初测用错端点（`/filter/*` 为 Tomcat 组件端点；Jetty 为 `/JettyFilter/*`、WebLogic 为 `/WeblogicFilter/*`），解包 jar 逐一 javap 确认后重新部署复测，Web 五步全过
- Struts2 showcase 镜像初判"无可利用入口"后经配置侧解锁（`struts.properties` 关闭 OGNL 安全层）完成注入；注入通道的装置简化（如解除 JSP 访问约束、写入配置文件）均在环境日志中如实记录，马的注册路径与公开工具一致，不影响被测对象
- JRE 容器缺 tools.jar 时 arthas attach 失败（`Can not find tools.jar under java home`），补入后 connect 由失败转 success（测试侧环境准备，非工具缺陷）；另有一次外来 tools.jar（平台不符）覆盖导致 `BsdVirtualMachine.getTempDir()` UnsatisfiedLinkError，从镜像原始层找回正确版本恢复——两次均为测试环境自伤，与 KMBA 无关
- Tomcat 10.1.34 官方镜像无 ROOT 应用，手工创建最小 `webapps/ROOT/index.jsp` 承载请求
- WebLogic 容器内 javap 等调试工具被裁剪，经 tools.jar 内 `com.sun.tools.javap.Main` 完成校验
- 交付 jar 复测环境三则装置教训（非 KMBA 问题，测试日志 §9）：①该 Tomcat 镜像无 `logs/catalina.out`，Tomcat/System.out 仅在 `docker logs` 可见——以 `grep … catalina.out` 计数的探针归零校验得到假 0，相关计数已全部以 `docker logs` 重取；②agent jar 重复 loadAgent 不重载已加载类（system classloader 缓存），更新样本 jar 后必须重启目标 JVM；③本容器 `jar xf` 落盘解包副本异常（javap/javac 读目录失败、读 jar 本体正常），以 `jar uf` 原位更新规避
- 2026-09-01 v4 复测 WebLogic CLI 段后出现一次 HTTP 全挂（hello/console 超时；JVM 进程存活非 OOM；容器内诊断命令同样超时）——**原因未能归因**（CLI jad/list/unload 连发与挂死之间无因果实证），容器重启后 web 段全程正常；该异常如实记录，CLI 段判定以武装态注入后的 unload 消息与探针行为为准（测试日志 §7）
- v4 复测装置三则教训（非 KMBA 问题，测试日志 §9）：①Tomcat 10 镜像无 unzip，MSP 样本类名以 `jar tf` 解析（`grep -oE` 提取会吞大写首字母）；②KMBA 内置 arthas-boot 的 tools.jar 探测按 `JAVA_HOME/jre`（JRE 布局容器需补 `…/jre/lib/tools.jar`）；③redefine 探针与样本⑥ attach 前必须业务预热（目标类未加载时 redefine 静默失败、马未武装）

---

## 九、附录

### 日志索引（与本报告同等脱敏标准）

- **Agent 专项**: `logs/agent-t9a-msp-AgentFilterChain.md`、`logs/agent-t9b-y4er-MyAgent.md`、`logs/agent-t9c-self-HttpServlet.md`、`logs/agent-t10-msp-jakarta-JDK17.md`、`logs/agent-solr-msp-JettyHandler.md`、`logs/agent-wl-Weblogic404.md`
- **struts2Action 组件**: `logs/agent-s2-struts2action.md`
- **修复延续性复验（v0.7）**: `logs/agent-tc24813-fix-continuity.md`
- **交付 jar 复测（2026-08-31）**: `logs/agent-0831-repack-verify.md`——问题清单存留项在交付 jar（`769f37da…`）上逐项复测全记录：#9 假 success 双通道取证 + dump 字节比对 + 原始字节对照实验、#10 列表残留 5/5 与 web 日志逐实例漂移证据、#12/#14/#18/#22 四项修复确认、transformer 型 Agent 6 轮复跑 + 3 探针甄别、#23 新发现、装置教训与清理复验
- **v2 jar 复测（2026-08-31）**: `logs/agent-0831-v2-verify.md`——redefine 回灌修复 v2（`e4d7cb1f…`）双环境双通道实测全记录：8u151 主路径提取物 md5 逐字节一致 + Web 日志全链（五步双通道通过）、8u432 `readFully(-1)` OGNL 失败取证与 dump 回退字节会话相关对照（CLI 基线 `d0a5140a…` / Web 被改写 `2b7731e2…`）、#31 新发现、T1 无效轮次（探针未武装）与装置教训、清理复验
- **v3 jar 复测（2026-08-31）**: `logs/agent-0831-v3-verify.md`——第三交付 v3（`a25cc5bf…`，称修复 #31）实测全记录：静态核验（提取模板与 v2 逐字节未变、restore() 新增、unloadAgentByVmtool 去 `-l -1`）、8u432 CLI/Web 双通道假 success 取证（探针计数 + dump md5 `2b7731e2…` + web 日志同异常）、8u151 双通道主路径回归通过（提取物 md5 == 原始 + 日志全链）、T2r transformer 模板变更回归与 #10 残留 1/1、装置教训与清理复验
- **v4 jar 复测（2026-09-01）**: `logs/agent-0901-v4-verify.md`——第四交付 v4（`d22aad21…`，开发者删除 redefine 处置链）实测全记录：静态核验（AgentUtils 整类删除、/agent/restore dump-only 且 web-only、VmtoolUtils / -vmtool 新增、dumpTargetClasses 29 类）、transformer 型 4 环境五步全过（①③④⑤）、redefine 探针双通道诚实拒绝（假 success 消除）+ /agent/restore 清态/历史会话对照（假 success 实锤：dump `2b7731e2…` 污染 vs `6430d8b2…` 干净）、8u151 样本⑥双通道诚实拒绝 + restore 假 success、#10 残留 1/1×2 通道、vmtool 双通道与 bootstrap 边界、#32/#33/#34 新发现、装置教训与清理复验
- **v0.8 处置链重建实测（2026-10-13）**: `logs/agent-1013-codesource-redefine-restore.md`——redefine 处置链重建（sc -d 确认存在 → 磁盘原始字节 → redefine 回灌）调试全记录：sc -d 子类污染分块过滤（HttpServlet 5 块）、code-source 提取与嵌套 jar 路径处理、临时目录落盘与回灌验证
- **v0.8 双马五步终验（2026-10-13）**: `logs/agent-1013-agentcrack-redefine-memshell.md`——8u432 双马装置（transformer 马 MSP + agentcrack 式 redefine 马）五步法全记录：transformer 马 unload 23.6s 五步全过、redefine 马 restore 强杀 16.6s 后命令失效（探针 +0）、`-l all` 全量 47.7s、探针计数口径坑与 attach→list 竞态复盘（§5）、环境还原
- 非 Agent 环境: `logs/env-01-shiro.md` ~ `logs/env-09-javarce-spring.md`、`logs/fix-env-*.md`（缺陷修复复验）、`logs/FIX-VERIFY-REPORT.md`（PASS=45 FAIL=0）、`logs/MASTER-TEST-LOG.md`
- round3 扩展环境与类型矩阵: `logs/round3/env-01-struts2.md` ~ `logs/round3/env-14-coexist-gateway.md`、`logs/round3/TOOLS-AND-ENVS-MATRIX.md`、`logs/round3/MEMSHELL-ID-INVENTORY.md`
- 工具核查: `logs/tool-survey-SUMMARY.md`（25 个公开工具核查，9 个含内存马模块、16 个阴性对照）

### 测试产物存档

| 产物                                                         | 位置                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| Y4er MyAgent.jar（原样，743,297 B）                          | `poc/agent/MyAgent.jar`                                      |
| 自建 HttpServlet 钩子 jar（存档版 792,957 B / md5 `2953cd5a…`；2026-08-31 复测用重建版 md5 `5887e237…`，差异仅 transformer 内 javassist LoaderClassPath 插入顺序——修复测试容器 javassist 解析时序的装置修复，恶意行为字节一致）+ 源码 | `poc/agent/self-http.HttpServlet-agent.jar`、`poc/agent/http-servlet-hook/{AgentMain,HttpServletTransformer}.java` |
| Weblogic404Memshell jar（本地构建，797,103 B）               | `poc/agent/weblogic404-memshell.jar`                         |
| redefine 型修复复验探针源码（模拟样本⑥"仅 redefineClasses 不注册 transformer"机制：RedefineMaker javassist 插桩 / RedefineAgent2 基准字节观测 / RedefineAgent4 redefine-only agentmain）及其 v2 复测五步法脚本（t1b/t2/wl/wlweb 等，2026-08-31） | `poc/agent/redefine-probe/`、`poc/agent/redefine-probe/v2-verify/` |
| redefine 探针 v4 复测五步法脚本（setup / build-probe / t1 / t2 / t2web / t3 / t4 / t5 / t6 / t6web 等，2026-09-01） | `poc/agent/redefine-probe/v4-verify/`                        |
| redefine 型 v0.8 注入装置源码（RedefineMakerCmd.java：-agent redeliver 前置注入，替代 v2/v3 OGNL 注入面） | KMBA0 开发树 `kmbatest/poc/`                                 |
| MSP 样本 jar（agent-tomcat9 94,226 B / agent-t10 94,242 B / agent-solr 94,400 B） | 测试服务器 `/tmp/`（生成参数见各日志）                       |
| 修复延续性复验自写马源码（KmbaLsn2 / KmbaPassValve / KmbaVHandler2 / KmbaWs 等） | `logs/agent-tc24813-fix-continuity.md` 附录                  |

### 脱敏清单

本报告已移除：测试服务器公网地址、SSH 端口与凭据、镜像站私有配置、容器宿主端口映射的具体数值（统一以容器本地 127.0.0.1 或省略形式呈现）。保留：Docker 内网网段（172.17.0.0/16）、测试容器名、CVE 编号、开源工具/项目名、JDK 版本。以上内容不构成对测试基础设施的定位信息。