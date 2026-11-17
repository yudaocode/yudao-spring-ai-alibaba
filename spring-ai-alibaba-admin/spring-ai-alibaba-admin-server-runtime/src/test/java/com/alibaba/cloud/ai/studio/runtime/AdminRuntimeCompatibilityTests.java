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

import java.util.List;

import com.alibaba.cloud.ai.studio.runtime.domain.Error;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.runtime.domain.agent.AgentStatus;
import com.alibaba.cloud.ai.studio.runtime.domain.chat.ChatMessage;
import com.alibaba.cloud.ai.studio.runtime.domain.chat.MultimodalContent;
import com.alibaba.cloud.ai.studio.runtime.domain.chat.MultimodalContentType;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.IndexConfig;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the retained Admin wire format against each route's real Jackson runtime.
 *
 * @author 芋道源码
 */
class AdminRuntimeCompatibilityTests {

	@Test
	void textContentKeepsUnicodeAndIgnoresFutureFields() {
		ChatMessage message = JsonUtils.fromJson(
				"{\"role\":\"assistant\",\"content\":\"预算 café 你好\",\"future_field\":true}", ChatMessage.class);
		assertThat(message.getContent()).isEqualTo("预算 café 你好");
		assertThat(JsonUtils.fromJson(JsonUtils.toJson(message), ChatMessage.class).getContent())
			.isEqualTo(message.getContent());
	}

	@Test
	void arrayContentProducesTypedMultimodalValues() {
		ChatMessage message = JsonUtils.fromJson("{\"role\":\"user\",\"content\":["
				+ "{\"type\":\"text\",\"text\":\"你好\"},"
				+ "{\"type\":\"image\",\"url\":\"https://example.invalid/image.png\"}]}", ChatMessage.class);
		assertThat(message.getContent()).isInstanceOf(List.class);
		List<?> values = (List<?>) message.getContent();
		assertThat(values).hasSize(2);
		assertThat(((MultimodalContent) values.get(0)).getType()).isEqualTo(MultimodalContentType.TEXT);
		assertThat(((MultimodalContent) values.get(0)).getText()).isEqualTo("你好");
		assertThat(((MultimodalContent) values.get(1)).getType()).isEqualTo(MultimodalContentType.IMAGE);
		assertThat(((MultimodalContent) values.get(1)).getUrl()).isEqualTo("https://example.invalid/image.png");
		ChatMessage roundTrip = JsonUtils.fromJson(JsonUtils.toJson(message), ChatMessage.class);
		assertThat(roundTrip.getContent()).isEqualTo(values);
	}

	@Test
	void objectContentRemainsJsonTree() {
		ChatMessage message = JsonUtils.fromJson("{\"content\":{\"tool\":\"weather\",\"count\":2}}", ChatMessage.class);
		assertThat(message.getContent()).isInstanceOf(JsonNode.class);
		assertThat(((JsonNode) message.getContent()).get("count").intValue()).isEqualTo(2);
	}

	@Test
	void nullContentRemainsNull() {
		assertThat(JsonUtils.fromJson("{\"content\":null}", ChatMessage.class).getContent()).isNull();
	}

	@Test
	void resultAndIndexConfigurationKeepSnakeCaseWireNames() {
		IndexConfig config = IndexConfig.builder().name("知识库").embeddingProvider("dashscope").embeddingModel("model").build();
		JsonNode tree = JsonUtils.fromJson(JsonUtils.toJson(Result.success("req-1", config)));
		assertThat(tree.get("request_id").asText()).isEqualTo("req-1");
		assertThat(tree.get("code").intValue()).isEqualTo(200);
		assertThat(tree.has("success")).isFalse();
		assertThat(tree.get("data").get("embedding_provider").asText()).isEqualTo("dashscope");
		assertThat(JsonUtils.fromJson(tree.get("data").toString(), IndexConfig.class)).isEqualTo(config);
	}

	@Test
	void formattedErrorsKeepStatusAndFailureResponse() {
		Error error = ErrorCode.INVALID_PARAMS.toError("model", "missing");
		assertThat(error.getStatusCode()).isEqualTo(400);
		assertThat(error.getMessage()).contains("model", "missing");
		assertThat(JsonUtils.fromJson(JsonUtils.toJson(error)).get("status_code").intValue()).isEqualTo(400);
		Result<Object> result = Result.error("req-error", error);
		assertThat(result.isSuccess()).isFalse();
		assertThat(result.getCode()).isEqualTo(400);
	}

	@Test
	void malformedJsonKeepsParsingCause() {
		assertThatThrownBy(() -> JsonUtils.fromJson("{", ChatMessage.class))
			.isInstanceOf(RuntimeException.class)
			.hasCauseInstanceOf(JsonProcessingException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "stop", "STOP", "length" })
	void completionReasonsMapToCompleted(String reason) {
		assertThat(AgentStatus.toAgentStatus(reason)).isEqualTo(AgentStatus.COMPLETED);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "tool_calls")
	void missingOrToolReasonsRemainInProgress(String reason) {
		assertThat(AgentStatus.toAgentStatus(reason)).isEqualTo(AgentStatus.IN_PROGRESS);
	}

	@Test
	void unknownReasonMapsToFailure() {
		assertThat(AgentStatus.toAgentStatus("unexpected")).isEqualTo(AgentStatus.FAILED);
	}

}
