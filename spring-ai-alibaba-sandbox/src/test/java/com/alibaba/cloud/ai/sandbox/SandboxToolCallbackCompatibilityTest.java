/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.sandbox;

import com.alibaba.cloud.ai.sandbox.tools.base.SaaBaseShellRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.runtime.sandbox.box.BaseSandbox;
import io.agentscope.runtime.sandbox.manager.ManagerConfig;
import io.agentscope.runtime.sandbox.manager.SandboxService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Spring AI tool JSON through the actual Alibaba adapter and remote sandbox
 * SDK.
 *
 * @author 芋道源码
 */
@Timeout(15)
class SandboxToolCallbackCompatibilityTest {

	@Test
	void shellToolPreservesSchemaUtf8ArgumentsResultAndSandboxLifecycle() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		List<JsonNode> calls = new CopyOnWriteArrayList<>();
		List<String> auth = new CopyOnWriteArrayList<>();
		AtomicInteger creates = new AtomicInteger();
		AtomicInteger removes = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		ExecutorService peer = Executors.newCachedThreadPool();
		server.setExecutor(peer);
		server.createContext("/sandbox/", exchange -> {
			try {
				auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
				JsonNode request = mapper.readTree(exchange.getRequestBody());
				String endpoint = exchange.getRequestURI().getPath();
				String response;
				if (endpoint.equals("/sandbox/createContainer")) {
					creates.incrementAndGet();
					response = "{\"data\":{\"containerId\":\"fixture-sandbox\",\"containerName\":\"fixture-owned\",\"sessionId\":\"fixture-session\",\"version\":\"fixture\"}}";
				}
				else if (endpoint.equals("/sandbox/callTool")) {
					calls.add(request);
					response = mapper
						.writeValueAsString(java.util.Collections.singletonMap("data", "sandbox-result-中文"));
				}
				else if (endpoint.equals("/sandbox/removeSandbox")) {
					removes.incrementAndGet();
					response = "{\"data\":true}";
				}
				else {
					throw new IllegalStateException("Unexpected fixture endpoint: " + endpoint);
				}
				byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, bytes.length);
				exchange.getResponseBody().write(bytes);
			}
			finally {
				exchange.close();
			}
		});
		server.start();
		try {
			ManagerConfig config = ManagerConfig.builder()
				.baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
				.bearerToken("fixture-token")
				.remoteRequestTimeout(Duration.ofSeconds(3))
				.build();
			try (SandboxService service = new SandboxService(config)) {
				service.start();
				SaaBaseShellRunner tool = new SaaBaseShellRunner();
				tool.setSandbox(new BaseSandbox(service, "fixture-user", "fixture-session"));
				org.springframework.ai.tool.ToolCallback callback = tool.buildTool();
				assertEquals("run_shell_command", callback.getToolDefinition().name());
				JsonNode schema = mapper.readTree(callback.getToolDefinition().inputSchema());
				assertTrue(schema.path("properties").has("command"));
				assertEquals("command", schema.path("required").get(0).asText());
				JsonNode first = mapper.readTree(callback.call("{\"command\":\"printf '你好'\"}"));
				assertEquals("sandbox-result-中文", first.path("Response").path("result").asText());
				JsonNode second = mapper.readTree(callback.call("{\"command\":\"printf second\"}"));
				assertEquals("sandbox-result-中文", second.path("Response").path("result").asText());
				assertEquals(1, creates.get());
				assertEquals(2, calls.size());
				assertEquals("fixture-sandbox", calls.get(0).path("sandboxId").asText());
				assertEquals("run_shell_command", calls.get(0).path("toolName").asText());
				assertEquals("printf '你好'", calls.get(0).path("arguments").path("command").asText());
				assertEquals("printf second", calls.get(1).path("arguments").path("command").asText());
			}
			assertEquals(1, removes.get());
			assertFalse(auth.isEmpty());
			assertTrue(auth.stream().allMatch("Bearer fixture-token"::equals));
		}
		finally {
			server.stop(0);
			peer.shutdownNow();
		}
	}

}
