# 版本与依赖选择

版本保留当前源码基线的官方编号，避免用Boot主版本冒充上游版本。
共用现代制品不增加JDK后缀；需要隔离维护的兼容路线增加JDK后缀。
开发坐标尚未发布Maven Central，需先从源码安装。

| 项目 | Boot2 / JDK8 | Boot3 / JDK17或25 | Boot4 / JDK17或25 |
| --- | --- | --- | --- |
| Spring AI | 2.1.0-jdk8-SNAPSHOT | 2.1.0-jdk17-SNAPSHOT | 2.1.0-jdk25-SNAPSHOT |
| Spring AI Alibaba | 1.1.2.2-jdk8-SNAPSHOT | 1.1.2.2-jdk17-SNAPSHOT | 1.1.2.2-jdk25-SNAPSHOT |
| AgentScope | 2.1.0-jdk8-SNAPSHOT | 2.1.0-SNAPSHOT | 共用2.1.0-SNAPSHOT |
| LangChain4j | 1.24.0-jdk8-SNAPSHOT / 1.24.0-beta34-jdk8-SNAPSHOT | 官方版本，当前组合验证1.23.0 | 官方版本，当前组合验证1.23.0 |
| LangGraph4j兼容BOM/适配器 | 1.9.9-jdk8-SNAPSHOT | 1.9.9-jdk17-SNAPSHOT | 1.9.9-jdk25-SNAPSHOT |

先按Spring Boot依赖线选择制品，再确认运行JDK。JDK25运行Boot3时，仍使用Boot3对应的jdk17路线。jdk25表示本仓Boot4消费验证路线，不表示产物必须使用Java25字节码；实际最低JDK在README兼容矩阵中单独说明。

LangChain4j stable/beta模块沿用各自官方编号，由BOM统一管理。LangGraph4j的现代组合继续使用官方org.bsc.langgraph4j核心及LangChain4j adapter1.9.9；我们仅为需要维护的Spring AI adapter/BOM提供兼容坐标。Java8才回迁图核心、async及两套adapter。

## 发布类型

- 统一格式：`官方版本[-jdk数字][-preview]-YYYYMMDD`，不增加同日序号；共用现代制品省略jdk后缀。保留官方版本原有的beta、RC等标记。
- 日常开发：仍使用上表SNAPSHOT，发版时才确定日期。这里只描述源码开发坐标，不能当成已发布制品。
- 官方正式版本的兼容发行：例如`1.9.9-jdk8-20261117`；共用制品例如`2.1.0-20261117`（仅为格式示例，不表示该官方版本或本仓制品已发布）。我们的修复不递增官方版本号。
- main提前预览：上游基线尚未正式发布时增加preview，例如`2.1.0-jdk8-preview-20261117`；共用现代制品为`2.1.0-preview-20261117`。不可将主线预览标成官方正式发行。
- 日期统一按北京时间确定。通常使用实际发版日期；同一基线、同一路线一天内确需再次发布时，版本日期在已使用日期基础上顺延至下一个可用日期，例如`20261117`之后使用`20261118`，不增加`.1`、`.2`等序号。已经发布的完整版本不可覆盖。
- 顺延的版本日期可以晚于实际发版日期；发布记录仍写真实发版时间，并记录上游SHA、本仓SHA、目标JDK/Boot及验证范围。不同兼容路线按兼容矩阵选择，不通过比较版本大小自动切换路线。
- 官方版本已满足功能和运行环境时，直接使用官方制品。本仓制品使用独立cn.iocoder命名空间；本次只更新命名约定，没有上传任何Maven制品。

## 维护范围

LangGraph4j跟进当前最新主线，不另外维护1.8.x兼容分支。官方1.8.x存在维护分支和发布记录；这只是本仓维护范围的选择，并不代表官方旧线停止维护。参见[官方发布记录](https://github.com/langgraph4j/langgraph4j/releases)。
Spring AI当前源码基线为2.1.0-SNAPSHOT，区别于[官方已发布的2.0.x说明](https://docs.spring.io/spring-ai/reference/getting-started.html)。

官方README正文保留；兼容版的真实支持范围、构建要求和用法以本仓README前缀及兼容说明为准。
