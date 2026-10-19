-- Each Teaz user's link to their Strava account. user_id: Keycloak "sub".
-- One Strava athlete links to at most one Teaz user.
CREATE TABLE strava_connection (
    user_id       varchar PRIMARY KEY,
    athlete_id    bigint NOT NULL UNIQUE,
    athlete_name  varchar,
    access_token  varchar NOT NULL,
    refresh_token varchar NOT NULL,
    expires_at    timestamp with time zone NOT NULL,
    connected_at  timestamp with time zone NOT NULL,
    -- Start of the last successful sync, null until the first one.
    synced_at     timestamp with time zone,
    sync_error    varchar
);

-- Runs synced from Strava (other sports are not kept). id: Strava's activity id.
-- start_local: the start in the athlete's local time, which weeks are counted in.
CREATE TABLE run_activity (
    id                bigint PRIMARY KEY,
    user_id           varchar NOT NULL,
    name              varchar NOT NULL,
    sport_type        varchar NOT NULL,
    start_time        timestamp with time zone NOT NULL,
    start_local       timestamp NOT NULL,
    distance_m        double precision NOT NULL,
    moving_time_s     integer NOT NULL,
    elapsed_time_s    integer NOT NULL,
    elevation_gain_m  double precision,
    average_heartrate double precision
);
CREATE INDEX run_activity_user_start ON run_activity (user_id, start_time);
CREATE INDEX run_activity_user_local ON run_activity (user_id, start_local);
