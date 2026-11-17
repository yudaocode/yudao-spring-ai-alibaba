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
import java.util.List;

import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.runtime.domain.chat.ChatMessage;
import com.alibaba.cloud.ai.studio.runtime.domain.chat.MultimodalContent;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses the host Boot HTTP converter and installed Admin artifact, without producer classes.
 *
 * @author 芋道源码
 */
class AdminRuntimeHttpTest {

	@Test
	void installedRuntimePreservesTextAndTypedMultimodalRequests() throws Exception {
		assertEquals(System.getProperty("compat.expected.vm"), System.getProperty("java.specification.version"));
		assertTrue(ChatMessage.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"));
		assertEquals(1, Collections.list(ChatMessage.class.getClassLoader().getResources(
				ChatMessage.class.getName().replace('.', '/') + ".class")).size());
		org.slf4j.MDC.put("admin-runtime-check", "native");
		try {
			assertEquals("native", org.slf4j.MDC.get("admin-runtime-check"));
			assertEquals("admin-runtime-check", org.slf4j.MarkerFactory.getMarker("admin-runtime-check").getName());
		}
		finally {
			org.slf4j.MDC.remove("admin-runtime-check");
		}
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
			.properties("server.address=127.0.0.1", "server.port=0", "spring.main.banner-mode=off",
					"logging.level.root=WARN")
			.run()) {
			int port = context.getEnvironment().getProperty("local.server.port", Integer.class);
			assertEquals("预算 café 你好", JsonUtils.fromJson(post(port,
					"{\"role\":\"user\",\"content\":\"预算 café 你好\",\"future_field\":true}"))
				.get("data").asText());
			assertEquals("你好", JsonUtils.fromJson(post(port,
					"{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"你好\"}]}"))
				.get("data").asText());
		}
	}

	private String post(int port, String body) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/echo").openConnection();
		try {
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
			try (java.io.OutputStream output = connection.getOutputStream()) {
				output.write(body.getBytes(StandardCharsets.UTF_8));
			}
			int status = connection.getResponseCode();
			try (InputStream input = status == 200 ? connection.getInputStream() : connection.getErrorStream()) {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				for (int read; (read = input.read(buffer)) != -1;) {
					bytes.write(buffer, 0, read);
				}
				String response = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
				assertEquals(200, status, response);
				assertEquals("http-request", JsonUtils.fromJson(response).get("request_id").asText());
				assertEquals(200, JsonUtils.fromJson(response).get("code").intValue());
				assertTrue(!JsonUtils.fromJson(response).has("success"), response);
				return response;
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
	@Import(Echo.class)
	static class App {
	}

	/**
	 * @author 芋道源码
	 */
	@RestController
	static class Echo {

		@PostMapping("/echo")
		Result<String> echo(@RequestBody ChatMessage message) {
			Object content = message.getContent();
			String text = content instanceof String ? (String) content
					: ((MultimodalContent) ((List<?>) content).get(0)).getText();
			return Result.success("http-request", text);
		}

	}

}
