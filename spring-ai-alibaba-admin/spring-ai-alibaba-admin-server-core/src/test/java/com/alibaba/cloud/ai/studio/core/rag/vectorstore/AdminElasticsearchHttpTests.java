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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.alibaba.cloud.ai.studio.core.rag.vectorstore.elasticsearch.AdminElasticsearchVectorStore;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises relocated Admin search modes through the real Elasticsearch HTTP client.
 *
 * @author 芋道源码
 */
class AdminElasticsearchHttpTests {

	@ParameterizedTest
	@CsvSource({ "SEMANTIC,content", "FULL_TEXT,content", "HYBRID,content",
            "SEMANTIC,text", "FULL_TEXT,text", "HYBRID,text" })
	void searchModesKeepFiltersAndDecodeStoredDocuments(SearchType type, String contentField) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		List<JsonNode> requests = new CopyOnWriteArrayList<>();
		server.createContext("/admin-test/_search", exchange -> {
			try {
				requests.add(JsonUtils.getObjectMapper().readTree(exchange.getRequestBody()));
				String response = "{\"took\":1,\"timed_out\":false,\"_shards\":{\"total\":1,\"successful\":1,"
						+ "\"skipped\":0,\"failed\":0},\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"},"
						+ "\"max_score\":0.9,\"hits\":[{\"_index\":\"admin-test\",\"_id\":\"one\",\"_score\":0.9,"
						+ "\"_source\":{\"id\":\"one\",\"" + contentField + "\":\"你好 café\",\"metadata\":{\"tenant\":\"test\"},"
						+ "\"embedding\":[1.0,0.0]}}]}}";
				byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.getResponseHeaders().set("X-Elastic-Product", "Elasticsearch");
				exchange.sendResponseHeaders(200, bytes.length);
				exchange.getResponseBody().write(bytes);
			}
			finally {
				exchange.close();
			}
		});
		server.start();
		try (RestClient client = RestClient.builder(new HttpHost("127.0.0.1", server.getAddress().getPort(), "http"))
			.setRequestConfigCallback(config -> config.setConnectTimeout(2000).setSocketTimeout(5000)).build()) {
			EmbeddingModel embedding = mock(EmbeddingModel.class);
			when(embedding.embed("你好")).thenReturn(new float[] { 1.0f, 0.0f });
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setIndexName("admin-test");
			AdminElasticsearchVectorStore store = AdminElasticsearchVectorStore.builder(client, embedding)
				.options(options).initializeSchema(false).build();
			List<Document> found = store.similaritySearch(AdminSearchRequest.adminBuilder().query("你好")
				.topK(4).similarityThreshold(0.4).searchType(type).hybridWeight(0.5f)
				.filterExpression("tenant == 'test'").build());
			assertThat(found).hasSize(1);
			assertThat(found.get(0).getId()).isEqualTo("one");
			assertThat(found.get(0).getText()).isEqualTo("你好 café");
			assertThat(found.get(0).getMetadata()).containsEntry("tenant", "test");
			assertThat(requests).hasSize(type == SearchType.HYBRID ? 2 : 1);
			for (JsonNode request : requests) {
				assertThat(request.toString()).contains("metadata.tenant", "test");
				if (request.has("knn")) {
					JsonNode knn = request.path("knn");
					assertThat((knn.isArray() ? knn.get(0) : knn).path("field").asText()).isEqualTo("embedding");
				}
				else {
					assertThat(request.path("query").toString()).contains("content", "你好");
				}
			}
		}
		finally {
			server.stop(0);
		}
	}

}
