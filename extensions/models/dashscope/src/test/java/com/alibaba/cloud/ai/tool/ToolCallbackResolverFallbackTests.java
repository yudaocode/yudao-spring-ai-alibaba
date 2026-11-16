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
package com.alibaba.cloud.ai.tool;

import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.tool.ObservableToolCallingManager;
import com.alibaba.cloud.ai.tool.DashScopeAsyncToolCallingManager;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Public-manager reproductions of resolver fallback with nullable default callbacks.
 * @author 芋道源码
 */
class ToolCallbackResolverFallbackTests {
    @Test
    void explicitCallbacksTakePrecedenceOverTheSameNamedTool() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        try {
            AtomicInteger lookups = new AtomicInteger();
            ToolCallbackResolver resolver = name -> { lookups.incrementAndGet(); return null; };
            ToolCallback callback = callback(new AtomicInteger());
            DashScopeChatOptions options = DashScopeChatOptions.builder().toolNames(Collections.singleton("local"))
                    .toolCallbacks(Collections.singletonList(callback)).build();
            for (ToolCallingManager manager : java.util.Arrays.asList(
                    new ObservableToolCallingManager(ObservationRegistry.NOOP, resolver, error -> "error"),
                    new DashScopeAsyncToolCallingManager(ObservationRegistry.NOOP, resolver, error -> "error", executor))) {
                assertEquals(1, manager.resolveToolDefinitions(options).size());
            }
            assertEquals(0, lookups.get());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void unresolvedNamedToolsFailClearlyDuringDefinitionDiscovery() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        try {
            DashScopeChatOptions options = DashScopeChatOptions.builder().toolNames(Collections.singleton("missing")).build();
            for (ToolCallingManager manager : java.util.Arrays.asList(
                    new ObservableToolCallingManager(ObservationRegistry.NOOP, name -> null, error -> "error"),
                    new DashScopeAsyncToolCallingManager(ObservationRegistry.NOOP, name -> null, error -> "error", executor))) {
                IllegalStateException failure = assertThrows(IllegalStateException.class, () -> manager.resolveToolDefinitions(options));
                assertTrue(failure.getMessage().contains("No ToolCallback found for tool name: missing"));
            }
        } finally { executor.shutdownNow(); }
    }

    @Test
    void concreteProviderToolNamesStillContributeDefinitions() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        try {
            ToolCallbackResolver resolver = name -> callback(new AtomicInteger());
            ToolCallingManager observable = new ObservableToolCallingManager(ObservationRegistry.NOOP, resolver, error -> "error");
            ToolCallingManager asynchronous = new DashScopeAsyncToolCallingManager(ObservationRegistry.NOOP, resolver, error -> "error", executor);
            DashScopeChatOptions options = DashScopeChatOptions.builder().toolNames(Collections.singleton("local")).build();
            assertAll(() -> assertEquals(1, observable.resolveToolDefinitions(options).size()),
                    () -> assertEquals(1, asynchronous.resolveToolDefinitions(options).size()));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void observableManagerUsesResolverWithDefaultProviderOptions() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallbackResolver resolver = name -> callback(calls);
        ToolCallingManager manager = new ObservableToolCallingManager(ObservationRegistry.NOOP, resolver, error -> "error");
        verify(manager, calls);
    }

    @Test
    void asynchronousManagerUsesResolverWithDefaultProviderOptions() {
        AtomicInteger calls = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        try {
            ToolCallingManager manager = new DashScopeAsyncToolCallingManager(ObservationRegistry.NOOP,
                    name -> callback(calls), error -> "error", executor);
            verify(manager, calls);
        } finally { executor.shutdownNow(); }
    }

    private ToolCallback callback(AtomicInteger calls) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("local").description("local test").inputSchema("{\"type\":\"object\"}").build();
            }
            @Override
            public String call(String input) {
                calls.incrementAndGet();
                return "local result";
            }
        };
    }

    private void verify(ToolCallingManager manager, AtomicInteger calls) {
        DashScopeChatOptions options = DashScopeChatOptions.builder().build();
        assertNull(options.getToolCallbacks(), "the fixture must exercise the nullable provider default");
        Prompt prompt = new Prompt("call the tool", options);
        AssistantMessage message = AssistantMessage.builder().toolCalls(Collections.singletonList(
                new AssistantMessage.ToolCall("id", "function", "local", "{}"))).build();
        ToolExecutionResult result = manager.executeToolCalls(prompt,
                new ChatResponse(Collections.singletonList(new Generation(message))));
        assertEquals(1, calls.get());
        assertEquals(3, result.conversationHistory().size());
        ToolResponseMessage response = (ToolResponseMessage) result.conversationHistory().get(2);
        assertEquals("local result", response.getResponses().get(0).responseData());
    }
}
