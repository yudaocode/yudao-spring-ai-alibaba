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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Java 8 equivalent of CompletableFuture.orTimeout, preserving the original future.
 *
 * @author 芋道源码
 */
public final class FutureTimeouts {

    private static final ScheduledThreadPoolExecutor TIMER;

    static {
        TIMER = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "spring-ai-alibaba-future-timeout");
            thread.setDaemon(true);
            return thread;
        });
        TIMER.setRemoveOnCancelPolicy(true);
    }

    private FutureTimeouts() { }

    public static <T> CompletableFuture<T> orTimeout(CompletableFuture<T> future, long timeout, TimeUnit unit) {
        Objects.requireNonNull(future, "future");
        Objects.requireNonNull(unit, "unit");
        if (!future.isDone()) {
            ScheduledFuture<?> timer = TIMER.schedule(
                    () -> future.completeExceptionally(new TimeoutException()), timeout, unit);
            future.whenComplete((value, error) -> timer.cancel(false));
        }
        return future;
    }

}
