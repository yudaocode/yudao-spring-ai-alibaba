# Admin OpenAPI / boot3

OpenAPI控制器/鉴权类库已纳入本分支默认reactor及Alibaba BOM；开发版本`1.1.2.2-jdk17-SNAPSHOT`。
先按根README源码安装对应Spring AI、AgentScope、Alibaba开发版，再在两份兼容BOM下引入：

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai-alibaba</groupId>
    <artifactId>spring-ai-alibaba-admin-server-openapi</artifactId>
</dependency>
```

当前未发布Maven Central。引入类库不会启动完整Admin平台。
宿主必须提供AgentService、WorkflowService、RedisManager、AccountService和ApiKeyService等业务服务，注册ChatController、ApiKeyAuthInterceptor，并通过WebMvcConfigurer把鉴权拦截器应用到`/api/v1/apps/**`。
最小宿主装配示例及真实端口验证见[独立消费者](../../compatibility-tests/admin-openapi/src/test/java/cn/iocoder/ai/compatibility/AdminOpenApiConsumerTest.java)。
客户端使用`Authorization: Bearer <key>`，向`/api/v1/apps/chat/completions`或`/workflow/completions`提交JSON；`stream=true`返回SSE。
普通JSON保留UTF-8与JSON媒体类型；同步/流式业务错误保留错误内容。流已开始写入后发生错误时，客户端应读取SSE错误内容，HTTP状态无法再更改。
修复了原订阅链将恢复后的错误响应丢掉、返回空流的问题。
Boot4另修复Jackson3在缺省is_draft时错误选择全参构造器并返回400的问题；未关闭全局严格反序列化。

本分支完整27模块clean install：1961项、0失败/错误、288条件跳过；重试关闭。
OpenAPI十项MVC测试在本路线每个目标JVM均零失败/错误/跳过，覆盖鉴权、Unicode JSON、同步错误、聊天/工作流SSE错误。
每个目标JVM另有一项独立安装jar消费测试，启动宿主Boot真实HTTP端口并发送五次请求；业务服务使用确定性fixture，不连接云端。

```bash
bash ./mvnw -Dsurefire.rerunFailingTestsCount=0 -Djvm=/path/to/target-jdk/bin/java clean install
bash ./mvnw -f compatibility-tests/admin-openapi/pom.xml test \
  -Dcompat.test.jvm=/path/to/target-jdk/bin/java -Dcompat.expected.vm=17
```

Start 已加入默认构建，连接与运行说明见 [Admin Start](../spring-ai-alibaba-admin-server-start/README-COMPATIBILITY.md)。MySQL/Redis/Elasticsearch/OSS/云模型需按部署环境验收。本批没有宣称所有控制器端点、异步工作流、所有第三方API路径均已通过。
Java8的Graal/Python与外部依赖限制继续见Core说明；未进行Maven部署。
