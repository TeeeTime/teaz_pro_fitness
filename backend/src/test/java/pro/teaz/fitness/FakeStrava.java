package pro.teaz.fitness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import pro.teaz.fitness.strava.StravaApi;
import pro.teaz.fitness.strava.StravaException;

/**
 * Strava for tests: one athlete's activities, which tests change between
 * syncs. Code {@code athlete-<id>} authorizes athlete {@code <id>}, code
 * {@code bad} is rejected. Tokens expire after six hours of the test clock;
 * {@link #revoked} makes every token and refresh fail, like access revoked in
 * the athlete's Strava settings.
 */
public class FakeStrava implements StravaApi {

	public final List<Activity> activities = new ArrayList<>();
	/** The {@code after} of every activities call, in order. */
	public final List<Instant> fetchedFrom = new ArrayList<>();
	public volatile boolean revoked;
	public int refreshes;
	public int deauthorizations;

	private final Map<String, Instant> tokenExpiry = new HashMap<>();
	private final Clock clock;
	private int issued;

	FakeStrava(Clock clock) {
		this.clock = clock;
	}

	public void reset() {
		activities.clear();
		fetchedFrom.clear();
		tokenExpiry.clear();
		revoked = false;
		refreshes = 0;
		deauthorizations = 0;
	}

	/** An activity started at a local time in {@code zone}. */
	public static Activity activity(long id, String sport, LocalDateTime local, ZoneId zone, double km, int movingSeconds) {
		return new Activity(id, "Activity " + id, sport, local.atZone(zone).toInstant(), local, km * 1000,
				movingSeconds, movingSeconds + 60, 42.0, 150.0);
	}

	public static Activity run(long id, LocalDateTime berlinTime, double km) {
		return activity(id, "Run", berlinTime, TestClock.BERLIN, km, (int) Math.round(km * 300));
	}

	@Override
	public String authorizeUrl(String state) {
		return "https://strava.example/oauth/authorize?state=" + state;
	}

	@Override
	public Tokens exchange(String code) {
		if (!code.startsWith("athlete-")) throw new StravaException.Unauthorized("bad code");
		long id = Long.parseLong(code.substring("athlete-".length()));
		return issue(new Athlete(id, "Athlete " + id));
	}

	@Override
	public Tokens refresh(String refreshToken) {
		if (revoked) throw new StravaException.Unauthorized("revoked");
		refreshes++;
		return issue(null);
	}

	@Override
	public synchronized List<Activity> activities(String accessToken, Instant after, int page, int perPage) {
		Instant expires = tokenExpiry.get(accessToken);
		if (revoked || expires == null || !expires.isAfter(clock.instant())) {
			throw new StravaException.Unauthorized("invalid token");
		}
		fetchedFrom.add(after);
		List<Activity> matching = activities.stream()
				.filter(a -> !a.startTime().isBefore(after))
				.sorted(Comparator.comparing(Activity::startTime))
				.toList();
		int from = Math.min((page - 1) * perPage, matching.size());
		return matching.subList(from, Math.min(from + perPage, matching.size()));
	}

	@Override
	public void deauthorize(String accessToken) {
		deauthorizations++;
	}

	@Override
	public boolean sample() {
		return false;
	}

	private synchronized Tokens issue(Athlete athlete) {
		issued++;
		Instant expires = clock.instant().plus(6, ChronoUnit.HOURS);
		String access = "access-" + issued;
		tokenExpiry.put(access, expires);
		return new Tokens(access, "refresh-" + issued, expires, athlete);
	}

	@TestConfiguration
	public static class Config {

		@Bean
		@Primary
		FakeStrava fakeStrava(Clock clock) {
			return new FakeStrava(clock);
		}
	}
}
