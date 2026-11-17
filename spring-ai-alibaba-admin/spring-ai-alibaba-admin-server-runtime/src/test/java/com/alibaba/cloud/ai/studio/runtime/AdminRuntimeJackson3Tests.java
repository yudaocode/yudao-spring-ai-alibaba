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

package com.alibaba.cloud.ai.studio.runtime;

import com.alibaba.cloud.ai.studio.runtime.domain.chat.ChatMessage;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the two host mapper paths compatible with the Admin runtime's content types.
 *
 * @author 芋道源码
 */
class AdminRuntimeJackson3Tests {

	@ParameterizedTest
	@ValueSource(strings = { "null", "\"预算 café 你好\"", "{\"tool\":\"weather\",\"count\":2}",
			"[{\"type\":\"text\",\"text\":\"你好\"},{\"type\":\"image\",\"url\":\"https://example.invalid/img\"}]" })
	void bothMappersProduceTheSameRuntimeContent(String content) {
		String json = "{\"role\":\"user\",\"content\":" + content + "}";
		ChatMessage existing = JsonUtils.fromJson(json, ChatMessage.class);
		ChatMessage modern = JsonMapper.builder().build().readValue(json, ChatMessage.class);
		assertThat(modern.getContent()).isEqualTo(existing.getContent());
		assertThat(modern.getRole()).isEqualTo(existing.getRole());
	}

}
