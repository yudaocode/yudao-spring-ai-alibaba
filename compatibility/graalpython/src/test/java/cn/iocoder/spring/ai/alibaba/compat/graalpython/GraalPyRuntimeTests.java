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

package cn.iocoder.spring.ai.alibaba.compat.graalpython;

import java.util.Collections;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the packaged Python implementation, standard library and private dependency sets.
 *
 * @author 芋道源码
 */
class GraalPyRuntimeTests {

    @Test
    void realPythonUsesJavaMapAndStandardLibrariesWithoutGlobalHome() {
        assertThat(System.getProperty("java.specification.version")).isEqualTo("1.8");
        String previous = System.getProperty("org.graalvm.language.python.home");
        try (Context context = GraalPyContexts.newContextBuilder().allowAllAccess(true).build()) {
            context.getBindings("python").putMember("params", Collections.singletonMap("value", 4));
            Value result = context.eval("python", "import json, math, datetime\n{'value': params.get('value')+1, 'text':'你好 café', 'items':[True,None,math.sqrt(9)], 'date':datetime.date(2026,11,18).isoformat(), 'json':json.loads('{\"ok\":true}')}" );
            Map<?, ?> values = (Map<?, ?>) GraalPyValues.toJavaObject(result);
            assertThat(values.get("items")).isInstanceOf(java.util.List.class);
            assertThat(((java.util.List<?>) values.get("items")).get(1)).isNull();
            assertThat(((Number) values.get("value")).intValue()).isEqualTo(5);
            assertThat(values.get("text")).isEqualTo("你好 café");
            assertThat(values.get("date")).isEqualTo("2026-11-18");
            assertThat(context.eval("python", "2**100").toString()).isEqualTo("1267650600228229401496703205376");
            assertThat(context.eval("python", "__import__('unicodedata').name('芋')").asString()).contains("CJK UNIFIED IDEOGRAPH");
            assertThat(context.eval("python", "__import__('lzma').decompress(__import__('lzma').compress(b'hello')) == b'hello'").asBoolean()).isTrue();
            assertThat(context.eval("python", "__import__('re').search('[芋道]+', 'x芋道y').group(0)").asString()).isEqualTo("芋道");
            assertThat(context.eval("python", "__import__('_imp').extension_suffixes()").getArraySize()).isZero();
            assertThat(context.getEngine().getLanguages()).doesNotContainKey("llvm");
        }
        assertThat(System.getProperty("org.graalvm.language.python.home")).isEqualTo(previous);
    }

    @Test
    void errorsAreRealPythonSyntaxErrorsAndContextsDoNotLeakBindings() {
        try (Context context = GraalPyContexts.newContextBuilder().allowAllAccess(true).build()) {
            context.getBindings("python").putMember("onlyHere", 4);
            assertThatThrownBy(() -> context.eval("python", "def broken("))
                    .isInstanceOfSatisfying(PolyglotException.class, error -> assertThat(error.isSyntaxError()).isTrue());
        }
        try (Context context = GraalPyContexts.newContextBuilder().allowAllAccess(true).build()) {
            assertThat(context.eval("python", "'onlyHere' in globals()").asBoolean()).isFalse();
        }
    }

    @Test
    void javascriptAndPythonRunTogetherBesideOtherIcuAntlrAndXzVersions() throws Exception {
        assertThat(Class.forName("cn.iocoder.spring.ai.alibaba.compat.graalpython.antlr.runtime.RuntimeMetaData")
                .getField("VERSION").get(null)).isEqualTo("4.9.2");
        assertThat(org.antlr.v4.runtime.RuntimeMetaData.VERSION).isEqualTo("4.13.2");
        try (Context context = GraalPyContexts.newContextBuilder("python", "js").allowAllAccess(true).build()) {
            assertThat(context.eval("python", "'你好'.encode('utf8').decode('utf8')").asString()).isEqualTo("你好");
            assertThat(context.eval("js", "JSON.stringify({message:'你好 café'})").asString()).contains("你好 café");
        }
    }

}
