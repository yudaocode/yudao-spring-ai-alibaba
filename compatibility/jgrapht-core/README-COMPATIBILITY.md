# JGraphT 1.5.2 Java8 兼容依赖

Admin Core原来使用JGraphT1.5.2，官方jar要求JDK11。本模块保留该版本完整core源码，使用JDK17工具链生成/编译Java8产物。
坐标为`cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-jgrapht-compat`，版本由Alibaba BOM管理；包名仍为org.jgrapht。
不要同时引入原版org.jgrapht:jgrapht-core，避免重复类。

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai-alibaba</groupId>
    <artifactId>spring-ai-alibaba-jgrapht-compat</artifactId>
</dependency>
```

先安装当前路线的yudao-spring-ai，再在本仓库根目录构建：

```bash
bash ./mvnw -Dmaven.javadoc.skip=true install
bash ./mvnw -f compatibility-tests/admin-jgrapht/pom.xml test -Dcompat.test.jvm=/path/to/jdk8/bin/java -Dcompat.expected.vm=1.8
```

## 验证与边界

- 官方基线：jgrapht-1.5.2 / fe2d4cdfa42073eb1141e789844adc9c1fc8bf36。
- 完整450个主源码、307个测试/性能源码参与编译；JPMS描述符不进入Java8产物。
- 上游默认单测选择6621项，真实Corretto8u472运行，零失败/错误/跳过。
- 保留上游默认排除的SlowTests、OptionalTests和perf/**；未将这些排除项计作通过。
- 当前默认31模块全量8532项，零失败/错误、286条件跳过，重试关闭。
- 独立Boot2/native8消费者2项全通过；实际启动Boot并验证DAG、拒绝循环、拓扑序、私有构造器/序列化恢复、最短路径及MDC/Marker。
- 安装jar含711个Java8类；原版689个类的4188个类与公开/受保护成员签名无缺失，新增类来自Java8编译桥接。
- 真实消费类路径下，owned JDK/图/堆/数值库/兼容工具成员检查无缺失。
- 61个消费test/runtime jar、17812个有效基础类均不高于major52；MR覆盖和module-info另行排除。
- 全依赖静态扫描仍保留两项SLF4J1.7 binder兼容回退引用，实际MDC/Marker路径已通过，不声称整个依赖树所有API路径均为零发现。

源码调整包括Java9反射访问API、IndexOutOfBoundsException整型构造器、测试集合toArray工厂，以及恢复序列化supplier的访问设置。
后者由独立消费者复现，原失败日志保存在任务笔记；没有改成公开测试边类型来绕过失败。
源文件保留上游作者和EPL2.0/LGPL2.1-or-later双许可证，来源与变换见UPSTREAM-SOURCES.json；许可证/贡献者同时随jar打包。
第三方源包保留原格式/许可头，因此仅此模块跳过Spring风格工具，Git行末空格例外也仅限其上游源码、资源和许可证，依赖版本/字节码门禁及真实测试继续执行。
本模块完成不代表Admin Core/Python或完整管理平台已在Java8完成；本批没有向Maven Central部署。
