# Admin runtime / main-jdk17

This branch supports the Admin DTO/JSON utility library on Boot3, using `1.1.2.2-jdk17-SNAPSHOT`.
Install the source tree first; these coordinates have not been published to Maven Central.
Keep the application's own Boot parent and import the matching `cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-bom`.

```xml
<dependency>
    <groupId>cn.iocoder.spring-ai-alibaba</groupId>
    <artifactId>spring-ai-alibaba-admin-server-runtime</artifactId>
</dependency>
```

Use the original `com.alibaba.cloud.ai.studio.runtime.*` Java packages.
For example, `JsonUtils.fromJson(json, ChatMessage.class)` handles text and typed multimodal content, while `Result.success(requestId, value)` keeps the Admin response field names.
The Boot4 artifact also carries a Jackson3 deserializer annotation/bridge for its default MVC converter; the existing Jackson2 runtime utility/tree contract remains available.
Boot2/3 artifacts contain no Jackson3 classes or dependency.

The runtime's14 focused cases cover JSON round trips, Unicode, polymorphic content, wire names, formatted errors and agent finish-reason mapping.
The full default reactor passed1909 cases with288 conditional skips; no test failures/errors and retries disabled.
The standalone [HTTP consumer](../../compatibility-tests/admin-runtime) uses installed jars and the host's real MVC converter, not producer output directories.
From the repository root, build with JDK17 and set the actual test VM explicitly:

```bash
./mvnw -pl spring-ai-alibaba-admin/spring-ai-alibaba-admin-server-runtime -am install \
  -Djvm=/path/to/target-jdk/bin/java -Dmaven.javadoc.skip=true
./mvnw -f compatibility-tests/admin-runtime/pom.xml test \
  -Dcompat.test.jvm=/path/to/target-jdk/bin/java -Dcompat.expected.vm=17
```

For modern routes repeat the standalone case with JDK25 and `-Dcompat.expected.vm=25`.
For Boot2 use a real JDK8 test VM even though the source transformation/compiler toolchain runs on JDK17.
This library does not start the Admin platform, configure its database or validate cloud services.
Core is included on Boot3/4 after its separate library gates; Admin openapi/start remain pending; the `admin-server-pending` profile is retained for porting work, not advertised as a working server distribution.
