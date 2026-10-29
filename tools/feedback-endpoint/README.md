# Feedback endpoint

The Send feedback screen in Family Tree posts one JSON message to `https://familytree-feedback.vercel.app/api/feedback`. This Vercel function checks it and emails it to the developer through [Resend](https://resend.com). The mod hardcodes that URL in `FeedbackSender.ENDPOINT`, so keep the project name `familytree-feedback` or keep that alias pointing here.

The function stores nothing. It uses the caller's IP address only for rate limiting and leaves it out of the email.

## Request and replies

The mod sends `message` (1 to 2,000 characters after trimming), optional `contact` (up to 200), and `mod_version`, `minecraft_version`, `loader`, `loader_version`, and `language` (up to 64 characters each, letters, digits, and `._+-:() `). Bodies over 8 KiB are refused.

| Status | Body | Meaning |
| --- | --- | --- |
| 200 | `{"ok":true}` | Resend accepted the email |
| 400 | `{"ok":false,"error":"empty"}` | No message text |
| 400 | `{"ok":false,"error":"too_long"}` | Message over 2,000 characters (413 for an oversized body) |
| 400 | `{"ok":false,"error":"invalid"}` | Missing or malformed field |
| 400 | `{"ok":false,"error":"spam"}` | No letters, more than two links, a run of 30 repeated characters, or a known spam word |
| 429 | `{"ok":false,"error":"rate_limited","retry_after":N}` | Too many requests |
| 502 | `{"ok":false,"error":"unavailable"}` | Resend failed or the function has no key |

The mod maps each `error` value to a `familytree.feedback.error.*` lang key, so new codes need a key in every language file.

## Rate limits

- Each function instance allows 3 messages per IP address in 10 minutes and 10 per day, and 200 messages in total per day. Counters live in memory, so a new instance starts from zero.
- The Vercel firewall rule "Feedback burst limit" allows 10 requests to `/api/feedback` per IP address in 10 minutes across all instances and returns 429 above that.
- The mod waits 60 seconds after a sent message before it allows another.

## Deploy

Use Egor's personal Vercel scope, `kharitonov-egors-projects`.

```sh
cd tools/feedback-endpoint
npm ci
npm test
npm run check
vercel link --yes --project familytree-feedback --scope kharitonov-egors-projects
vercel env add RESEND_API_KEY production --sensitive --scope kharitonov-egors-projects
vercel env add FEEDBACK_TO production --scope kharitonov-egors-projects
vercel env add FEEDBACK_FROM production --scope kharitonov-egors-projects
vercel deploy --prod --scope kharitonov-egors-projects
```

`FEEDBACK_TO` is the inbox, `ega.khar@gmail.com`. `FEEDBACK_FROM` must use a domain verified in the same Resend account, such as `Family Tree feedback <feedback@example.com>`. Resend's `onboarding@resend.dev` sender delivers only to the Resend account's own address. Paste values when prompted; never put the key on the command line or in a file in this repository.

Add the firewall rule once per project:

```sh
vercel firewall rules add "Feedback burst limit" --condition '{"type":"path","op":"eq","value":"/api/feedback"}' \
  --action rate_limit --rate-limit-window 600 --rate-limit-requests 10 --rate-limit-keys ip \
  --rate-limit-action rate_limit --yes --scope kharitonov-egors-projects
vercel firewall publish --yes --scope kharitonov-egors-projects
```

## Check a deployment

```sh
curl -s -X POST https://familytree-feedback.vercel.app/api/feedback -H 'content-type: application/json' \
  -d '{"message":"","mod_version":"dev","minecraft_version":"26.2","loader":"curl","loader_version":"0","language":"en_us"}'
```

This returns `{"ok":false,"error":"empty"}` without sending mail. Runtime errors from Resend appear in `vercel logs --scope kharitonov-egors-projects`.
