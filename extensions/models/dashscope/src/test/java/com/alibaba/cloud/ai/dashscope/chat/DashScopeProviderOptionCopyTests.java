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
package com.alibaba.cloud.ai.dashscope.chat;

import java.util.Collections;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeApiSpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Guards provider-specific settings at Spring AI option-copy boundaries.
 * @author 芋道源码
 */
class DashScopeProviderOptionCopyTests {
    @Test
    void mutationAndSnapshotPreserveEveryPreviouslyDroppedSetting() {
        DashScopeChatOptions source = DashScopeChatOptions.builder().model("local-model").build();
        source.setMaxInputTokens(17);
        source.setAsrOptions(Collections.singletonMap("language", "zh"));
        source.setOcrOptions(new DashScopeApiSpec.OCROption("text_recognition", Collections.emptyList()));
        source.setLogprobs(true);
        source.setTopLogProbs(3);
        source.setTranslationOptions(new DashScopeApiSpec.TranslationOptions("zh", "en",
                Collections.emptyList(), Collections.emptyList(), "technical"));
        source.setOutputFormat("text");
        source.setVlEnableImageHwOutput(true);
        for (DashScopeChatOptions copy : new DashScopeChatOptions[] {
                DashScopeChatOptions.fromOptions(source), source.mutate().build() }) {
            assertNotSame(source, copy);
            assertEquals(source.getMaxInputTokens(), copy.getMaxInputTokens());
            assertEquals(source.getAsrOptions(), copy.getAsrOptions());
            assertEquals(source.getOcrOptions(), copy.getOcrOptions());
            assertEquals(source.getLogprobs(), copy.getLogprobs());
            assertEquals(source.getTopLogProbs(), copy.getTopLogProbs());
            assertEquals(source.getTranslationOptions(), copy.getTranslationOptions());
            assertEquals(source.getOutputFormat(), copy.getOutputFormat());
            assertEquals(source.getVlEnableImageHwOutput(), copy.getVlEnableImageHwOutput());
        }
    }
}
