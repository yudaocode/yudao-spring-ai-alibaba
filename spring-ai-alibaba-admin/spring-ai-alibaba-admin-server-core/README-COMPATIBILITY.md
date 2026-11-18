# Admin Core / Boot2 / Java8

Core 类库已加入默认 reactor 和 Alibaba BOM，开发版本 `1.1.2.2-jdk8-SNAPSHOT`。
先安装本分支依赖的 Spring AI/AgentScope 开发版，再使用 JDK17 构建本仓库。
所有坐标尚未发布 Maven Central。

宿主使用 Boot2.7.18 parent，按照根 README 导入 Spring AI、Alibaba 两个兼容 BOM，再添加：

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai-alibaba</groupId>
    <artifactId>spring-ai-alibaba-admin-server-core</artifactId>
</dependency>
```

不要混入官方 Alibaba Core1.0、原版 JGraphT1.5.2、GraalJS/Python 语言实现；传递依赖已经使用本分支的隔离兼容包。
Spring Framework5、Jackson2、Retry1.x、Boot2 自动配置路线保持一致；Micrometer 等公共 SDK 依赖版本由兼容 BOM 管理，必须同时导入两份 BOM。
检索扩展改为 `com.alibaba.cloud.ai.studio.core.rag.vectorstore.*`，不覆盖 `org.springframework.ai.*` 框架类。

## 验证

```bash
bash ./mvnw -Dsurefire.rerunFailingTestsCount=0 -Djvm=/path/to/jdk8/bin/java clean install
bash ./mvnw -f compatibility-tests/admin-core/pom.xml clean test \
  -Dcompat.test.jvm=/path/to/jdk8/bin/java -Dcompat.expected.vm=1.8
```

34模块完整 clean install：8580项，零失败/错误、286项条件跳过，重试关闭。
Core自身42项、JS/Python各3项，均在原生Java8运行且零跳过。
独立安装jar消费者4项在macOS Corretto8及Linux Corretto8通过：实际启动宿主Boot/H2/MyBatis、JavaScript/Python，验证同步/流式/向量HTTP401错误映射。
Elasticsearch搜索使用真实HTTP本地fixture覆盖三种检索模式和content/text字段，不等于真实集群验收。

四个本批SDK包共22740个类均不高于class52，2973个JDK成员引用检查零缺失。
完整消费路径352个jar、160085个有效base类均不高于class52；这是字节码门禁，不能据此宣称所有外部依赖API路径都可用。
完整依赖API扫描仍有57个原始引用报告，包括Guava/Javassist/Kryo的高JDK选择分支、JDK Flow适配器、SLF4J可选/兼容分支，及原Elasticsearch 8.13.4 OpenTelemetry的Java9 Buffer调用。
这些不计入单测失败，也未全部当作误报消除；其中 Elasticsearch 查询请求体追踪已在下一节复验。

## 边界

内嵌Python为GraalPython22.0.0.2/Python3.8.5解释执行路线；不支持LLVM、原生C扩展和任意PyPI依赖。
普通Java8不会提供Graal JIT性能。详见[GraalPython说明](../../compatibility/graalpython/README-COMPATIBILITY.md)。
完整Admin服务装配与真实MySQL/Redis/ES/OSS/云模型等仍待验收；Core类库通过不等于整个平台可以部署。

## Java 8 Elasticsearch 请求体追踪

Admin 使用 Elasticsearch Java API Client 和 REST Client 8.19.11。Boot 2.7 parent 的默认 REST Client 版本是 7.x，宿主请在 `properties` 中设置：

```xml
<elasticsearch.version>8.19.11</elasticsearch.version>
```

同一请求在原生 Java 8 上复现旧客户端 8.13.4 的 Buffer 链接错误。升级后，测试验证 `db.statement` 记录的 JSON 与本地 HTTP 服务收到的请求一致，包含 Unicode，并复测三种检索模式。

8.19.11 保留 Java 8 与 Jackson 2 依赖路线。8.19.12 起客户端默认传递依赖 Jackson 3。本次测试使用本地 Elasticsearch HTTP fixture。
