<img src="Ghidra/Features/Base/src/main/resources/images/GHIDRA_3.png" width="400">

# Ghidra Software Reverse Engineering Framework
Ghidra is a software reverse engineering (SRE) framework created and maintained by the 
[National Security Agency][nsa] Research Directorate. This framework includes a suite of 
full-featured, high-end software analysis tools that enable users to analyze compiled code on a 
variety of platforms including Windows, macOS, and Linux. Capabilities include disassembly, 
assembly, decompilation, graphing, and scripting, along with hundreds of other features. Ghidra 
supports a wide variety of processor instruction sets and executable formats and can be run in both 
user-interactive and automated modes. Users may also develop their own Ghidra extension components 
and/or scripts using Java or Python.

In support of NSA's Cybersecurity mission, Ghidra was built to solve scaling and teaming problems 
on complex SRE efforts, and to provide a customizable and extensible SRE research platform. NSA has 
applied Ghidra SRE capabilities to a variety of problems that involve analyzing malicious code and 
generating deep insights for SRE analysts who seek a better understanding of potential 
vulnerabilities in networks and systems.

If you are a U.S. citizen interested in projects like this, to develop Ghidra and other 
cybersecurity tools for NSA to help protect our nation and its allies, consider applying for a 
[career with us][career].

## Security Warning
**WARNING:** There are known security vulnerabilities within certain versions of Ghidra.  Before 
proceeding, please read through Ghidra's [Security Advisories][security] for a better understanding 
of how you might be impacted.

## Install
To install an official pre-built multi-platform Ghidra release:  
* Install [JDK 21 64-bit][jdk]
* Download a Ghidra [release file][releases]
  - **NOTE:** The official multi-platform release file is named 
    `ghidra_<version>_<release>_<date>.zip` which can be found under the "Assets" drop-down.
    Downloading either of the files named "Source Code" is not correct for this step.
* Extract the Ghidra release file
  - **NOTE:** Do not extract on top of an existing installation
* Launch Ghidra: `./ghidraRun` (`ghidraRun.bat` for Windows)
  - or launch [PyGhidra][pyghidra]: `./support/pyGhidraRun` (`support\pyGhidraRun.bat` for Windows)

For additional information and troubleshooting tips about installing and running a Ghidra release, 
please refer to the [Getting Started][gettingstarted] document which can be found at the root of a 
Ghidra installation directory. 

## Build
To create the latest development build for your platform from this source repository:

##### Install build tools:
* [JDK 21 64-bit][jdk]
* [Gradle 8.5+][gradle] (or provided Gradle wrapper if Internet connection is available)
* [Python3][python3] (version 3.9 to 3.14) with bundled pip
* GCC or Clang, and make (Linux/macOS-only)
* [Microsoft Visual Studio][vs] 2017+ or [Microsoft C++ Build Tools][vcbuildtools] with the
  following components installed (Windows-only):
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

##### Download additional build dependencies into source repository:
**NOTE:** If an Internet connection is available and you did not install Gradle, the 
`./gradlew` (or `gradlew.bat`) command may be used in place of the `gradle` command in the following
instructions.

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
