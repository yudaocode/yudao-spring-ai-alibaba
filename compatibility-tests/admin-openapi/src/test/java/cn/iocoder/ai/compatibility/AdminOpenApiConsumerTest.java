/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.iocoder.ai.compatibility;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import com.alibaba.cloud.ai.studio.controller.ChatController;
import com.alibaba.cloud.ai.studio.interceptor.ApiKeyAuthInterceptor;
import com.alibaba.cloud.ai.studio.core.base.manager.RedisManager;
import com.alibaba.cloud.ai.studio.core.base.service.AccountService;
import com.alibaba.cloud.ai.studio.core.base.service.AgentService;
import com.alibaba.cloud.ai.studio.core.base.service.ApiKeyService;
import com.alibaba.cloud.ai.studio.core.base.service.WorkflowService;
import com.alibaba.cloud.ai.studio.runtime.domain.account.Account;
import com.alibaba.cloud.ai.studio.runtime.domain.account.ApiKey;
import com.alibaba.cloud.ai.studio.runtime.domain.agent.AgentResponse;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.debug.WorkflowResponse;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the installed OpenAPI controller and interceptor on the host Boot HTTP server.
 * Only application services are fixtures; servlet, converter and SSE paths are real.
 *
 * @author 芋道源码
 */
class AdminOpenApiConsumerTest {

	@Test
	void installedOpenApiServesAuthenticatedJsonAndStreamErrors() throws Exception {
		assertEquals(System.getProperty("compat.expected.vm"), System.getProperty("java.specification.version"));
		assertTrue(ChatController.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"));
		assertEquals(1,
				Collections
					.list(ChatController.class.getClassLoader()
						.getResources(ChatController.class.getName().replace('.', '/') + ".class"))
					.size());
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
			.properties("server.address=127.0.0.1", "server.port=0", "spring.main.banner-mode=off",
					"logging.level.root=WARN")
			.run()) {
			int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
			post(port, "chat", false, false, 401, "\"code\":401");
			post(port, "chat", false, true, 200, "\"request_id\":\"响应-café\"");
			post(port, "workflow", false, true, 200, "\"request_id\":\"workflow-a\"");
			post(port, "chat", true, true, 403, ErrorCode.FORBIDDEN.getCode());
			post(port, "workflow", true, true, 403, ErrorCode.FORBIDDEN.getCode());
		}
	}

	private void post(int port, String kind, boolean stream, boolean authorized, int expectedStatus,
			String expectedText) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) new URL(
				"http://127.0.0.1:" + port + "/api/v1/apps/" + kind + "/completions")
			.openConnection();
		try {
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
			if (authorized)
				connection.setRequestProperty("Authorization", "Bearer secret");
			try (java.io.OutputStream output = connection.getOutputStream()) {
				output.write(("{\"stream\":" + stream + "}").getBytes(StandardCharsets.UTF_8));
			}
			int status = connection.getResponseCode();
			try (InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				for (int read; (read = input.read(buffer)) != -1;)
					bytes.write(buffer, 0, read);
				String body = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
				assertEquals(expectedStatus, status, body);
				assertTrue(body.contains(expectedText),
						"route=" + kind + " contentType=" + connection.getContentType() + " body=" + body);
				if (stream) {
					assertEquals("no", connection.getHeaderField("X-Accel-Buffering"));
					assertTrue(body.contains("data:"), body);
				}
				else
					assertTrue(connection.getContentType().startsWith("application/json"), connection.getContentType());
			}
		}
		finally {
			connection.disconnect();
		}
	}

	/**
	 * @author 芋道源码
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class App {

		@Bean
		AccountService accounts() {
			AccountService service = mock(AccountService.class);
			Account account = new Account();
			account.setAccountId("account-a");
			account.setDefaultWorkspaceId("workspace-a");
			when(service.getAccount("account-a")).thenReturn(account);
			return service;
		}

		@Bean
		ApiKeyService keys() {
			ApiKeyService service = mock(ApiKeyService.class);
			ApiKey key = new ApiKey();
			key.setAccountId("account-a");
			when(service.getApiKey("secret")).thenReturn(key);
			return service;
		}

		@Bean
		ApiKeyAuthInterceptor auth(AccountService accounts, ApiKeyService keys) {
			return new ApiKeyAuthInterceptor(accounts, keys);
		}

		@Bean
		WebMvcConfigurer routes(final ApiKeyAuthInterceptor auth) {
			return new WebMvcConfigurer() {
				@Override
				public void addInterceptors(InterceptorRegistry registry) {
					registry.addInterceptor(auth).addPathPatterns("/api/v1/apps/**");
				}
			};
		}

		@Bean
		ChatController controller() {
			AgentService agents = mock(AgentService.class);
			when(agents.call(any())).thenReturn(AgentResponse.builder().requestId("响应-café").build());
			when(agents.streamCall(any())).thenReturn(Flux.error(new BizException(ErrorCode.FORBIDDEN.toError())));
			WorkflowService workflows = mock(WorkflowService.class);
			when(workflows.call(any())).thenReturn(WorkflowResponse.builder().requestId("workflow-a").build());
			when(workflows.streamCall(any())).thenReturn(Flux.error(new BizException(ErrorCode.FORBIDDEN.toError())));
			return new ChatController(agents, workflows, mock(RedisManager.class));
		}

	}

}
