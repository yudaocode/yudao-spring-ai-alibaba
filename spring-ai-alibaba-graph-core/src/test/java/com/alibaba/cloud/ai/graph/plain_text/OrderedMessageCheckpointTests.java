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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.UnknownPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.zhipuai.ZhiPuAiAssistantMessage;
import org.springframework.util.MimeTypeUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies checkpoint replay preserves content needed by modern model providers.
 *
 * @author 芋道源码
 */
class OrderedMessageCheckpointTests {

    private List<MessagePart> parts() {
        OpaquePayload signature = new OpaquePayload("anthropic", "signature", "opaque+/=\nexact");
        return List.of(new ReasoningPart("thinking", "summary", signature, Map.of("index", "0")),
                TextPart.of("first"),
                new ToolCallPart(new AssistantMessage.ToolCall("call-1", "function", "lookup", "{\"q\":1}"),
                        new OpaquePayload("google", "thought_signature", "AA=="), Map.of()),
                MediaPart.of(Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new byte[] {0, 1, -1}).build()),
                TextPart.of("last"),
                new UnknownPart("provider", "future_part", "{\"exact\":true}", signature, Map.of()));
    }

    @Test
    void assistantRetainsOrderPayloadsMediaAndMetadata() throws Exception {
        var original = AssistantMessage.builder().parts(parts()).properties(Map.of("request", "42")).build();
        assertCheckpoint(original);
    }

    @Test
    void userRetainsInterleavedMediaAndText() throws Exception {
        assertCheckpoint(UserMessage.builder().parts(parts()).metadata(Map.of("request", "42")).build());
    }

    @Test
    void legacyProviderSubclassesRetainModernParts() throws Exception {
        assertCheckpoint(DeepSeekAssistantMessage.builder().parts(parts()).properties(Map.of("request", "42")).build());
        assertCheckpoint(ZhiPuAiAssistantMessage.builder().parts(parts()).properties(Map.of("request", "42")).build());
    }

    @Test
    void emptyPartsRemainEmptyWithGlobalNonEmptyInclusion() throws Exception {
        assertCheckpoint(AssistantMessage.builder().parts(List.of()).build());
    }

    @Test
    void oldCheckpointWithoutPartsStillReads() throws Exception {
        var serializer = serializer();
        String legacy = "{\"@class\":\"org.springframework.ai.chat.messages.AssistantMessage\","
                + "\"text\":\"old\",\"toolCalls\":[{\"id\":\"1\",\"type\":\"function\","
                + "\"name\":\"lookup\",\"arguments\":\"{}\"}],\"metadata\":{\"request\":\"42\"}}";
        AssistantMessage message = serializer.objectMapper().readValue(legacy, AssistantMessage.class);
        assertEquals("old", message.getText());
        assertEquals("lookup", message.getToolCalls().get(0).name());
        assertEquals("42", message.getMetadata().get("request"));
    }

    private SpringAIJacksonStateSerializer serializer() {
        ObjectMapper mapper = new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);
        return new SpringAIJacksonStateSerializer(OverAllState::new, mapper);
    }

    private void assertCheckpoint(AbstractMessage original) throws Exception {
        var serializer = serializer();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            serializer.writeData(Map.of("message", original), output);
        }
        AbstractMessage restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (AbstractMessage) serializer.readData(input).get("message");
        }
        assertEquals(original.getClass(), restored.getClass());
        assertEquals(original.getParts(), restored.getParts());
        assertEquals(original.getMetadata(), restored.getMetadata());
        if (restored.getParts().size() > 3) {
            assertArrayEquals(new byte[] {0, 1, -1}, (byte[]) ((MediaPart) restored.getParts().get(3)).media().getData());
        }
    }
}
