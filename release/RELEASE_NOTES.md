# KMBA v0.3

KMBA（Killer of Memory-Based Attacks）是一款 Java 内存马应急响应工具，支持 **Web 管理界面**和 **CLI 命令行**两种模式，覆盖 Tomcat 与 SpringMVC 共 12 种内存马类型，使用 [Arthas](https://arthas.aliyun.com/) 作为诊断引擎，无需重启目标应用即可完成检测与清除。

## 更新日志

### v0.3
- **新增 CLI 命令行模式** — 支持 headless 环境、SSH 远程和脚本批量操作，无需浏览器即可完成检测与卸载
- 新增 SpringMVC Controller / Interceptor 两种内存马支持（共 12 种类型）
- 入口类 `KmbaApplication` 自动识别 Web / CLI 模式
- CLI 模式默认关闭日志输出，可通过 `--log true` 开启调试

### v0.2
- 添加 SpringMVC 两种内存马支持
- 优化代码结构

---

## 📦 下载说明

| 文件名 | 平台 | 说明 |
|--------|------|------|
| `KMBA-0.3-win-amd64-with-jdk.zip` | Windows x86_64 | 含 JDK 8，双击 `start.bat` 启动 |
| `KMBA-0.3-win-arm64-with-jdk.zip` | Windows ARM64 | 含 JDK 8，双击 `start.bat` 启动 |
| `KMBA-0.3-macos-amd64-with-jdk.zip` | macOS Intel | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-0.3-macos-aarch64-with-jdk.zip` | macOS Apple Silicon | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-0.3-linux-amd64-with-jdk.zip` | Linux x86_64 | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-0.3-linux-aarch64-with-jdk.zip` | Linux ARM64 | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-0.3.jar` | 全平台 | 需自行安装 JDK 8+，`java -jar KMBA-0.3.jar` 启动 |

### with-jdk 版本适用情况

with-jdk 版本**内置 JDK 8，开箱即用，无需提前配置 Java 环境**，适合以下场景：

- 应急响应时目标机器未安装 JDK，或已有 JDK 版本不兼容
- 隔离网络、受限环境或无法联网安装依赖的场景
- 希望快速部署、避免环境问题干扰排查效率

> with-jdk 包体积较大（约 200MB+）。若系统已安装 JDK 8+，推荐直接使用 `KMBA-*.jar`。

---

## ⚠️ 免责声明

本工具仅限**授权的安全运维和应急响应**场景使用。请仅在您拥有或被明确授权分析的系统中使用。作者不承担任何滥用导致的后果。
