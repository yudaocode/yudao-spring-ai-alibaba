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

package com.alibaba.cloud.ai.studio.core.rag.reranker;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec;
import com.alibaba.cloud.ai.studio.core.model.reranker.dashscope.DashscopeReranker;
import com.alibaba.cloud.ai.studio.core.model.reranker.dashscope.DashScopeRerankerOptions;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Runs the real reranker against an isolated API boundary, not a mock of the subject.
 *
 * @author 芋道源码
 */
class DashscopeRerankerRuntimeTests {

	@Test
	void mapsCurrentRequestAndReorderedScoresWithoutLosingDocumentData() {
		DashScopeApi api = mock(DashScopeApi.class);
		DashScopeApiSpec.RerankResponse response = JsonUtils.fromJson(
				"{\"output\":{\"results\":[{\"index\":1,\"relevance_score\":0.9},"
						+ "{\"index\":0,\"relevance_score\":0.6}]},\"request_id\":\"local\"}",
				DashScopeApiSpec.RerankResponse.class);
		when(api.rerankEntity(any(DashScopeApiSpec.RerankRequest.class))).thenReturn(ResponseEntity.ok(response));
		Document first = Document.builder().id("a").text("预算 café")
			.metadata(Collections.singletonMap("group", "finance")).build();
		Document second = Document.builder().id("b").text("你好")
			.metadata(Collections.singletonMap("group", "engineering")).build();
		List<Document> ranked = subject(api).process(Query.builder().text("query").build(), Arrays.asList(first, second));
		assertThat(ranked).extracting(Document::getId).containsExactly("b", "a");
		assertThat(ranked.get(0).getText()).isEqualTo("你好");
		assertThat(ranked.get(0).getMetadata()).containsEntry("group", "engineering");
		assertThat(ranked.get(0).getScore()).isCloseTo(0.9, offset(0.00001));
		assertThat(ranked.get(1).getScore()).isCloseTo(0.6, offset(0.00001));
		ArgumentCaptor<DashScopeApiSpec.RerankRequest> request = ArgumentCaptor.forClass(DashScopeApiSpec.RerankRequest.class);
		verify(api).rerankEntity(request.capture());
		assertThat(request.getValue().model()).isEqualTo("local-rerank");
		assertThat(request.getValue().input().query()).isEqualTo("query");
		assertThat(request.getValue().input().documents()).containsExactly("预算 café", "你好");
	}

	@Test
	void rejectsNullDocumentsBeforeTheEmptyListShortcut() {
		DashScopeApi api = mock(DashScopeApi.class);
		assertThatThrownBy(() -> subject(api).process(Query.builder().text("valid query").build(), null))
			.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(api);
	}

	@Test
	void rejectsNullQueryWithRealDocuments() {
		DashScopeApi api = mock(DashScopeApi.class);
		assertThatThrownBy(() -> subject(api).process(null, Collections.singletonList(new Document("text"))))
			.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(api);
	}

	@Test
	void emptyDocumentsDoNotCallTheProvider() {
		DashScopeApi api = mock(DashScopeApi.class);
		assertThat(subject(api).process(Query.builder().text("query").build(), Collections.emptyList())).isEmpty();
		verifyNoInteractions(api);
	}

	@Test
	void missingProviderBodyFailsExplicitly() {
		DashScopeApi api = mock(DashScopeApi.class);
		when(api.rerankEntity(any(DashScopeApiSpec.RerankRequest.class))).thenReturn(ResponseEntity.ok(null));
		assertThatThrownBy(() -> subject(api).process(Query.builder().text("query").build(),
				Collections.singletonList(new Document("text"))))
			.isInstanceOf(RuntimeException.class).hasMessageContaining("rerank response is null");
	}

	private DashscopeReranker subject(DashScopeApi api) {
		return new DashscopeReranker(api, RetryUtils.DEFAULT_RETRY_TEMPLATE,
				DashScopeRerankerOptions.builder().model("local-rerank").topN(2).returnDocuments(false).build());
	}

}
