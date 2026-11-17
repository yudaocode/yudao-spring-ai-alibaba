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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;

/**
 * Detaches JSON-compatible Python values while their context is still open.
 * Array checks precede member/hash views so older polyglot lists remain JSON arrays.
 *
 * @author 芋道源码
 */
public final class GraalPyValues {

    private GraalPyValues() {
    }

    public static Object toJavaObject(Value value) {
        if (value == null || value.isNull()) return null;
        if (value.isHostObject()) return value.asHostObject();
        if (value.isBoolean()) return value.asBoolean();
        if (value.isString()) return value.asString();
        if (value.isNumber()) {
            if (value.fitsInInt()) return value.asInt();
            if (value.fitsInLong()) return value.asLong();
            if (value.fitsInDouble()) return value.asDouble();
            return new BigInteger(value.toString());
        }
        if (value.hasArrayElements()) {
            List<Object> result = new ArrayList<>();
            for (long index = 0; index < value.getArraySize(); index++) {
                result.add(toJavaObject(value.getArrayElement(index)));
            }
            return result;
        }
        if (value.hasHashEntries()) {
            Map<Object, Object> result = new LinkedHashMap<>();
            Value entries = value.getHashEntriesIterator();
            while (entries.hasIteratorNextElement()) {
                Value entry = entries.getIteratorNextElement();
                result.put(toJavaObject(entry.getArrayElement(0)), toJavaObject(entry.getArrayElement(1)));
            }
            return result;
        }
        throw new IllegalArgumentException("Python result is not a JSON-compatible value: " + value);
    }

}
