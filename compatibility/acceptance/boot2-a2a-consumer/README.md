# Independent Java 8 A2A consumer

Build and locally install the Java 8 compatibility branch first. This project does not inherit the library build parent.
Run Maven itself, the compiler and Surefire on an actual JDK 8. Set MAVEN_BASEDIR to this directory so the repository's build-JDK-17 `.mvn/jvm.config` is not applied to Java 8.

```sh
MAVEN_BASEDIR="$PWD/compatibility/acceptance/boot2-a2a-consumer" JAVA_HOME=/path/to/jdk8 /path/to/mvn -f compatibility/acceptance/boot2-a2a-consumer/pom.xml test
```

The alternate `pom-boot-parent.xml` exercises consumers inheriting Boot starter-parent 2.7.18. Importing a compatibility BOM does not replace dependency-management entries already inherited from that parent. Its explicit Jackson, Micrometer, Reactor, Netty, HttpClient5/Core5 and Kotlin version properties are part of the tested recipe.

```sh
MAVEN_BASEDIR="$PWD/compatibility/acceptance/boot2-a2a-consumer" JAVA_HOME=/path/to/jdk8 /path/to/mvn -f compatibility/acceptance/boot2-a2a-consumer/pom-boot-parent.xml test
```

Six tests check runtime versions; record-port canonical validation and JSON round trips; JSON-RPC request/response and server streaming; UTF-8/CRLF SSE; error status handling; and stream cancellation. They bind only a temporary loopback HTTP server, use no external model credentials and do not replace the three business snapshot acceptance gates.
