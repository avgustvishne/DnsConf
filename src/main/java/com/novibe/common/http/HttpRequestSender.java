package com.novibe.common.http;

import com.novibe.common.exception.DnsHttpError;
import com.novibe.common.util.Jsonable;
import com.novibe.common.util.Log;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import static java.util.Objects.isNull;

@Setter(onMethod_ = @Autowired)
public abstract class HttpRequestSender {

    private static final int FIRST_ATTEMPT = 1;

    private final Semaphore semaphore = new Semaphore(100);

    protected static final String GET = "GET";
    protected static final String POST = "POST";
    protected static final String DELETE = "DELETE";

    protected HttpClient httpClient;

    protected abstract String requestUrl(String path);

    protected abstract Map<String, String> headers();

    protected abstract int retryAttempts();

    protected abstract Duration retryDelay();

    protected abstract void reactOnError(DnsHttpError dnsHttpError);

    protected <R extends Jsonable> HttpResponse<String> sendRequest(String method, String path, R body) {
        return sendRequest(method, path, body, FIRST_ATTEMPT);
    }

    protected <R extends Jsonable> HttpResponse<String> sendRequest(String method, String path, R body, int attempt) {
        try {
            HttpResponse<String> response = send(method, path, body);
            int responseCode = response.statusCode();

            if (responseCode > 299) {
                DnsHttpError httpError = new DnsHttpError(response, body);
                if (isTemporaryError(responseCode) && attempt < retryAttempts()) {
                    waitForRetry(response, attempt);
                    return sendRequest(method, path, body, attempt + 1);
                }
                reactOnError(httpError);
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("HTTP request interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("HTTP request failed", e);
        }
    }

    private <R extends Jsonable> HttpResponse<String> send(String method, String path, R body)
            throws IOException, InterruptedException {
        HttpRequest.BodyPublisher requestBody = isNull(body)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body.toJson());

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(requestUrl(path)))
                .timeout(Duration.ofSeconds(30))
                .method(method, requestBody);
        headers().forEach(request::header);

        semaphore.acquire();
        try {
            return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } finally {
            semaphore.release();
        }
    }

    private static boolean isTemporaryError(int responseCode) {
        return switch (responseCode) {
            case 429, 524 -> true;
            case int code when code >= 500 -> true;
            default -> false;
        };
    }

    private void waitForRetry(HttpResponse<String> response, int attempt) {
        Duration delay = retryAfter(response).orElse(retryDelay());
        long seconds = Math.max(0, delay.toSeconds());
        Log.common("\\nCode %s received. Attempt %s of %s, waiting %s seconds before retry"
                .formatted(response.statusCode(), attempt, retryAttempts(), seconds));
        for (long secondsLeft = seconds; secondsLeft > 0; secondsLeft--) {
            try {
                Thread.sleep(Duration.ofSeconds(1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting to retry HTTP request", e);
            }
            Log.progress("Waiting for reset: " + secondsLeft + " seconds");
        }
    }

    private Optional<Duration> retryAfter(HttpResponse<?> response) {
        if (response.headers() == null) {
            return Optional.empty();
        }
        Optional<String> value = response.headers().firstValue("Retry-After");
        if (value.isEmpty()) {
            return Optional.empty();
        }
        String header = value.get().strip();
        try {
            return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(header))));
        } catch (NumberFormatException ignored) {
            try {
                Duration untilRetry = Duration.between(Instant.now(),
                        ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return Optional.of(untilRetry.isNegative() ? Duration.ZERO : untilRetry);
            } catch (Exception invalidDate) {
                return Optional.empty();
            }
        }
    }
}
