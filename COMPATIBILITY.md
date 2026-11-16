# Yudao Spring AI Alibaba compatibility

| Branch | Framework | Java | Consumer |
| --- | --- | --- | --- |
| main | Spring Boot 4 | 17+ | ruoyi-vue-pro-sb4 |
| main-jdk17 | Spring Boot 3 | 17 | ruoyi-vue-pro-jdk25 |
| main-jdk8 | Spring Boot 2 | 8 | ruoyi-vue-pro-jdk8 |

This branch targets Spring AI `2.0.0-SNAPSHOT` and Boot 2.7.18; minimum target Java 8, consumer validation JVM 8.
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

Default reactor: Boot2=28 modules; Boot3/4=23 modules. Unit suites and dependency gates passed by combined module/closure receipts; this is not a claim that every external integration ran. Boot2 executes on real Java8; Boot3 on Java17; Boot4 consumer tests on Java25.

Native DashScope sync/UTF8 SSE/provider errors and ChatClient option merging have five common HTTP tests passing on each JVM. The native getOptions method returns defensive copies; the legacy getter delegates. Global embedding selection uses spring.ai.model.embedding. The extensions BOM does not mark the model runtime optional.

Tika parser's original14 tests pass on each line with the corresponding family (2.9.4/3.3.1/4.0.0); Boot4 uses TikaInputStream and standard-package type pom.

The three frozen business consumers start and pass local chat/image/knowledge flows. The Java8 optional AgentScope starter is included in the supported reactor/BOM and uses the Yudao Java8 AgentScope port. The optional runtime-sandbox module now uses the matching Yudao runtime SDK and is included in the supported reactor/BOM. Original consumer working directories remain untouched. No remote release has been published.

## Additional validation and limits

The same ten local HTTP acceptance tests pass in all three business lines, including native DashScope embedding/image credentials, requested/default models and application observation callbacks. The matched chat service/tool regressions pass on actual JVM8/17/25 (51/54/61 tests). Streaming reasoning consumption uses the delegate delta rather than appending cumulative content.

The Java8 optional AgentScope adapter uses `cn.iocoder.agentscope:agentscope-core:2.0.0-SNAPSHOT`, based on the pinned current AgentScope source. It uses `AgentStateStore` in place of the removed `Session` API, copies builders per invocation, isolates graph thread/agent state, and closes temporary agents after stream termination. `session(AgentStateStore)` remains a deprecated builder alias; new code should use `stateStore(AgentStateStore)`. The main and main-jdk17 adapters also use the pinned current SDK, with compatibility version3.0.0-SNAPSHOT. Model errors persist safe conversation input before propagating the failure. Cancellation follows core state ownership: committed file state remains available, in-flight writes finish before their session ownership is released, and the adapter does not perform an additional blanket save.

The expanded 28-module Java8 default verify passes 1896 tests with 0 failures/errors and 286 skips; skipped cases requiring external services are not acceptance evidence. The optional adapter's 18 tests include local multi-turn state isolation, direct failure/cancellation persistence checks with a rebuilt graph, and lazy agent lifecycle coverage; 5 external-service cases remain skipped. Runtime sandbox is now included in the default reactor/BOM. No model provider credentials or remote model calls are required for these local gates.

## Retained admin source boundary

The retained `spring-ai-alibaba-admin` source tree is outside the supported default reactor and BOM. Its historical external `spring-ai-alibaba-core:1.0.0.3`, `spring-ai-alibaba-graph-core:1.0.0.4` and `spring-ai-alibaba-autoconfigure-arms-observation:1.0.0.4` dependencies keep the official `com.alibaba.cloud.ai` coordinates; these releases are not produced by this fork. Fork-owned admin project references use `cn.iocoder.spring-ai-alibaba`. No Boot/JDK compatibility is claimed for this retained admin tree.

## Runtime sandbox (optional)

`cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-sandbox:2.0.0-SNAPSHOT` uses
`cn.iocoder.agentscope:agentscope-runtime-sandbox-core:2.0.0-SNAPSHOT`. Boot4 deliberately
shares AgentScope version3 with Boot3. These development artifacts must be installed
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
