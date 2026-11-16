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
package com.alibaba.cloud.ai.dashscope.chat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real HTTP codecs and model boundary without external credentials.
 *
 * @author 芋道源码
 */
class DashScopeHttpRuntimeTests {

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> sseHeader = new AtomicReference<>();
    private HttpServer server;
    private DashScopeChatModel model;
    private String responseBody;
    private String contentType;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            requestBody.set(StreamUtils.copyToString(exchange.getRequestBody(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sseHeader.set(exchange.getRequestHeaders().getFirst("X-DashScope-SSE"));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
                // Split every byte to exercise UTF-8 and SSE framing across writes.
                for (byte value : bytes) {
                    output.write(value);
                    output.flush();
                }
            }
            finally {
                exchange.close();
            }
        });
        server.start();
        DashScopeApi api = DashScopeApi.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .completionsPath("/chat").apiKey("local-test-key").build();
        model = DashScopeChatModel.builder().dashScopeApi(api)
                .defaultOptions(DashScopeChatOptions.builder().model("qwen-plus").incrementalOutput(true).build())
                .build();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void synchronousChatUsesNativeHttpCodecsAndUsage() throws Exception {
        contentType = "application/json";
        responseBody = completion("hello", "stop", true);
        ChatResponse response = model.call(new Prompt("question"));
        assertThat(response.getResult().getOutput().getText()).isEqualTo("hello");
        assertThat(response.getMetadata().getId()).isEqualTo("local-request");
        assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(12);
        assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(5);
        assertRequest();
    }

    @Test
    void streamingChatDecodesUtf8DeltasAndCompletes() throws Exception {
        contentType = "text/event-stream";
        responseBody = "data: " + completion("\u6c49", "null", false) + "\r\n\r\n"
                + "data: " + completion("\u5b57", "stop", true) + "\r\n\r\n"
                + "data: [DONE]\r\n\r\n";
        List<ChatResponse> responses = model.stream(new Prompt("question")).collectList().block(Duration.ofSeconds(10));
        assertThat(responses).isNotEmpty();
        String text = responses.stream().map(response -> response.getResult().getOutput().getText())
                .collect(java.util.stream.Collectors.joining());
        assertThat(text).isEqualTo("\u6c49\u5b57");
        assertThat(responses.get(responses.size() - 1).getMetadata().getUsage().getTotalTokens()).isEqualTo(17);
        assertThat(sseHeader.get()).isEqualTo("enable");
        assertRequest();
    }

    @Test
    void providerSseErrorIsNotReturnedAsSuccessfulText() {
        contentType = "text/event-stream";
        responseBody = "data: {\"code\":\"InvalidParameter\",\"message\":\"local invalid request\","
                + "\"request_id\":\"local-error\"}\n\n";
        assertThatThrownBy(() -> model.stream(new Prompt("question")).collectList().block(Duration.ofSeconds(10)))
                .hasMessageContaining("InvalidParameter").hasMessageContaining("local invalid request");
    }

    @Test
    void chatClientKeepsNativeRuntimeOptions() throws Exception {
        contentType = "application/json";
        responseBody = completion("hello", "stop", true);
        DashScopeChatOptions options = DashScopeChatOptions.builder().model("custom-qwen")
                .enableThinking(false).thinkingBudget(2048).temperature(0.2).maxToken(123).build();
        ChatResponse response = org.springframework.ai.chat.client.ChatClient.create(model)
                .prompt(new Prompt("question", options)).call().chatResponse();
        assertThat(response.getResult().getOutput().getText()).isEqualTo("hello");
        JsonNode body = new ObjectMapper().readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("custom-qwen");
        assertThat(body.path("parameters").path("enable_thinking").booleanValue()).isFalse();
        assertThat(body.path("parameters").path("thinking_budget").asInt()).isEqualTo(2048);
        assertThat(body.path("parameters").path("max_tokens").asInt()).isEqualTo(123);
        assertThat(body.path("parameters").path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(model.getOptions().getModel()).isEqualTo("qwen-plus");
    }

    @Test
    void nativeOptionsAreCopiedForEachClient() {
        DashScopeChatOptions options = model.getOptions();
        options.setModel("changed");
        assertThat(model.getOptions().getModel()).isEqualTo("qwen-plus");
        assertThat(model.getDefaultOptions().getModel()).isEqualTo("qwen-plus");
    }

    private void assertRequest() throws Exception {
        assertThat(authorization.get()).isEqualTo("Bearer local-test-key");
        JsonNode body = new ObjectMapper().readTree(requestBody.get());
        assertThat(body.path("model").asText()).isEqualTo("qwen-plus");
        assertThat(body.path("input").path("messages").get(0).path("content").asText()).isEqualTo("question");
    }

    private static String completion(String text, String finish, boolean usage) {
        return "{\"request_id\":\"local-request\",\"output\":{\"choices\":[{\"index\":0,"
                + "\"finish_reason\":" + ("null".equals(finish) ? "null" : "\"" + finish + "\"")
                + ",\"message\":{\"role\":\"assistant\",\"content\":\"" + text + "\"}}]}"
                + (usage ? ",\"usage\":{\"input_tokens\":12,\"output_tokens\":5,\"total_tokens\":17}" : "") + "}";
    }
}
