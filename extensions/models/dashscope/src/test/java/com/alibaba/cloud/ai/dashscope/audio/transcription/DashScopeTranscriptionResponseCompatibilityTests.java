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
package com.alibaba.cloud.ai.dashscope.audio.transcription;

import java.util.Collections;
import com.alibaba.cloud.ai.dashscope.audio.transcription.DashScopeAsrTranscriptionApiSpec.DashScopeAudioAsrTranscriptionResponse;
import com.alibaba.cloud.ai.dashscope.audio.transcription.DashScopeAsrTranscriptionApiSpec.DashScopeAudioAsrTranscriptionResponse.TranscriptionResult;
import com.alibaba.cloud.ai.dashscope.metadata.audio.DashScopeAudioTranscriptionResponseMetadata.Sentence;
import com.alibaba.cloud.ai.dashscope.metadata.audio.DashScopeAudioTranscriptionResponseMetadata.Usage;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the common Spring AI transcription contract for provider response containers.
 * @author 芋道源码
 */
class DashScopeTranscriptionResponseCompatibilityTests {
    @Test
    void fileAsrExposesTheTranscriptThroughTheCommonInterface() {
        TranscriptionResult.Transcript transcript = new TranscriptionResult.Transcript(0, 12, "local transcript", Collections.emptyList());
        TranscriptionResult result = new TranscriptionResult("local.wav", null, Collections.singletonList(transcript));
        DashScopeAudioAsrTranscriptionResponse response = new DashScopeAudioAsrTranscriptionResponse(result);
        assertEquals("local transcript", response.getResult().getOutput());
        assertEquals("local transcript", response.getResults().get(0).getOutput());
        assertEquals(result, response.getTranscriptionResults().get(0));
        assertEquals("", new DashScopeAudioAsrTranscriptionResponse().getResult().getOutput());
        assertEquals("", new DashScopeAudioAsrTranscriptionResponse((java.util.List<TranscriptionResult>) null).getResult().getOutput());
    }

    @Test
    void qwenWireResponsesExposeMessageAndDeltaText() throws Exception {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        DashScopeTranscriptionApiSpec.DashScopeAudioTranscriptionResponse response = mapper.readValue(
                "{\"id\":\"local\",\"choices\":[{\"message\":{\"content\":\"one\",\"role\":\"assistant\"}},{\"delta\":{\"content\":\"two\"}}]}",
                DashScopeTranscriptionApiSpec.DashScopeAudioTranscriptionResponse.class);
        assertEquals("one\ntwo", response.getResult().getOutput());
        assertEquals("one\ntwo", response.getResults().get(0).getOutput());
        assertEquals(2, response.getChoices().size());
        assertEquals("local", response.getId());
    }

    @Test
    void streamingSentenceHasANonNullResultAndPreservesMetadata() {
        Sentence sentence = new Sentence(0, 12, "stream text", false, true, null, null, Collections.emptyList(), 1, 0);
        DashScopeTranscriptionResponse response = new DashScopeTranscriptionResponse(sentence, new Usage(2));
        assertEquals("stream text", response.getResult().getOutput());
        assertEquals("stream text", response.getResults().get(0).getOutput());
        assertEquals(sentence, response.getMetadata().getSentence());
        assertEquals("", new DashScopeTranscriptionResponse(Collections.emptyList(), null).getResult().getOutput());
    }
}
