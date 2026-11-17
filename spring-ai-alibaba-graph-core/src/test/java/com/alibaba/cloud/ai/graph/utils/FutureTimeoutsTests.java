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
package com.alibaba.cloud.ai.graph.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author 芋道源码
 */
class FutureTimeoutsTests {
    @Test
    void timeoutCompletesTheOriginalFutureWithoutCancellingIt() {
        CompletableFuture<String> original = new CompletableFuture<>();
        assertSame(original, FutureTimeouts.orTimeout(original, 1, TimeUnit.MILLISECONDS));
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> original.get(2, TimeUnit.SECONDS));
        assertTrue(error.getCause() instanceof TimeoutException);
        assertFalse(original.isCancelled());
    }

    @Test
    void priorCompletionKeepsTheValueAndIdentity() throws Exception {
        CompletableFuture<String> original = CompletableFuture.completedFuture("done");
        assertSame(original, FutureTimeouts.orTimeout(original, -1, TimeUnit.MILLISECONDS));
        assertEquals("done", original.get());
        assertThrows(NullPointerException.class, () -> FutureTimeouts.orTimeout(original, 1, null));
    }

    @Test
    void exceptionalCompletionIsPreserved() {
        CompletableFuture<String> original = new CompletableFuture<>();
        IllegalStateException cause = new IllegalStateException("original");
        original.completeExceptionally(cause);
        FutureTimeouts.orTimeout(original, -1, TimeUnit.MILLISECONDS);
        assertSame(cause, assertThrows(ExecutionException.class, original::get).getCause());
    }
}
