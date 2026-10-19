# teaz_pro_fitness
Teaz Fitness, at `https://fitness.teaz.pro`: every user links their Strava
account and sees their latest runs and their weekly distance.

Monorepo: `frontend/` (Vue 3 + TypeScript) and `backend/` (Spring Boot, `pro.teaz:fitness`).

Stack, conventions, style guide and color tokens shared by every Teaz app
live in **`teaz_pro`**: start with `docs/TECH_STACK.md` there. This
README only covers what is specific to this app.

## How it works

| Part | Where | What |
| ---- | ----- | ---- |
| Linking | `strava/StravaController.java` | OAuth with Strava, scope `read,activity:read_all`. `POST /api/strava/connect` returns Strava's authorize URL with a random `state` tied to the Teaz user. Strava sends the browser back to the frontend's `/strava/callback`, which hands `code`, `state` and `scope` to `POST /api/strava/callback` with the user's token. So the backend has no endpoint without login, not even the callback. One Strava account links to one Teaz user. |
| Sync | `strava/StravaSync.java` | First sync after linking: 52 weeks back (`fitness.strava.backfill-weeks`). After that, every 15 minutes in the background (`sync-interval`), for each linked user, and on "Sync now". Each sync fetches again from 14 days before the previous one (`resync-window`) and replaces the stored runs from then on, so runs edited or deleted on Strava in that window change here too. Only `Run`, `TrailRun` and `VirtualRun` are kept. |
| Tokens | `strava/StravaConnections.java` | Strava access tokens last 6 hours and are refreshed before a sync when they have less than 5 minutes left. Strava may hand out a new refresh token each time; it's stored. |
| Revoked access | `StravaSync` | When Strava rejects the tokens (access revoked in the athlete's Strava settings), the link and the user's stored runs are deleted, as the Strava API agreement asks. "Disconnect Strava" does the same and also revokes access on Strava. |
| Weeks | `run/RunController.java` | Monday to Sunday, counted in the athlete's **local** time where the run happened (Strava's `start_date_local`), so a Sunday-night run abroad stays on Sunday. "This week" is the week of today in `Europe/Berlin`. |

Strava's rate limits apply to the app as a whole (all users together; a
limit per 15 minutes and one per day, current numbers on the app's page at
strava.com/settings/api). A background sync costs one request per user, plus
one token refresh every 6 hours, so this is fine for tens of users. At the
limit the background sync stops for the round and tries again later. Strava
webhooks (push instead of polling) would be the next step if that's ever
too little.

## API for other apps

Two endpoints for Portal tiles, with the caller's Keycloak access token
(any Teaz app's token works, same realm; no role needed). Both are about the
logged-in user. CORS allows the origins in `fitness.cors.allowed-origins`
(`application-prod.properties`, currently `https://portal.teaz.pro`), for
these two paths only.

### Latest run

`GET https://fitness.teaz.pro/api/runs/latest`

```json
{
  "connected": true,
  "syncedAt": "2026-10-19T23:52:10+02:00",
  "run": {
    "id": 16212345678,
    "name": "Long Run",
    "sportType": "Run",
    "startTime": "2026-10-18T08:00:00+02:00",
    "distanceKm": 17.84,
    "movingTimeSeconds": 5912,
    "elapsedTimeSeconds": 6030,
    "paceSecondsPerKm": 331,
    "elevationGainMeters": 231.0,
    "averageHeartrate": 161.0,
    "stravaUrl": "https://www.strava.com/activities/16212345678"
  },
  "attribution": "Powered by Strava"
}
```

### Weekly distance

`GET https://fitness.teaz.pro/api/runs/weekly?weeks=12`

```json
{
  "connected": true,
  "syncedAt": "2026-10-19T23:52:10+02:00",
  "weeks": [
    { "weekStart": "2026-07-27", "distanceKm": 30.12, "runs": 3, "movingTimeSeconds": 9840 },
    "… one entry per week, oldest first …",
    { "weekStart": "2026-10-19", "distanceKm": 8.4, "runs": 1, "movingTimeSeconds": 2791 }
  ],
  "thisWeekKm": 8.4,
  "lastWeekKm": 37.3,
  "averageKm": 34.6,
  "attribution": "Powered by Strava"
}
```

| Field | Meaning |
| ----- | ------- |
| `connected` | `false`: the user hasn't linked Strava yet (`run` is `null`, every week `0`). Link to `https://fitness.teaz.pro` to connect. |
| `syncedAt` | Last successful sync, `null` before the first one. |
| `run` | `null` if there are no runs yet. |
| `sportType` | `Run`, `TrailRun` or `VirtualRun`. |
| `distanceKm` | Two decimals. |
| `paceSecondsPerKm` | Average over the moving time; `null` without distance. |
| `elevationGainMeters`, `averageHeartrate` | `null` when Strava has none (e.g. no heart rate monitor). |
| `weeks` | Exactly `weeks` entries (1-52, default 12), oldest first. `weekStart` is the Monday. The last entry is the **current week so far**. |
| `thisWeekKm`, `lastWeekKm` | Distances of the last two entries. |
| `averageKm` | Mean of the completed weeks (all but the current one); `null` for `weeks=1`. |
| `attribution` | Show it with the data (Strava's brand guidelines). |

All times are ISO 8601 with the offset of `Europe/Berlin`. Errors are RFC
9457 problem details. Fields are only ever added to these responses, never
renamed or removed.

Tile ideas, the same way as Portal's Study tile
(`teaz_pro_portal/frontend/src/data/studyOverview.ts`: fetch with the
user's token, refresh every few minutes):

- **Weekly km**: `StatSparklineTile` from `teaz_pro_ui` fits as is.
  `value` = `thisWeekKm` + " km", delta = against `lastWeekKm` (or
  `averageKm`, which is fairer early in the week), `trend` = the 12
  `distanceKm` values of `weeks=12`.
- **Latest run**: name, date, distance, pace; click opens Fitness.

### Other endpoints (used by the Fitness UI)

| Endpoint | What |
| -------- | ---- |
| `GET /api/runs?limit=20` | The user's most recent runs (1-100), newest first, same fields as `run` above. |
| `GET /api/strava/connection` | `connected`, `sample`, `athleteName`, `connectedAt`, `syncedAt`, `syncing`, `syncError`. |
| `POST /api/strava/connect` | `{ "url": … }`: send the browser there to authorize. |
| `POST /api/strava/callback` | Body `{ "code", "state", "scope" }` from Strava's redirect. Links and runs the first sync, returns the connection. `400` for an expired/foreign state, missing activity scope or a code Strava rejects; `409` if the Strava account is linked to another user. |
| `POST /api/strava/sync` | Syncs now, returns the connection (a failure is in `syncError`). `409`: Strava access was revoked, the link is gone. |
| `DELETE /api/strava/connection` | Unlinks: revokes access on Strava, deletes the user's runs here. |

## Running locally

```sh
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev
```

Or use the IntelliJ run configs in `.idea/runConfigurations/`. Locally:

- **No Docker, no database install:** the backend uses a file-based H2
  database in PostgreSQL mode (`backend/data/`, gitignored). Delete that
  folder for a fresh start.
- **Offline:** without `fitness.strava.client-id`, "Connect with Strava"
  comes straight back and the backend makes up runs
  (`strava/SampleStravaApi.java`: easy runs Tuesdays, intervals Thursdays,
  a trail run or a ride Saturdays, a long run Sundays; the same every sync).
  The UI says "sample data".
- **With the real Strava:** set `FITNESS_STRAVA_CLIENT_ID` and
  `FITNESS_STRAVA_CLIENT_SECRET` in the backend's environment. Strava
  accepts `localhost` as callback domain, so the same Strava app works for
  local dev and prod.
- Auth is mocked on both sides; there's one local user.
- Backend port 8080 is also the other apps' default: only one of them can
  run locally at a time.

## Shared UI

`TopBar`, `useTheme()` and the theme tokens come from **`teaz_pro_ui`**
(pinned in `frontend/package.json`), see `teaz_pro/docs/UI_LIBRARY.md`. The
weekly chart (`frontend/src/components/WeeklyChart.vue`) is plain HTML/CSS
bars, no chart library.

## Auth

- Frontend `frontend/src/auth/`: Keycloak (`.env.production`: `auth.teaz.pro`,
  realm `teazpro`, client `fitness`) in builds, mock user in `npm run dev`.
- Backend `backend/src/main/java/pro/teaz/fitness/security/`, with the
  `prod` profile: every request needs a Keycloak Bearer JWT. **No role**:
  every Teaz user may use Fitness. Each user's link and runs are keyed by
  the token's `sub`.

Keycloak client setup: see the Auth section in `teaz_pro/docs/TECH_STACK.md`.

## Strava app

Once, at `https://www.strava.com/settings/api` (with the Strava account
that should own the app):

- **Website** `https://fitness.teaz.pro`, **Authorization Callback Domain**
  `fitness.teaz.pro`.
- Copy **Client ID** and **Client Secret** into `.env` (below).
- Check the app's athlete capacity on that page: new Strava apps may be
  limited to very few athletes (the owner) until Strava raises it on
  request, which matters as soon as other Teaz users should connect.
- The UI shows "Powered by Strava" as text and links runs with "View on
  Strava". Strava's brand guidelines ask for their logo and orange
  "Connect with Strava" button assets; swap those in if that matters for
  the app review.

## Database

Fitness uses the shared Postgres server on the VPS (`/opt/teazpro/postgres`),
reached over the external Docker network `db`, with its **own database
`fitness` and own user `fitness`** (owner of that database, no superuser
rights). It never reads another app's database.

- Schema: Flyway migrations in `backend/src/main/resources/db/migration`.
- Connection only from the environment: `SPRING_DATASOURCE_URL`,
  `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` in `.env`.
- `strava_connection` holds each user's Strava tokens (plain, like any
  secret in that database: keep backups private). `run_activity` can always
  be fetched again from Strava.

## Deployment

Once, on the Postgres server: create the user and database, exactly as in
`teaz_pro_study`'s README ("First deploy"), with `fitness` instead of
`study`. Then `.env` next to `compose.yml` (gitignored):

```sh
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/fitness
SPRING_DATASOURCE_USERNAME=fitness
SPRING_DATASOURCE_PASSWORD=<the password given to the fitness role>
FITNESS_STRAVA_CLIENT_ID=<Client ID of the Strava app>
FITNESS_STRAVA_CLIENT_SECRET=<Client Secret of the Strava app>
```

Then, as for every app:

```sh
git pull && docker compose up -d --build
```

`compose.yml` routes `fitness.teaz.pro` to the frontend (nginx) and
`fitness.teaz.pro/api/*` to the backend, via the shared Traefik (network
`proxy`, cert resolver `le`). The backend also joins the external network
`db` for Postgres.
