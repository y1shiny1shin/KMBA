## 更新日志

### v0.4.1
- 优化`Arthas`连接问题，优化建立连接和断开连接的逻辑
- 优化`springFluxWebFilter`支持
- ~~优化代码逻辑，减少意外中的报错~~
- 优化一部分日志输出逻辑

---

## 📦 下载说明

| 文件名                                 | 平台 | 说明 |
|-------------------------------------|------|------|
| `KMBA-*-win-amd64-with-jdk.zip`     | Windows x86_64 | 含 JDK 8，双击 `start.bat` 启动 |
| `KMBA-*-win-arm64-with-jdk.zip`     | Windows ARM64 | 含 JDK 8，双击 `start.bat` 启动 |
| `KMBA-*-macos-amd64-with-jdk.zip`   | macOS Intel | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-*-macos-aarch64-with-jdk.zip` | macOS Apple Silicon | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-*-linux-amd64-with-jdk.zip`   | Linux x86_64 | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-*-linux-aarch64-with-jdk.zip` | Linux ARM64 | 含 JDK 8，运行 `./start.sh` 启动 |
| `KMBA-*.jar`                        | 全平台 | 需自行安装 JDK 8+，`java -jar KMBA-0.3.jar` 启动 |

### with-jdk 版本适用情况

with-jdk 版本**内置 JDK 8，开箱即用，无需提前配置 Java 环境**，适合以下场景：

- 应急响应时目标机器未安装 JDK，或已有 JDK 版本不兼容
- 隔离网络、受限环境或无法联网安装依赖的场景
- 希望快速部署、避免环境问题干扰排查效率

> with-jdk 包体积较大（约 200MB+）。若系统已安装 JDK 8+，推荐直接使用 `KMBA-*.jar`。

---

## ⚠️ 免责声明

本工具仅限**授权的安全运维和应急响应**场景使用。请仅在您拥有或被明确授权分析的系统中使用。作者不承担任何滥用导致的后果。
