# Yudao Spring AI Alibaba compatibility

| Branch | Framework | Java | Consumer |
| --- | --- | --- | --- |
| main | Spring Boot 4 | 17+ | ruoyi-vue-pro-sb4 |
| main-jdk17 | Spring Boot 3 | 17 | ruoyi-vue-pro-jdk25 |
| main-jdk8 | Spring Boot 2 | 8 | ruoyi-vue-pro-jdk8 |

This branch targets Spring AI `2.1.1-jdk25-SNAPSHOT` and Boot 4.1.2; minimum target Java 17+, consumer validation JVM 25.
Internal Alibaba state/option serializers retain the Jackson 2 implementation supplied
by the Boot 4 Jackson 2 BOM; the framework/MCP JSON implementation is Jackson 3.
This boundary requires explicit HTTP codec and runtime validation.
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

The three frozen business consumers start and pass local chat/image/knowledge flows. The optional AgentScope adapter uses the Yudao SDK on every branch: main/main-jdk17 use2.1.0-SNAPSHOT and main-jdk8 uses2.1.1-jdk8-SNAPSHOT. The Java8 adapter is included in its default reactor/BOM; its runtime-sandbox module is also included after the independent Java8 port. Original consumer working directories remain untouched. No remote release has been published.

## Additional validation and limits

The same ten local HTTP acceptance tests pass in all three business lines, including native DashScope embedding/image credentials, requested/default models and application observation callbacks. The matched chat service/tool regressions pass on actual JVM8/17/25 (51/54/61 tests). Streaming reasoning consumption uses the delegate delta rather than appending cumulative content.

The optional AgentScope adapter uses `cn.iocoder.agentscope:agentscope-core:2.1.0-SNAPSHOT`, from the pinned current AgentScope source (the compatibility version is independent of upstream's version). It uses `AgentStateStore` instead of the removed `Session` API. Each invocation copies the builder, scopes persisted state by graph thread and agent name, creates the temporary agent on subscription, and closes it on termination. `session(AgentStateStore)` is retained as a deprecated builder alias; use `stateStore(AgentStateStore)` for new code. Model errors save safe input before propagating; cancellation retains committed file state without an extra blanket save.

The adapter's local regression suite passes on native JDK17 for both Boot3 and Boot4: 18 tests, 0 failures/errors, 5 external-service skips. It covers multi-turn/thread/agent isolation, lazy lifecycle, failed model calls and cancellation recovery through a fresh graph. This is local adapter evidence; skipped provider integrations are not covered by it.

### Using the optional AgentScope adapter

Before installing this repository, install `yudao-agentscope-java` branch `main` with JDK17 and an installed Maven 3.9+. That SDK has no Maven wrapper. From its own checkout, build the BOMs, core and DashScope model extension used by this adapter:

```bash
git clone -b main https://github.com/yudaocode/yudao-agentscope-java.git
cd yudao-agentscope-java
mvn -pl agentscope-dependencies-bom,agentscope-distribution/agentscope-bom,agentscope-extensions/agentscope-extensions-model/agentscope-extensions-model-dashscope -am -DskipTests -Dmaven.javadoc.skip=true install
```

Then return to your Alibaba checkout for its build. Alongside the Spring AI/Alibaba BOMs above, add:

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai-alibaba</groupId>
    <artifactId>spring-ai-alibaba-starter-agentscope</artifactId>
</dependency>
```

The Alibaba BOM manages the adapter version; its POM selects AgentScope `2.1.0-SNAPSHOT` on both Boot3 and Boot4. Do not use the Boot4 Alibaba version as the AgentScope version. Configure an AgentScope `ReActAgent.Builder` with your `Model`, then wrap it with `AgentScopeAgent.fromBuilder(builder).name("assistant").stateStore(store).build()`. `store` is an AgentScope `AgentStateStore` such as `InMemoryAgentStateStore` or `JsonFileAgentStateStore`; pass a graph `RunnableConfig` with a thread ID for persisted conversations. Java packages remain `io.agentscope.*` and `com.alibaba.cloud.ai.*`. DashScope starter does not acquire AgentScope unless this adapter is explicitly added.


## Runtime sandbox (optional)

`cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-sandbox:1.1.2.2-jdk25-SNAPSHOT` uses
`cn.iocoder.agentscope:agentscope-runtime-sandbox-core:2.1.0-SNAPSHOT`. Boot4 deliberately
shares AgentScope 2.1.0-SNAPSHOT with Boot3. These development artifacts must be installed
from source before use; no remote artifact release has been published.

First install matching yudao-spring-ai. In yudao-agentscope-java branch `main`,
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

Development coordinates now retain Alibaba baseline1.1.2.2 and use the selected jdk8/jdk17/jdk25 route. The Spring AI BOM is separately versioned2.1.0 with the same route; AgentScope modern version is2.1.0-SNAPSHOT without a JDK suffix. Do not reuse the Alibaba version property for these other BOMs. Date-only release naming is described in VERSIONING.md.

## Admin runtime compatibility

The Admin parent and `spring-ai-alibaba-admin-server-runtime` now join this branch's default reactor and BOM.
The runtime retains the146 upstream DTO/utility source files; unused Guava is removed, wildcard imports are made explicit and framework/Java versions come from the branch root.
The parent/runtime use the same fork revision and publication configuration as the root, rather than independent1.0.0 snapshots.
This is the runtime library, not a completed Admin server.
OpenAPI and Start remain outside the supported default reactor in the explicit `admin-server-pending` profile; the Core library checkpoint is recorded below.
Their historical official graph/ARMS dependencies are not produced by this fork and have no compatibility claim yet.

This branch's full25-module clean install passed1913 cases, zero failures/errors and288 conditional skips on nativeJDK17, with retries disabled.
The focused runtime suite has18 cases; Boot3/4 also passed those cases on native25.
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

## Admin Core library checkpoint (2026-11-18)

The modern branch now includes Core in its default reactor and BOM. Full native17 clean install:
26 modules, 1955 cases, zero failures/errors, 288 conditional skips, retries disabled.
The previous25-module runtime-only count above remains a historical checkpoint.
Core42 tests pass on native17 and25; four standalone installed-jar cases pass on each VM.
The standalone consumer starts the host Boot context, real H2 JDBC/MyBatis and JS/Python engines;
it also preserves provider401 status/code/Unicode messages for sync chat, streaming and embedding.

The old official1.0 core dependency is replaced by the current fork DashScope module.
MCP uses current core plus Jackson2 transport, not a Jackson3 umbrella on Boot3.
GraalJS/Python/Truffle use24.2.1 with the Python standard-library resource dependency.
Core depends directly on Redisson SDK because its own configuration already creates the client;
database pool selection stays in the host/application layer.
Boot4 uses its matching MyBatis starter/service imports and Spring7 HTTP callback/header APIs.
The three org.springframework.ai source copies are relocated into Admin-owned namespaces.
Elasticsearch reads its storage DTO before building a Spring AI Document, supporting content/text
on both Jackson families without injecting replacement framework classes.

The pre-fix consumer401 regression and Boot4 Document deserialization failures are retained in the
task notes. No all-provider/cloud credentials, complete Admin UI/database/MQ deployment or Maven Central
release is claimed. Core on Java8 remains pending. See the Core module's README-COMPATIBILITY.md.

## OpenAPI default-reactor checkpoint (2026-11-18)

27 modules, 1966 cases, zero failures/errors, 288 conditional skips; full clean install on native17, retries disabled. This supersedes older aggregate counts above.
Ten MVC cases and one installed-jar real HTTP consumer case pass per target VM (Boot2/native8; Boot3/4/native17 and25). Consumer fixtures replace application services, not servlet/JSON/SSE transport.
OpenAPI is included in reactor/BOM. Start remains pending; no complete platform/backend or every endpoint/API-path claim. See OpenAPI module README for usage and limits.

## Admin Start (2026-11-18)

Admin Start is included in the default reactor on this route. It uses Boot 4.1.2, runs on JDK 17/25, and builds an executable jar. The app uses the fork's current Graph, model and observation components. Route-specific SpringDoc, Initializr and database dependencies keep the underlying Boot stack aligned.

See the [Admin Start guide](spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/README-COMPATIBILITY.md) for startup configuration and reproducible test scope.

The current default reactor reports 28 modules on native JDK17, 1969 tests, 0 failures/errors and 290 conditional skips. With no Docker connection the PostgreSQL container integration test follows the same Docker availability condition used by the other container tests.
