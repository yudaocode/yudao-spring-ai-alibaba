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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real Micrometer to OpenTelemetry bridge on Boot 2 and Java 8.
 *
 * @author 芋道源码
 */
class AdminTracingConfigurationTests {

	@Test
	void opensAndClearsAnActualTraceScope() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				AdminTracingConfiguration.class)) {
			Tracer tracer = context.getBean(Tracer.class);
			Observation observation = Observation.start("admin.generator", context.getBean(ObservationRegistry.class));
			try {
				try (Observation.Scope scope = observation.openScope()) {
					assertThat(tracer.currentSpan()).isNotNull();
					assertThat(tracer.currentSpan().context().traceId()).matches("[a-f0-9]{32}");
				}
				assertThat(tracer.currentSpan()).isNull();
			}
			finally {
				observation.stop();
			}
		}
	}

}
