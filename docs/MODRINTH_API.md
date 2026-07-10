# Modrinth API — Publishing Guide

How to publish new versions of Family Tree to Modrinth via the API.
Sources: https://docs.modrinth.com/api/ and https://docs.modrinth.com/guide/oauth/

## Basics

- **Base URL:** `https://api.modrinth.com` (production), `https://staging-api.modrinth.com` (testing)
- **Rate limit:** 300 requests/minute per IP (`X-Ratelimit-Limit` / `-Remaining` / `-Reset` headers)
- **User-Agent header is mandatory** and must uniquely identify the app, e.g.
  `egakh/family_tree/0.3.1 (ega.khar@gmail.com)`. Vague agents (e.g. `okhttp/4.9.3`) may be blocked.
- Project/version IDs are eight-character base62 strings. Slugs work too but can change — prefer IDs in scripts.

## Authentication

Two options; **a Personal Access Token (PAT) is the right choice for publishing our own releases** (OAuth is for multi-user apps).

### Personal Access Token (recommended)

1. Create at https://modrinth.com/settings/pats
2. Grant scopes: `VERSION_CREATE` (create versions), `VERSION_FILE_WRITE` (upload files). Add `PROJECT_WRITE` only if editing project metadata.
3. Send it in the header (note: no `Bearer` prefix for PATs):

```
Authorization: mrp_xxxxxxxx
```

Wrong/missing scopes return `401`. Never commit the token.

**This repo's token lives in `.env` (gitignored) as `MODRINTH_TOKEN`.** Read it from there when publishing, e.g. in PowerShell:

```powershell
$env:MODRINTH_TOKEN = (Get-Content .env | Where-Object { $_ -match '^MODRINTH_TOKEN=' }) -replace '^MODRINTH_TOKEN=', ''
```

### OAuth2 (only for apps acting on behalf of other users)

1. Register the app at https://modrinth.com/settings/applications — set scopes and redirect URIs; you get a client ID and secret. Regenerate the secret immediately if it ever leaks.
2. Send the user to `https://modrinth.com/auth/authorize` with query params:
   `response_type=code`, `client_id`, `scope` (space-separated, `+`-joined), `redirect_uri`, optional `state` (CSRF protection).
3. After approval the user is redirected back with a `code`.
4. Exchange it: `POST https://api.modrinth.com/_internal/oauth/token` with
   `Content-Type: application/x-www-form-urlencoded`, client secret in the Authorization header, and form fields `code`, `client_id`, `redirect_uri`, `grant_type=authorization_code`.
5. Response: `access_token`, `token_type` (`Bearer`), `expires_in`. Use as `Authorization: Bearer <token>`.

Scope identifiers are listed in the Labrinth source: `apps/labrinth/src/models/v3/pats.rs`.

## Key endpoints

| Purpose | Endpoint | Auth |
|---|---|---|
| Get project | `GET /v2/project/{id_or_slug}` | none (public) |
| List versions | `GET /v2/project/{id_or_slug}/version` | none (public) |
| Create version + upload jar | `POST /v2/version` (multipart) | `VERSION_CREATE` |
| Add files to existing version | `POST /v2/version/{id}/file` (multipart) | `VERSION_FILE_WRITE` |

## Publishing a new version

`POST /v2/version` takes multipart form data: a `data` part with JSON metadata, plus one part per file (the jar from `build/libs/`, built by the user with `./gradlew build` — do not run the build yourself).

`data` JSON fields:

```json
{
  "project_id": "CVQKDAe7",
  "version_number": "0.3.1",
  "version_title": "Family Tree 0.3.1",
  "changelog": "Markdown changelog here",
  "dependencies": [],
  "game_versions": ["26.1.1"],
  "version_type": "release",
  "loaders": ["fabric"],
  "featured": true,
  "file_parts": ["file"],
  "primary_file": ["file"]
}
```

`version_number` must match `mod_version` in `gradle.properties`. `version_type` is `release`, `beta`, or `alpha`.

Example with curl:

```bash
curl -X POST "https://api.modrinth.com/v2/version" \
  -H "Authorization: $MODRINTH_TOKEN" \
  -H "User-Agent: egakh/family_tree/0.3.1 (ega.khar@gmail.com)" \
  -F 'data={"project_id":"CVQKDAe7","version_number":"0.3.1","version_title":"Family Tree 0.3.1","changelog":"...","dependencies":[],"game_versions":["26.1.1"],"version_type":"release","loaders":["fabric"],"featured":true,"file_parts":["file"],"primary_file":["file"]};type=application/json' \
  -F "file=@build/libs/familytree-0.3.1.jar"
```

Test against the staging API first if unsure. Verify afterwards with `GET /v2/project/{id}/version` and on the project page.
