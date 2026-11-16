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
package com.alibaba.cloud.ai.graph.plain_text;

import java.util.Arrays;
import java.util.Collections;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.ai.zhipuai.ZhiPuAiAssistantMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Preserves mutable reasoning overrides independently of immutable ordered parts.
 * @author 芋道源码
 */
class ReasoningCheckpointOverrideTests {
    @Test
    void deepSeekRetainsNullAndNonNullOverrides() throws Exception {
        SpringAIJacksonStateSerializer serializer = new SpringAIJacksonStateSerializer(OverAllState::new);
        for (String override : Arrays.asList(null, "replacement")) {
            DeepSeekAssistantMessage source = DeepSeekAssistantMessage.builder().content("text")
                    .reasoningContent("old").properties(Collections.singletonMap("id", "42")).build();
            source.setReasoningContent(override);
            DeepSeekAssistantMessage restored = serializer.objectMapper().readValue(
                    serializer.objectMapper().writeValueAsString(source), DeepSeekAssistantMessage.class);
            assertEquals(source.getParts(), restored.getParts());
            assertEquals(source.getMetadata(), restored.getMetadata());
            assertEquals(override, restored.getReasoningContent());
            assertTrue(restored.hasReasoningContentOverride());
        }
    }

    @Test
    void zhiPuRetainsNullAndNonNullOverrides() throws Exception {
        SpringAIJacksonStateSerializer serializer = new SpringAIJacksonStateSerializer(OverAllState::new);
        for (String override : Arrays.asList(null, "replacement")) {
            ZhiPuAiAssistantMessage source = ZhiPuAiAssistantMessage.builder().content("text")
                    .reasoningContent("old").properties(Collections.singletonMap("id", "42")).build();
            source.setReasoningContent(override);
            ZhiPuAiAssistantMessage restored = serializer.objectMapper().readValue(
                    serializer.objectMapper().writeValueAsString(source), ZhiPuAiAssistantMessage.class);
            assertEquals(source.getParts(), restored.getParts());
            assertEquals(source.getMetadata(), restored.getMetadata());
            assertEquals(override, restored.getReasoningContent());
            assertTrue(restored.hasReasoningContentOverride());
        }
    }

    @Test
    void orderedPartsAndEqualValueSetterIntentSurviveFutureMutation() throws Exception {
        SpringAIJacksonStateSerializer serializer = new SpringAIJacksonStateSerializer(OverAllState::new);
        java.util.List<org.springframework.ai.chat.messages.part.MessagePart> parts = Arrays.asList(
                ReasoningPart.of("first"), TextPart.of("middle"), ReasoningPart.of("second"));
        for (boolean deepSeek : new boolean[] { true, false }) {
            for (boolean override : new boolean[] { false, true }) {
                AssistantMessage source = deepSeek ? DeepSeekAssistantMessage.builder().parts(parts).build()
                        : ZhiPuAiAssistantMessage.builder().parts(parts).build();
                if (override) {
                    if (deepSeek) ((DeepSeekAssistantMessage) source).setReasoningContent("firstsecond");
                    else ((ZhiPuAiAssistantMessage) source).setReasoningContent("firstsecond");
                }
                String json = serializer.objectMapper().writeValueAsString(source);
                AssistantMessage restored = serializer.objectMapper().readValue(json, source.getClass());
                assertEquals(source.getParts(), restored.getParts());
                assertEquals(source.mutate().build().getParts(), restored.mutate().build().getParts());
                assertEquals(override, deepSeek ? ((DeepSeekAssistantMessage) restored).hasReasoningContentOverride()
                        : ((ZhiPuAiAssistantMessage) restored).hasReasoningContentOverride());
                if (!override) {
                    AssistantMessage legacy = serializer.objectMapper().readValue(legacy(json), source.getClass());
                    assertEquals(parts, legacy.mutate().build().getParts());
                }
            }
        }
    }

    @Test
    void legacyCheckpointsInferNullAndChangedOverrides() throws Exception {
        SpringAIJacksonStateSerializer serializer = new SpringAIJacksonStateSerializer(OverAllState::new);
        for (String override : Arrays.asList(null, "replacement")) {
            DeepSeekAssistantMessage source = DeepSeekAssistantMessage.builder().content("text").reasoningContent("old").build();
            source.setReasoningContent(override);
            DeepSeekAssistantMessage restored = serializer.objectMapper().readValue(
                    legacy(serializer.objectMapper().writeValueAsString(source)), DeepSeekAssistantMessage.class);
            assertEquals(override, restored.getReasoningContent());
            ZhiPuAiAssistantMessage zhiPu = ZhiPuAiAssistantMessage.builder().content("text").reasoningContent("old").build();
            zhiPu.setReasoningContent(override);
            ZhiPuAiAssistantMessage zhiPuRestored = serializer.objectMapper().readValue(
                    legacy(serializer.objectMapper().writeValueAsString(zhiPu)), ZhiPuAiAssistantMessage.class);
            assertEquals(override, zhiPuRestored.getReasoningContent());
        }
    }

    private String legacy(String json) {
        return json.replace(",\"reasoningContentOverridden\":true", "")
                .replace(",\"reasoningContentOverridden\":false", "");
    }

}
