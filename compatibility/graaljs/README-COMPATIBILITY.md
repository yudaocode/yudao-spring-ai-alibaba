# GraalJS22.0.0.2 / Java8 兼容运行时

这是本分支Admin Core使用的语言运行时。开发坐标为
`cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-graaljs-compat:1.1.2.2-jdk8-SNAPSHOT`，尚未发布Maven Central。
在根README的两份BOM下直接添加对应artifact即可；Core已经传递引入。
不要同时引入原版JS/Python语言实现；公共SDK/Truffle API仍使用22.0.0.2。

使用JDK17从根reactor构建，产物运行于Java8；构建工具和ASM不进入运行依赖。
上游输入、URL、SHA256、源码补丁记录见[UPSTREAM-SOURCES.json](UPSTREAM-SOURCES.json)，构建校验原始输入哈希。
不会通过修改class版本冒充Java8兼容。许可原文、第三方通知及源码随runtime/sources jar保留。

保留上游公共Polyglot/JSR223 API，仅将内置ICU移入私有包；不暴露其内部ICU类型作为稳定API。

```java
import org.graalvm.polyglot.Context;
try (Context context = Context.newBuilder("js").build()) {
    int answer = context.eval("js", "6 * 7").asInt();
}
```

需要Intl时显式设置`allowExperimentalOptions(true).option("js.intl-402", "true")`。
3项原生Java8组件测试覆盖JSON/Unicode/JavaMap/BigInt、货币/时间/中文排序和JSR223，并与外部ICU74.2共存。
普通Java8解释执行，未提供AOT/native-image或Graal JIT性能验收。
