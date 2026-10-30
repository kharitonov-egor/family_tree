package com.egakh.familytree.client.feedback;

import com.egakh.familytree.client.platform.ClientEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Sends one feedback message when the player presses Send. Nothing else in Family Tree makes web requests. */
public final class FeedbackSender {
    public static final URI ENDPOINT = URI.create("https://familytree-feedback.vercel.app/api/feedback");
    public static final int MAX_MESSAGE = 2000;
    public static final int MAX_CONTACT = 200;
    private static final long COOLDOWN_NANOS = 60_000_000_000L;
    private static final Set<String> KNOWN_ERRORS = Set.of("empty", "too_long", "spam", "rate_limited", "invalid", "unavailable");
    private static volatile long lastSent = Long.MIN_VALUE;

    public record Report(String message, String contact, ClientEnvironment.Versions versions, String language) {
        public String json() {
            JsonObject body = new JsonObject();
            body.addProperty("message", message);
            if (!contact.isBlank()) body.addProperty("contact", contact);
            body.addProperty("mod_version", versions.mod());
            body.addProperty("minecraft_version", versions.minecraft());
            body.addProperty("loader", versions.loader());
            body.addProperty("loader_version", versions.loaderVersion());
            body.addProperty("language", language);
            return body.toString();
        }
    }

    /** {@code error} is null on success, otherwise a code with a matching familytree.feedback.error lang key. */
    public record Outcome(String error) {
        public boolean sent() { return error == null; }
    }

    private FeedbackSender() {}

    public static boolean coolingDown() {
        return lastSent != Long.MIN_VALUE && System.nanoTime() - lastSent < COOLDOWN_NANOS;
    }

    public static CompletableFuture<Outcome> send(Report report) {
        if (report.message().isBlank()) return CompletableFuture.completedFuture(new Outcome("empty"));
        if (report.message().length() > MAX_MESSAGE) return CompletableFuture.completedFuture(new Outcome("too_long"));
        if (coolingDown()) return CompletableFuture.completedFuture(new Outcome("cooldown"));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        ClientEnvironment.Versions versions = report.versions();
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("User-Agent", "FamilyTree/" + versions.mod() + " (Minecraft " + versions.minecraft() + "; " + versions.loader() + ")")
                .POST(HttpRequest.BodyPublishers.ofString(report.json(), StandardCharsets.UTF_8))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> outcome(response.statusCode(), response.body()))
                .exceptionally(failure -> {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
                    return new Outcome(cause instanceof HttpTimeoutException ? "timeout" : "network");
                })
                .thenApply(outcome -> {
                    if (outcome.sent()) lastSent = System.nanoTime();
                    return outcome;
                });
    }

    static Outcome outcome(int status, String responseBody) {
        String error = null;
        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            if (parsed.isJsonObject()) {
                JsonObject body = parsed.getAsJsonObject();
                if (status == 200 && body.has("ok") && body.get("ok").getAsBoolean()) return new Outcome(null);
                if (body.has("error") && body.get("error").isJsonPrimitive()) error = body.get("error").getAsString().toLowerCase(Locale.ROOT);
            }
        } catch (RuntimeException unreadable) {
            error = null;
        }
        if (error != null && KNOWN_ERRORS.contains(error)) return new Outcome(error);
        return new Outcome(status == 429 ? "rate_limited" : "unavailable");
    }
}
