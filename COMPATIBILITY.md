# Yudao Spring AI Alibaba compatibility

| Branch | Framework | Java | Consumer |
| --- | --- | --- | --- |
| main | Spring Boot 4 | 17+ | ruoyi-vue-pro-sb4 |
| main-jdk17 | Spring Boot 3 | 17 | ruoyi-vue-pro-jdk25 |
| main-jdk8 | Spring Boot 2 | 8 | ruoyi-vue-pro-jdk8 |

This branch targets Spring AI `2.1.0-boot3-SNAPSHOT` and Boot 3.5.15; minimum target Java 17, consumer validation JVM 17.
Default reactor unit tests and dependency gates have passed by recorded full/closure runs. Consumer source alignment is still in progress.
No release has been published. Original groupIds, artifactIds and packages are retained.

## Upstream source provenance

- Alibaba main: https://github.com/alibaba/spring-ai-alibaba/tree/17e26ff6fe951d605771c1845229ca3edb043634
- `extensions/`: https://github.com/spring-ai-alibaba/spring-ai-extensions/tree/72fcf8ae1b89fdc22d771f8d6a5c30a8271550fb

The imported extensions are the dependency closure of the main reactor: DashScope,
its auto configuration and starter, document parsers, observation and MCP registry/common.
Their Apache 2.0 headers and existing authors are preserved. The extensions BOM is
limited to modules included in this repository. Unrelated extension modules are not advertised.

The branch is a development SNAPSHOT. Maven artifact publication is managed separately from source publication.

## Verified scope (2026-11-16)

Default reactor: Boot2=26 modules; Boot3/4=23 modules. Unit suites and dependency gates passed by combined module/closure receipts; this is not a claim that every external integration ran. Boot2 executes on real Java8; Boot3 on Java17; Boot4 consumer tests on Java25.

Native DashScope sync/UTF8 SSE/provider errors and ChatClient option merging have five common HTTP tests passing on each JVM. The native getOptions method returns defensive copies; the legacy getter delegates. Global embedding selection uses spring.ai.model.embedding. The extensions BOM does not mark the model runtime optional.

Tika parser's original14 tests pass on each line with the corresponding family (2.9.4/3.3.1/4.0.0); Boot4 uses TikaInputStream and standard-package type pom.

The three frozen business consumers start and pass local chat/image/knowledge flows. AgentScope SDK compatibility is a later phase. Java8 currently excludes its optional starter and runtime-sandbox module from default build/BOM; those are not claimed as supported. Original consumer working directories remain untouched. No remote release has been published.

## Additional validation and limits

The same ten local HTTP acceptance tests pass in all three business lines, including native DashScope embedding/image credentials, requested/default models and application observation callbacks. The matched chat service/tool regressions pass on actual JVM8/17/25 (51/54/61 tests). Streaming reasoning consumption uses the delegate delta rather than appending cumulative content.

The optional AgentScope adapter currently follows the pinned upstream integration (`agentscope-core` 1.0.9). Migration to current AgentScope 2.x is a separate compatibility task; these checks do not claim that migration is complete. Java8 excludes the optional AgentScope starter and runtime-sandbox from its default supported reactor/BOM.
