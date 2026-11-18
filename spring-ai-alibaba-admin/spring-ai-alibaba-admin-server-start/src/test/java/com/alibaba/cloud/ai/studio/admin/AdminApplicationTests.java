/*
 * Copyright 2025 the original author or authors.
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

package com.alibaba.cloud.ai.studio.admin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import com.alibaba.cloud.ai.studio.admin.service.impl.NacosClientService;
import com.alibaba.cloud.ai.studio.admin.repository.ModelConfigRepository;
import com.alibaba.cloud.ai.studio.core.base.mq.MqConsumerManager;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the production Admin entry point with an H2 database and middleware fixtures.
 *
 * @author 芋道源码
 */
@SpringBootTest(classes = SaaStudioAdmin.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"server.address=127.0.0.1",
				"spring.datasource.url=jdbc:h2:mem:admin-start;MODE=MySQL;DB_CLOSE_DELAY=-1",
				"spring.datasource.driver-class-name=org.h2.Driver",
				"spring.datasource.username=sa", "spring.datasource.password=",
				"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
				"spring.elasticsearch.url=http://127.0.0.1:1",
				"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1",
				"nacos.server-addr=127.0.0.1:1", "rocketmq.endpoints=127.0.0.1:1",
				"spring.elasticsearch.uris=http://127.0.0.1:1",
				"management.otlp.metrics.export.enabled=false",
				"management.tracing.sampling.probability=0",
				"management.otlp.tracing.endpoint=http://127.0.0.1:1/v1/traces",
				"management.opentelemetry.tracing.export.otlp.endpoint=http://127.0.0.1:1",
				"management.otlp.logging.endpoint=http://127.0.0.1:1",
				"spring.main.banner-mode=off", "logging.level.root=WARN"
		})
class AdminApplicationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private ModelConfigRepository models;

	@DynamicPropertySource
	static void modelConfig(DynamicPropertyRegistry properties) throws IOException {
		Path directory = Files.createTempDirectory("admin-model-test-");
		directory.toFile().deleteOnExit();
		Path model = directory.resolve("model-config.yml");
		model.toFile().deleteOnExit();
		String yaml = "models:\n  - id: 1001\n    name: 测试模型\n    provider: openai\n"
				+ "    modelName: fixture\n    baseUrl: http://127.0.0.1:1\n    apiKey: test-fixture\n";
		Files.write(model, yaml.getBytes(StandardCharsets.UTF_8));
		properties.add("MODEL_CONFIG_FILE", model::toString);
	}

	@MockBean
	private Producer documentIndexProducer;

	@MockBean
	private MqConsumerManager mqConsumerManager;

	@MockBean
	private RedissonClient redissonClient;

	@MockBean
	private NacosClientService nacosClientService;

	@Test
	void servesAdminAndOpenApiWithTheTargetBootRuntime() throws Exception {
		try (Connection connection = this.dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("select 1")) {
			assertThat(result.next()).isTrue();
			assertThat(result.getInt(1)).isEqualTo(1);
		}
		assertThat(this.models.findById(1001L)).isNotNull();
		assertThat(this.models.findById(1001L).getName()).isEqualTo("测试模型");
		assertThat(request("/console/v1/system/health", null, 200)).isEqualTo("ok");
		assertThat(JsonUtils.fromJson(request("/console/v1/system/global-config", null, 200)).path("code").asInt())
			.isEqualTo(200);
		assertThat(JsonUtils.fromJson(request("/api/v1/apps/chat/completions", "{}", 401)).path("code").asInt())
			.isEqualTo(401);
		assertThat(JsonUtils.fromJson(request("/v3/api-docs", null, 200)).path("openapi").asText())
			.startsWith("3.");
	}

	@Test
	void readsImportedOpenApiSpecWithTheSameSwaggerClasses() {
		String document = "openapi: 3.0.3\ninfo: {title: Demo, version: '1'}\npaths: {}\n";
		assertThat(new OpenAPIV3Parser().readContents(document, null, null).getOpenAPI().getInfo().getTitle())
			.isEqualTo("Demo");
	}

	private String request(String path, String requestBody, int expectedStatus) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + this.port + path)
			.openConnection();
		try {
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(15000);
			if (requestBody != null) {
				connection.setRequestMethod("POST");
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", "application/json");
				try (java.io.OutputStream output = connection.getOutputStream()) {
					output.write(requestBody.getBytes(StandardCharsets.UTF_8));
				}
			}
			int status = connection.getResponseCode();
			try (InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
				ByteArrayOutputStream content = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				for (int read; (read = input.read(buffer)) != -1;) {
					content.write(buffer, 0, read);
				}
				String body = new String(content.toByteArray(), StandardCharsets.UTF_8);
				assertThat(status).as("%s: %s", path, body).isEqualTo(expectedStatus);
				return body;
			}
		}
		finally {
			connection.disconnect();
		}
	}

}
