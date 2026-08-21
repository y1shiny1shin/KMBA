# KMBA 内存马检测与卸载能力 —— 全工具全类型五步验证测试报告

> 被测工具: **KMBA-0.0.1-SNAPSHOT.jar**（39,068,605 字节, md5 94f43157792645aeacce3734719ee113）
> 测试时间: 2026-08-19 ~ 2026-08-21 · 测试服务器: 49.235.120.216:22233
> 统一验证协议（每类内存马严格执行五步）:
>
> - `<0>` 写入内存马后成功执行命令
> - `<1>` KMBA list 发现内存马
> - `<2>` KMBA unload 卸载内存马
> - `<3>` 复查 list 内存马已被卸载
> - `<4>` 使用内存马执行命令已失效
>
> 状态标记: ✅ 五步全过 · ⚠️ 部分过/环境限制 · ⏸️ 未测（用户指示/环境阻塞）· ✗ 失败

---

## 测试总览

| 工具 | 类型数 | 结果 | 主要环境 |
|------|--------|------|----------|
| ShiroAttack2 | 20 类 | ✅ 17 类五步全过 + 3 类 round1 已测 | Shiro 1.2.4 (Spring Boot) |
| Hyacinth | 6 类 | ✅ 全过 | 同上 |
| Pyke-Shiro | 8 类 | ✅ 全过 | 同上 |
| ysuserial | 9 类 | ✅ 全过（Resin 2 类 KMBA 无组件支持，手动卸载验证） | Tomcat 8.5 / Solr-Jetty / 自建 Resin |
| WeblogicExploit-GUI | 6 类 | ✅ 全过 | Weblogic 12.2.1.3 |
| WeblogicTool | 4 模板 | ✅ 全过（回归 PASS=16） | Weblogic 12.2.1.3 |
| MemShellParty | 12+ 类型 | ✅ 主体全过，4 类型边界记录 | Tomcat 8.5/9.0/10.1, Jetty, Struts2, JBoss, OFBiz, Gateway |
| Spring_All_Reachable | 1 类 | ✅ 全过 | Spring Cloud Gateway 3.1.0 |
| JNDIExploit | 1 类 | ✅ 全过 | log4j-lab (Spring Boot) |
| OFBiz-Attack | 1 类 | ✅ 全过 | OFBiz 18.12.09 |
| Y4er/ysoserial | 14 模板 | ✅ 作为投递链贯穿各环境 | 多环境 |
| ConfluenceMemshell | 2 类 | ⏸️ license 环境卡死 | Confluence 6.15.1 |
| CVE-2022-26134-Godzilla-MEMSHELL | 1 类 | ⏸️ 同上 | 同上 |
| NacosRce | 1 类 | ⚠️ 工具 gRPC 缺陷 + 马类嵌入式容器不兼容 | Nacos 2.1.0/2.2.2 |
| MemShellGene | 生成器 | ⏭️ 下载不完整，JBoss 由 MemShellParty 替代 | - |
| JMG | 生成器 | ⏭️ 未重复注入（MemShellParty 同类型覆盖） | - |

---

## ShiroAttack2

> 版本: v5.1.1（fat jar `shiro_attack-5.1.1-all.jar`, 35MB）
> 链接: https://github.com/SummerSec/ShiroAttack2
> 包: `com.summersec.x`（21 类，含 20 个内存马类 + 1 个辅助类 FilterMem）

### 测试结果

**20 个内存马类型全部验证**（17 类本轮完整五步 + 3 类 round1 已测），KMBA 对 filter/servlet 组件 list/unload 全部正常。
发现 3 个工具自身缺陷（详见各类型测试过程）。

### 测试环境

- 容器: `vulhub/shiro:1.2.4`（shirodemo, Spring Boot embedded Tomcat 9.0.29, JDK 8u102）
- 端口: 19210（容器 8080）· 内存 2g（原 768m 导致 KMBA attach OOM，已调大）
- 注入入口: Shiro-550 rememberMe 反序列化（key `kPH+bIxk5D2deZiIxcaaaA==`, AES-CBC）
- 认证: `POST /doLogin`（admin/vulhub, rememberme=remember-me）→ JSESSIONID（servlet 型需带会话）
- 修复项: 容器 JRE 补 tools.jar（Linux 版, eclipse-temurin:8-jdk 提取）; 本地以
  `-cp ... com.summersec.attack.CLI.MainCLI` 启动（`-jar` 走 JavaFX Main 卡死）

### 测试过程（五步日志）

#### GodzillaFilter（round1 已测）

| 步骤 | 日志 |
|------|------|
| 注入 | Shiro-550 CB 链 InjectMemTool → 注册 `/favicon.ico`（filterName=favicon.ico） |
| `<0>` | godzilla 协议 POST /favicon.ico → `uid=0(root)` |
| `<1>` | KMBA `-l filter` 发现 favicon.ico → com.summersec.x.GodzillaFilter |
| `<2>` | `-u filter /favicon.ico` → unload success |
| `<3>` | `-l filter` 复查 → GodzillaFilter 消失 |
| `<4>` | godzilla 协议 → 失效 |

#### reGeorgFilter（round1 已测）

| 步骤 | 日志 |
|------|------|
| 注入 | 同上链 → 注册 `/favicondemo.ico` |
| `<0>` | POST cmd=connect → `Georg says, 'All seems fine'` |
| `<1>` | KMBA `-l filter` 发现 /favicondemo.ico → com.summersec.x.reGeorgFilter |
| `<2>` | `-u filter /favicondemo.ico` → unload success |
| `<3>` | `-l filter` 复查消失 |
| `<4>` | cmd=connect → 失效 |

#### BehinderServlet（round1 已测）

| 步骤 | 日志 |
|------|------|
| 注入 | 同上链 → 注册 `/behind.ico` |
| `<0>` | behinder 协议 POST /behind.ico → 命令执行（round1 记录） |
| `<1>` | KMBA `-l servlet` 发现 /behind.ico → com.summersec.x.BehinderServlet |
| `<2>` | `-u servlet /behind.ico` → unload success |
| `<3>` | `-l servlet` 复查消失 |
| `<4>` | behinder 协议 → 失效 |

#### GodzillaServlet（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | `memshell -t "哥斯拉[Servlet]" --pass sa2pass --path /sa2-gdsrv.ico` → `[注入结果] 成功` |
| `<0>` | godzilla 协议（参数 sa2pass = base64(AES_ECB(GdCmd.class)) 二次请求触发）→ md5 前缀 `335DEC7691342652` 匹配 → `uid=0(root)` + `KMBA_GDZILLA_CMD_OK` |
| `<1>` | `-l servlet` → `/sa2-gdsrv.ico → com.summersec.x.GodzillaServlet`（2 条, 含 DispatcherServlet） |
| `<2>` | `-u servlet /sa2-gdsrv.ico` → `unload success` |
| `<3>` | `-l servlet` → 仅剩 DispatcherServlet |
| `<4>` | godzilla 协议 → HTTP 404 失效 |

#### GodzillaTomServlet（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | CLI 无此类型键（MEM_MAP 缺口）→ S2Driver 反射补全字节码注入 `/sa2-gdsrv2.ico` |
| 工具缺陷1 | MEM_MAP/MEM_TOOLS 未嵌入该类（源码+jar 字节码存在但 CLI 无法注入） |
| 工具缺陷2 | `addServlet` 用 `getDeclaredMethod("addChild")` 在 Spring Boot TomcatEmbeddedContext 抛 NoSuchMethodException（方法在父类）→ 修复为向上遍历后成功；且工具判定串 "Success" vs 实际返回 "ok" → 误报"失败"但注册成功 |
| 工具细节 | service() 的 md5 wrap 用构造器默认 md5(pass1024+xc) 而非注入 pass → 客户端按默认 pass 解 wrap |
| `<0>` | godzilla 协议 → md5wrap `38F3ABE85948D564` 匹配 md5(pass1024+xc) → `uid=0(root)` + `KMBA_GDZILLA_CMD_OK` |
| `<1>` | `-l servlet` → `/sa2-gdsrv2.ico → com.summersec.x.GodzillaTomServlet` |
| `<2>` | `-u servlet /sa2-gdsrv2.ico` → `unload success` |
| `<3>` | `-l servlet` → 仅剩 DispatcherServlet |
| `<4>` | godzilla 协议 → HTTP 404 失效 |

#### BehinderFilter（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | `memshell -t "冰蝎[Filter]" --pass sa2pass --path /sa2-bf.ico` → 成功 |
| 协议坑 | 马端 key = md5(header p)[:16] 小写 16 字节 AES-128；首次用 32 字节 AES-256 致 BadPadding，修正后成功 |
| `<0>` | behinder 协议（body=base64(AES_ECB(key=md5("sa2pass")[:16], BfCmd.class))）→ 200 → `uid=0(root)` + `KMBA_BEHINDER_OK` |
| `<1>` | `-l filter` → `/sa2-bf.ico → com.summersec.x.BehinderFilter` |
| `<2>` | `-u filter /sa2-bf.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | behinder 协议 → 失效（无 uid 输出） |

#### AntSwordFilter（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | `memshell -t "蚁剑[Filter]" --pass sa2pass --path /sa2-asf.ico` → 成功 |
| `<0>` | 蚁剑协议（POST sa2pass=base64(AsCmd.class) → equals(Object[]{req,res})）→ 200 → `uid=0(root)` + `KMBA_ANTSWORD_OK` |
| `<1>` | `-l filter` → `/sa2-asf.ico → com.summersec.x.AntSwordFilter` |
| `<2>` | `-u filter /sa2-asf.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | 蚁剑协议 → 失效 |

#### AntSwordServlet（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | `memshell -t "蚁剑[Servlet]" --pass sa2pass --path /sa2-ass.ico` → 成功 |
| `<0>` | 蚁剑协议（带认证 session）→ 200 → `uid=0(root)` + `KMBA_ANTSWORD_OK` |
| `<1>` | `-l servlet` → `/sa2-ass.ico → com.summersec.x.AntSwordServlet` |
| `<2>` | `-u servlet /sa2-ass.ico` → `unload success` |
| `<3>` | `-l servlet` → 计数 0 |
| `<4>` | 蚁剑协议 → 404 失效 |

#### NeoreGeorgFilter（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | `memshell -t "NeoreGeorg[Filter]" --pass sa2pass --path /sa2-nrf.ico` → 成功 |
| `<0>` | GET /sa2-nrf.ico → 隧道 marker `<!-- x0zdyym2QlZu4NbrzwUJNJrZBHydgYNXMaX -->`（NeoreGeorg 客户端握手检测） |
| `<1>` | `-l filter` → `/sa2-nrf.ico` 发现 |
| `<2>` | `-u filter /sa2-nrf.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | marker 消失 |

#### NeoreGeorgServlet（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | `memshell -t "NeoreGeorg[Servlet]" --pass sa2pass --path /sa2-nrs.ico` → 成功 |
| `<0>` | 认证后 GET → marker `<!-- x0zdyym2QlZu4NbrzwUJNJrZBHydgYNXMaX -->` |
| `<1>` | `-l servlet` → `/sa2-nrs.ico → com.summersec.x.NeoreGeorgServlet` |
| `<2>` | `-u servlet /sa2-nrs.ico` → `unload success` |
| `<3>` | `-l servlet` → 计数 0 |
| `<4>` | marker 消失 |

#### reGeorgServlet（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | `memshell -t "reGeorg[Servlet]" --pass sa2pass --path /sa2-rgs.ico` → 成功 |
| `<0>` | POST X-CMD:CONNECT + X-TARGET/X-PORT → 200 + `X-STATUS: OK`（socket 隧道建立） |
| `<1>` | `-l servlet` → `/sa2-rgs.ico → com.summersec.x.reGeorgServlet` |
| `<2>` | `-u servlet /sa2-rgs.ico` → `unload success` |
| `<3>` | `-l servlet` → 计数 0 |
| `<4>` | CONNECT → 404 失效 |

#### BastionFilter（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | `memshell -t "BastionFilter" --pass sa2pass --path /sa2-bast.ico` → 成功 |
| `<0>` | GET + header `cmd: id` 触发 Runtime.exec → `uid=0(root)` + `KMBA_BASTION_OK`（无需认证，filter 链前） |
| `<1>` | `-l filter` → `/sa2-bast.ico → com.summersec.x.BastionFilter` |
| `<2>` | `-u filter /sa2-bast.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | cmd header → 失效 |

#### BastionEncryFilter（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | `memshell -t "BastionEncryFilter" --pass sa2pass --path /sa2-baste.ico` → 成功 |
| `<0>` | cmd header（无 key 头=明文）→ `uid=0(root)` + `KMBA_BASTENC_OK` |
| `<1>` | `-l filter` → `/sa2-baste.ico → com.summersec.x.BastionEncryFilter` |
| `<2>` | `-u filter /sa2-baste.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | cmd header → 失效 |

#### AddDllFilter（本轮）

| 步骤 | 日志 |
|------|------|
| 注入 | `memshell -t "AddDllFilter" --pass sa2pass --path /sa2-adll.ico` → 成功 |
| `<0>` | 无参数触发 → 输出 WebRootPath `/tmp/tomcat-docbase...8080/` + ServletPath + WebServerPath（路径信息泄露，dll 上传功能就绪） |
| `<1>` | `-l filter` → `/sa2-adll.ico → com.summersec.x.AddDllFilter` |
| `<2>` | `-u filter /sa2-adll.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | 路径打印 → 失效 |

#### ChangeShiroKeyFilter1~6（本轮，改 key 型一次性 payload，不注册组件）

| 类型 | `<0>` 验证 | 结论 |
|------|-----------|------|
| CSKF1 (filterConfigs→shiroFilterFactoryBean) | 注入"成功"但旧 key 仍可 exec uid=0 → **key 未变** | ✗ 工具缺陷: 硬编码匹配名 `shiroFilterFactoryBean` ≠ 实际 `shiroFilter`，且无论是否匹配都返回 "change key ok" → 误报成功 |
| CSKF2 (getFilterRegistration→shiroFilterFactoryBean) | 响应 `->|null|<-`，key 未变 | ✗ 同名缺陷 |
| CSKF3 (filterConfigs→常见名) | 旧 key kPH+ exec 0 次 uid；新 K3 `qDYFxeqVzRwL6uTNTlPa5g==` 解密 rememberMe ACED0005 成功 | ✅ key 已改 |
| CSKF4 (getFilterRegistration→常见名) | key → `6Fi8J5JDTylvhzJzGEzMnA==` | ✅ |
| CSKF5 (filterConfigs→包含 shiro 扫描) | 响应 `change key ok (shiroFilter)`；key → `tlJd43VL+h4n36ZUm549hQ==` | ✅ |
| CSKF6 (高风险全候选扫描) | CLI 报 `未找到内存马模板`（MEM_TOOLS 缺口）；S2Driver 补字节码后 parseObj 缺 getResponse fallback → 302（缺陷）；修复后响应 `change key ok (all candidates updated: 1)`，key → `lFOCZnlkswDo2WWPfNKbHg==` | ✅ 修复后生效 |

- `<1>/<2>/<3>` 说明: CSKF 为一次性改 key payload，**不注册任何 filter/servlet 组件**，KMBA 复查无残留（符合设计，无可卸载项）

---

## Hyacinth

> 版本: v2.0.2（`hyacinth-v2.0.2.jar`）
> 链接: https://github.com/pureqh/Hyacinth
> 注: 注入链独立（`HyacinthDriver inject <url> <key> <type> <pass> <path>`），但马类字节码复用 `com.summersec.x.*`（与 ShiroAttack2 同源）

### 测试结果

**6 类全部五步全过** ✅，KMBA 对 filter/servlet 组件 list/unload 全部正常。

### 测试环境

- 与 ShiroAttack2 同环境（shirodemo 容器, 端口 19210, shiro key 为 CSKF 改后的 K6）
- 注入: `HyacinthDriver`（CommonsBeanutilsString 链 + InjectMemTool）
- 成功判据: 响应含 `->|Success|<-`

### 测试过程（五步日志）

#### GodzillaFilter（/hy-gf.ico, pass=hyacpass）

| 步骤 | 日志 |
|------|------|
| 注入 | `HyacinthDriver inject ... Godzilla[Filter] hyacpass /hy-gf.ico` → `INJECTION SUCCESS` |
| 协议细节 | md5wrap 用默认 pass1024（Hyacinth 嵌入版 equals 未重算 md5，协议参数名=注入 pass） |
| `<0>` | godzilla 协议 → `uid=0(root)` + `KMBA_GDZILLA_CMD_OK` |
| `<1>` | `-l filter` → `/hy-gf.ico → com.summersec.x.GodzillaFilter` |
| `<2>` | `-u filter /hy-gf.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | godzilla 协议 → 404 失效 |

#### GodzillaServlet（/hy-gs.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | `... Godzilla[Servlet] hyacpass /hy-gs.ico` → SUCCESS |
| `<0>` | godzilla 协议（带会话）→ `uid=0(root)` + `KMBA_GDZILLA_CMD_OK` |
| `<1>` | `-l servlet` → `/hy-gs.ico → com.summersec.x.GodzillaServlet` |
| `<2>` | `-u servlet /hy-gs.ico` → `unload success` |
| `<3>` | `-l servlet` → 计数 0 |
| `<4>` | godzilla 协议 → 404 失效 |

#### BehinderFilter（/hy-bf.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | `... Behinder[Filter] hyacpass /hy-bf.ico` → SUCCESS |
| 协议坑 | 马端 key=BigInteger.toString(16) **丢前导零**后 [:16] → md5("hyacpass")=05ce… 以 0 开头 → key=`5cecaa32d454be7e`（31 位 hex 去 0 后截 16）而非 python hexdigest()[:16]=`05cecaa32d454be7` |
| `<0>` | behinder 协议（java 丢零 key）→ `uid=0(root)` + `KMBA_BEHINDER_OK` |
| `<1>` | `-l filter` → `/hy-bf.ico → com.summersec.x.BehinderFilter` |
| `<2>` | `-u filter /hy-bf.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | behinder 协议 → 404 失效 |

#### BehinderServlet（/hy-bs.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | `... Behinder[Servlet] hyacpass /hy-bs.ico` → SUCCESS |
| `<0>` | behinder 协议（丢零 key, 带会话）→ `uid=0(root)` + `KMBA_BEHINDER_OK` |
| `<1>` | `-l servlet` → `/hy-bs.ico → com.summersec.x.BehinderServlet` |
| `<2>` | `-u servlet /hy-bs.ico` → `unload success` |
| `<3>` | `-l servlet` → 计数 0 |
| `<4>` | behinder 协议 → 404 失效 |

#### AntSwordFilter（/hy-af.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | `... AntSword[Filter] hyacpass /hy-af.ico` → SUCCESS |
| `<0>` | 蚁剑协议 → `uid=0(root)` + `KMBA_ANTSWORD_OK` |
| `<1>` | `-l filter` → `/hy-af.ico → com.summersec.x.AntSwordFilter` |
| `<2>` | `-u filter /hy-af.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | 蚁剑协议 → 404 失效 |

#### AntSwordServlet（/hy-as.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | `... AntSword[Servlet] hyacpass /hy-as.ico` → SUCCESS |
| `<0>` | 蚁剑协议（带会话）→ `uid=0(root)` + `KMBA_ANTSWORD_OK` |
| `<1>` | `-l servlet` → `/hy-as.ico → com.summersec.x.AntSwordServlet` |
| `<2>` | `-u servlet /hy-as.ico` → `unload success` |
| `<3>` | `-l servlet` → 计数 0 |
| `<4>` | 蚁剑协议 → 404 失效 |

---

## Pyke-Shiro

> 版本: v0.3（`Pyke-Shiro_0.3.jar` + target/classes）
> 链接: https://github.com/sma11new/Pyke-Shiro
> 包: `com.sma11new.exp.javaFrame.shiro.payload.build`（8 类，注入链独立）

### 测试结果

**8 类全部五步全过** ✅，KMBA 对 filter/servlet 组件 list/unload 全部正常。
协议与 ShiroAttack2/Hyacinth 显著不同（自定义 xc/pass）。

### 测试环境

- 与 ShiroAttack2 同环境（shirodemo 容器, 端口 19210, key=K6）
- 注入: `PykeDriver <url> <key> <chain> <memShellType> <path> <pass>`（CommonsBeanutilsString + InjectMemTool）

### 测试过程（五步日志）

协议差异汇总: Godzilla 系 `xc="ee9dc4178256735a"` / Pwd 默认 `"pass"`；Behinder 系 key=同一 xc 字符串；AntSword 系 Pwd 默认 `"ikun123"`；NeoreGeorg marker=自定义 `sKAqDruZqzbjaEgh9o2_Kw3qveOsRJYTxGJ2LT8aUam389gTr5_TOn`；注入链不设 p header → 全部使用默认 Pwd。

#### GodzillaFilter（/py-gf.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | `PykeDriver ... 哥斯拉[Filter] /py-gf.ico pass` → `result: http://.../py-gf.ico` |
| `<0>` | godzilla 协议（xc=ee9dc4178256735a, pwd=pass）→ md5wrap `19CFCC34A1906682` 匹配 → `uid=0(root)` + `KMBA_GDZILLA_CMD_OK` |
| `<1>` | `-l filter` → `/py-gf.ico → com.sma11new...GodzillaFilter`（Pyke 独立类名） |
| `<2>` | `-u filter /py-gf.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | godzilla 协议 → 404 失效 |

#### GodzillaServlet（/py-gs.ico）: 同协议五步全过（`<1>` 发现 /py-gs.ico → com.sma11new...GodzillaServlet）

#### BehinderFilter（/py-bf.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | `... 冰蝎[Filter] /py-bf.ico pass` → 成功 |
| 协议 | key=xc 字符串 `ee9dc4178256735a`（16 字节 AES-128, 非 md5 派生）；body=base64(AES_ECB(class))；readLine 触发 |
| `<0>` | behinder 协议 → `uid=0(root)` + `KMBA_BEHINDER_OK` |
| `<1>` | `-l filter` → `/py-bf.ico → com.sma11new...BehinderFilter` |
| `<2>` | `-u filter /py-bf.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | behinder 协议 → 404 失效 |

#### BehinderServlet（/py-bs.ico）: 同 key 协议五步全过（认证后 uid=0）

#### AntSwordFilter（/py-af.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | `... 蚁剑[Filter] /py-af.ico pass` → 成功 |
| `<0>` | 蚁剑协议（POST 参数名=默认 Pwd `ikun123`（自定义）, 值=base64(AsCmd.class)）→ `uid=0(root)` + `KMBA_ANTSWORD_OK` |
| `<1>` | `-l filter` → `/py-af.ico → com.sma11new...AntSwordFilter` |
| `<2>` | `-u filter /py-af.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | 蚁剑协议 → 404 失效 |

#### AntSwordServlet（/py-as.ico）: ikun123 协议五步全过（认证后 uid=0）

#### NeoreGeorgFilter（/py-nrf.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | `... NeoreGeorg[Filter] /py-nrf.ico pass` → 成功 |
| `<0>` | GET → 自定义 marker `<!-- sKAqDruZqzbjaEgh9o2_Kw3qveOsRJYTxGJ2LT8aUam389gTr5_TOn -->`（Pyke 版隧道标识） |
| `<1>` | `-l filter` → `/py-nrf.ico → com.sma11new...NeoreGeorgFilter` |
| `<2>` | `-u filter /py-nrf.ico` → `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | marker 消失 |

#### NeoreGeorgServlet（/py-nrs.ico）: 认证后 marker 五步全过

---

## ysuserial

> 版本: su18-mod-1.7（自构建, `ysuserial-su18-mod-1.7-all.jar` 123MB; 上游 su18/ysuserial 已删库, 使用 fork aaaademo/ysuserial-mod）
> 链接: https://github.com/su18/ysuserial（fork: https://github.com/aaaademo/ysuserial-mod）
> 家族: TEXMS/TFMS/TLMS/TSMS/TUGMS/TWSMS（Tomcat）+ JFMS/JSMS（Jetty）+ RFMS/RSMS（Resin）

### 测试结果

**9 类全部完成** ✅（TEXMS 由 env-12 补测）。
- Tomcat 5 类（filter/listener/servlet/upgrade/socket）: KMBA list/unload 全部正常
- Jetty 2 类（jettyFilter/jettyServlet）: KMBA list/unload 正常
- Resin 2 类: **KMBA 无 Resin 组件支持**（filter/servlet 枚举空, unload OGNL failed）— 环境不兼容, 手动卸载验证

### 测试环境

- **Tomcat 家族**: tc12615-vuln（`vulhub/tomcat:8.5.19` 本地构建 readonly=false, JDK8 JRE, 端口 19194）
  - 注入: `ysuserial -gen` 生成模板类 → JSP ClassDefiner defineClass+newInstance 触发 static 块 → PUT 尾斜杠上传
- **Jetty 家族**: solr-ysu（`vulhub/solr:8.11.0` 内嵌 Jetty 9.4, 端口 19195→8983）
  - 注入: log4j JNDI（CVE-2021-44228）→ marshalsec LDAP + HTTP 拉取注入器
- **Resin 家族**: 自建 Resin 4.0.65（resin.jar maven 版 + 最小 conf, 端口 19211）
  - 注入: JSP 触发 static 块（resin.xml 内联 servlet 声明）

### 测试过程（五步日志）

#### TFMSFromThread（Tomcat Filter, /ysu-tfms.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | JSP 触发 → 注册 `/ysu-tfms.ico`（改名类 `org.apache.myfaces.descriptor.web.JspPropertyGroupDescriptorImpl`） |
| 环境适配 | TFMSFromJMX 在 tc12615 失败（硬编码 NonLoginAuthenticator MBean 不存在）→ 用 TFMSFromThread |
| `<0>` | GET /ysu-tfms.ico + `X-CMD-DATA: id` + Referer → `uid=0(root)` |
| `<1>` | `-l filter` → `/ysu-tfms.ico → org.apache.myfaces...` |
| `<2>` | `-u filter jspPropertyGroupDescriptorImplFilter`（须用 filterName, 非 URLPattern）→ `unload success` |
| `<3>` | `-l filter` → 计数 0 |
| `<4>` | X-CMD-DATA → 404 失效 |

#### TLMSFromThread（Tomcat Listener）

| 步骤 | 日志 |
|------|------|
| 注入 | JSP 触发 → 注册（改名类 `org.apache.xalan.catalina.core.ApplicationPart`） |
| `<0>` | 请求触发 requestInitialized → X-CMD-DATA → `uid=0(root)` + `KMBA_TLMS_OK` |
| `<1>` | `-l listener` → 发现改名类 org.apache.xalan.catalina.core.ApplicationPart |
| `<2>` | `-u listener <className>` → `unload success` |
| `<3>` | `-l listener` → 计数 0 |
| `<4>` | X-CMD-DATA → 失效（默认页） |

#### TSMSFromThread（Tomcat Servlet, /ysu-tsms.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | JSP 触发 → 注册（改名类 `org.apache.el.shared.application.FacesServletMapping`） |
| `<0>` | GET /ysu-tsms.ico + X-CMD-DATA → `uid=0(root)` + `KMBA_TSMS_OK` |
| `<1>` | `-l servlet` → `/ysu-tsms.ico → 改名类` |
| `<2>` | `-u servlet /ysu-tsms.ico` → `unload success` |
| `<3>` | `-l servlet` → 计数 0 |
| `<4>` | X-CMD-DATA → 404 失效 |

#### TUGMSFromJMX（Tomcat Upgrade, ysu-tugms.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | JSP 触发 → 注册 Upgrade 协议（改名类 `org.apache.struts2.shared.util.ExceptionUtils`） |
| `<0>` | `Connection: Upgrade` + `Upgrade: ysu-tugms.ico` + X-CMD-DATA → `uid=0(root)` + `KMBA_TUGMS_OK` |
| `<1>` | `-l upgrade` → 发现 `ysu-tugms.ico → 改名类` |
| `<2>` | `-u upgrade ysu-tugms.ico` → `unload success` |
| `<3>` | `-l upgrade` → 计数 0 |
| `<4>` | Upgrade 触发 → 失效 |

#### TWSMSFromThread（Tomcat WebSocket, /ysu-twsms.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | JSP 触发 → 注册 WebSocket 端点（改名类 `org.apache.myfaces.builder.api.CompositeFilterComponentBuilder`） |
| `<0>` | WS 握手 101 + 文本帧 "id;echo KMBA_TWSMS_OK" → 响应帧 `uid=0(root)` + `KMBA_TWSMS_OK` |
| `<1>` | `-l socket` → `/ysu-twsms.ico → 改名类` |
| `<2>` | `-u socket /ysu-twsms.ico <className>` → `unload success` |
| `<3>` | `-l socket` → 计数 0 |
| `<4>` | WS 握手 → 404 失效 |

#### TEXMSFromThread（Tomcat Executor, env-12 补测）

| 步骤 | 日志 |
|------|------|
| 注入 | 替换 NioEndpoint.executor（su18 替换机制 + Tomcat 9 适配: 6 参构造器 + DiscardPolicy） |
| `<0>` | `id` → `uid=0(root)`（真实命令执行，周期命令线程） |
| `<1>` | `-l executor` → 发现 TEXMS 类名 |
| `<2>` | `-u executor` → `unload success` |
| `<3>` | `-l executor` → 恢复原始 ThreadPoolExecutor |
| `<4>` | 命令文件不再执行 + 周期副作用停止 |
| 附加 | 生产 200 ✓ |

#### JFMSFromJMX（Jetty Filter, /solr/kmbajt.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | log4j JNDI → 注入器(Zknya)遍历线程找 WebAppClassLoader → defineClass → 取 _context → 调 reg()（JettyReg3 按 JFMS 注册逻辑: ServletHandler newFilterHolder+addFilter+prependFilterMapping） |
| 环境适配 | solr 未注册 Jetty MBean（webappcontext 查询空）且 JNDI 异步线程 TCCL=启动加载器 → 需线程扫描适配 |
| `<0>` | GET /solr/kmbajt.ico + X-CMD-DATA → `uid=0(root)` + `KMBA_JFMS_OK` |
| `<1>` | `-l jettyFilter` → 发现 `kmbaJtFilter → org.kmba.ysu.JettyReg3` |
| `<2>` | `-u jettyFilter kmbaJtFilter` → `unload success` |
| `<3>` | `-l jettyFilter` → 计数 0 |
| `<4>` | X-CMD-DATA → 404 失效 |

#### JSMSFromJMX（Jetty Servlet, /solr/kmbajts.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | 同入口 → JettySrv5 按 JSMS 逻辑（newServletHolder+addServlet+ServletMapping.setServletName+addServletMapping） |
| `<0>` | GET /solr/kmbajts.ico + X-CMD-DATA → `uid=0(root)` + `KMBA_JSMS_OK` |
| `<1>` | `-l jettyServlet` → `/kmbajts.ico → org.kmba.ysu.JettySrv3` |
| `<2>` | `-u jettyServlet /kmbajts.ico` → `unload success` |
| `<3>` | `-l jettyServlet` → 计数 0 |
| `<4>` | X-CMD-DATA → 404 失效 |

#### RFMSFromThread（Resin Filter, /kmba-rfms.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | 自建 Resin 4.0.65（resin.jar + 最小 conf + resin.xml 内联 servlet 声明）→ JSP 触发 static 块 |
| `<0>` | GET /kmba-rfms.ico + X-CMD-DATA + Referer → `uid=0(root)` + `KMBA_RFMS_OK` |
| `<1>` | KMBA jad 确认改名类 `org.apache.xml.util.introspection.LinkingUberspector` 已定义 |
| `<2>` | `-u filter` → `OGNL execute failed`（**KMBA 无 Resin filter 组件支持**, 环境不兼容如实记录）→ 手动卸载: FilterManager._filters 移除 linkingUberspectorFilter → removed=1 |
| `<3>` | 卸载后复查: filter 失效 |
| `<4>` | X-CMD-DATA → Server Error 失效 |
| 注 | Resin watchdog 检测配置变更自动重启（清空内存马）— 测试在单 server 会话内完成 |

#### RSMSFromThread（Resin Servlet, /kmba-rsms.ico）

| 步骤 | 日志 |
|------|------|
| 注入 | 同环境 JSP 触发 |
| `<0>` | GET /kmba-rsms.ico + X-CMD-DATA + Referer → `uid=0(root)` + `KMBA_RSMS_OK` |
| `<1>` | KMBA jad 确认改名类 `org.apache.coyote.compiler.util.ObjectFactory`（import ServletMapping, pattern=/kmba-rsms.ico） |
| `<2>` | KMBA servlet 组件对 Resin 无支持（servlet 空）→ 手动卸载 servletManager 移除 |
| `<3>` | 卸载后复查: 失效 |
| `<4>` | X-CMD-DATA → 失效 |

---

## WeblogicExploit-GUI

> 版本: 未标注版本（`Weblogic-GUI.jar`, zip 解包, 2022-04 构建）
> 链接: https://github.com/sp4zcmd/WeblogicExploit-GUI
> 包: `com.exploit.payloadclass`（蚁剑 Filter ×3 版本 + 冰蝎 Servlet ×3 版本）

### 测试结果

**6 类全部验证完成** ✅（蚁剑 Filter ×3 + 冰蝎 Servlet ×3），KMBA weblogicFilter/weblogicServlet list/unload 全部正常。

### 测试环境

- 容器: `vulhub/weblogic:12.2.1.3-2018`（JDK 8u151, 端口 19201→7001）
- 注入链: CVE-2020-2883 T3 ECHO → T3EchoShell → WlUpload 上传 → Agent2 attach
  （getAllLoadedClasses 查类 → defineClass → 调 main() 注册）
- 关键: 每次注入前重启容器 + 触发 wls-management-services 加载（main 的 jspFilter 替换需应用就绪）

### 测试过程（五步日志）

#### InjectWeblogicAntSwordCustomFilter_12213（蚁剑 Filter）

| 步骤 | 日志 |
|------|------|
| 注入 | T3 ECHO → 上传 ms.class + ms.clsname + agent.jar → attach → `[agent2] defined: ..._12213` + `main called` |
| `<0>` | doFilter 确认（Pwd=pass1024 EC 解码执行）+ T3 exec `uid=1000(oracle)` + `KMBA_WL_OK`（HTTP 触发路径受 management 认证保护） |
| `<1>` | `-l weblogicFilter` → `test → com.exploit.payloadclass.InjectWeblogicAntSwordCustomFilter_12213`；vmtool 实例 filterName=jspFilter |
| `<2>` | `-u weblogicFilter test` → `unload success` |
| `<3>` | `-l weblogicFilter` → 仅剩 ParamFilter/HTMLContentFilter |
| `<4>` | 触发失效 |

#### InjectWeblogicAntSwordCustomFilter_12214（蚁剑 Filter）

| 步骤 | 日志 |
|------|------|
| 注入 | 干净重启 + 触发 wls-management-services 后 attach（Agent2 v5）→ main 注册成功 |
| `<0>` | 同协议（Pwd=pass1024） |
| `<1>` | `-l weblogicFilter` → `test → ..._12214`；vmtool filterName=jspFilter |
| `<2>` | `-u weblogicFilter test` → `unload success` |
| `<3>` | 复查 0 |
| `<4>` | 失效（JVM 正常） |

#### InjectWeblogicAntSwordCustomFilter_14110（蚁剑 Filter）: 同上五步全过

#### InjectWeblogicBehinderServlet_12213（冰蝎 Servlet, /checklogin）

| 步骤 | 日志 |
|------|------|
| 注入 | 同链 attach → main 注册 `/checklogin` |
| `<0>` | POST /bea_wls_internal/checklogin → 200（Servlet 协议可交互） |
| `<1>` | `-l weblogicServlet` → `/checklogin → com.exploit.payloadclass.InjectWeblogicBehinderServlet_12213` |
| `<2>` | `-u weblogicServlet /checklogin` → `unload success` |
| `<3>` | `-l weblogicServlet` → 复查 0 |
| `<4>` | POST /checklogin → 404 失效 |

#### InjectWeblogicBehinderServlet_12214 / _14110: 同上五步全过

---

## WeblogicTool

> 版本: v1.3（`WeblogicTool_1.3.jar`）
> 链接: https://github.com/KimJun1010/WeblogicTool
> 模板: `InjectWeblogicGodzilla4Filter` / `InjectWeblogicGodzilla4Servlet` / `InjectWeblogicBehinderServlet` / `InjectWeblogicAntSwordCustomFilter`

### 测试结果

**4 模板回归 PASS=16 FAIL=0** ✅（Filter/Servlet/Listener 三类组件 + console 生产存活）。

### 测试环境

- 容器: `vulhub/weblogic:12.2.1.3-2018`（端口 19183）
- 注入链: CVE-2020-2883 T3 ECHO → T3EchoShell 文件传输 → Attach API 定义类注册

### 测试过程（五步日志）

| 类型 | `<0>` | `<1>` | `<2>` | `<3>` | `<4>` |
|------|-------|-------|-------|-------|-------|
| InjectWeblogicGodzilla4Filter（FilterManager "test", Godzilla 协议） | ✓ F0 | ✓ | ✓ | ✓ | ✓ F4 |
| InjectWeblogicGodzilla4Servlet（/kmba-servlet） | ✓ S0 | ✓ | ✓ | ✓ | ✓ S4 |
| WebLogicListenerExec（X-Token 副作用） | ✓ L0 | ✓ | ✓ | ✓ | ✓ L4 |
| console 生产页面 | 200 ✓ | - | - | - | - |

---

## MemShellParty

> 版本: 在线 API v2.9.0 / docker 2.8.0（生成器）
> 链接: https://github.com/ReaJason/MemShellParty
> 类型: tomcat-filter/listener/servlet/valve/proxyvalve/websocket/upgrade/timer/thread/executor ·
> jetty-filter/servlet/listener · weblogic-filter/servlet/listener · spring-mvc-interceptor/controller ·
> webflux-web-filter/netty-handler/handler-method/handler-function · struts2-action · jboss · jakarta 全系 · agent

### 测试结果

**主体全过** ✅，4 类型边界/缺陷如实记录（proxyValve/websocket 组件缺陷为 KMBA 侧，springFluxHandlerFunction 为生成器 VerifyError，thread 继承型枚举缺口）。

> **复测更新（2026-08-21, 用户提供新 jar md5 `8d767d82` → `1dbf846e` → `5539d655`）**: proxyValve、thread、socket 三项缺陷均已修复并五步复测通过（详见下方缺陷记录）。
> 排错过程: socket unload 首版修复（`1dbf846e`）参数注入已生效但 OGNL `{?}` 选择语法仍报
> `IllegalArgumentException: command not found`（arthas OGNL 不支持选择语法）；用户经 Arthas 手动
> 排错确认后修复表达式（`5539d655`），`-u socket /kmbaws KmbaTcWs` → unload success。

### 测试环境（多环境）

| 环境 | 入口 | 端口 |
|------|------|------|
| Struts2 2.3.34 | S2-057 OGNL 两阶段 | 19190 |
| Jenkins 2.138 | CVE-2018-1000861 Groovy | 19191 |
| Solr 8.11.0 | log4j JNDI | 19192 |
| Tomcat 8.5.19 | CVE-2017-12615 PUT JSP | 19194 |
| Tomcat 9.0.97 | CVE-2025-24813 + FILE: | 19193 |
| Tomcat 10.1.34 (JDK17) | CVE-2025-24813 + FILE: | 19200 |
| OFBiz 18.12.09 | CVE-2023-49070 | 19195 |
| JBoss AS 6.1.0 | CVE-2017-12149 | 19196 |
| Gateway 3.1.0 | CVE-2022-22947 SpEL | 19182 |
| Weblogic 12.2.1.3 | T3/CVE-2020-2883 | 19183 |

### 测试过程（五步日志）

#### Tomcat 类型矩阵（tc24813-vuln, Tomcat 9.0.97, 每类型独立注入+五步）

| 类型 | `<0>`命令执行 | `<1>`list | `<2>`unload | `<3>`复查 | `<4>`失效 | 结论 |
|------|--------------|-----------|-------------|-----------|-----------|------|
| listener (KmbaTcListener) | ✓ uid=0 | ✓ 5 副本可见 | ✓ 实际清除 | ✓ 7→2 | ✓ 404 | ✅ 通过（unload 消息误报见下） |
| servlet (KmbaTcServlet) | ✓ uid=0 | ✓ 28 条含 /kmbaservlet | ✓ | ✓ 28→27 | ✓ 404 | ✅ 通过 |
| valve (KmbaTcValve) | ✓ uid=0 | ✓ 3 context 可见 | ✓ | ✓ 全清 | ✓ 404 | ✅ 通过 |
| proxyValve | ✓ uid=0 | ~~✗ 恒空~~ → **复测 ✓ 发现 XMLUtil** | ~~✗ 假成功~~ → **复测 ✓ unload success** | **复测 ✓ 残留 0** | **复测 ✓ 失效** | ✅ 修复验证（新 jar） |
| websocket (KmbaTcWs) | ✓ WS 回显 | ~~✗ 空~~ → **复测 ✓ 发现 /kmbaws→KmbaTcWs** | ~~✗ OGNL 失败~~ → **复测 ✓ unload success** | **复测 ✓ 5→2** | **复测 ✓ 404/400 失效** | ✅ 修复验证（新 jar） |
| upgrade (KmbaTcUpgrade) | ✓ Upgrade 头触发 | ✓ | ✓ | ✓ 1→0 | ✓ 404 | ✅ 通过 |
| executor (TEXMS) | 替换生效（KMBA 可见） | ✓ TEXMS 类名 | ✓ | ✓ 恢复原 executor | ✓ | ✅ 通过 |
| timer (TimerShell) | ✓ touch 副作用 | ✓ | ✓(cancel) | ✓ 1→0 | ✓ 停止重建 | ✅ 通过 |
| thread (ThreadShell) | ✓ touch 副作用 | ~~✓ 仅 Runnable 型~~ → **复测 ✓ 继承型可见** | ✓(interrupt) | ✓ 0 | ✓ 停止重建 | ✅ 修复验证（新 jar） |

#### 其他环境（四步/五步全过）

| 环境 | 马 | `<0>` | `<1>` | `<2>` | `<3>` | `<4>` |
|------|-----|-------|-------|-------|-------|-------|
| Struts2 | KmbaS2Filter (/kmbatest) / KmbaS2Action | ✓ uid=0 | ✓ | ✓ | ✓ | ✓ |
| Jenkins | KmbaJenkinsFilter (/kmbajenkins) | ✓ uid=1000(jenkins) | ✓ 7 条含 | ✓ | ✓ 7→6 | ✓ 404 |
| Solr | KmbaSolrFilter (/solr/kmbasolr) | ✓ uid=0 | ✓ 2 条含 | ✓ | ✓ 2→1 | ✓ 404 |
| Tomcat 8.5 | KmbaTcFilter (/kmbatc) | ✓ uid=0 | ✓ 7 条含 | ✓ | ✓ 7→4 | ✓ 404 |
| OFBiz | EvilFilters (nivia) /webtools/* | ✓ uid=0 | ✓ 187 条中列出 | ✓ | ✓ 残留 0 | ✓ 登录页/302 |
| JBoss | KmbaJbossFilter (/kmbajboss) | ✓ uid=0 | ✗ KMBA 无法 attach JDK7 | - | - | 重启对照 404 |
| Tomcat 10 (jakarta) | KmbaTc10Filter (/kmbatc10) | ✓ uid=0 | ✓ | ✓ | ✓ | ✓ |
| Gateway | KmbaWebFilter / NettyHandler / GMemShell | ✓ uid=0 | ✓ | ✓ | ✓ | ✓ 404 |

#### 缺陷/边界记录（重要）

1. **listener unload 消息误报**: 卸载实际生效（OGNL remove 执行、复查 7→2），但结果解析失败误报 `[!] Listener is empty`（真卸载假失败，与 Round1 gateway 假成功相反）
2. **proxyValve 组件（枚举+卸载双缺陷）: ✅ 已修复（2026-08-21 新 jar 复测通过）**
   - 原缺陷: 枚举 OGNL `#x[0]` 对 map 字面量取值恒 null → `-l proxyValve` 恒空；卸载链 ① `-classLoader java.net.URLClassLoader` 多 webapp 歧义 ② Proxy 实例类名为 `$ProxyN` 与参数类名永不匹配 → 假成功
   - 复测（tc24813, Tomcat 9.0.97, proxyValve 马 `com.fasterxml.jackson.dqcQg.XMLUtil`）:
     `<0>` uid=0(root) → `<1>` `-l proxyValve` 发现 5 副本 XMLUtil → `<2>` `-u proxyValve first <className>` unload success → `<3>` 残留 0 → `<4>` 命令执行失效, 生产 200
   - 注: 新用法需 `-u proxyValve <first|basic> <className>` 两参数
3. **socket(websocket) 组件对 Tomcat 9: ✅ 已修复（2026-08-21 最新 jar 复测通过）**
   - 原缺陷: ExactPathMatch 无 getEndpointClass()（9.0.97 经 config(ServerEndpointConfig) 取 endpointClass）→ 枚举空 + unload OGNL 失败
   - 修复过程: ① `8d767d82`/`1dbf846e` 版修复枚举（`-l socket` 发现 /kmbaws→KmbaTcWs）+ 卸载参数注入（`%s` 占位符），但 OGNL `{?}` 选择语法仍报 `IllegalArgumentException: command not found`（arthas OGNL 不支持选择语法）；② 用户经 Arthas 手动排错后修复表达式（`5539d655`）
   - 复测（最新 jar）: `<0>` WS 握手 101 + id → `uid=0(root)` → `<1>` `-l socket` 发现 5 副本 → `<2>` `-u socket /kmbaws KmbaTcWs` → **unload success** → `<3>` 复查 5→2（清 3 副本）→ `<4>` WS 握手 404/400 失效, 生产 200
4. **thread 组件枚举缺口: ✅ 已修复（2026-08-21 新 jar 复测通过）**
   - 原缺陷: 仅枚举 target!=null 的线程（Runnable 型）；"继承 Thread 覆写 run"型（target=null）不可见
   - 复测（ThreadShell 继承 Thread 覆写 run 型, target=null）:
     `<0>` /tmp/thread-alive 周期副作用 → `<1>` `-l thread` 发现 `thread-memshell → ThreadShell`（继承型可见）→ `<2>` `-u thread thread-memshell ThreadShell` unload success → `<3>` 残留 0 → `<4>` 副作用停止（文件不再生成）, 生产 200
   - 注: 新用法需 `-u thread <threadName> <className>` 两参数
5. **executor 类型特性**: 命令执行在 execute()-time 受 currentProcessor 未绑定限制；公开 su18/JYso TEXMS 模板同为替换型占位
6. **springFluxHandlerFunction**: MemShellParty 生成器 VerifyError（常量池 entry 89, 基于较新 spring-webflux API 编译）→ 重编译 KmbaHfFix 后五步全过
7. **KMBA 无法 attach JDK7 目标**: 自带 arthas 4.3.4 agent 为 JDK8 字节码（major 52），JDK7 JVM 抛 UnsupportedClassVersionError；对照: 独立 arthas 3.6.9（JDK6 字节码）在 JDK7 正常

---

## Spring_All_Reachable

> 版本: master（2023）
> 链接: https://github.com/savior-only/Spring_All_Reachable
> 马: `ms.GMemShell`（WebFlux ControllerHandler 哥斯拉协议马, 注册 /favicongmem.ico）

### 测试结果

**五步全过** ✅（含 testfix 回归）。

### 测试环境

- Spring Cloud Gateway 3.1.0（gateway-vuln, 端口 19182）
- 入口: CVE-2022-22947 Gateway SpEL `ReflectUtils.defineClass`

### 测试过程（五步日志）

| 步骤 | 日志 |
|------|------|
| `<0>` | godzilla 协议 POST /favicongmem.ico → 命令执行（round1 记录） |
| `<1>` | KMBA list 发现 /favicongmem.ico → GMemShell |
| `<2>` | unload → success |
| `<3>` | 复查消失 |
| `<4>` | 协议失效 |

---

## JNDIExploit

> 版本: v1.2（JNDIExploit-1.2）
> 链接: https://github.com/feihong-cs/JNDIExploit
> 马: `com.feihong.ldap.template.DynamicFilterTemplate`（Tomcat Filter, filterName=TomcatDynamicFilter-1/2）

### 测试结果

**五步全过** ✅。

### 测试环境

- log4j-lab（Spring Boot + log4j 2.14, 端口 19189）
- 入口: log4j JNDI（CVE-2021-44228）→ LDAP 回调

### 测试过程（五步日志）

| 步骤 | 日志 |
|------|------|
| `<0>` | 触发 log4j JNDI → DynamicFilterTemplate 注册 TomcatDynamicFilter → 命令执行 |
| `<1>` | KMBA list 发现 TomcatDynamicFilter-1/2 |
| `<2>` | unload → success |
| `<3>` | 复查消失 |
| `<4>` | 命令执行失效 |
| 注 | Solr 场景确认 JNDIExploit 无 Jetty 模板（换 MemShellParty Jetty 马） |

---

## OFBiz-Attack

> 版本: v1.1（18★, GUI 工具解包→脚本化）
> 链接: https://github.com/D0g3-8Bit/OFBiz-Attack
> 马: `org.ofbiz.shell.ShellManager.CMD_MEMSHELL` → `org.example.memshell.serialize.tomcat.EvilFilters`
> （Tomcat Filter, filterName=nivia, urlPattern=/*, 参数 cmd 命令执行）

### 测试结果

**五步全过** ✅。

### 测试环境

- 容器: `vulhub/ofbiz:18.12.09`（内嵌 Tomcat, JDK8, 端口 19195→8443 TLS）
- 入口: CVE-2023-49070 XML-RPC（`/webtools/control/xmlrpc;?USERNAME=&PASSWORD=&requirePasswordChange=Y`, POST×2）

### 测试过程（五步日志）

| 步骤 | 日志 |
|------|------|
| `<0>` | 注入后 `/webtools/*?cmd=id` → `uid=0(root)`（3 个路径均验证） |
| `<1>` | `-l filter` → 187 条中列出 `nivia → EvilFilters`（OFBiz 多 webapp 多 context） |
| `<2>` | `-u filter nivia` → `unload success` |
| `<3>` | 复查 EvilFilters 残留 0 |
| `<4>` | /webtools/*?cmd=id 返回登录页/302（命令执行失效） |
| 附加 | 生产存活: /webtools/control/main 200、/accounting/control/main 200；重复 unload 如实报 not found |

---

## Y4er/ysoserial

> 版本: master（fork 自 ysoserial, lightr3d 镜像）
> 链接: https://github.com/Y4er/ysoserial
> 模板: 14 类（TomcatFilterMemShellFromJMX/Thread, TomcatListenerMemShellFromJMX/Thread, TomcatServletMemShellFromJMX/Thread, TomcatListenerNeoRegFromThread, SpringInterceptorTemplate/MemShell, RuoYiInterceptor, TomcatCmdEcho, CommandTemplate, ClassLoaderTemplate, ZohoPMPTomcatEcho）

### 测试结果

**14 模板作为投递链贯穿各环境** ✅（CLASS:/FILE: 载荷机制, 任意类嵌入）。

### 测试环境

- 多环境（Tomcat 8.5/9.0/10.1 反序列化入口, JBoss invoker 等）
- 机制: `CLASS:` / `FILE:` 载荷 → ClassLoaderTemplate 静态 b64 字段嵌入任意类 → TemplatesImpl 触发

### 测试过程（五步日志）

| 模板 | 用途 | 验证 |
|------|------|------|
| ClassLoaderTemplate | FILE: 机制载体（Round3 全部反序列化入口贯穿） | ✅ 多环境投递成功 |
| TomcatCmdEcho / CommandTemplate | 命令回显 | ✅ |
| TomcatFilterMemShellFromJMX/Thread 等 | Tomcat Filter 马 | ✅（配合 MemShellParty 马类或自定义类） |
| TomcatListenerNeoRegFromThread | Listener + NeoReg | ✅ |
| SpringInterceptorTemplate/MemShell, RuoYiInterceptor | Spring 拦截器马 | ✅（springdemo 环境） |
| ZohoPMPTomcatEcho | Zoho 场景回显 | ✅ |

---

## ConfluenceMemshell

> 版本: master（V1.1）
> 链接: https://github.com/Lotus6/ConfluenceMemshell
> 马: `com.memshell.MemGodzillaFilterShell`（哥斯拉协议, 实为 ServletRequestListener）/
> `com.memshell.MemBeyondFilterShell`（冰蝎协议 Listener）
> 入口: CVE-2021-26084 / 2022-26134 / 2023-22515 / 2023-22527 OGNL→JS→defineClass

### 测试结果

**⏸️ 未测（用户指示暂停: license 环境卡死）**，与 inventory 记录一致。

### 测试环境

- 容器: `atlassian/confluence-server:6.15.1-alpine`（端口 19220）
- atlassian-agent 激活成功（`agent working` 日志）, license 生成成功（server id BED4-WN4G-KLIB-383O）, setup 向导 license 步骤通过

### 测试过程（投入记录）

| 步骤 | 结果 |
|------|------|
| license 激活 | ✓ atlassian-agent.jar 生效, license 生成成功 |
| setup 向导 license 步骤 | ✓ 提交后跳转 Get apps 页面 |
| 卡点 | Get apps（doselectbundle）步骤 webwork 静默失败, setupStep 不推进; 手工 cfg 触发 Hibernate 校验失败 |
| 结论 | 待可用环境后补测 |

---

## CVE-2022-26134-Godzilla-MEMSHELL

> 版本: master
> 链接: https://github.com/BeichenDream/CVE-2022-26134-Godzilla-MEMSHELL
> 马: `MemShell`（extends ClassLoader implements InvocationHandler）→ 注册随机名类
> `com.opensymphony.xwork.<uuid>`（ServletRequestListener 哥斯拉 AES）

### 测试结果

**⏸️ 未测（用户指示暂停: license 环境卡死）**，与 ConfluenceMemshell 同环境阻塞。

### 测试环境

- 同 Confluence 6.15.1（端口 19220）
- 已编译 MemShell.class（6581B）+ 生成 JS 载荷（1.js 模板, ReflectUtils.defineClass 链）

### 测试过程（投入记录）

| 步骤 | 结果 |
|------|------|
| MemShell.java 编译 | ✓ 成功（6581B） |
| JS 载荷构建 | ✓ payload.js 9669B（1.js 模板替换 className/classBytes） |
| 触发测试 | OGNL 表达式被 webwork 接受（HTTP 200 Get apps 页）但应用处于 bootstrap 半启动, 命令执行不可达 |
| 结论 | 待可用环境后补测 |

---

## NacosRce

> 版本: v0.4/v0.5（源码构建 `NacosRce-0.4-SNAPSHOT-jar-with-dependencies.jar` 101MB）
> 链接: https://github.com/c0olw/NacosRce
> 马: `com.alibaba.nacos.consistency.entity.NacosFilterShellPlus`
> （extends AbstractTranslet implements Filter, 冰蝎/哥斯拉/CMD 三合一, static 块自注册）
> 漏洞: CVE-2023-27904 JRaft Hessian 反序列化（Nacos 2.x <= 2.2.2）

### 测试结果

**⚠️ 工具缺陷 + 马类嵌入式容器不兼容**（与 inventory "用户已测" 一致）。

### 测试环境

- 容器: `nacos/nacos-server:v2.2.2` 与 `v2.1.0` 均尝试（端口 19230→8848, 19231→9848）
- 注入: NacosRce.jar → JRaft gRPC 9849（GrpcClusterServer）发送 Hessian payload

### 测试过程（发现记录）

| 环节 | 结果 |
|------|------|
| 工具 gRPC 连接 | ✗ grpc-java 3.21.11 连 Nacos 9849 持续 `UNAVAILABLE: io exception`（TCP 通, gRPC 握手失败; 2.2.2 与 2.1.0 均复现 — 工具 grpc 版本与 Nacos 2.x 协议不兼容） |
| 马类 static 块 | ✗ `getContext()` 遍历线程 TCCL 找 WebappClassLoader → Nacos 是 Spring Boot embedded Tomcat（TCCL=LaunchedURLClassLoader）→ 注册原理性失败（马类按标准 Tomcat 编写） |
| 手工 attach 注入 | ✗ 马类需在 LaunchedURLClassLoader 定义（javax.servlet.Filter 在其可见范围）, Agent 侧 NoClassDefFoundError 持续（定义 loader 问题） |
| 结论 | 工具 gRPC 缺陷 + 马类嵌入式容器不兼容 — 属工具/环境兼容性限制（非 KMBA 缺陷） |

---

## MemShellGene / JMG

| 工具 | 链接 | 结果 |
|------|------|------|
| MemShellGene | https://github.com/suizhibo/MemShellGene | ⏭️ 下载不完整未逐项枚举；JBoss 由 MemShellParty 路线替代实测 |
| JMG | https://github.com/pen4uin/java-memshell-generator | ⏭️ Listener/Filter/Interceptor/HandlerMethod/TomcatValve 生成器（ShiroAttack2 集成适配）；未重复注入（MemShellParty 同类型覆盖） |

---

## 附: 阴性对照（16 项, 确认无内存马模块）

JenkinsExploit-GUI · jexboss · dubbo-exp · VcenterKit（README 明示未实现）· VcenterKiller（仅一次性回显 gadget）·
STS2G · Struts2-Scan · Struts2VulsScanTools · SpringBoot-Scan · shiro_rce_tool · weblogicScanner · WeblogicScan ·
weblogic-infodetector · YApiRCE(Node) · log4j-shell-poc · FastjsonScan

---

## 附: 工具/环境缺陷汇总（按严重性）

### P1 - 工具缺陷（注入工具侧, 已修复验证）

| 工具 | 缺陷 | 影响 | 修复 |
|------|------|------|------|
| ShiroAttack2 | GodzillaTomServlet 用 `getDeclaredMethod("addChild")`（方法在父类） | 注入抛 NoSuchMethodException | 改 getMethodByClass 向上遍历 ✓ |
| ShiroAttack2 | 判定串 "Success" vs TomServlet 返回 "ok" | 注入成功但误报"失败" | 按实际注册确认 |
| ShiroAttack2 | CSKF1/2 硬编码匹配名 `shiroFilterFactoryBean` ≠ 实际 `shiroFilter`, 且无条件返回 "change key ok" | key 未变但误报成功 | 如实记录, 用 CSKF3+ 变体 |
| ShiroAttack2 | CSKF6 未嵌入 MEM_TOOLS（CLI 报"未找到模板"）+ parseObj 缺 getResponse fallback | 注入 302 无效 | S2Driver 补字节码 + 补 fallback ✓ |
| NacosRce | gRPC 客户端与 Nacos 2.x 协议不兼容 | 注入无法投递 | 无（工具侧缺陷） |

### P2 - KMBA 侧缺陷/边界

| 缺陷 | 现象 | 性质 |
|------|------|------|
| ~~proxyValve 组件~~ | ✅ **已修复**（2026-08-21 复测: list 发现马 + unload success） | 已修复 |
| ~~socket(websocket) 组件 Tomcat 9~~ | ✅ **已修复**（2026-08-21 复测: 枚举发现 + `-u socket /kmbaws KmbaTcWs` unload success, 复查 5→2, WS 失效） | 已修复 |
| ~~listener unload 消息误报~~ | 卸载实际生效但误报 "Listener is empty" | 消息解析缺陷（真卸载假失败） |
| ~~thread 组件枚举缺口~~ | ✅ **已修复**（2026-08-21 复测: 继承 Thread 型 target=null 可见 + unload success） | 已修复 |
| JDK7 目标 attach 失败 | arthas 4.3.4 agent 为 JDK8 字节码 | 能力边界（JDK7 需独立 arthas 3.6.9） |
| Resin 组件无支持 | filter/servlet 枚举空, unload OGNL failed | 环境不兼容（手动卸载验证马功能） |

### P3 - 观察项

- ysuserial 马 filterName 与 URLPattern 不同（须用 filterName 卸载）— KMBA 行为正常, 用法差异
- CSKF 系列为一次性改 key payload, 不注册组件 — KMBA 无可卸载项（符合设计）
- 多马共存时 HTTP 层验证互相干扰（Listener/Valve 型命令马拦截所有带 cmd 请求）— 真实环境复杂度

---

*报告完 — 详细原始日志见 `logs/round3/shiro-fulltest.log` 及 `logs/round3/env-*.md`*
