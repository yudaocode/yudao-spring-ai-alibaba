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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.alibaba.cloud.ai.studio.core.base.manager.ProviderManager;
import com.alibaba.cloud.ai.studio.core.model.llm.ModelFactory;
import com.alibaba.cloud.ai.studio.core.model.llm.domain.ModelCredential;
import com.alibaba.cloud.ai.studio.core.model.llm.domain.ProviderConfigInfo;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.IndexConfig;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Keeps provider HTTP error details observable through the installed Admin SDK.
 *
 * @author 芋道源码
 */
class ModelFactoryErrorHttpTest {

	@ParameterizedTest
	@ValueSource(strings = { "sync", "stream", "embedding" })
	void currentSdkKeepsAdminBusinessErrors(String operation) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/", exchange -> {
			try {
				byte[] response = "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"预算 café\",\"type\":\"invalid_request_error\"}}"
					.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(401, response.length);
				exchange.getResponseBody().write(response);
			}
			finally {
				exchange.close();
			}
		});
		server.start();
		try {
			ModelCredential credential = new ModelCredential();
			credential.setApiKey("local-key");
			credential.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
			ProviderConfigInfo provider = new ProviderConfigInfo();
			provider.setCredential(credential);
			ProviderManager providers = mock(ProviderManager.class);
			when(providers.getProviderDetail("local", false)).thenReturn(provider);
			ModelFactory factory = new ModelFactory();
			ReflectionTestUtils.setField(factory, "providerManager", providers);
			ReflectionTestUtils.setField(factory, "observationRegistry", ObservationRegistry.NOOP);
			ReflectionTestUtils.setField(factory, "customChatModelObservationConvention",
					new DefaultChatModelObservationConvention());
			assertThatThrownBy(() -> {
				if (operation.equals("embedding")) {
					factory.getEmbeddingModel(MetadataMode.NONE, IndexConfig.builder()
						.embeddingProvider("local").embeddingModel("local").build()).embed("你好");
				}
				else {
					ChatModel model = factory.getChatModel("local");
					Prompt prompt = new Prompt("你好", OpenAiChatOptions.builder().model("local").build());
					if (operation.equals("stream")) {
						model.stream(prompt).blockLast(Duration.ofSeconds(10));
					}
					else {
						model.call(prompt);
					}
				}
			}).isInstanceOfSatisfying(BizException.class, error -> {
				org.assertj.core.api.Assertions.assertThat(error.getError().getStatusCode()).isEqualTo(401);
				org.assertj.core.api.Assertions.assertThat(error.getError().getCode()).isEqualTo("invalid_api_key");
				org.assertj.core.api.Assertions.assertThat(error.getError().getMessage()).isEqualTo("预算 café");
			});
		}
		finally {
			server.stop(0);
		}
	}

}
