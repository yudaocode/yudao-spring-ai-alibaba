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

package com.alibaba.cloud.ai.observation.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * Attaches observation metadata through tool context while preserving the provider's
 * immutable options and builder type.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class ObservationMetadataContext implements ObservationMetadataAwareOptions {

    private static final String KEY = ObservationMetadataContext.class.getName();

    private final Map<String, String> metadata = new ConcurrentHashMap<>();

    private ObservationMetadataContext(Map<String, String> metadata) {
        if (metadata != null) this.metadata.putAll(metadata);
    }

    public static ToolCallingChatOptions attach(ToolCallingChatOptions options, Map<String, String> metadata) {
        return options.mutate().toolContext(KEY, new ObservationMetadataContext(metadata)).build();
    }

    public static ObservationMetadataAwareOptions find(ChatOptions options) {
        if (options instanceof ObservationMetadataAwareOptions aware) return aware;
        if (options instanceof ToolCallingChatOptions toolOptions && toolOptions.getToolContext() != null) {
            Object value = toolOptions.getToolContext().get(KEY);
            if (value instanceof ObservationMetadataAwareOptions aware) return aware;
        }
        return null;
    }

    @Override
    public Map<String, String> getObservationMetadata() {
        return this.metadata;
    }

    @Override
    public void setObservationMetadata(Map<String, String> metadata) {
        this.metadata.clear();
        if (metadata != null) this.metadata.putAll(metadata);
    }
}
