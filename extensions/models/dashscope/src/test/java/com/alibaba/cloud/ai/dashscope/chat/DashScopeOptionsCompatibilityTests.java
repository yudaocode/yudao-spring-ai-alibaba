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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.agent.DashScopeAgentOptions;
import com.alibaba.cloud.ai.dashscope.util.DashScopeOptionsUtils;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies option snapshots and Spring AI 2.1 builder contracts.
 *
 * @author 芋道源码
 */
class DashScopeOptionsCompatibilityTests {

    @Test
    void mutateRetainsProviderOptionsAndKeepsTheOriginalIndependent() {
        var original = DashScopeChatOptions.builder().model("qwen").seed(42).toolChoice("auto")
            .toolContext(new HashMap<>(Map.of("initial", "value"))).build();
        var changed = original.mutate().model("changed").toolContext("new", "value").build();
        assertThat(changed.getSeed()).isEqualTo(42);
        assertThat(changed.getToolChoice()).isEqualTo("auto");
        assertThat(changed.getToolContext()).containsKey("new");
        assertThat(original.getModel()).isEqualTo("qwen");
        assertThat(original.getToolContext()).doesNotContainKey("new");
    }

    @Test
    void builderAndCallerCollectionChangesDoNotModifyAnEarlierBuild() {
        var stops = new ArrayList<String>(List.of("first"));
        var builder = DashScopeChatOptions.builder().stopSequences(stops).model("first");
        var first = builder.build();
        stops.add("second");
        var second = builder.model("second").build();
        assertThat(first.getModel()).isEqualTo("first");
        assertThat(first.getStopSequences()).containsExactly("first");
        assertThat(second.getModel()).isEqualTo("second");
    }

    @Test
    void genericBuilderMergesNonNullOptionsAndRetainsProviderFields() {
        ToolCallback callback = mock(ToolCallback.class);
        var builder = DashScopeChatOptions.builder().model("default").seed(42).toolCallbacks(callback);
        var merged = builder.clone().combineWith(ToolCallingChatOptions.builder().model("runtime")
            .temperature(0.2)).build();
        assertThat(merged.getModel()).isEqualTo("runtime");
        assertThat(merged.getTemperature()).isEqualTo(0.2);
        assertThat(merged.getSeed()).isEqualTo(42);
        assertThat(merged.getToolCallbacks()).containsExactly(callback);
        assertThat(builder.build().getModel()).isEqualTo("default");
    }

    @Test
    void falseAndZeroOverrideDefaultsDuringProviderOptionMerging() {
        var defaults = DashScopeChatOptions.builder().enableThinking(true).maxTokens(100).build();
        var runtime = DashScopeChatOptions.builder().enableThinking(false).maxTokens(0).build();
        var merged = DashScopeOptionsUtils.merge(runtime, defaults, DashScopeChatOptions.class);
        assertThat(merged.getEnableThinking()).isFalse();
        assertThat(merged.getMaxTokens()).isZero();
    }

    @Test
    void unsupportedInternalToolExecutionFailsBeforeAnHttpCall() {
        DashScopeApi api = mock(DashScopeApi.class);
        var model = DashScopeChatModel.builder().dashScopeApi(api).build();
        var prompt = new Prompt("call a tool", DashScopeChatOptions.builder().internalToolExecutionEnabled(true).build());
        assertThatThrownBy(() -> model.call(prompt)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ToolCallingAdvisor");
        verifyNoInteractions(api);
    }

    @Test
    void agentBuilderCloneAndGenericModelOverrideKeepOriginalState() {
        var original = DashScopeAgentOptions.builder().appId("app").modelId("first").build();
        var changed = original.mutate().clone().combineWith(ChatOptions.builder().model("second")).build();
        assertThat(changed.getAppId()).isEqualTo("app");
        assertThat(changed.getModel()).isEqualTo("second");
        assertThat(original.getModel()).isEqualTo("first");
    }
}
