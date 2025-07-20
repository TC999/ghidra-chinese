<img src="Ghidra/Features/Base/src/main/resources/images/GHIDRA_3.png" width="400">

这是 Ghidra 的社区汉化版，原版请访问[NationalSecurityAgency/ghidra](https://github.com/NationalSecurityAgency/ghidra)

# Ghidra 软件逆向工程框架
Ghidra 是由[美国国家安全局][nsa]研究部门创建和维护的软件逆向工程（SRE）框架。该框架包含一套功能齐全的高端软件分析工具，使用户能够分析多种平台（包括 Windows、macOS 和 Linux）上的编译代码。其功能涵盖反汇编、汇编、反编译、图形化分析和脚本编写，以及数百项其他特性。Ghidra 支持广泛的处理器指令集和可执行格式，并可在用户交互模式和自动化模式下运行。用户还可使用 Java 或 Python 开发自己的 Ghidra 扩展组件和/或脚本。

为支持 NSA 的网络安全使命，Ghidra 旨在解决复杂 SRE 项目中的规模化和团队协作问题，并提供可定制、可扩展的 SRE 研究平台。NSA 已将 Ghidra 的 SRE 能力应用于各类涉及恶意代码分析的任务，并为寻求深入理解网络和系统潜在漏洞的 SRE 分析师提供深度洞察。

如果您是美国公民，并对开发 Ghidra 及其他网络安全工具以帮助保护美国及其盟友的项目感兴趣，请考虑申请[加入我们][career]。

## 安全警告
**警告：** 某些版本的 Ghidra 存在已知安全漏洞。在继续操作前，请仔细阅读 Ghidra 的[安全公告][security]，以了解可能受到的影响。

## 安装
要安装官方预构建的多平台 Ghidra 版本：
* 安装 [JDK 21 64 位版][jdk]
* 下载 Ghidra [发行文件][releases]
  - **注意：** 官方多平台发行文件名为 `ghidra_<版本>_<发行版>_<日期>.zip`，可在 "Assets" 下拉列表中找到。此步骤不应下载任何标记为 "Source Code" 的文件。
* 解压 Ghidra 发行文件
* 启动 Ghidra：`./ghidraRun`（Windows 使用 `ghidraRun.bat`）
  - 或启动 [PyGhidra][pyghidra]：`./support/pyGhidraRun`（Windows 使用 `support\pyGhidraRun.bat`）

有关安装和运行 Ghidra 版本的更多信息及故障排除提示，请参阅 Ghidra 安装目录根目录下的[入门指南][gettingstarted]。

## 构建
从源代码仓库创建适用于您平台的最新开发构建：

##### 安装构建工具：
* [JDK 21 64 位版][jdk]
* [Gradle 8.5+][gradle]（如有网络连接可使用附带的 Gradle 包装器）
* [Python3][python3]（3.9 至 3.13 版本）含内置 pip
* make、gcc/g++ 或 clang（仅限 Linux/macOS）
* [Microsoft Visual Studio][vs] 2017+ 或安装以下组件的 [Microsoft C++ 生成工具][vcbuildtools]（仅限 Windows）：
  - MSVC
  - Windows SDK
  - C++ ATL

##### 下载并解压源代码：
[从 GitHub 下载][chinese]
```
unzip ghidra-chinese
cd ghidra-chinese
```
**注意：** 您也可以克隆 GitHub 仓库代替下载压缩包：`git clone https://github.com/TC999/ghidra-chinese.git`


##### 下载额外构建依赖到源代码仓库：
**注意：** 如已连接网络且未安装 Gradle，以下 `gradle` 命令可替换为 `./gradle(.bat)`。

```
gradle -I gradle/support/fetchDependencies.gradle
```

##### 创建开发构建：
```
gradle buildGhidra
```
压缩的开发构建文件将位于 `build/dist/` 目录。

更详细的构建说明请参阅[开发者指南][devguide]。构建问题可查看[已知问题][known-issues]获取解决方案。

此外，汉化作者 tc999 编写了一个 [GitHub 工作流](.github/workflows/build.yml)自动编译

## 开发

### 用户脚本与扩展
Ghidra 安装包支持用户通过 Eclipse 的 *GhidraDev* 插件编写自定义脚本和扩展。该插件及说明文档位于发行版的 `Extensions/Eclipse/GhidraDev/` 目录或[此链接][ghidradev]。您也可通过脚本管理器中的 Visual Studio Code 图标使用 VS Code 编辑脚本。完整的 VS Code 项目可通过 Ghidra 代码浏览器窗口的 _工具 -> 创建 VSCode 模块项目_ 生成。

**注意：** 适用于 Eclipse 的 *GhidraDev* 插件和 VS Code 集成仅支持基于完整构建的 Ghidra 安装包（需从[发行版][releases]页面下载）。

### 高级开发
建议使用 Eclipse 进行 Ghidra 核心开发，因其已深度适配 Ghidra 开发流程。

##### 安装构建与开发工具：
* 完成上述[构建步骤](#build)确保无错误
* 安装 [Eclipse IDE for Java Developers][eclipse]

##### 准备开发环境：
``` 
gradle prepdev eclipse buildNatives
```

##### 将 Ghidra 项目导入 Eclipse：
* *文件* -> *导入...*
* *常规* | *现有项目到工作空间*
* 选择克隆/下载的 ghidra 源代码仓库作为根目录
* 勾选 *搜索嵌套项目*
* 点击 *完成*

Eclipse 完成项目构建后，可通过预置的 **Ghidra** 运行配置启动和调试程序。更详细的开发说明请参阅[开发者指南][devguide]。

## 贡献
如果您希望为 Ghidra 贡献错误修复、改进或新功能，请查阅我们的[贡献者指南][contrib]，了解如何参与这个开源项目。


[nsa]: https://www.nsa.gov
[contrib]: CONTRIBUTING.md
[devguide]: DevGuide.md
[gettingstarted]: GhidraDocs/GettingStarted.md
[known-issues]: DevGuide.md#known-issues
[career]: https://www.intelligencecareers.gov/nsa
[releases]: https://github.com/NationalSecurityAgency/ghidra/releases
[jdk]: https://adoptium.net/temurin/releases
[gradle]: https://gradle.org/releases/
[python3]: https://www.python.org/downloads/
[vs]: https://visualstudio.microsoft.com/vs/community/
[vcbuildtools]: https://visualstudio.microsoft.com/visual-cpp-build-tools/
[eclipse]: https://www.eclipse.org/downloads/packages/
[chinese]: https://github.com/tc999/ghidra-chinese/archive/refs/heads/chinese.zip
[security]: https://github.com/NationalSecurityAgency/ghidra/security/advisories
[ghidradev]: GhidraBuild/EclipsePlugins/GhidraDev/GhidraDevPlugin/README.md
[pyghidra]: Ghidra/Features/PyGhidra/README.md
