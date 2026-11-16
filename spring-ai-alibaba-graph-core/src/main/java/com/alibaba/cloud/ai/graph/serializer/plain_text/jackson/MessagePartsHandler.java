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
package com.alibaba.cloud.ai.graph.serializer.plain_text.jackson;

import java.io.IOException;
import java.util.List;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.messages.part.MessagePart;

/**
 * Retains the ordered message content while keeping legacy checkpoint fields readable.
 *
 * @author 芋道源码
 */
final class MessagePartsHandler {

    private static final tools.jackson.core.type.TypeReference<List<MessagePart>> PARTS =
            new tools.jackson.core.type.TypeReference<>() { };

    // Spring AI 2.x parts carry Jackson 3 converters (notably binary media).
    // Encode this structural boundary with their native mapper; graph state stays Jackson 2.
    private static final tools.jackson.databind.json.JsonMapper PART_MAPPER =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private MessagePartsHandler() { }

    static void serialize(List<MessagePart> parts, JsonGenerator generator) throws IOException {
        generator.writeFieldName("parts");
        generator.writeRawValue(PART_MAPPER.writerFor(PARTS).writeValueAsString(parts));
    }

    static List<MessagePart> deserialize(ObjectMapper mapper, JsonNode node) throws IOException {
        JsonNode parts = node.get("parts");
        if (parts == null || parts.isNull()) {
            return null;
        }
        if (!parts.isArray()) {
            throw new IOException("Message parts must be an array");
        }
        return PART_MAPPER.readValue(parts.toString(), PARTS);
    }



}
