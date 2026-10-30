// Receives feedback that a player chose to send from the Family Tree screen and emails it through Resend.

export const MAX_MESSAGE = 2000;
export const MAX_CONTACT = 200;
const MAX_BODY_BYTES = 8192;
const MAX_FIELD = 64;
const VERSION_FIELDS = ["mod_version", "minecraft_version", "loader", "loader_version", "language"] as const;

export type ErrorCode = "empty" | "too_long" | "invalid" | "spam" | "rate_limited" | "unavailable";

export interface Feedback {
  message: string;
  contact: string | null;
  modVersion: string;
  minecraftVersion: string;
  loader: string;
  loaderVersion: string;
  language: string;
}

export type Parsed = { ok: true; feedback: Feedback } | { ok: false; error: ErrorCode };

interface Window {
  ms: number;
  max: number;
}

/** Fixed-window counters. They live as long as the function instance; the Vercel firewall rule backs them up. */
export class RateLimiter {
  private readonly hits = new Map<string, number[]>();
  private readonly windows: readonly Window[];

  constructor(windows: readonly Window[]) {
    this.windows = windows;
  }

  /** Records a hit and returns the seconds to wait, or 0 when the hit is allowed. */
  hit(key: string, now: number): number {
    const longest = Math.max(...this.windows.map((window) => window.ms));
    const recent = (this.hits.get(key) ?? []).filter((time) => now - time < longest);
    for (const window of this.windows) {
      const inWindow = recent.filter((time) => now - time < window.ms);
      if (inWindow.length >= window.max) {
        this.hits.set(key, recent);
        return Math.max(1, Math.ceil((inWindow[0] + window.ms - now) / 1000));
      }
    }
    recent.push(now);
    this.hits.set(key, recent);
    if (this.hits.size > 10_000) this.prune(now, longest);
    return 0;
  }

  private prune(now: number, longest: number): void {
    for (const [key, times] of this.hits) {
      if (times.every((time) => now - time >= longest)) this.hits.delete(key);
    }
  }
}

const MINUTE = 60_000;
const DAY = 24 * 60 * MINUTE;
const perAddress = new RateLimiter([{ ms: 10 * MINUTE, max: 3 }, { ms: DAY, max: 10 }]);
const everyone = new RateLimiter([{ ms: DAY, max: 200 }]);

const SPAM_WORDS = /\b(casino|viagra|cialis|porn|crypto ?currency|forex|backlinks?|seo services|loan offer|escort)\b/i;
const LINK = /(https?:\/\/|www\.)/gi;

export function looksLikeSpam(message: string): boolean {
  if (!/\p{L}/u.test(message)) return true;
  if ((message.match(LINK) ?? []).length > 2) return true;
  if (/(.)\1{29,}/u.test(message)) return true;
  return SPAM_WORDS.test(message);
}

function cleanText(value: string): string {
  return value.replace(/\r\n?/g, "\n").replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "").trim();
}

export function parseFeedback(body: unknown): Parsed {
  if (typeof body !== "object" || body === null || Array.isArray(body)) return { ok: false, error: "invalid" };
  const fields = body as Record<string, unknown>;
  if (typeof fields.message !== "string") return { ok: false, error: "invalid" };
  const message = cleanText(fields.message);
  if (message.length === 0) return { ok: false, error: "empty" };
  if (message.length > MAX_MESSAGE) return { ok: false, error: "too_long" };

  let contact: string | null = null;
  if (fields.contact !== undefined && fields.contact !== null) {
    if (typeof fields.contact !== "string") return { ok: false, error: "invalid" };
    contact = cleanText(fields.contact).replace(/\n/g, " ");
    if (contact.length > MAX_CONTACT) return { ok: false, error: "invalid" };
    if (contact.length === 0) contact = null;
  }

  const versions: Record<string, string> = {};
  for (const name of VERSION_FIELDS) {
    const value = fields[name];
    if (typeof value !== "string" || value.length > MAX_FIELD || !/^[\w .+\-:()]*$/.test(value)) {
      return { ok: false, error: "invalid" };
    }
    versions[name] = value;
  }

  if (looksLikeSpam(message) || looksLikeSpam(`${message}\n${contact ?? ""}`)) return { ok: false, error: "spam" };

  return {
    ok: true,
    feedback: {
      message,
      contact,
      modVersion: versions.mod_version,
      minecraftVersion: versions.minecraft_version,
      loader: versions.loader,
      loaderVersion: versions.loader_version,
      language: versions.language,
    },
  };
}

export function emailText(feedback: Feedback, received: Date): string {
  return [
    feedback.message,
    "",
    "--",
    `Contact: ${feedback.contact ?? "none"}`,
    `Family Tree: ${feedback.modVersion}`,
    `Minecraft: ${feedback.minecraftVersion}`,
    `Loader: ${feedback.loader} ${feedback.loaderVersion}`,
    `Language: ${feedback.language}`,
    `Received: ${received.toISOString()}`,
  ].join("\n");
}

export function emailSubject(feedback: Feedback): string {
  const firstLine = feedback.message.split("\n")[0];
  const preview = firstLine.length > 60 ? `${firstLine.slice(0, 57)}...` : firstLine;
  return `Family Tree feedback (${feedback.loader}, ${feedback.minecraftVersion}): ${preview}`;
}

function reply(status: number, body: { ok: true } | { ok: false; error: ErrorCode; retry_after?: number }): Response {
  const headers: Record<string, string> = { "content-type": "application/json", "cache-control": "no-store" };
  if (!body.ok && body.retry_after !== undefined) headers["retry-after"] = String(body.retry_after);
  return new Response(JSON.stringify(body), { status, headers });
}

function clientAddress(request: Request): string {
  return request.headers.get("x-real-ip") ?? request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "unknown";
}

async function sendEmail(feedback: Feedback): Promise<boolean> {
  const key = process.env.RESEND_API_KEY;
  const to = process.env.FEEDBACK_TO;
  const from = process.env.FEEDBACK_FROM;
  if (!key || !to || !from) return false;
  const replyTo = feedback.contact !== null && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(feedback.contact) ? feedback.contact : undefined;
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { authorization: `Bearer ${key}`, "content-type": "application/json" },
    body: JSON.stringify({
      from,
      to: [to],
      subject: emailSubject(feedback),
      text: emailText(feedback, new Date()),
      ...(replyTo ? { reply_to: replyTo } : {}),
    }),
    signal: AbortSignal.timeout(8000),
  });
  if (!response.ok) console.error(`Resend returned ${response.status}: ${(await response.text()).slice(0, 300)}`);
  return response.ok;
}

export async function POST(request: Request): Promise<Response> {
  const now = Date.now();
  const wait = perAddress.hit(clientAddress(request), now);
  if (wait > 0) return reply(429, { ok: false, error: "rate_limited", retry_after: wait });

  const raw = await request.text();
  if (new TextEncoder().encode(raw).length > MAX_BODY_BYTES) return reply(413, { ok: false, error: "too_long" });
  let body: unknown;
  try {
    body = JSON.parse(raw);
  } catch {
    return reply(400, { ok: false, error: "invalid" });
  }
  const parsed = parseFeedback(body);
  if (!parsed.ok) return reply(400, { ok: false, error: parsed.error });

  const globalWait = everyone.hit("all", now);
  if (globalWait > 0) return reply(429, { ok: false, error: "rate_limited", retry_after: globalWait });

  try {
    if (await sendEmail(parsed.feedback)) return reply(200, { ok: true });
  } catch (failure) {
    console.error("Could not reach Resend", failure);
  }
  return reply(502, { ok: false, error: "unavailable" });
}

export function GET(): Response {
  return new Response(JSON.stringify({ ok: false, error: "invalid" }), {
    status: 405,
    headers: { "content-type": "application/json", allow: "POST" },
  });
}
