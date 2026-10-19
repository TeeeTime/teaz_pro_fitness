package pro.teaz.fitness.run;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pro.teaz.fitness.strava.StravaApi;

/** Each user's runs as last synced from Strava (table run_activity). */
@Repository
public class Runs {

	/** One run's day (in the athlete's local time) and size, for weekly totals. */
	public record Day(LocalDate date, double distanceMeters, int movingTimeSeconds) {
	}

	private final JdbcClient jdbc;

	Runs(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Replaces the user's runs that started at or after {@code from} with
	 * {@code runs}, which must be everything Strava has from then on: runs
	 * missing from it were deleted (or changed to another sport) on Strava.
	 */
	@Transactional
	public void replaceFrom(String userId, Instant from, Collection<StravaApi.Activity> runs) {
		jdbc.sql("DELETE FROM run_activity WHERE user_id = ? AND start_time >= ?").params(userId, utc(from)).update();
		for (StravaApi.Activity run : runs) {
			// Its start may have been moved to before "from" since the last sync.
			jdbc.sql("DELETE FROM run_activity WHERE id = ?").param(run.id()).update();
			jdbc.sql("""
					INSERT INTO run_activity (id, user_id, name, sport_type, start_time, start_local, distance_m,
					  moving_time_s, elapsed_time_s, elevation_gain_m, average_heartrate)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
					.params(run.id(), userId, run.name(), run.sportType(), utc(run.startTime()), run.startLocal(),
							run.distanceMeters(), run.movingTimeSeconds(), run.elapsedTimeSeconds(),
							run.elevationGainMeters(), run.averageHeartrate())
					.update();
		}
	}

	/** The user's most recent runs, newest first, with times in {@code zone}. */
	public List<Run> recent(String userId, int limit, ZoneId zone) {
		return jdbc.sql("""
				SELECT id, name, sport_type, start_time, distance_m, moving_time_s, elapsed_time_s,
				  elevation_gain_m, average_heartrate
				FROM run_activity WHERE user_id = ? ORDER BY start_time DESC LIMIT ?""")
				.params(userId, limit)
				.query((rs, i) -> Run.of(
						rs.getLong("id"),
						rs.getString("name"),
						rs.getString("sport_type"),
						rs.getObject("start_time", OffsetDateTime.class).atZoneSameInstant(zone).toOffsetDateTime(),
						rs.getDouble("distance_m"),
						rs.getInt("moving_time_s"),
						rs.getInt("elapsed_time_s"),
						rs.getObject("elevation_gain_m", Double.class),
						rs.getObject("average_heartrate", Double.class)))
				.list();
	}

	/** Every run of the user that started on or after {@code from} (local time where it happened). */
	public List<Day> daysFrom(String userId, LocalDate from) {
		return jdbc.sql("""
				SELECT start_local, distance_m, moving_time_s FROM run_activity
				WHERE user_id = ? AND start_local >= ?""")
				.params(userId, from.atStartOfDay())
				.query((rs, i) -> new Day(
						rs.getObject("start_local", LocalDateTime.class).toLocalDate(),
						rs.getDouble("distance_m"),
						rs.getInt("moving_time_s")))
				.list();
	}

	public void deleteAll(String userId) {
		jdbc.sql("DELETE FROM run_activity WHERE user_id = ?").param(userId).update();
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
