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

package com.alibaba.cloud.ai.studio.core.model.llm;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import com.alibaba.cloud.ai.studio.core.base.manager.ProviderManager;
import com.alibaba.cloud.ai.studio.core.model.llm.domain.ModelCredential;
import com.alibaba.cloud.ai.studio.core.model.llm.domain.ProviderConfigInfo;
import com.alibaba.cloud.ai.studio.core.utils.api.ApiUtils;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.IndexConfig;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the SDK-based model factory through real local HTTP and stored endpoint forms.
 *
 * @author 芋道源码
 */
class ModelFactoryHttpTests {

	@ParameterizedTest
	@ValueSource(strings = { "", "/v1", "/v1/" })
	void sdkKeepsEndpointPrefixCredentialsHeadersAndUnicode(String suffix) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		AtomicInteger requests = new AtomicInteger();
		server.createContext("/prefix/v1/", exchange -> {
			try {
				assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer local-key");
				assertThat(exchange.getRequestHeaders().get("User-Agent").toString()).contains(ApiUtils.userAgent());
				ByteArrayOutputStream input = new ByteArrayOutputStream();
				try (InputStream body = exchange.getRequestBody()) {
					byte[] buffer = new byte[4096];
					for (int read; (read = body.read(buffer)) != -1;) {
						input.write(buffer, 0, read);
					}
				}
				assertThat(new String(input.toByteArray(), StandardCharsets.UTF_8)).contains("你好");
				String response;
				if (exchange.getRequestURI().getPath().endsWith("/embeddings")) {
					response = "{\"object\":\"list\",\"data\":[{\"index\":0,\"object\":\"embedding\","
							+ "\"embedding\":[1.0,0.0]}],\"model\":\"local\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
				}
				else {
					assertThat(exchange.getRequestURI().getPath()).endsWith("/chat/completions");
					response = "{\"id\":\"local\",\"object\":\"chat.completion\",\"created\":0,\"model\":\"local\","
							+ "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"你好\"},"
							+ "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
				}
				byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
				requests.incrementAndGet();
				exchange.sendResponseHeaders(200, bytes.length);
				exchange.getResponseBody().write(bytes);
			}
			finally {
				exchange.close();
			}
		});
		server.start();
		try {
			ModelCredential credential = new ModelCredential();
			credential.setApiKey("local-key");
			credential.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/prefix" + suffix);
			ProviderConfigInfo provider = new ProviderConfigInfo();
			provider.setCredential(credential);
			ProviderManager manager = mock(ProviderManager.class);
			when(manager.getProviderDetail("local", false)).thenReturn(provider);
			ModelFactory factory = new ModelFactory();
			ReflectionTestUtils.setField(factory, "providerManager", manager);
			ReflectionTestUtils.setField(factory, "observationRegistry", ObservationRegistry.NOOP);
			ReflectionTestUtils.setField(factory, "customChatModelObservationConvention",
					new DefaultChatModelObservationConvention());
			ChatModel chat = factory.getChatModel("local");
			assertThat(chat.call(new Prompt(Collections.singletonList(new UserMessage("你好")),
					OpenAiChatOptions.builder().model("local").build())).getResult().getOutput().getText())
				.isEqualTo("你好");
			EmbeddingModel embedding = factory.getEmbeddingModel(MetadataMode.NONE,
					IndexConfig.builder().name("local-index").embeddingProvider("local").embeddingModel("local").build());
			assertThat(embedding.embed("你好")).containsExactly(1.0f, 0.0f);
			assertThat(requests.get()).isEqualTo(2);
		}
		finally {
			server.stop(0);
		}
	}

}
