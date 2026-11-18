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
import java.util.concurrent.atomic.AtomicReference;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.instrumentation.OpenTelemetryForElasticsearch;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the active Elasticsearch search request body capture path on Java 8.
 *
 * @author 芋道源码
 */
class AdminElasticsearchTelemetryTests {

	@Test
	void recordsSearchBodyWithoutConsumingItBeforeSending() throws Exception {
		AtomicReference<JsonNode> received = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/admin-test/_search", exchange -> {
			try {
				received.set(JsonUtils.getObjectMapper().readTree(exchange.getRequestBody()));
				byte[] body = ("{\"took\":1,\"timed_out\":false,\"_shards\":{\"total\":1,\"successful\":1,"
						+ "\"skipped\":0,\"failed\":0},\"hits\":{\"hits\":[]}}").getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.getResponseHeaders().set("X-Elastic-Product", "Elasticsearch");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			}
			finally {
				exchange.close();
			}
		});
		server.start();
		InMemorySpanExporter exporter = InMemorySpanExporter.create();
		try (SdkTracerProvider provider = SdkTracerProvider.builder()
			.addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
			RestClientTransport transport = new RestClientTransport(
					RestClient.builder(new HttpHost("127.0.0.1", server.getAddress().getPort(), "http"))
						.setRequestConfigCallback(config -> config.setConnectTimeout(2000).setSocketTimeout(5000))
						.build(),
					new JacksonJsonpMapper(), null,
					new OpenTelemetryForElasticsearch(OpenTelemetrySdk.builder().setTracerProvider(provider).build(), true))) {
			ElasticsearchClient client = new ElasticsearchClient(transport);
			assertThat(client.search(request -> request.index("admin-test").query(query -> query
				.match(match -> match.field("content").query("你好 café"))), JsonNode.class).hits().hits()).isEmpty();
			assertThat(received.get()).isNotNull();
			assertThat(exporter.getFinishedSpanItems()).hasSize(1);
			String statement = exporter.getFinishedSpanItems().get(0).getAttributes()
				.get(AttributeKey.stringKey("db.statement"));
			assertThat(statement).isNotBlank();
			assertThat(JsonUtils.getObjectMapper().readTree(statement)).isEqualTo(received.get());
			assertThat(received.get().path("query").path("match").path("content").path("query").asText())
				.isEqualTo("你好 café");
		}
		finally {
			server.stop(0);
		}
	}

}
