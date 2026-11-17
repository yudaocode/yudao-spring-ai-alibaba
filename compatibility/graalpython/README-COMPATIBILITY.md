# GraalPython22.0.0.2 / Java8 兼容运行时

这是本分支Admin Core使用的语言运行时。开发坐标为
`cn.iocoder.spring-ai-alibaba:spring-ai-alibaba-graalpython-compat:1.1.2.2-jdk8-SNAPSHOT`，尚未发布Maven Central。
在根README的两份BOM下直接添加对应artifact即可；Core已经传递引入。
不要同时引入原版JS/Python语言实现；公共SDK/Truffle API仍使用22.0.0.2。

使用JDK17从根reactor构建，产物运行于Java8；构建工具和ASM不进入运行依赖。
上游输入、URL、SHA256、源码补丁记录见[UPSTREAM-SOURCES.json](UPSTREAM-SOURCES.json)，构建校验原始输入哈希。
不会通过修改class版本冒充Java8兼容。许可原文、第三方通知及源码随runtime/sources jar保留。

Python版本3.8.5，来源为官方vm-22.0.0.2；保留911个跨平台标准库文件，使用Java POSIX解释执行。
原始安装包虽是Darwin发行载体，本制品不打包其中原生二进制；macOS与Linux/Corretto8的实际消费已通过。
ICU、ANTLR、XZ使用私有包，避免宿主常见版本冲突。原始NIO Buffer调用做语义不变的Java8适配；两组Python源码及真实LLVM Toolchain接口以release8重编译。

```java
import cn.iocoder.spring.ai.alibaba.compat.graalpython.GraalPyContexts;
import org.graalvm.polyglot.Context;

try (Context context = GraalPyContexts.newContextBuilder().build()) {
    int answer = context.eval("python", "6 * 7").asInt();
}
```

标准库在首次创建context时解压到临时目录，使用每个context的home参数，不设置全局python.home。
复杂list/map结果可使用本包`GraalPyValues.toJavaObject(Value)`转换为普通Java值；只支持JSON形状的数据，不支持任意Python对象图。
使用Java互操作的可信脚本可显式设置host access；Admin原有脚本功能保留其权限语义，不提供不可信代码安全隔离保证。

3项组件测试覆盖json/math/datetime/unicodedata、Unicode/大整数、None/list/map、lzma/regex、语法错误、context隔离，以及JS共存和外部ICU74.2/ANTLR4.13.2/XZ1.10冲突场景。
不支持LLVM、原生C扩展、任意PyPI包、所有标准库/OS行为；普通JDK8仅解释执行，未承诺Graal JIT性能。
许可证包括UPL1.0、PSF及打包通知中的第三方条款；真实LLVM接口的BSD3版权通知单独保留在META-INF/licenses/llvm-toolchain。
