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

package cn.iocoder.spring.ai.alibaba.compat.graaljs;

import java.util.Collections;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs isolated ICU and standard JavaScript embedding APIs beside another ICU version.
 *
 * @author 芋道源码
 */
class GraalJsRuntimeTests {

    @Test
    void unicodeJsonBindingsAndBigIntegersRunOnTheTargetVm() {
        assertThat(System.getProperty("java.specification.version")).isEqualTo("1.8");
        try (Context context = Context.newBuilder("js").allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false").build()) {
            context.getBindings("js").putMember("params", Collections.singletonMap("value", 4));
            assertThat(context.eval("js", "JSON.stringify({value: params.get('value') + 1, message:'你好 café'})")
                    .asString()).contains("5", "你好 café");
            assertThat(context.eval("js", "(2n ** 100n).toString()").asString())
                    .isEqualTo("1267650600228229401496703205376");
        }
    }

    @Test
    void privateIcuResourcesDriveCurrencyCalendarAndCollation() throws Exception {
        assertThat(System.getProperty("java.specification.version")).isEqualTo("1.8");
        Class<?> isolated = Class.forName("cn.iocoder.spring.ai.alibaba.compat.graaljs.icu.util.VersionInfo");
        assertThat(isolated.getField("ICU_VERSION").get(null).toString()).isNotEqualTo(com.ibm.icu.util.VersionInfo.ICU_VERSION.toString());
        try (Context context = Context.newBuilder("js").allowExperimentalOptions(true).option("js.intl-402", "true")
                .option("engine.WarnInterpreterOnly", "false").build()) {
            assertThat(context.eval("js", "new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(1234.5)")
                    .asString()).isEqualTo("$1,234.50");
            assertThat(context.eval("js", "new Intl.DateTimeFormat('en-US',{year:'numeric',timeZone:'UTC'}).format(new Date('2026-11-18T00:00:00Z'))")
                    .asString()).isEqualTo("2026");
            assertThat(context.eval("js", "new Intl.Collator('zh-CN').compare('阿','波') < 0").asBoolean()).isTrue();
        }
    }

    @Test
    void jsr223ProviderIsAvailable() throws Exception {
        assertThat(System.getProperty("java.specification.version")).isEqualTo("1.8");
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("graal.js");
        assertThat(engine).isNotNull();
        assertThat(engine.eval("'你好 café'")).isEqualTo("你好 café");
    }

}
