<!-- YUDAO-COMPATIBILITY-INTRO-BEGIN -->

> 版本命名：保留当前源码基线的官方版本号；共用现代制品不加 JDK 后缀，隔离维护的兼容路线添加 `jdk8` / `jdk17` / `jdk25`。SNAPSHOT 尚未发布 Maven Central。JDK 路线后缀不等于最低字节码版本，框架依赖仍按 Boot 版本选择。
> 完整版本规则与预览命名见 [VERSIONING.md](VERSIONING.md)。

# yudao-spring-ai-alibaba

yudao-spring-ai-alibaba 是 [Spring AI Alibaba](https://github.com/alibaba/spring-ai-alibaba) 的芋道社区兼容维护版本，并非官方发布。补齐 **Spring AI 2.x** 的 API 和依赖适配，让 **Spring Boot 2.x、Spring Boot 3.x、Spring Boot 4.x** 项目选择对应兼容版本。

## 仓库与版本线

| 平台 | 仓库地址 |
| --- | --- |
| Gitee | [yudao-spring-ai-alibaba](https://gitee.com/yudaocode/yudao-spring-ai-alibaba) |
| GitHub | [yudao-spring-ai-alibaba](https://github.com/yudaocode/yudao-spring-ai-alibaba) |

| 分支 | 已验证 Spring Boot | 运行 JDK | 源码开发版本 | 发布规则 |
| --- | --- | --- | --- | --- |
| [`main`](https://github.com/yudaocode/yudao-spring-ai-alibaba/tree/main) | 4.1.2 | 17+（业务消费使用25） | `1.1.2.2-jdk25-SNAPSHOT` | 官方版本 + 路线 + 日期 |
| [`main-jdk17`](https://github.com/yudaocode/yudao-spring-ai-alibaba/tree/main-jdk17) | 3.5.15 | 17+ | `1.1.2.2-jdk17-SNAPSHOT` | 官方版本 + 路线 + 日期 |
| [`main-jdk8`](https://github.com/yudaocode/yudao-spring-ai-alibaba/tree/main-jdk8) | 2.7.18 | 8+ | `1.1.2.2-jdk8-SNAPSHOT` | 官方版本 + 路线 + 日期 |

版本保留上游源码基线编号，以 JDK 路线隔离 Boot 依赖；发布时追加北京时间日期，不增加同日序号。当前分支：**`main-jdk17`**。

Maven groupId 为 **`cn.iocoder.spring-ai-alibaba`**，artifactId 和 Java 包名沿用官方。同一个应用只选择一条版本线，同一项目的模块使用同一版本；替换已有依赖时，也要移除对应官方 groupId 下的同类模块，避免重复类。

**当前仅提供源码开发版，尚未发布这些坐标到 Maven Central 或远端 SNAPSHOT 仓库。** 下面的 SNAPSHOT 示例须先从源码安装到本地仓库。发布版本按 `官方版本[-jdk数字][-preview]-YYYYMMDD` 命名；共用制品省略 JDK，未发布上游基线增加 preview，无同日序号。

## 如何使用

先安装相同版本线的 [yudao-spring-ai](https://github.com/yudaocode/yudao-spring-ai)，再使用 JDK 17.0.19+ 构建本仓库。Java 8 分支同样使用 JDK 17 构建，产物供 JDK 8 运行。 默认源码构建包含 AgentScope 与 sandbox 模块，还需先安装 [yudao-agentscope-java](https://github.com/yudaocode/yudao-agentscope-java) 的对应依赖：Boot 2 选择 `main-jdk8`，Boot 3/4 选择 `main`；模块构建命令见 [COMPATIBILITY.md](COMPATIBILITY.md#runtime-sandbox-optional)。

```bash
git clone -b main-jdk17 https://github.com/yudaocode/yudao-spring-ai-alibaba.git
cd yudao-spring-ai-alibaba
bash ./mvnw -DskipTests -Dmaven.javadoc.skip=true install
```

宿主保留自己的 Spring Boot parent/BOM，导入两份兼容 BOM，再引入 DashScope starter：

```xml
<properties>
    <yudao-spring-ai-alibaba.version>1.1.2.2-jdk17-SNAPSHOT</yudao-spring-ai-alibaba.version>
</properties>
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.iocoder.spring-ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.1.0-jdk17-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>cn.iocoder.spring-ai-alibaba</groupId>
            <artifactId>spring-ai-alibaba-bom</artifactId>
            <version>${yudao-spring-ai-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<dependencies>
    <dependency>
        <groupId>cn.iocoder.spring-ai-alibaba</groupId>
        <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
    </dependency>
</dependencies>
```

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
```

配置环境变量后，通过 Spring AI 的 `ChatModel` / `ChatClient.Builder` 调用；Java 导入仍为 `com.alibaba.cloud.ai.*`、`org.springframework.ai.*`。三个分支均已适配可选 AgentScope starter：Boot 3/4 使用 `cn.iocoder.agentscope:agentscope-core:2.1.0-SNAPSHOT`，Boot 2 使用 `2.1.0-jdk8-SNAPSHOT`。需先安装对应 AgentScope 源码版本，再显式引入 `cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-starter-agentscope`（版本由 Alibaba BOM 管理）；状态存储使用 `stateStore(AgentStateStore)`。详细示例见下方兼容说明。三个分支均提供可选 sandbox 模块，具体接入见兼容说明。AgentScope 集成不会成为 DashScope starter 的强制依赖。

详细构建、源码基线、API 差异及当前支持边界见 [COMPATIBILITY.md](COMPATIBILITY.md)。

下方逐字保留官方项目原始 README，其中的官方 Maven 坐标和发布状态仅针对上游项目；使用本兼容版时，以本节坐标、版本线和支持范围为准。

Admin runtime DTO/JSON 工具库已接入当前版本线：在现有 Alibaba BOM 下添加 `cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-admin-server-runtime`。
版本为 `1.1.2.2-jdk17-SNAPSHOT`，须先源码安装；保留 `com.alibaba.cloud.ai.studio.runtime.*` 包名。
当前完整25模块构建在原生JDK17通过1909项测试，零失败/错误、288项条件跳过；三路线安装包在Boot2/JDK8、Boot3/4×JDK17/25均完成真实HTTP文本/多模态验证。
Boot4已补Jackson3转换器适配，Boot2/3继续使用Jackson2。Boot3/4 的 Core 类库已纳入默认构建；OpenAPI/Start 继续迁移。Boot2 的 Core 仍待兼容，本批不代表完整管理平台可部署。
依赖示例、验证命令和边界见 [Admin runtime 使用说明](spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-runtime/README-COMPATIBILITY.md)。

## Admin Core（Boot 3 / Boot 4）

现代分支的 `spring-ai-alibaba-admin-server-core` 已加入默认 reactor 和 Alibaba BOM。
当前分支完整 26 模块 clean install：1951 项，0 失败/错误，288 条件跳过；
Core 自身 42 项在 JDK17/25 均零失败/错误/跳过。
每个 JVM 另有 4 项独立安装 jar 消费测试，实际启动 Boot、H2/MyBatis、JS/Python，并验证同步/流式/向量调用的 HTTP 错误映射。

在前述 BOM 下按需添加：

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai-alibaba</groupId>
    <artifactId>spring-ai-alibaba-admin-server-core</artifactId>
</dependency>
```

这是 Core 类库，不是完整 Admin 服务。检索扩展已迁入 Admin 自己的包，不再覆盖 Spring AI 官方类；
Boot4 的 Elasticsearch JSON 映射、GraalPy 标准库和 OpenAI SDK 业务错误映射已有实际回归。
Boot2 Core/Python 的正式移植，以及 OpenAPI/Start、完整服务装配和外部后端验收仍在推进。
接入、测试和边界见 [Core 使用说明](spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/README-COMPATIBILITY.md)。

<!-- YUDAO-COMPATIBILITY-INTRO-END -->

---

# [Spring AI Alibaba](https://java2ai.com)

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![CI Status](https://github.com/alibaba/spring-ai-alibaba/actions/workflows/build-and-test.yml/badge.svg?branch=main)](https://github.com/alibaba/spring-ai-alibaba/actions/workflows/build-and-test.yml)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/alibaba/spring-ai-alibaba)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-v1.1.2.2-blue)](https://central.sonatype.com/artifact/com.alibaba.cloud.ai/spring-ai-alibaba/1.1.2.2)
[![gitleaks badge](https://img.shields.io/badge/protected%20by-gitleaks-blue)](https://github.com/gitleaks/gitleaks)

<html>
    <h3 align="center">
      A production-ready framework for building Agentic, Workflow, and Multi-agent applications.
    </h3>
    <h3 align="center">
      <a href="https://java2ai.com/docs/quick-start/" target="_blank">Agent Framework Docs</a>,
      <a href="https://java2ai.com/docs/frameworks/graph-core/quick-start/" target="_blank">Graph Docs</a>,
      <a href="https://java2ai.com/ecosystem/spring-ai/reference/concepts/" target="_blank">Spring AI</a>,
      <a href="https://github.com/alibaba/spring-ai-alibaba/tree/main/examples" target="_blank">Examples</a>.
    </h3>
</html>

## Architecture

<p align="center">
    <img src="./docs/imgs/architecture-new.png" alt="architecture" style="max-width: 740px; height: auto" />
</p>

**Spring AI Alibaba Admin** is a one-stop Agent platform that supports visualized Agent development, observability, evaluation, and MCP management, etc. It also integrates with open-source low-code platforms like Dify, enabling rapid migration from DSL to Spring AI Alibaba project.

**Spring AI Alibaba Agent Framework** is an agent development framework that can quickly develop agents with built-in **Context Engineering** and **Human In The Loop** support. For scenarios requiring more complex process control, Agent Framework offers built-in workflows like `SequentialAgent`, `ParallelAgent`, `RoutingAgent`, `LoopAgent`.

**Spring AI Alibaba Graph** serves as the underlying runtime of the Agent Framework, providing essential capabilities such as persistence, workflow orchestration, and streaming required for long-running stateful agents. Compared to the Agent Framework, users can build more flexible multi-agent workflows based on the Graph API.

## Core Features

* **[Multi-Agent Orchestration](https://github.com/alibaba/spring-ai-alibaba/tree/main/examples/multiagent-patterns)**: Compose multiple agents with built-in patterns including `SequentialAgent`, `ParallelAgent`, `RoutingAgent`, and `LoopAgent` for complex task execution.

* **[Multimodal Support](https://github.com/alibaba/spring-ai-alibaba/tree/main/examples/multimodal)**: ReactAgent with text and media input (image understanding). ReactAgent with tool based image or audio generation.

* **[Voice Agent](https://github.com/alibaba/spring-ai-alibaba/tree/main/examples/voice-agent)**: WebSocket-based real-time voice agent that supports streaming audio or text input and responds with generated audio.

* **[Context Engineering](https://java2ai.com/docs/frameworks/agent-framework/tutorials/hooks)**: Built-in best practices for context engineering policies to improve agent reliability and performance, including human-in-the-loop, context compaction, context editing, model & tool call limit, tool retry, planning, dynamic tool selection.

* **[Graph-based Workflow](https://java2ai.com/docs/frameworks/graph-core/quick-start)**: Graph based workflow runtime and api for conditional routing, nested graphs, parallel execution, and state management. Export workflows to PlantUML and Mermaid formats.

* **[A2A Support](https://java2ai.com/docs/frameworks/agent-framework/advanced/a2a)**: Agent-to-Agent communication support with Nacos integration, enabling distributed agent coordination and collaboration across services.

* **[Rich Model, Tool and MCP Support](https://java2ai.com/integration/chatmodels/dashScope)**: Leveraging core concepts of Spring AI, supports multiple LLM providers (DashScope, OpenAI, etc.), tool calling, and Model Context Protocol (MCP).

* **[One-stop Agent Platform](https://java2ai.com/ecosystem/admin/quick-start)**: Build agent in a visualized way, deploy agent without code or export as a standalone java project.

<p align="center">
    <img src="./docs/imgs/saa-admin.png" alt="architecture" style="max-width: 740px; height: auto" />
</p>

## Getting Started

### Prerequisites

* Requires JDK 17+.
* Choose your LLM provider and get the API-KEY.

### Quickly Run a ChatBot

There's a ChatBot example provided by the community at [examples/chatbot](https://github.com/alibaba/spring-ai-alibaba/tree/main/examples/chatbot).

1. Download the code.

	```shell
	git clone --depth=1 https://github.com/alibaba/spring-ai-alibaba.git
	cd spring-ai-alibaba
	```

2. Start the ChatBot.

	Before starting, set API-KEY first (visit <a href="https://bailian.console.aliyun.com/?apiKey=1&tab=api#/api" target="_blank">Aliyun Bailian</a> to get API-KEY):
	```shell
	# this example uses 'spring-ai-alibaba-starter-dashscope', visit https://java2ai.com to learn how to use OpenAI/DeepSeek.
	export AI_DASHSCOPE_API_KEY=your-api-key
	```
	
	```shell
	# Maven installation is optional when using mvnw.
	./mvnw -pl examples/chatbot spring-boot:run
	```

3. Chat with ChatBot.

	Open the browser and visit [http://localhost:8080/chatui/index.html](http://localhost:8080/chatui/index.html) to chat with the ChatBot.
	
<p align="center">
	<img src="./docs/imgs/chatbot-chat-ui.gif" alt="chatbot-ui" style="max-width: 740px; height: auto" />
</p>

## Chatbot Code Explained

1. Add dependencies

	```xml
	<dependencies>
	  <dependency>
	    <groupId>com.alibaba.cloud.ai</groupId>
	    <artifactId>spring-ai-alibaba-agent-framework</artifactId>
	    <version>1.1.2.0</version>
	  </dependency>
	  <!-- Assume you are going to use DashScope Model. Refer to docs for how to choose model.-->
	  <dependency>
	    <groupId>com.alibaba.cloud.ai</groupId>
	    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
	    <version>1.1.2.1</version>
	  </dependency>
	</dependencies>
	```

2. Define Chatbot
   
	For more details of how to write a Chatbot, please check the [Quick Start](https://java2ai.com/docs/quick-start) on our official website.

## 📚 Documentation
* [Overview](https://java2ai.com/docs/overview) - High level overview of the framework
* [Quick Start](https://java2ai.com/docs/quick-start) - Get started with a simple agent
* [Agent Framework Tutorials](https://java2ai.com/docs/frameworks/agent-framework/tutorials/agents) - Step by step tutorials
* [Use Graph API to Build Complex Workflows](https://java2ai.com/docs/frameworks/agent-framework/advanced/context-engineering) - In-depth user guide for building multi-agent and workflows
* [Spring AI Basics](https://java2ai.com/ecosystem/spring-ai/reference/concepts) - Ai Application basic concepts, including ChatModel, MCP, Tool, Messages, etc.
* [Chat Memory](https://docs.spring.io/spring-ai/reference/api/chatclient.html#chat-memory) - Spring AI reference for chat memory repositories and usage

## Project Structure

This project consists of several core components:

* spring-ai-alibaba-agent-framework: A multi-agent framework designed for building intelligent agents with built-in context engineering best practices.
* spring-ai-alibaba-graph: The underlying runtime for Agent Framework. We recommend developers to use Agent Framework but it's totally fine to use the Graph API directly.
* spring-ai-alibaba-admin: A one-stop Agent platform that supports visualized Agent development, observability, evaluation, and MCP management, etc.
* spring-ai-alibaba-studio: The embedded ui for quickly debugging agent in a visualized way.
* spring-boot-starters: Starters integrating Agent Framework with Nacos to provide A2A and dynamic config features.

## Spring AI Alibaba Ecosystem
 Repository | Description | ⭐
  --- | --- | ---
| [Spring AI Alibaba Graph](https://github.com/alibaba/spring-ai-alibaba/tree/main/spring-ai-alibaba-graph-core) | A low-level orchestration framework and runtime for building, managing, and deploying long-running, stateful agents. | ![GitHub Repo stars](https://img.shields.io/github/stars/alibaba/spring-ai-alibaba?style=for-the-badge&label=)
| [Spring AI Alibaba Admin](https://github.com/spring-ai-alibaba/spring-ai-alibaba-admin) |  Local visualization toolkit for the development of agent applications, supporting project management, runtime visualization, tracing, and agent evaluation. | ![GitHub Repo stars](https://img.shields.io/github/stars/spring-ai-alibaba/spring-ai-alibaba-admin?style=for-the-badge&label=)
| [Spring AI Extensions](https://github.com/spring-ai-alibaba/spring-ai-extensions) | Extended implementations for Spring AI core concepts, including DashScopeChatModel, MCP registry, etc. |  ![GitHub Repo stars](https://img.shields.io/github/stars/spring-ai-alibaba/spring-ai-extensions?style=for-the-badge&label=)
| [Spring AI Alibaba Examples](https://github.com/spring-ai-alibaba/examples) | Spring AI Alibaba Examples. |  ![GitHub Repo stars](https://img.shields.io/github/stars/spring-ai-alibaba/examples?style=for-the-badge&label=)
| [JManus](https://github.com/spring-ai-alibaba/jmanus) | A Java implementation of Manus built with Spring AI Alibaba, currently used in many applications within Alibaba Group. | ![GitHub Repo stars](https://img.shields.io/github/stars/spring-ai-alibaba/jmanus?style=for-the-badge&label=)
| [DataAgent](https://github.com/spring-ai-alibaba/dataagent) | A natural language to SQL project based on Spring AI Alibaba, enabling you to query databases directly with natural language without writing complex SQL. | ![GitHub Repo stars](https://img.shields.io/github/stars/spring-ai-alibaba/dataagent?style=for-the-badge&label=)
| [DeepResearch](https://github.com/spring-ai-alibaba/deepresearch) |  Deep Research implemented based on spring-ai-alibaba-graph. | ![GitHub Repo stars](https://img.shields.io/github/stars/spring-ai-alibaba/deepresearch?style=for-the-badge&label=)

## Contact Us

* Dingtalk Group (钉钉群), search `182255021470` and join.

<img src="./docs/imgs/dingding-group.png" style="width: 260px; height: auto"/>

* WeChat Group (微信公众号), scan the QR code below and follow us.

<img src="./docs/imgs/wechat-account.jpg" style="width: 260px; height: auto"/>

## Resources
* [AI-Native Application Architecture White Paper](https://developer.aliyun.com/ebook/8479): Co-authored by 40 frontline engineers and endorsed by 15 industry experts, this 200,000+ word white paper is the first comprehensive guide dedicated to the full DevOps lifecycle of AI-native applications. It systematically breaks down core concepts and key challenges, offering practical problem-solving approaches and architectural insights.


## Star History

[![Star History Chart](https://starchart.cc/alibaba/spring-ai-alibaba.svg?variant=adaptive)](https://starchart.cc/alibaba/spring-ai-alibaba)

---

<p align="center">
    Made with ❤️ by the Spring AI Alibaba Team
