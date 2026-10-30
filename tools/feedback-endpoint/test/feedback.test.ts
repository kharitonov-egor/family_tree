import assert from "node:assert/strict";
import { test } from "node:test";
import { emailSubject, emailText, looksLikeSpam, parseFeedback, POST, RateLimiter } from "../api/feedback.ts";

const valid = {
  message: "The tree screen is great.\nCould it show cats' colors?",
  contact: "me@example.com",
  mod_version: "1.3.0+26.2",
  minecraft_version: "26.2",
  loader: "Fabric Loader",
  loader_version: "0.19.3",
  language: "en_us",
};

test("accepts the fields the mod sends", () => {
  const parsed = parseFeedback(valid);
  assert.ok(parsed.ok);
  assert.equal(parsed.feedback.contact, "me@example.com");
  assert.equal(parsed.feedback.loaderVersion, "0.19.3");
});

test("contact is optional", () => {
  const { contact: _, ...withoutContact } = valid;
  const parsed = parseFeedback(withoutContact);
  assert.ok(parsed.ok);
  assert.equal(parsed.feedback.contact, null);
});

test("rejects empty, oversized, and malformed bodies", () => {
  assert.deepEqual(parseFeedback({ ...valid, message: "   \n " }), { ok: false, error: "empty" });
  assert.deepEqual(parseFeedback({ ...valid, message: "a".repeat(2001) }), { ok: false, error: "too_long" });
  assert.deepEqual(parseFeedback({ ...valid, contact: "x".repeat(201) }), { ok: false, error: "invalid" });
  assert.deepEqual(parseFeedback({ ...valid, loader: "<script>" }), { ok: false, error: "invalid" });
  assert.deepEqual(parseFeedback({ ...valid, language: undefined }), { ok: false, error: "invalid" });
  assert.deepEqual(parseFeedback("hello"), { ok: false, error: "invalid" });
  assert.deepEqual(parseFeedback(null), { ok: false, error: "invalid" });
});

test("flags obvious spam", () => {
  assert.ok(looksLikeSpam("1234 5678"));
  assert.ok(looksLikeSpam("buy https://a.example https://b.example www.c.example"));
  assert.ok(looksLikeSpam("Best casino bonus"));
  assert.ok(looksLikeSpam("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
  assert.ok(!looksLikeSpam("Horse stats don't update after a potion. See https://github.com/x/y/issues/1"));
  assert.ok(!looksLikeSpam("Отличный мод, спасибо!"));
  assert.deepEqual(parseFeedback({ ...valid, contact: "https://a.example https://b.example https://c.example" }), { ok: false, error: "spam" });
});

test("rate limits per key and reports the wait", () => {
  const limiter = new RateLimiter([{ ms: 60_000, max: 2 }]);
  assert.equal(limiter.hit("a", 0), 0);
  assert.equal(limiter.hit("a", 1_000), 0);
  assert.equal(limiter.hit("a", 2_000), 58);
  assert.equal(limiter.hit("b", 2_000), 0);
  assert.equal(limiter.hit("a", 61_000), 0);
});

test("email lists every field and omits the address", () => {
  const parsed = parseFeedback(valid);
  assert.ok(parsed.ok);
  const text = emailText(parsed.feedback, new Date("2026-10-29T12:00:00Z"));
  for (const part of ["Could it show cats' colors?", "Contact: me@example.com", "Family Tree: 1.3.0+26.2", "Loader: Fabric Loader 0.19.3", "Language: en_us"]) {
    assert.ok(text.includes(part), part);
  }
  assert.equal(emailSubject(parsed.feedback), "Family Tree feedback (Fabric Loader, 26.2): The tree screen is great.");
});

test("POST returns codes the mod understands and limits bursts", async () => {
  const send = (body: unknown, address: string) => POST(new Request("https://example.test/api/feedback", {
    method: "POST",
    headers: { "x-real-ip": address, "content-type": "application/json" },
    body: typeof body === "string" ? body : JSON.stringify(body),
  }));
  let response = await send("not json", "10.0.0.1");
  assert.equal(response.status, 400);
  assert.deepEqual(await response.json(), { ok: false, error: "invalid" });
  response = await send({ ...valid, message: "" }, "10.0.0.1");
  assert.deepEqual(await response.json(), { ok: false, error: "empty" });
  response = await send(valid, "10.0.0.1");
  assert.equal(response.status, 502, "no Resend key in tests, so sending fails");
  response = await send(valid, "10.0.0.1");
  assert.equal(response.status, 429);
  assert.equal((await response.json()).error, "rate_limited");
  assert.ok(Number(response.headers.get("retry-after")) > 0);
});
