# KMBA v0.1

KMBA（Killer of Memory-Based Attacks）是一款基于 Web 的 Java 内存马应急响应工具，专为 Apache Tomcat 环境设计，使用 [Arthas](https://arthas.aliyun.com/) 作为诊断引擎，无需重启目标应用即可完成检测与清除。

## 🛡️ 功能特性

- **10 种内存马类型**全覆盖：Servlet、Filter、Listener、WebSocket、ProxyValve、Valve、Executor、Thread、Timer、Upgrade
- **按需 JAD 反编译**：直接从运行中的 JVM 提取任意已加载类的 Java 源码
- **关键字可疑扫描**：可自定义规则引擎，标记包含恶意特征的类（如 `ProcessBuilder`、`getRuntime`、`javax.crypto.*` 等）
- **多种卸载策略**：针对不同类型提供温和（cancel/interrupt）和强制两种模式，附带风险说明
- **本地 & 远程连接**：通过 `jps` + 内置 `arthas-boot.jar` 连接本地 JVM，或通过 WebSocket 连接远程 Arthas 代理
- **单页 Web 管理界面**：暗色主题，无 CDN 依赖，所有前端资源本地化

启动后访问 `http://localhost:9099`

---

## 📦 下载说明

| 文件名 | 平台 | 说明 |
|--------|------|------|
| `KMBA-0.1-win-amd64-with-jdk.zip` | Windows x86_64 | 含 JDK 8，双击 `start.bat` 启动 |
| `KMBA-0.1-win-arm64-with-jdk.zip` | Windows ARM64 | 含 JDK 8，双击 `start.bat` 启动 |
| `KMBA-0.1-macos-amd64-with-jdk.zip` | macOS Intel | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-0.1-macos-aarch64-with-jdk.zip` | macOS Apple Silicon | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-0.1-linux-amd64-with-jdk.zip` | Linux x86_64 | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-0.1-linux-aarch64-with-jdk.zip` | Linux ARM64 | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-0.1.jar` | 全平台 | 需自行安装 JDK 8+，`java -jar KMBA-0.1.jar` 启动 |

### with-jdk 版本适用情况

with-jdk 版本**内置 JDK 8，开箱即用，无需提前配置 Java 环境**，适合以下场景：

- 应急响应时目标机器未安装 JDK，或已有 JDK 版本不兼容
- 隔离网络、受限环境或无法联网安装依赖的场景
- 希望快速部署、避免环境问题干扰排查效率

> with-jdk 包体积较大（约 200MB+）。若系统已安装 JDK 8+，推荐直接使用 `KMBA-0.1.jar`。

---

## ❤️ 致谢

感谢以下文章和项目的作者，本工具的实现参考了他们的研究成果：

- [su18](https://su18.org/post/memory-shell/) — Java 内存马系列深度研究
- [drun1baby](https://drun1baby.top/2022/08/22/Java内存马系列-03-Tomcat-之-Filter-型内存马/) — Tomcat Filter 型内存马分析
- [y4er](https://y4er.com/posts/tomcat-upgrade-memshell/) — Tomcat Valve / Upgrade 内存马研究
- [chenlvtang](https://chenlvtang.top/2022/08/03/Tomcat之Listener内存马/) — Tomcat Listener 型内存马分析
- [FreeBuf](https://www.freebuf.com/articles/vuls/345119.html) — Tomcat Upgrade 内存马技术分析
- [ReaJason](https://github.com/ReaJason/MemShellParty) — MemShellParty 内存马注入框架
- [Arthas](https://arthas.aliyun.com/) — 阿里巴巴 Java 诊断工具，本项目核心引擎

---

## ⚠️ 免责声明

本工具仅限**授权的安全运维和应急响应**场景使用。请仅在您拥有或被明确授权分析的系统中使用。作者不承担任何滥用导致的后果。
