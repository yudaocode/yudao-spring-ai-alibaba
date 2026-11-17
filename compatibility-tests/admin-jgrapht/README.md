# Boot2 / Java8 图依赖消费

使用独立Boot2.7.18 parent和已安装的兼容jar；先在仓库根目录安装Spring AI和本SDK。

```bash
bash ./mvnw -f compatibility-tests/admin-jgrapht/pom.xml clean test -Dcompat.test.jvm=/path/to/jdk8/bin/java -Dcompat.expected.vm=1.8
```

两个用例实际启动Boot上下文，验证图/循环/拓扑、序列化恢复、最短路径及MDC/Marker。
类来源和单份Graph.class资源均校验，未使用producer target/classes。
这只是Admin的图依赖验收，不是完整Admin平台或全部第三方后端验收。
