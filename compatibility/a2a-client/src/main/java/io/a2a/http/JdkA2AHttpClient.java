package io.a2a.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class JdkA2AHttpClient implements A2AHttpClient {

    private static final java.util.concurrent.ExecutorService SSE_READERS =
            java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "a2a-java8-sse-reader");
                thread.setDaemon(true);
                return thread;
            });

    private final HttpClient httpClient;

    public JdkA2AHttpClient() {
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void close() {
        this.httpClient.close();
    }

    @Override
    public GetBuilder createGet() {
        return new JdkGetBuilder();
    }

    @Override
    public PostBuilder createPost() {
        return new JdkPostBuilder();
    }

    private abstract class JdkBuilder<T extends Builder<T>> implements Builder<T> {
        private String url;
        private Map<String, String> headers = new HashMap<>();

        @Override
        public T url(String url) {
            this.url = url;
            return self();
        }

        @Override
        public T addHeader(String name, String value) {
            headers.put(name, value);
            return self();
        }

        @SuppressWarnings("unchecked")
        T self() {
            return (T) this;
        }

        protected HttpRequest.Builder createRequestBuilder() throws IOException {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url));
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                builder.header(headerEntry.getKey(), headerEntry.getValue());
            }
            return builder;
        }

        protected CompletableFuture<Void> asyncRequest(
                HttpRequest request,
                Consumer<String> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable
        ) {
            java.util.concurrent.atomic.AtomicReference<java.io.InputStream> inputRef = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
            CompletableFuture<HttpResponse<java.io.InputStream>> response =
                    httpClient.sendAsync(request, BodyHandlers.ofInputStream());
            CompletableFuture<Void> result = response.thenCompose(headers -> CompletableFuture.runAsync(() -> {
                try (java.io.InputStream input = headers.body();
                     java.io.BufferedReader reader = new java.io.BufferedReader(
                             new java.io.InputStreamReader(input, StandardCharsets.UTF_8))) {
                    inputRef.set(input);
                    if (cancelled.get()) {
                        return;
                    }
                    if (!JdkHttpResponse.success(headers)) {
                        throw new IOException("Request failed " + headers.statusCode());
                    }
                    String line;
                    while (!cancelled.get() && (line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String item = line.substring(5).trim();
                            if (!item.isEmpty()) {
                                messageConsumer.accept(item);
                            }
                        }
                    }
                    if (!cancelled.get()) {
                        completeRunnable.run();
                    }
                }
                catch (Throwable error) {
                    if (!cancelled.get()) {
                        errorConsumer.accept(error);
                    }
                    throw new java.util.concurrent.CompletionException(error);
                }
            }, SSE_READERS));
            result.whenComplete((ignored, error) -> {
                if (result.isCancelled()) {
                    cancelled.set(true);
                    response.cancel(true);
                    java.io.InputStream input = inputRef.get();
                    if (input != null) {
                        try {
                            input.close();
                        }
                        catch (IOException closeError) {
                            // Cancellation has already completed the returned future.
                        }
                    }
                }
            });
            return result;
        }
    }

    private class JdkGetBuilder extends JdkBuilder<GetBuilder> implements A2AHttpClient.GetBuilder {

        private HttpRequest.Builder createRequestBuilder(boolean SSE) throws IOException {
            HttpRequest.Builder builder = super.createRequestBuilder().GET();
            if (SSE) {
                builder.header("Accept", "text/event-stream");
            }
            return builder;
        }

        @Override
        public A2AHttpResponse get() throws IOException, InterruptedException {
            HttpRequest request = createRequestBuilder(false)
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, BodyHandlers.ofString());
            return new JdkHttpResponse(response);
        }

        @Override
        public CompletableFuture<Void> getAsyncSSE(
                Consumer<String> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) throws IOException, InterruptedException {
            HttpRequest request = createRequestBuilder(true)
                    .build();
            return super.asyncRequest(request, messageConsumer, errorConsumer, completeRunnable);
        }
    }

    private class JdkPostBuilder extends JdkBuilder<PostBuilder> implements A2AHttpClient.PostBuilder {
        String body = "";

        @Override
        public PostBuilder body(String body) {
            this.body = body;
            return self();
        }

        private HttpRequest.Builder createRequestBuilder(boolean SSE) throws IOException {
            HttpRequest.Builder builder = super.createRequestBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (SSE) {
                builder.header("Accept", "text/event-stream");
            }
            return builder;
        }

        @Override
        public A2AHttpResponse post() throws IOException, InterruptedException {
            HttpRequest request = createRequestBuilder(false)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, BodyHandlers.ofString());
            return new JdkHttpResponse(response);
        }

        @Override
        public CompletableFuture<Void> postAsyncSSE(
                Consumer<String> messageConsumer,
                Consumer<Throwable> errorConsumer,
                Runnable completeRunnable) throws IOException, InterruptedException {
            HttpRequest request = createRequestBuilder(true)
                    .build();
            return super.asyncRequest(request, messageConsumer, errorConsumer, completeRunnable);
        }
    }

    private record JdkHttpResponse(HttpResponse<String> response) implements A2AHttpResponse {

        @Override
        public int status() {
            return response.statusCode();
        }

        @Override
        public boolean success() {// Send the request and get the response
            return success(response);
        }

        static boolean success(HttpResponse<?> response) {
            return response.statusCode() >= 200 && response.statusCode() < 300;
        }

        @Override
        public String body() {
            return response.body();
        }
    }
}
