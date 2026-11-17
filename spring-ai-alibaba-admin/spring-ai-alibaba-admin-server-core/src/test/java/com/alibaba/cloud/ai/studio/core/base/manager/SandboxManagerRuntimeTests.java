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

package com.alibaba.cloud.ai.studio.core.base.manager;

import java.util.Collections;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the actual script engines and checks their serialized workflow outputs.
 *
 * @author 芋道源码
 */
class SandboxManagerRuntimeTests {

	@Test
	void optionalGraalJavascriptEngineUsesTheSamePolyglotRuntime() throws Exception {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("graal.js");
		assertThat(engine).isNotNull();
		assertThat(engine.eval("'你好 café'")).isEqualTo("你好 café");
	}

	@Test
	void javascriptRunsWithBindingsAndDoesNotLeakThemAcrossCalls() throws Exception {
		SandboxManager manager = new SandboxManager();
		Result<String> first = manager.executeScript("input + ' café'", Collections.singletonMap("input", "你好"), "js");
		assertThat(first.isSuccess()).isTrue();
		assertThat(first.getRequestId()).isEqualTo("js");
		assertThat(JsonUtils.getObjectMapper().readTree(first.getData()).path("data").asText()).isEqualTo("你好 café");
		Result<String> second = manager.executeScript("typeof input", Collections.emptyMap(), "js2");
		assertThat(JsonUtils.getObjectMapper().readTree(second.getData()).path("data").asText()).isEqualTo("undefined");
	}

	@Test
	void javascriptSyntaxErrorIsReportedInTheExistingWorkflowEnvelope() throws Exception {
		Result<String> result = new SandboxManager().executeScript("function {", Collections.emptyMap(), "bad");
		assertThat(result.isSuccess()).isTrue();
		assertThat(JsonUtils.getObjectMapper().readTree(result.getData()).path("success").asBoolean()).isFalse();
		assertThat(JsonUtils.getObjectMapper().readTree(result.getData()).path("code").asText()).isEqualTo("SCRIPT_ERROR");
	}

	@Test
	void pythonRunsImportsJavaBindingsAndReturnsDetachedNestedData() throws Exception {
		Map<String, Object> input = Collections.singletonMap("value", 4);
		Result<String> result = new SandboxManager().executePython3Script(
				"import json, math\n{'message': '你好 café', 'value': params.get('value') + 1, "
						+ "'items': [True, None, math.sqrt(9)], 'json': json.loads('{\"ok\":true}')}" ,
				Collections.singletonMap("params", input), "python");
		assertThat(result.isSuccess()).as("Python result: %s", result).isTrue();
		assertThat(result.getRequestId()).isEqualTo("python");
		com.fasterxml.jackson.databind.JsonNode data = JsonUtils.getObjectMapper().readTree(result.getData()).path("data");
		assertThat(data.path("message").asText()).isEqualTo("你好 café");
		assertThat(data.path("value").asInt()).isEqualTo(5);
		assertThat(data.path("items").get(0).asBoolean()).isTrue();
		assertThat(data.path("items").get(1).isNull()).isTrue();
		assertThat(data.path("items").get(2).asDouble()).isEqualTo(3.0);
		assertThat(data.path("json").path("ok").asBoolean()).isTrue();
	}

	@Test
	void pythonSyntaxErrorReturnsTheExistingSystemError() {
		Result<String> result = new SandboxManager().executePython3Script("def bad(:", Collections.emptyMap(), "bad");
		assertThat(result.isSuccess()).isFalse();
		assertThat(result.getRequestId()).isEqualTo("bad");
	}

}
