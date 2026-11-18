# Yudao Spring AI Alibaba compatibility

| Branch | Framework | Java | Consumer |
| --- | --- | --- | --- |
| main | Spring Boot 4 | 17+ | ruoyi-vue-pro-sb4 |
| main-jdk17 | Spring Boot 3 | 17 | ruoyi-vue-pro-jdk25 |
| main-jdk8 | Spring Boot 2 | 8 | ruoyi-vue-pro-jdk8 |

This branch targets Spring AI `2.1.0-jdk8-SNAPSHOT` and Boot 2.7.18; minimum target Java 8, consumer validation JVM 8.
Default reactor unit tests and dependency gates have passed by recorded full/closure runs. Consumer source alignment is still in progress.
No release has been published. Maven groups are cn.iocoder.spring-ai-alibaba and cn.iocoder.spring-ai; upstream artifactIds and Java packages are retained.

## Upstream source provenance

- Alibaba main: https://github.com/alibaba/spring-ai-alibaba/tree/17e26ff6fe951d605771c1845229ca3edb043634
- `extensions/`: https://github.com/spring-ai-alibaba/spring-ai-extensions/tree/72fcf8ae1b89fdc22d771f8d6a5c30a8271550fb

The imported extensions are the dependency closure of the main reactor: DashScope,
its auto configuration and starter, document parsers, observation and MCP registry/common.
Their Apache 2.0 headers and existing authors are preserved. The extensions BOM is
limited to modules included in this repository. Unrelated extension modules are not advertised.

The branch is a development SNAPSHOT. Maven artifact publication is managed separately from source publication.

## Verified scope (2026-11-16)

Earlier reactor checkpoint: Boot2=28 modules; Boot3/4=23 modules. Current Admin runtime expansion is recorded below. Unit suites and dependency gates passed by combined module/closure receipts; this is not a claim that every external integration ran. Boot2 executes on real Java8; Boot3 on Java17; Boot4 consumer tests on Java25.

Native DashScope sync/UTF8 SSE/provider errors and ChatClient option merging have five common HTTP tests passing on each JVM. The native getOptions method returns defensive copies; the legacy getter delegates. Global embedding selection uses spring.ai.model.embedding. The extensions BOM does not mark the model runtime optional.

Tika parser's original14 tests pass on each line with the corresponding family (2.9.4/3.3.1/4.0.0); Boot4 uses TikaInputStream and standard-package type pom.

The three frozen business consumers start and pass local chat/image/knowledge flows. The Java8 optional AgentScope starter is included in the supported reactor/BOM and uses the Yudao Java8 AgentScope port. The optional runtime-sandbox module now uses the matching Yudao runtime SDK and is included in the supported reactor/BOM. Original consumer working directories remain untouched. No remote release has been published.

## Additional validation and limits

The same ten local HTTP acceptance tests pass in all three business lines, including native DashScope embedding/image credentials, requested/default models and application observation callbacks. The matched chat service/tool regressions pass on actual JVM8/17/25 (51/54/61 tests). Streaming reasoning consumption uses the delegate delta rather than appending cumulative content.

The Java8 optional AgentScope adapter uses `cn.iocoder.agentscope:agentscope-core:2.1.0-jdk8-SNAPSHOT`, based on the pinned current AgentScope source. It uses `AgentStateStore` in place of the removed `Session` API, copies builders per invocation, isolates graph thread/agent state, and closes temporary agents after stream termination. `session(AgentStateStore)` remains a deprecated builder alias; new code should use `stateStore(AgentStateStore)`. The main and main-jdk17 adapters also use the pinned current SDK, with compatibility version2.1.0-SNAPSHOT. Model errors persist safe conversation input before propagating the failure. Cancellation follows core state ownership: committed file state remains available, in-flight writes finish before their session ownership is released, and the adapter does not perform an additional blanket save.

The expanded 28-module Java8 default verify passes 1896 tests with 0 failures/errors and 286 skips; skipped cases requiring external services are not acceptance evidence. The optional adapter's 18 tests include local multi-turn state isolation, direct failure/cancellation persistence checks with a rebuilt graph, and lazy agent lifecycle coverage; 5 external-service cases remain skipped. Runtime sandbox is now included in the default reactor/BOM. No model provider credentials or remote model calls are required for these local gates.


## Runtime sandbox (optional)

`cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-sandbox:1.1.2.2-jdk8-SNAPSHOT` uses
`cn.iocoder.agentscope:agentscope-runtime-sandbox-core:2.1.0-jdk8-SNAPSHOT`. Boot4 deliberately
shares AgentScope 2.1.0-SNAPSHOT with Boot3. These development artifacts must be installed
from source before use; no remote artifact release has been published.

First install matching yudao-spring-ai. In yudao-agentscope-java branch `main-jdk8`,
use JDK17 and installed Maven3.9+ (the SDK has no mvnw) to install the BOMs and both
optional Alibaba integration dependencies:

```bash
mvn -pl agentscope-dependencies-bom,agentscope-distribution/agentscope-bom,agentscope-extensions/agentscope-extensions-model/agentscope-extensions-model-dashscope,agentscope-runtime-sandbox-core -am -DskipTests -Dmaven.javadoc.skip=true install
```

Then build this Alibaba checkout using its Maven wrapper as shown in the README.
Import the Spring AI and Alibaba BOMs, and add the sandbox dependency explicitly:

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai-alibaba</groupId>
    <artifactId>spring-ai-alibaba-sandbox</artifactId>
</dependency>
```

Configure an `io.agentscope.runtime.sandbox.manager.SandboxService`, create a
`BaseSandbox`, and bind it with `SaaBaseShellRunner.setSandbox(sandbox)`. Calling
`buildTool()` returns a Spring AI `ToolCallback`; its JSON input is
`{"command":"printf 'hello'"}`. Close the service when its sandboxes are no longer
needed. Use a compatible `/sandbox/*` manager endpoint for remote mode; local mode
requires Docker and the configured tool image. Request timeout, ownership and
backend configuration are documented in the SDK runtime module's README.

The same local HTTP ToolCallback regression passes on real8/17/25: schema, UTF8
arguments, response JSON, sandbox reuse and cleanup. Complete reactor verify passes
on all three lines: Boot2=28 modules/1896 tests/286 skips; Boot3/4=23 modules/1895
tests/288 skips each, with no failures or errors. Provider-gated tests stay skipped;
these gates do not claim acceptance of every remote sandbox backend.

### ITU Java 8 API fix

The Networknt schema paths exclude `com.ethlo.time:itu`; MCP common explicitly consumes `cn.iocoder.spring-ai:spring-ai-itu-compat`. This avoids a Java 9-only Math overload in original ITU binaries, verified by a real Java 8 duration-parsing regression. Keep the Spring AI compatibility BOM at the matching revision.

Development coordinates now retain Alibaba baseline1.1.2.2 and use the selected jdk8/jdk17/jdk25 route. The Spring AI BOM is separately versioned2.1.0 with the same route; AgentScope modern version is2.1.0-SNAPSHOT without a JDK suffix. Do not reuse the Alibaba version property for these other BOMs. Date-only release naming is described in VERSIONING.md.

## Admin runtime compatibility

The Admin parent and `spring-ai-alibaba-admin-server-runtime` now join this branch's default reactor and BOM.
The runtime retains the146 upstream DTO/utility source files; unused Guava is removed, wildcard imports are made explicit and framework/Java versions come from the branch root.
The parent/runtime use the same fork revision and publication configuration as the root, rather than independent1.0.0 snapshots.
This is the runtime library, not a completed Admin server.
Core, OpenAPI and Start remain outside the supported default reactor in the explicit `admin-server-pending` profile.
Their historical official core/graph/ARMS dependencies are not produced by this fork and have no compatibility claim yet.

This branch's full30-module clean install passed1911 cases, zero failures/errors and286 conditional skips on nativeJDK8, with retries disabled.
The focused runtime suite has14 cases; Boot3/4 also passed those cases on native25.
Five standalone installed-jar HTTP cases cover Boot2/native8 and Boot3/4 × native17/25.
Each uses the host's real MVC converter and tests Unicode text, typed multimodal content, response field names, ignored future fields and the MDC/Marker paths.
Boot4 initially returned500 for multimodal requests because its default Jackson3 mapper ignored the Jackson2 custom deserializer.
The Boot4 route adds a Jackson3 annotation/deserializer bridge while retaining the runtime's existing Jackson2 utility/tree representation; Boot2/3 do not acquire that Jackson3 dependency.
The Java8 runtime jar has234 classes, all <=major52, with zero missing JDK/Spring/Jackson/Mybatis members against the actual consumer classpath.
The full consumer test/runtime closure has63 jars/20099 effective base classes, all <=major52.
Its two raw static findings are SLF4J binder fallback references; the native8 MDC/Marker regression passes, and these findings remain disclosed rather than being called an all-dependency zero scan.

The Boot2/Boot3 full logs retain an upstream protobuf-java-util3.22.1 invalid-POM warning during intermediate dependency traversal; the inspected Boot3 Nacos/KMS classpath selects4.0.0-rc-2 for both protobuf artifacts.
The original3.22.1 metadata matches Maven Central byte-for-byte; no local cache patch was made.
This warning is separate from the zero test failure/error counts and is not represented as fully cleaned dependency metadata.
No real Nacos/KMS/cloud account, complete Admin UI/database deployment or Maven Central release is claimed by this batch.
See [runtime usage and boundaries](spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-runtime/README-COMPATIBILITY.md).

## Java8 graph dependency checkpoint (2026-11-18)

# JGraphT 1.5.2 Java8 兼容依赖

Admin Core原来使用JGraphT1.5.2，官方jar要求JDK11。本模块保留该版本完整core源码，使用JDK17工具链生成/编译Java8产物。
坐标为`cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-jgrapht-compat`，版本由Alibaba BOM管理；包名仍为org.jgrapht。
不要同时引入原版org.jgrapht:jgrapht-core，避免重复类。

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai-alibaba</groupId>
    <artifactId>spring-ai-alibaba-jgrapht-compat</artifactId>
</dependency>
```

先安装当前路线的yudao-spring-ai，再在本仓库根目录构建：

```bash
bash ./mvnw -Dmaven.javadoc.skip=true install
bash ./mvnw -f compatibility-tests/admin-jgrapht/pom.xml test -Dcompat.test.jvm=/path/to/jdk8/bin/java -Dcompat.expected.vm=1.8
```

## 验证与边界

- 官方基线：jgrapht-1.5.2 / fe2d4cdfa42073eb1141e789844adc9c1fc8bf36。
- 完整450个主源码、307个测试/性能源码参与编译；JPMS描述符不进入Java8产物。
- 上游默认单测选择6621项，真实Corretto8u472运行，零失败/错误/跳过。
- 保留上游默认排除的SlowTests、OptionalTests和perf/**；未将这些排除项计作通过。
- 当前默认31模块全量8532项，零失败/错误、286条件跳过，重试关闭。
- 独立Boot2/native8消费者2项全通过；实际启动Boot并验证DAG、拒绝循环、拓扑序、私有构造器/序列化恢复、最短路径及MDC/Marker。
- 安装jar含711个Java8类；原版689个类的4188个类与公开/受保护成员签名无缺失，新增类来自Java8编译桥接。
- 真实消费类路径下，owned JDK/图/堆/数值库/兼容工具成员检查无缺失。
- 61个消费test/runtime jar、17812个有效基础类均不高于major52；MR覆盖和module-info另行排除。
- 全依赖静态扫描仍保留两项SLF4J1.7 binder兼容回退引用，实际MDC/Marker路径已通过，不声称整个依赖树所有API路径均为零发现。

源码调整包括Java9反射访问API、IndexOutOfBoundsException整型构造器、测试集合toArray工厂，以及恢复序列化supplier的访问设置。
后者由独立消费者复现，原失败日志保存在任务笔记；没有改成公开测试边类型来绕过失败。
源文件保留上游作者和EPL2.0/LGPL2.1-or-later双许可证，来源与变换见UPSTREAM-SOURCES.json；许可证/贡献者同时随jar打包。
第三方源包保留原格式/许可头，因此仅此模块跳过Spring风格工具，依赖版本/字节码门禁及真实测试继续执行。
本模块完成不代表Admin Core/Python或完整管理平台已在Java8完成；本批没有向Maven Central部署。

## Admin Core / Graal Java8 checkpoint (2026-11-18)

Default reactor now34 modules: native8 clean install8580 cases, zero failures/errors,286 conditional skips;609 XML suites, retries disabled.
This supersedes the historical31-module JGraphT checkpoint above. Core42 cases, JS3 and Python3 pass with no skips.
Standalone installed Core consumer4 cases pass on macOS/Corretto8 and Linux/Corretto8; host Boot2/H2/MyBatis and real JS/Python engines run.
Core, GraalJS and GraalPython are included in the default reactor and BOM. OpenAPI/Start remain in the pending profile.
The fork-specific Elasticsearch search extension no longer shadows Spring AI classes.
The full consumer has352 jars with160085 effective base classes at major52 or below; four owned SDK jars have22740 classes and zero missing JDK API references.
The whole dependency API scan still reports57 raw references; these are not all dismissed as false positives. Active Elasticsearch OpenTelemetry instrumentation and all optional external dependency paths remain unverified on8.
See [Core usage/boundaries](spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/README-COMPATIBILITY.md),
[GraalJS](compatibility/graaljs/README-COMPATIBILITY.md), and[GraalPython](compatibility/graalpython/README-COMPATIBILITY.md).
No full platform/cloud backend, native extension/LLVM, arbitrary PyPI, AOT or JIT performance claim; no Maven Central deployment.

## OpenAPI default-reactor checkpoint (2026-11-18)

35 modules, 8590 cases, zero failures/errors, 286 conditional skips; full clean install on native1.8, retries disabled. This supersedes older aggregate counts above.
Ten MVC cases and one installed-jar real HTTP consumer case pass per target VM (Boot2/native8; Boot3/4/native17 and25). Consumer fixtures replace application services, not servlet/JSON/SSE transport.
OpenAPI is included in reactor/BOM. Start remains pending; no complete platform/backend or every endpoint/API-path claim. See OpenAPI module README for usage and limits.

## Elasticsearch request-body tracing on Java 8 (2026-11-18)

Admin pins Elasticsearch Java API Client and REST Client 8.19.11.
Native Java 8 reproduced the 8.13.4 ByteBuffer linkage error with active OpenTelemetry capture.
The new regression checks the exported span and the actual JSON received by the local HTTP server.
Boot 2 consumers set the Elasticsearch version property documented in the Core README.
The earlier whole-tree report remains a historical scan.
