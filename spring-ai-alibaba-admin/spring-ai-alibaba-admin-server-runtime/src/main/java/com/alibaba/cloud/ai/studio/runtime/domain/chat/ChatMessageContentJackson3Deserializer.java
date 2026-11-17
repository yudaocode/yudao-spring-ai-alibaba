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

package com.alibaba.cloud.ai.studio.runtime.domain.chat;

import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Lets Boot4's HTTP mapper preserve the existing Admin content representation.
 *
 * @author 芋道源码
 * @since 1.1.2.2
 */
public class ChatMessageContentJackson3Deserializer extends ValueDeserializer<Object> {

	@Override
	public Object deserialize(JsonParser parser, DeserializationContext context) {
		JsonNode node = context.readTree(parser);
		if (node.isArray()) {
			return JsonUtils.fromJsonToList(node.toString(), MultimodalContent.class);
		}
		if (node.isString()) {
			return node.asString();
		}
		// Admin processors still consume Jackson2 trees for structured content.
		return JsonUtils.fromJson(node.toString());
	}

}
