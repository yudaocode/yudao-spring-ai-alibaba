/*
 * Copyright 2024-2026 the original author or authors.
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
package com.alibaba.cloud.ai.autoconfigure.dashscope;

import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures DashScope participates in the current global embedding-model selection.
 *
 * @author 芋道源码
 */
class DashScopeModelSelectionTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DashScopeEmbeddingAutoConfiguration.class))
            .withPropertyValues("spring.ai.dashscope.api-key=local-test-key");

    @Test
    void anotherProviderDoesNotCreateAnAdditionalEmbeddingModel() {
        runner.withPropertyValues("spring.ai.model.embedding=openai")
                .run(context -> assertThat(context).doesNotHaveBean(DashScopeEmbeddingModel.class));
    }

    @Test
    void noneDisablesTheEmbeddingModel() {
        runner.withPropertyValues("spring.ai.model.embedding=none")
                .run(context -> assertThat(context).doesNotHaveBean(DashScopeEmbeddingModel.class));
    }

    @Test
    void explicitDashScopeCreatesOneEmbeddingModel() {
        runner.withPropertyValues("spring.ai.model.embedding=dashscope")
                .run(context -> assertThat(context).hasSingleBean(DashScopeEmbeddingModel.class));
    }

    @Test
    void missingSelectionPreservesTheDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(DashScopeEmbeddingModel.class));
    }
}
