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
package cn.iocoder.ai.compatibility;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.a2a.http.JdkA2AHttpClient;
import io.a2a.server.requesthandlers.JSONRPCHandler;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.*;
import io.a2a.util.Utils;
import mutiny.zero.ZeroPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.ai.compat.jdkhttp.FlowAdapters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Runs the source-ported A2A protocol, server streaming and HTTP transport on Java 8.
 *
 * @author 芋道源码
 */
class A2aJava8RuntimeTests {
    private static final String CARD = "{\"name\":\"local\",\"description\":\"test\",\"url\":\"http://127.0.0.1/\","
            + "\"version\":\"1\",\"capabilities\":{\"streaming\":true,\"pushNotifications\":false,\"stateTransitionHistory\":false},"
            + "\"defaultInputModes\":[\"text\"],\"defaultOutputModes\":[\"text\"],\"skills\":[],\"protocolVersion\":\"0.2.5\"}";
    private static final String PARAMS = "{\"message\":{\"role\":\"user\",\"kind\":\"message\",\"messageId\":\"m1\","
            + "\"parts\":[{\"kind\":\"text\",\"text\":\"hello\"}]}}";

    @Test
    void consumerRetainsTheAuditedFrameworkAndCodecVersions() {
        assertEquals("2.7.18", org.springframework.boot.SpringBootVersion.getVersion());
        assertEquals("5.3.31", org.springframework.core.SpringVersion.getVersion());
        assertEquals("2.21.4", com.fasterxml.jackson.databind.ObjectMapper.class.getPackage().getImplementationVersion());
        assertEquals("3.7.19", reactor.core.publisher.Flux.class.getPackage().getImplementationVersion());
    }

    @Test
    void realJava8LoadsRecordPortsAndValidatesCanonicalConstructors() throws Exception {
        assertEquals("1.8", System.getProperty("java.specification.version"));
        AgentCard card = Utils.OBJECT_MAPPER.readValue(CARD, AgentCard.class);
        assertEquals("local", card.name());
        assertTrue(card.capabilities().streaming());
        AgentCard restored = Utils.OBJECT_MAPPER.readValue(Utils.OBJECT_MAPPER.writeValueAsString(card), AgentCard.class);
        assertEquals(card, restored);
        assertThrows(Exception.class, () -> Utils.OBJECT_MAPPER.readValue("{}", AgentCard.class));
    }

    @Test
    void jsonRpcRequestResponseAndStreamingUseTheSameProtocolTypes() throws Exception {
        AgentCard card = Utils.OBJECT_MAPPER.readValue(CARD, AgentCard.class);
        Message output = new Message.Builder().role(Message.Role.AGENT)
                .parts(Collections.singletonList(new TextPart("reply"))).messageId("out-1").build();
        RequestHandler requestHandler = mock(RequestHandler.class);
        when(requestHandler.onMessageSend(any())).thenReturn(output);
        when(requestHandler.onMessageSendStream(any())).thenReturn(ZeroPublisher.fromItems(output));
        JSONRPCHandler handler = new JSONRPCHandler(card, requestHandler);
        SendMessageRequest request = Utils.OBJECT_MAPPER.readValue(
                "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"message/send\",\"params\":" + PARAMS + "}", SendMessageRequest.class);
        JsonNode response = Utils.OBJECT_MAPPER.readTree(Utils.OBJECT_MAPPER.writeValueAsString(handler.onMessageSend(request)));
        assertEquals(42, response.get("id").asInt());
        assertEquals("reply", response.path("result").path("parts").get(0).path("text").asText());
        SendStreamingMessageRequest stream = Utils.OBJECT_MAPPER.readValue(
                "{\"jsonrpc\":\"2.0\",\"id\":43,\"method\":\"message/stream\",\"params\":" + PARAMS + "}", SendStreamingMessageRequest.class);
        List<SendStreamingMessageResponse> chunks = FlowAdapters.flowPublisherToFlux(handler.onMessageSendStream(stream))
                .collectList().block(Duration.ofSeconds(2));
        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        JsonNode chunk = Utils.OBJECT_MAPPER.readTree(Utils.OBJECT_MAPPER.writeValueAsString(chunks.get(0)));
        assertEquals(43, chunk.path("id").asInt());
        assertEquals("reply", chunk.path("result").path("parts").get(0).path("text").asText());
    }

    @Test
    void ssePreservesSplitUtf8AndCallsCompletionOnce() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            byte[] data = "data: 第一行\n\ndata: second\r\n\r\n".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseBody().write(data, 0, 7);
            exchange.getResponseBody().flush();
            exchange.getResponseBody().write(data, 7, data.length - 7);
            exchange.close();
        });
        server.start();
        try {
            List<String> messages = new ArrayList<>();
            AtomicInteger errors = new AtomicInteger();
            AtomicInteger completions = new AtomicInteger();
            new JdkA2AHttpClient().createGet().url(url(server)).getAsyncSSE(messages::add,
                    error -> errors.incrementAndGet(), completions::incrementAndGet).get(3, TimeUnit.SECONDS);
            assertEquals(Arrays.asList("第一行", "second"), messages);
            assertEquals(0, errors.get());
            assertEquals(1, completions.get());
        }
        finally { server.stop(0); }
    }

    @Test
    void failingStatusDoesNotDeliverSuccessEvents() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(503, 0);
            exchange.getResponseBody().write("data: error\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            AtomicInteger messages = new AtomicInteger();
            AtomicInteger errors = new AtomicInteger();
            AtomicInteger completions = new AtomicInteger();
            CompletableFuture<Void> future = new JdkA2AHttpClient().createGet().url(url(server))
                    .getAsyncSSE(message -> messages.incrementAndGet(), error -> errors.incrementAndGet(), completions::incrementAndGet);
            assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
            assertEquals(0, messages.get());
            assertEquals(1, errors.get());
            assertEquals(0, completions.get());
        }
        finally { server.stop(0); }
    }

    @Test
    void cancellationStopsAnOpenEventStream() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("data: ready\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            AtomicInteger completions = new AtomicInteger();
            AtomicInteger errors = new AtomicInteger();
            CompletableFuture<Void> future = new JdkA2AHttpClient().createGet().url(url(server))
                    .getAsyncSSE(message -> received.countDown(), error -> errors.incrementAndGet(), completions::incrementAndGet);
            assertTrue(received.await(2, TimeUnit.SECONDS));
            assertTrue(future.cancel(true));
            assertTrue(future.isCancelled());
            assertEquals(0, errors.get());
            assertEquals(0, completions.get());
        }
        finally { release.countDown(); server.stop(0); }
    }

    private String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort() + "/"; }
}
