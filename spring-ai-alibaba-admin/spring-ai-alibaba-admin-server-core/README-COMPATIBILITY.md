# Admin Core 兼容类库

本分支面向 Boot4 / Spring7，最低 JDK17，已验证 JDK17/25。
`cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-admin-server-core` 的版本由 Alibaba BOM 管理。
先按根 README 安装相同路线 Spring AI、AgentScope，再在本仓库执行：

```bash
bash ./mvnw -Dmaven.javadoc.skip=true install
bash ./mvnw -f compatibility-tests/admin-core/pom.xml clean test
```

独立消费者使用自己的 Boot parent 和已安装 jar，未加载 producer target/classes。
通过 `-Dcompat.test.jvm=/path/to/java -Dcompat.expected.vm=17`（或25）可选真实目标 JVM。
Core42项；另有4项消费用例，均零失败/错误/跳过。
默认26模块全量1955项、288条件跳过；跳过的外部条件未计为通过。
构建工具为JDK17，运行验证的JDK17/25分别记录；重试关闭。
本批保留根Checkstyle和Spotless检查，spring-javaformat validate及springdoc生成使用既有验证脚本的skip选项。

## 接入范围

这是服务、工作流、模型和检索组件的类库；不会仅因添加依赖就启动完整 Admin 平台。
宿主按需导入组件/配置，数据库连接池由宿主选择；完整平台的OpenAPI、Start和服务装配继续迁移。
测试消费者只导入ScriptEngineConfig/SandboxManager，启动真实H2/MyBatis，并执行实际脚本。
没有全量扫描并装配所有业务组件，没有访问原业务仓数据库或外部模型账号。

## 兼容处理

- 使用本仓库当前DashScope和Spring AI2.x，移除历史官方core1.0依赖。
- 不覆盖org.springframework.ai类：AdminSearchRequest、SearchType、AdminElasticsearchVectorStore均在com.alibaba.cloud.ai.studio.core.rag.vectorstore下。
- 语义、全文、混合查询实际通过本地HTTP协议夹具；同时读取旧content和新text文档字段。
- HTTP夹具验证序列化/协议契约，不代替真实Elasticsearch集群验收。
- Graal家族24.2.1补齐Python标准库资源，实际执行JS、GraalJS、Python、JSON/math和Unicode。
- 模型工厂保留endpoint的/v1前缀、凭据、头、观测及不可变工具参数；SDK错误映射保留Admin的BizException与原始cause。
- 同步聊天、流式聊天和向量调用的HTTP401已通过独立消费者回归。
- Core直接依赖Redisson SDK；已有RedissonConfig负责创建客户端，不额外引入另一套自动配置。
- Boot4使用对应MyBatis starter/service API与Spring7 HTTP接口；Boot3保持其依赖路线。

脚本接口保持上游allowAllAccess行为，未将其宣称为执行不可信代码的安全隔离环境。
云服务、全部数据库/MQ/Redis后端、完整UI部署和所有脚本包并未全部验收。
Boot2 Core仍在移植；Java8 Python原型13项通过尚不等同于正式SDK支持。
没有向Maven Central或远端SNAPSHOT仓库部署。
