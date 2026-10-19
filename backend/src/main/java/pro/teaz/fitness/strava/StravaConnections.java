package pro.teaz.fitness.strava;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Each user's link to their Strava account, with its tokens (table strava_connection). */
@Repository
public class StravaConnections {

	/**
	 * @param syncedAt  start of the last successful sync; null until the first one
	 * @param syncError why the last sync failed; null if it didn't
	 */
	public record Connection(
			String userId,
			long athleteId,
			String athleteName,
			String accessToken,
			String refreshToken,
			Instant expiresAt,
			Instant connectedAt,
			Instant syncedAt,
			String syncError) {
	}

	private static final String COLUMNS = """
			user_id, athlete_id, athlete_name, access_token, refresh_token, expires_at,
			connected_at, synced_at, sync_error""";

	private final JdbcClient jdbc;

	StravaConnections(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Connection> find(String userId) {
		return jdbc.sql("SELECT " + COLUMNS + " FROM strava_connection WHERE user_id = ?")
				.param(userId)
				.query(StravaConnections::row)
				.optional();
	}

	/** The Teaz user the Strava athlete is linked to, if any. */
	public Optional<String> userOfAthlete(long athleteId) {
		return jdbc.sql("SELECT user_id FROM strava_connection WHERE athlete_id = ?")
				.param(athleteId)
				.query(String.class)
				.optional();
	}

	public List<Connection> all() {
		return jdbc.sql("SELECT " + COLUMNS + " FROM strava_connection ORDER BY user_id")
				.query(StravaConnections::row)
				.list();
	}

	/** Links the user to an athlete, replacing an earlier link and its sync state. */
	public void connect(String userId, StravaApi.Tokens tokens, Instant now) {
		delete(userId);
		jdbc.sql("INSERT INTO strava_connection (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL)")
				.params(userId, tokens.athlete().id(), tokens.athlete().name(), tokens.accessToken(),
						tokens.refreshToken(), utc(tokens.expiresAt()), utc(now))
				.update();
	}

	public void updateTokens(String userId, StravaApi.Tokens tokens) {
		jdbc.sql("UPDATE strava_connection SET access_token = ?, refresh_token = ?, expires_at = ? WHERE user_id = ?")
				.params(tokens.accessToken(), tokens.refreshToken(), utc(tokens.expiresAt()), userId)
				.update();
	}

	public void markSynced(String userId, Instant syncedAt) {
		jdbc.sql("UPDATE strava_connection SET synced_at = ?, sync_error = NULL WHERE user_id = ?")
				.params(utc(syncedAt), userId)
				.update();
	}

	public void markFailed(String userId, String error) {
		jdbc.sql("UPDATE strava_connection SET sync_error = ? WHERE user_id = ?").params(error, userId).update();
	}

	public void delete(String userId) {
		jdbc.sql("DELETE FROM strava_connection WHERE user_id = ?").param(userId).update();
	}

	private static Connection row(ResultSet rs, int i) throws SQLException {
		return new Connection(
				rs.getString("user_id"),
				rs.getLong("athlete_id"),
				rs.getString("athlete_name"),
				rs.getString("access_token"),
				rs.getString("refresh_token"),
				instant(rs, "expires_at"),
				instant(rs, "connected_at"),
				instant(rs, "synced_at"),
				rs.getString("sync_error"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
