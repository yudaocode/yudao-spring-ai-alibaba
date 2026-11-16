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
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.messages.part.MessagePart;

/**
 * Retains the ordered message content while keeping legacy checkpoint fields readable.
 *
 * @author 芋道源码
 */
final class MessagePartsHandler {

    private static final TypeReference<List<MessagePart>> PARTS = new TypeReference<>() { };

    private MessagePartsHandler() { }

    static void serialize(List<MessagePart> parts, JsonGenerator generator) throws IOException {
        generator.writeFieldName("parts");
        // Empty attributes are constructor invariants, and the part discriminator is
        // defined by MessagePart itself rather than graph state default typing.
        ObjectMapper mapper = partsMapper((ObjectMapper) generator.getCodec());
        mapper.writerFor(PARTS).writeValue(generator, parts);
    }

    static List<MessagePart> deserialize(ObjectMapper mapper, JsonNode node) throws IOException {
        JsonNode parts = node.get("parts");
        if (parts == null || parts.isNull()) {
            return null;
        }
        if (!parts.isArray()) {
            throw new IOException("Message parts must be an array");
        }
        ObjectMapper partMapper = partsMapper(mapper);
        List<MessagePart> result = new ArrayList<>();
        for (JsonNode part : parts) {
            // Decode directly: graph's generic collection deserializer can degrade
            // an invalid typed element to a Map, hiding a broken checkpoint.
            result.add(partMapper.treeToValue(part, MessagePart.class));
        }
        return result;
    }

    private static ObjectMapper partsMapper(ObjectMapper mapper) {
        return mapper.copy().deactivateDefaultTyping()
                .setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
    }

}
