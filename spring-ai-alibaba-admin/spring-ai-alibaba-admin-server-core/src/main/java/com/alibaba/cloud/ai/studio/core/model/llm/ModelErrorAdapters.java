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

package com.alibaba.cloud.ai.studio.core.model.llm;

import java.util.Map;
import java.util.function.Supplier;

import com.alibaba.cloud.ai.studio.runtime.domain.Error;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import com.openai.errors.OpenAIServiceException;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Preserves Admin provider errors while delegating options, metadata and observations.
 *
 * @author 芋道源码
 */
final class ModelErrorAdapters {

	private ModelErrorAdapters() {
	}

	static ChatModel chat(ChatModel delegate) {
		return new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				return invoke(() -> delegate.call(prompt));
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				return Flux.defer(() -> delegate.stream(prompt)).onErrorMap(ModelErrorAdapters::mapError);
			}

			@Override
			public ChatOptions getOptions() {
				return delegate.getOptions();
			}
		};
	}

	static EmbeddingModel embedding(EmbeddingModel delegate) {
		return new EmbeddingModel() {
			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {
				return invoke(() -> delegate.call(request));
			}

			@Override
			public float[] embed(Document document) {
				return invoke(() -> delegate.embed(document));
			}

			@Override
			public String getEmbeddingContent(Document document) {
				return delegate.getEmbeddingContent(document);
			}

			@Override
			public int dimensions() {
				return invoke(delegate::dimensions);
			}
		};
	}

	private static <T> T invoke(Supplier<T> call) {
		try {
			return call.get();
		}
		catch (RuntimeException failure) {
			Throwable mapped = mapError(failure);
			throw mapped instanceof RuntimeException ? (RuntimeException) mapped : failure;
		}
	}

	private static Throwable mapError(Throwable failure) {
		if (failure instanceof BizException) {
			return failure;
		}
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof OpenAIServiceException) {
				OpenAIServiceException service = (OpenAIServiceException) cause;
				try {
					Object body = service.body().convert(Object.class);
					Object details = body instanceof Map && ((Map<?, ?>) body).get("error") instanceof Map
							? ((Map<?, ?>) body).get("error") : body;
					String code = "ModelCall";
					String message = body instanceof String ? (String) body : JsonUtils.toJson(body);
					if (details instanceof Map) {
						Map<?, ?> fields = (Map<?, ?>) details;
						if (fields.get("code") != null) {
							code = String.valueOf(fields.get("code"));
						}
						if (fields.get("message") != null) {
							message = String.valueOf(fields.get("message"));
						}
					}
					return new BizException(Error.builder().statusCode(service.statusCode())
						.code(code).message(message).build(), failure);
				}
				catch (RuntimeException mappingFailure) {
					failure.addSuppressed(mappingFailure);
					return failure;
				}
			}
		}
		return failure;
	}

}
