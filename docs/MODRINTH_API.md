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
| Get / list versions | `GET /v2/version/{id}` · `GET /v2/project/{id_or_slug}/version` | none (public) |
| Create version + upload jar | `POST /v2/version` (multipart) | `VERSION_CREATE` |
| Add a file to an existing version | `POST /v2/version/{id}/file` (multipart) | `VERSION_FILE_WRITE` |
| Edit a version (e.g. `game_versions`, `changelog`) | `PATCH /v2/version/{id}` (JSON) | `VERSION_WRITE` |

Project id is `CVQKDAe7`. The current released version id is discoverable via the list endpoint (e.g. `9j4QmEV4` was `0.3.2`).

## Multi-version jars (Stonecutter)

The build produces one jar **per Minecraft version**: `versions/<mcver>/build/libs/familytree-<modver>+<mcver>.jar` (see `CLAUDE.md`). Build them with `./gradlew buildAll` — you may run this build yourself when publishing a release. On Modrinth a single *version* carries one `game_versions` + `loaders` list that applies to **all** its files, so there are two layouts:

- **One Modrinth version per Minecraft version** (recommended, cleanest): `POST /v2/version` once per jar, with `version_number` like `0.3.3+26.2` and `game_versions: ["26.2"]`. Users only ever see the jar for their MC version.
- **Multiple files on one Modrinth version** (what `0.3.2` currently does): keep one version, add the other MC's jar with `POST /v2/version/{id}/file`, and `PATCH` its `game_versions` to list every supported MC. Simpler, but Modrinth can't tell which file is for which MC, so users on any listed version see all jars and must pick the right `+<mcver>` one.

### Add a file to an existing version + extend its game versions

```bash
# 1) extend the version's game_versions
curl -X PATCH "https://api.modrinth.com/v2/version/9j4QmEV4" \
  -H "Authorization: $MODRINTH_TOKEN" \
  -H "User-Agent: egakh/family_tree/<modver> (ega.khar@gmail.com)" \
  -H "Content-Type: application/json" \
  -d '{"game_versions":["26.1.1","26.2"]}'          # returns 204

# 2) upload the extra jar (data part just names the file part)
curl -X POST "https://api.modrinth.com/v2/version/9j4QmEV4/file" \
  -H "Authorization: $MODRINTH_TOKEN" \
  -H "User-Agent: egakh/family_tree/<modver> (ega.khar@gmail.com)" \
  -F 'data={"file_parts":["file"]};type=application/json' \
  -F "file=@versions/26.2/build/libs/familytree-<modver>+26.2.jar;type=application/java-archive"   # returns 204
```

`PATCH` needs the `VERSION_WRITE` scope; the file upload needs `VERSION_FILE_WRITE`. If either 401s, the PAT is missing that scope.

## Publishing a brand-new version

`POST /v2/version` takes multipart form data: a `data` part with JSON metadata, plus one part per file (a jar from `versions/<mcver>/build/libs/`). Build with `./gradlew buildAll` — you may run this build yourself when publishing a release.

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
  "primary_file": "file"
}
```

`version_number` is based on `mod_version` in `gradle.properties`; for a per-MC version use the `<modver>+<mcver>` form (e.g. `0.3.3+26.2`) and set `game_versions` to that single MC. It must be **unique** per project — you cannot reuse a `version_number` that already exists. `version_type` is `release`, `beta`, or `alpha`.

Example with curl (one MC version):

```bash
curl -X POST "https://api.modrinth.com/v2/version" \
  -H "Authorization: $MODRINTH_TOKEN" \
  -H "User-Agent: egakh/family_tree/0.3.3 (ega.khar@gmail.com)" \
  -F 'data={"project_id":"CVQKDAe7","version_number":"0.3.3+26.2","version_title":"Family Tree 0.3.3 (26.2)","changelog":"...","dependencies":[],"game_versions":["26.2"],"version_type":"release","loaders":["fabric"],"featured":true,"file_parts":["file"],"primary_file":"file"};type=application/json' \
  -F "file=@versions/26.2/build/libs/familytree-0.3.3+26.2.jar;type=application/java-archive"
```

Test against the staging API first if unsure. Verify afterwards with `GET /v2/project/{id}/version` and on the project page.
