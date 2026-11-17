# Admin Core 独立消费者

先安装当前分支的 Spring AI、Alibaba 默认依赖，以及 Admin Core 候选模块。
本项目使用独立 Boot parent，只加载本地仓库已安装的 jar，不加载 producer target/classes。

```bash
./mvnw -f spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/pom.xml install
./mvnw -f compatibility-tests/admin-core/pom.xml clean test
```

可用 `-Dcompat.test.jvm=/path/to/java -Dcompat.expected.vm=17`（或25）指定实际消费 JVM。
验证 Boot 容器、H2 JDBC/MyBatis 自动配置、真实 JS/Python 标准库和 SDK 来源唯一性。
不启动完整 Admin 服务、不访问业务数据库或云账号，也不代表 UI 或所有后端均已验收。
Core 已加入现代分支默认 reactor；完整管理平台其余模块继续迁移。
