package com.egakh.familytree.client.feedback;

import com.egakh.familytree.client.platform.ClientEnvironment;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackSenderTest {
    private static final ClientEnvironment.Versions VERSIONS = new ClientEnvironment.Versions("1.3.0+26.2", "26.2", "Fabric Loader", "0.19.3");

    @Test void sendsOnlyTheValuesTheScreenLists() {
        JsonObject body = JsonParser.parseString(new FeedbackSender.Report("Love it", "me@example.com", VERSIONS, "en_us").json()).getAsJsonObject();
        assertEquals(java.util.Set.of("message", "contact", "mod_version", "minecraft_version", "loader", "loader_version", "language"), body.keySet());
        assertEquals("Love it", body.get("message").getAsString());
        assertEquals("me@example.com", body.get("contact").getAsString());
        assertEquals("en_us", body.get("language").getAsString());
        assertFalse(JsonParser.parseString(new FeedbackSender.Report("Hi", "", VERSIONS, "de_de").json()).getAsJsonObject().has("contact"));
    }

    @Test void readsServerReplies() {
        assertTrue(FeedbackSender.outcome(200, "{\"ok\":true}").sent());
        assertEquals("rate_limited", FeedbackSender.outcome(429, "{\"ok\":false,\"error\":\"rate_limited\"}").error());
        assertEquals("rate_limited", FeedbackSender.outcome(429, "Too Many Requests").error());
        assertEquals("spam", FeedbackSender.outcome(400, "{\"ok\":false,\"error\":\"spam\"}").error());
        assertEquals("unavailable", FeedbackSender.outcome(400, "{\"ok\":false,\"error\":\"something_new\"}").error());
        assertEquals("unavailable", FeedbackSender.outcome(502, "<html>").error());
        assertEquals("unavailable", FeedbackSender.outcome(200, "{\"ok\":false}").error());
    }

    @Test void refusesEmptyAndOversizedMessagesBeforeSending() throws Exception {
        assertEquals("empty", FeedbackSender.send(new FeedbackSender.Report("  ", "", VERSIONS, "en_us")).get().error());
        assertEquals("too_long", FeedbackSender.send(new FeedbackSender.Report("x".repeat(FeedbackSender.MAX_MESSAGE + 1), "", VERSIONS, "en_us")).get().error());
    }
}
