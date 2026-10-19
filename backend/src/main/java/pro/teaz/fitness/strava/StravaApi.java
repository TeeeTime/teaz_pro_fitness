package pro.teaz.fitness.strava;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The few Strava calls Fitness needs: OAuth (authorize, token exchange and
 * refresh, deauthorize) and the athlete's activities. {@link HttpStravaApi}
 * talks to strava.com, {@link SampleStravaApi} makes up runs for local dev.
 * Every call may throw a {@link StravaException}.
 */
public interface StravaApi {

	/** Scopes asked for: activity:read_all includes activities only the athlete can see. */
	String SCOPE = "read,activity:read_all";

	/** Strava's page size limit for activity lists. */
	int MAX_PER_PAGE = 200;

	/** Where to send the user to grant access; Strava redirects back with {@code code} and {@code state}. */
	String authorizeUrl(String state);

	/** Trades the code from the redirect for tokens and the athlete who granted access. */
	Tokens exchange(String code);

	/** New tokens for an expired access token; Strava may also hand out a new refresh token. */
	Tokens refresh(String refreshToken);

	/** One page (1-based) of the athlete's activities that started at or after {@code after}, any sport. */
	List<Activity> activities(String accessToken, Instant after, int page, int perPage);

	/** Revokes the app's access, as if the athlete had done it in their Strava settings. */
	void deauthorize(String accessToken);

	/** Made-up data instead of strava.com (no client id configured). */
	boolean sample();

	/** @param athlete only set by {@link #exchange} */
	record Tokens(String accessToken, String refreshToken, Instant expiresAt, Athlete athlete) {
	}

	record Athlete(long id, String name) {
	}

	/**
	 * A summary activity, as Strava lists it.
	 *
	 * @param sportType  e.g. Run, TrailRun, VirtualRun, Ride
	 * @param startLocal the start in the athlete's local time where it happened
	 */
	record Activity(
			long id,
			String name,
			String sportType,
			Instant startTime,
			LocalDateTime startLocal,
			double distanceMeters,
			int movingTimeSeconds,
			int elapsedTimeSeconds,
			Double elevationGainMeters,
			Double averageHeartrate) {
	}
}
