package pro.teaz.fitness.run;

import java.time.OffsetDateTime;

/**
 * One run, as the API returns it.
 *
 * @param id               Strava activity id
 * @param sportType        Run, TrailRun or VirtualRun
 * @param paceSecondsPerKm average over the moving time; null without distance (e.g. a treadmill run without a sensor)
 * @param elevationGainMeters null if Strava has none
 * @param averageHeartrate beats per minute; null without a heart rate monitor
 */
public record Run(
		long id,
		String name,
		String sportType,
		OffsetDateTime startTime,
		double distanceKm,
		int movingTimeSeconds,
		int elapsedTimeSeconds,
		Integer paceSecondsPerKm,
		Double elevationGainMeters,
		Double averageHeartrate,
		String stravaUrl) {

	static Run of(long id, String name, String sportType, OffsetDateTime startTime, double distanceMeters,
			int movingTimeSeconds, int elapsedTimeSeconds, Double elevationGainMeters, Double averageHeartrate) {
		Integer pace = distanceMeters >= 10 ? (int) Math.round(movingTimeSeconds / (distanceMeters / 1000)) : null;
		return new Run(id, name, sportType, startTime, kilometers(distanceMeters), movingTimeSeconds,
				elapsedTimeSeconds, pace, elevationGainMeters,
				averageHeartrate == null ? null : Math.round(averageHeartrate * 10) / 10.0,
				"https://www.strava.com/activities/" + id);
	}

	/** Meters as kilometers with two decimals. */
	static double kilometers(double meters) {
		return Math.round(meters / 10) / 100.0;
	}
}
