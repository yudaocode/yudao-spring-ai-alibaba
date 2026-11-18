/*
 * Copyright 2025 the original author or authors.
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

package com.alibaba.cloud.ai.studio.admin.observation;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.otel.bridge.Slf4JEventListener;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Wires the OpenTelemetry tracing bridge for Admin on Spring Boot 2.
 *
 * @author 芋道源码
 */
@Configuration(proxyBeanMethods = false)
class AdminTracingConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ObservationRegistry observationRegistry() {
		return ObservationRegistry.create();
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnMissingBean(Tracer.class)
	SdkTracerProvider adminTracerProvider(
			@Value("${management.tracing.enabled:true}") boolean enabled,
			@Value("${management.tracing.sampling.probability:1.0}") double probability,
			@Value("${management.otlp.tracing.export.enabled:true}") boolean exportEnabled,
			@Value("${management.otlp.tracing.endpoint:}") String endpoint) {
		SdkTracerProviderBuilder builder = SdkTracerProvider.builder()
			.setSampler(Sampler.parentBased(enabled ? Sampler.traceIdRatioBased(probability) : Sampler.alwaysOff()));
		if (enabled && exportEnabled && StringUtils.hasText(endpoint)) {
			builder.addSpanProcessor(BatchSpanProcessor.builder(OtlpHttpSpanExporter.builder()
				.setEndpoint(endpoint).build()).build());
		}
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	Tracer adminTracer(SdkTracerProvider provider) {
		Slf4JEventListener events = new Slf4JEventListener();
		return new OtelTracer(provider.get("spring-ai-alibaba-admin"), new OtelCurrentTraceContext(), events::onEvent);
	}

	@Bean
	SmartInitializingSingleton adminTracingObservationHandler(ObservationRegistry registry, Tracer tracer) {
		return () -> registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));
	}

}
