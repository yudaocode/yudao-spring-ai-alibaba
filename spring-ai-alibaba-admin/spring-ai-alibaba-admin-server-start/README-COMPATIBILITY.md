# Admin Start 兼容运行说明

当前分支的 Admin Start 使用 Spring Boot 3.5.15，目标运行 JDK 17/25。从根目录使用 JDK 17 构建。Initializr 使用 0.23.0，SpringDoc 使用 2.8.17，Elasticsearch Java 客户端使用 8.13.4。

先完成根 README 中对应路线的 Spring AI、AgentScope 兼容制品安装，再从本仓库根目录运行：

```bash
bash ./mvnw -pl spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start -am clean package
```

Java 8 路线可追加 `-Djvm=/实际/JDK8/bin/java`，让 Surefire 使用原生 JDK8。现代路线也可以用对应的 `-Djvm` 验证 JDK25。

可执行包路径为 `spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/target/spring-ai-alibaba-admin-server-start.jar`。设置中间件连接后，以目标运行 JDK 启动：

```bash
java -jar spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-start/target/spring-ai-alibaba-admin-server-start.jar
```

- MySQL 使用 `spring.datasource.*`，先应用 [Admin schema](../docker/middleware/init/mysql/admin-schema.sql)。
- Redis 使用 `spring.data.redis.*` 配置 Admin 的 Redisson 客户端。
- Elasticsearch 使用 `spring.elasticsearch.url` 配置 Admin 客户端。
- RocketMQ 使用 `rocketmq.endpoints` 连接支持 gRPC 的 RocketMQ 5 Proxy，并配置索引主题和消费组。
- Nacos 使用 `nacos.server-addr`。
- 使用 `MODEL_CONFIG_FILE` 提供模型配置路径。按实际部署配置提供密钥和外部模型地址。

启动后可检查 `/console/v1/system/health`、`/console/v1/system/global-config` 和 `/v3/api-docs`。默认 Admin 启动入口是 `SaaStudioAdmin`。生成器依然按所选项目模板设置目标 Boot/JDK。

Start 回归在本地实际启动 Web 端口和 Druid/H2，读取临时模型 YAML，验证系统接口、OpenAPI 401、SpringDoc 文档和 OpenAPI YAML 解析。格式化测试运行 Eclipse 格式化器并检查 Unicode。Redis、RocketMQ 和 Nacos 在可重复的测试中使用 fixture，云模型调用和外部整套部署需按环境另行验证。

本分支完整默认构建共 28 个模块，原生 JDK17 报告 1964 项，0 失败、0 错误、290 跳过。Docker 不可用时，PostgreSQL 容器用例按条件跳过。
