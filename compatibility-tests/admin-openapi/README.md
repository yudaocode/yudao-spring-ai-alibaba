# 安装jar / boot3 OpenAPI HTTP验收

先安装根reactor，再使用根README的对应JDK路线运行：

```bash
bash ./mvnw -f compatibility-tests/admin-openapi/pom.xml clean test \
  -Dcompat.test.jvm=/path/to/target-jdk/bin/java -Dcompat.expected.vm=17
```

单项测试包含五次真实端口请求：无key401、聊天Unicode JSON、工作流JSON、聊天SSE403、工作流SSE403。
检查控制器来自唯一安装jar、实际JVM、响应内容/状态/媒体类型及SSE事件。宿主Boot及servlet转换器真实运行，业务服务为fixture，不等于完整管理平台验收。
