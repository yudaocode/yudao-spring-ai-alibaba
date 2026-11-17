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

package com.alibaba.cloud.ai.studio.core.rag.vectorstore;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies Admin search settings with the actual, unique Spring AI request class.
 *
 * @author 芋道源码
 */
class AdminSearchRequestTests {

	@Test
	void libraryRequestIsProvidedOnceFromItsInstalledJar() throws Exception {
		assertThat(Collections.list(SearchRequest.class.getClassLoader().getResources(
				"org/springframework/ai/vectorstore/SearchRequest.class"))).hasSize(1);
		assertThat(SearchRequest.class.getProtectionDomain().getCodeSource().getLocation().getPath()).endsWith(".jar");
		assertThat(AdminSearchRequest.adminBuilder().build()).isInstanceOf(SearchRequest.class);
	}

	@Test
	void keepsAdminDefaultsAndNativeRequestValues() {
		AdminSearchRequest defaults = AdminSearchRequest.adminBuilder().build();
		assertThat(defaults.getSimilarityThreshold()).isEqualTo(0.4);
		assertThat(defaults.getSearchType()).isEqualTo(SearchType.SEMANTIC);
		assertThat(defaults.getHybridWeight()).isEqualTo(0.7f);
		SearchRequest nativeRequest = SearchRequest.builder().query("预算 café").topK(3)
			.similarityThreshold(0.9).filterExpression("category == 'finance'").build();
		AdminSearchRequest copied = AdminSearchRequest.copyOf(nativeRequest).build();
		assertThat(copied.getQuery()).isEqualTo(nativeRequest.getQuery());
		assertThat(copied.getTopK()).isEqualTo(3);
		assertThat(copied.getSimilarityThreshold()).isEqualTo(0.9);
		assertThat(copied.getFilterExpression()).isEqualTo(nativeRequest.getFilterExpression());
	}

	@Test
	void copiesHybridPaginationAndIncludesThemInEquality() {
		AdminSearchRequest original = AdminSearchRequest.adminBuilder().query("q").topK(0)
			.from(20).searchType(SearchType.HYBRID).hybridWeight(0.6f).build();
		AdminSearchRequest copied = AdminSearchRequest.copyOf(original).build();
		assertThat(copied).isEqualTo(original).hasSameHashCodeAs(original);
		assertThat(copied.getFrom()).isEqualTo(20);
		assertThat(copied.getSearchType()).isEqualTo(SearchType.HYBRID);
		assertThat(copied.getHybridWeight()).isEqualTo(0.6f);
		assertThat(AdminSearchRequest.copyOf(original).from(21).build()).isNotEqualTo(original);
	}

	@Test
	void builderReuseDoesNotMutateEarlierRequests() {
		AdminSearchRequest.Builder builder = AdminSearchRequest.adminBuilder().query("first").from(1);
		AdminSearchRequest first = builder.build();
		AdminSearchRequest second = builder.query("second").from(2).build();
		assertThat(first.getQuery()).isEqualTo("first");
		assertThat(first.getFrom()).isEqualTo(1);
		assertThat(second.getQuery()).isEqualTo("second");
		assertThat(second.getFrom()).isEqualTo(2);
	}

	@Test
	void delegatesSharedValidationToTheLibrary() {
		assertThatThrownBy(() -> AdminSearchRequest.adminBuilder().topK(-1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AdminSearchRequest.adminBuilder().similarityThreshold(1.1))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
