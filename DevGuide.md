# 开发者指南

## 开发环境
* **主要语言**: [Java][java]
* **次要语言**: [C++][cpp]、[Sleigh][sleigh]、[Python 3][python]、[Jython 2.7][jython]
* **集成开发环境 (IDE)**: [Eclipse][eclipse]
* **构建系统**: [Gradle][gradle]
* **版本控制**: [Git][git]

具体版本要求和下载链接请参阅 [README.md](README.md)。

---

## 快速入门
按照 [README.md](README.md) 中的 [高级开发指南](README.md#高级开发) 快速配置开发环境。

---

## 许可与版权
* **主许可证**: [Apache License 2.0][apache]
* **其他许可证**: [参见 licenses 目录](licenses)

开发 Ghidra 时请尽量遵循 Apache 2.0 许可证。如需引入其他兼容许可证，需将 GPL 代码独立存放在 `GPL/` 目录下并单独构建。

代码贡献者的署名将通过 Git 提交记录体现，请确保 Git 账户信息正确关联 GitHub 账号。**不推荐**在源码中直接添加作者信息。

---

## 常用 Gradle 任务
| 任务                                      | 功能说明                                                                 |
|------------------------------------------|--------------------------------------------------------------------------|
| `gradle -I gradle/support/fetchDependencies.gradle` | 下载非 Maven 依赖项（生成 `dependencies` 目录）                          |
| `gradle prepdev`                         | 下载 Maven 依赖并初始化开发环境（依赖存放于 `$HOME/.gradle/`）            |
| `gradle cleanEclipse eclipse`            | 生成 Eclipse 项目文件（支持嵌套项目导入）                                |
| `gradle buildNatives`                    | 构建当前平台的本地组件（需安装本地工具链）                                |
| `gradle sleighCompile`                   | 手动编译 Sleigh 文件（运行时也会自动编译）                               |
| `gradle createJavadocs`                  | 生成 Javadoc 文档                                                        |
| `gradle buildPyPackage`                  | 构建 PyGhidra 和 Debugger 的 Python3 包                                  |
| `gradle assembleAll`                     | 生成未压缩的 Ghidra 分发版（仅限当前平台运行）                           |
| `gradle buildGhidra`                     | 生成压缩的 Ghidra 分发版（仅限当前平台运行）                             |
| `gradle buildGhidra -x <task>`           | 跳过指定任务加速构建（如 `-x ip` 跳过 IP 头检查）                        |

---

## PyGhidra 开发
**推荐工具**: Eclipse + [PyDev][pydev] 插件  
**配置步骤**:
1. 运行 `gradle prepPyGhidra` 创建 Python 虚拟环境 (`build/venv`)。
2. 在 Eclipse 中配置解释器路径为 `build/venv/bin/python3`。
3. 添加类型提示路径 `build/typestubs/pypredef`。

---

## GhidraDev 插件开发
**前提**: 安装 Eclipse PDE (插件开发环境)  
**初始化项目**:  
```bash
gradle prepGhidraDev eclipse -PeclipsePDE
```
导入生成的 GhidraDev 项目后，需激活目标平台 `GhidraDev.target`。

---

## 离线开发环境迁移
1. 在线环境执行:  
   ```bash
   gradle -I gradle/support/fetchDependencies.gradle
   gradle -g dependencies/gradle prepdev
   ```
2. 将整个仓库（含 `dependencies` 目录）迁移至离线环境。
3. 离线环境执行:  
   ```bash
   gradle -g dependencies/gradle buildGhidra
   ```

---

## 测试与持续集成
| 测试类型                 | 命令                          |
|-------------------------|-------------------------------|
| 单元测试                | `gradle unitTestReport`       |
| 集成测试                | `gradle integrationTest`      |
| 综合测试报告            | `gradle combinedTestReport`   |

**无头模式配置 (Linux/CI)**:
```bash
Xvfb :99 -nolisten tcp &
export DISPLAY=:99
```

---

## 支持数据构建
### 数据类型存档 (Data Type Archives)
1. 通过 Ghidra GUI: **File -> Parse C Source** 创建解析配置。
2. 点击 **Parse to File** 生成 `.gdt` 存档。
3. 将存档复制到 `Ghidra/Features/Base/data/typeinfo`。

### 函数ID数据库 (FID Databases)
1. 启用 **Function ID** 插件。
2. 创建空数据库: **Tools -> Function ID -> Create new empty FidDb**。
3. 填充数据库: **Tools -> Function ID -> Populate FidDb**。

官方构建数据托管于 [ghidra-data][ghidra-data] 仓库。

---

## 调试器开发
### 架构概览
- **后端协议**: 使用 Python3 + Protobuf 实现跨平台通信。
- **核心组件**: 
  - `Debugger-rmi-trace`: RMI 协议实现。
  - `Debugger-agent-*`: 各调试器适配 (GDB/WinDbg/LLDB)。
  - `Framework-TraceModeling`: 状态跟踪数据库模型。

### 开发新连接器
1. **参考实现**: 以 `Debugger-agent-gdb` 或 `Debugger-agent-dbgeng` 为模板。
2. **Python 代码**: 位于各模块的 `src/main/py` 目录。
3. **测试分类**: 
   - **Commands**: 验证 CLI 命令功能。
   - **Methods**: 测试远程方法调用。
   - **Hooks**: 检查事件监听机制。
4. **更新包**: 修改代码后运行 `gradle assemblePyPackage`。

---

## 故障排查
### Eclipse 问题
- **项目错误**: 删除带 `?` 图标的无效项目，重新生成并导入。
- **启动配置丢失**: 确保模块项目已导入，检查 `.launch/` 目录。

### 已知问题
- **Gradle 本地化问题**: 非英文系统需设置 `LC_MESSAGES=en_US.UTF-8`。
- **Python 环境**: 建议使用虚拟环境解决 `pip` 访问问题。

[java]: https://dev.java
[cpp]: https://isocpp.org
[sleigh]: https://htmlpreview.github.io/?https://github.com/NationalSecurityAgency/ghidra/blob/stable/GhidraDocs/languages/index.html
[python]: https://www.python.org
[venv]: https://docs.python.org/3/tutorial/venv.html
[jython]: https://www.jython.org
[eclipse]: https://www.eclipse.org/downloads/
[pydev]: https://www.pydev.org
[gradle]: https://gradle.org
[git]: https://git-scm.com
[apache]: https://www.apache.org/licenses/LICENSE-2.0
[fork]: https://docs.github.com/en/get-started/quickstart/fork-a-repo
[ghidra-data]: https://github.com/NationalSecurityAgency/ghidra-data
[DbgGuide]: DebuggerDevGuide.md
