/*
 * Copyright 2024-2026 the original author or authors.
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
package com.alibaba.cloud.ai.agent.nacos;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.cloud.ai.agent.nacos.utils.ChatOptionsProxy;
import com.alibaba.cloud.ai.agent.nacos.vo.ModelVO;
import com.alibaba.cloud.ai.observation.model.ObservationMetadataContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Exercises Nacos model construction and hot replacement with real immutable options.
 *
 * @author 芋道源码
 */
class NacosModelRuntimeTests {

    @Test
    void configuredVersionPathAndHeadersReachLocalOpenAiEndpoint() throws Exception {
        assertHttpEndpoint("/v1");
    }

    @Test
    void rootBaseUrlRetainsLegacyVersionedEndpoint() throws Exception {
        assertHttpEndpoint("");
    }

    private void assertHttpEndpoint(String suffix) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"id\":\"chat-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"gpt-4\","
                    + "\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"local-ok\"}}],"
                    + "\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":1,\"total_tokens\":3}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ModelVO config = new ModelVO();
            config.setApiKey("local-test-token");
            config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + suffix);
            OpenAiChatOptions options = (OpenAiChatOptions) ChatOptionsProxy.createProxy(
                    OpenAiChatOptions.builder().model("gpt-4").temperature(0.25).build(), Map.of("agent", "runtime"));
            ChatModel model = ReflectionTestUtils.invokeMethod(NacosReactAgentBuilder.class, "createModel",
                    mock(NacosOptions.class), config, options);
            assertNotNull(model);
            ChatResponse response = model.call(new Prompt("hello"));
            assertEquals("local-ok", response.getResult().getOutput().getText());
            assertEquals("/v1/chat/completions", path.get());
            assertEquals("Bearer local-test-token", authorization.get());
            assertTrue(body.get().contains("gpt-4"));
            assertTrue(body.get().contains("0.25"));
            assertFalse(body.get().contains("ObservationMetadataContext"));
            assertEquals("runtime", ObservationMetadataContext.find(model.getDefaultOptions()).getObservationMetadata().get("agent"));
        }
        finally {
            server.stop(0);
        }
    }

    @Test
    void hotReplacementChangesModelAndDefaultOptions() throws Exception {
        AtomicReference<Prompt> received = new AtomicReference<>();
        ChatModel before = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("before"))));
        ChatModel after = new ChatModel() {
            @Override
            public OpenAiChatOptions getOptions() {
                return OpenAiChatOptions.builder().model("default-model").build();
            }
            @Override
            public ChatResponse call(Prompt prompt) {
                received.set(prompt);
                return new ChatResponse(List.of(new Generation(new AssistantMessage("after"))));
            }
        };
        ChatClient client = ChatClient.builder(before)
                .defaultOptions(OpenAiChatOptions.builder().model("before-model")).build();
        OpenAiChatOptions replacement = (OpenAiChatOptions) ChatOptionsProxy.createProxy(
                OpenAiChatOptions.builder().model("after-model").temperature(0.3).build(), Map.of("version", "2"));
        NacosModelInjector.replaceModel(client, after, replacement);
        assertEquals("after", client.prompt().user("hello").call().content());
        assertEquals("after-model", received.get().getOptions().getModel());
        assertEquals(0.3, received.get().getOptions().getTemperature());
        assertEquals("2", ObservationMetadataContext.find(received.get().getOptions()).getObservationMetadata().get("version"));
    }
}
