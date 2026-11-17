# 独立 Boot2 / Java8 Admin Core 消费验证

这是独立宿主parent的安装jar验证项目；先安装根reactor，按根README导入两份兼容BOM。

```bash
bash ./mvnw -f compatibility-tests/admin-core/pom.xml clean test \
  -Dcompat.test.jvm=/path/to/jdk8/bin/java -Dcompat.expected.vm=1.8
```

4项测试运行实际Boot2/H2/MyBatis、JS/Python，并验证同步、流式和向量调用HTTP401错误映射。
使用安装进Maven仓库的Core jar，不通过reactor的target/classes绕过消费依赖。
已在macOS/Corretto8和Linux/Corretto8通过；Linux使用内部Docker网络，禁止外部路由，同时给MyBatis ID生成器提供非回环网卡。
该结果不代表完整Admin服务或真实外部后端通过，边界见[Core说明](../../spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-core/README-COMPATIBILITY.md)。
