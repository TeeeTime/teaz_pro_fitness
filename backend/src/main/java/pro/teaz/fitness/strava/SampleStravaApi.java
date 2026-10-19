package pro.teaz.fitness.strava;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Local-dev stand-in for {@link HttpStravaApi}, used when no Strava client id
 * is configured: offline, like the mock auth. "Authorizing" sends the browser
 * straight back to the callback, and the athlete runs three to four times a
 * week (easy runs, intervals, a long run on Sundays, now and then a ride
 * that isn't a run), made up from the date, so every sync sees the same
 * activities.
 */
class SampleStravaApi implements StravaApi {

	static final long ATHLETE_ID = 1;

	private final Clock clock;
	private final ZoneId zone;
	private final String redirectUri;

	SampleStravaApi(Clock clock, ZoneId zone, String redirectUri) {
		this.clock = clock;
		this.zone = zone;
		this.redirectUri = redirectUri;
	}

	@Override
	public String authorizeUrl(String state) {
		return redirectUri + "?code=sample&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8)
				+ "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
	}

	@Override
	public Tokens exchange(String code) {
		return tokens(new Athlete(ATHLETE_ID, "Sample Runner"));
	}

	@Override
	public Tokens refresh(String refreshToken) {
		return tokens(null);
	}

	@Override
	public List<Activity> activities(String accessToken, Instant after, int page, int perPage) {
		Instant now = clock.instant();
		List<Activity> all = new ArrayList<>();
		for (LocalDate day = LocalDate.ofInstant(after, zone); !day.isAfter(LocalDate.ofInstant(now, zone)); day = day.plusDays(1)) {
			Activity activity = activityOn(day);
			if (activity != null && !activity.startTime().isBefore(after) && activity.startTime().isBefore(now)) {
				all.add(activity);
			}
		}
		int from = Math.min((page - 1) * perPage, all.size());
		return all.subList(from, Math.min(from + perPage, all.size()));
	}

	@Override
	public void deauthorize(String accessToken) {
	}

	@Override
	public boolean sample() {
		return true;
	}

	private Tokens tokens(Athlete athlete) {
		return new Tokens("sample-access", "sample-refresh", clock.instant().plus(6, ChronoUnit.HOURS), athlete);
	}

	/** The day's activity, or null for a rest day. */
	private Activity activityOn(LocalDate day) {
		// SplittableRandom, not Random: Random's first values barely differ for neighboring seeds.
		SplittableRandom random = new SplittableRandom(day.toEpochDay());
		DayOfWeek weekday = day.getDayOfWeek();
		String name;
		String sport = "Run";
		double km;
		LocalTime start;
		switch (weekday) {
			case TUESDAY -> {
				name = "Evening Run";
				km = 6 + random.nextDouble() * 3;
				start = LocalTime.of(18, 30);
			}
			case THURSDAY -> {
				name = "Intervals";
				km = 7 + random.nextDouble() * 2;
				start = LocalTime.of(18, 0);
			}
			case SATURDAY -> {
				boolean ride = random.nextInt(4) == 0;
				name = ride ? "Afternoon Ride" : "Morning Run";
				sport = ride ? "Ride" : "TrailRun";
				km = ride ? 30 + random.nextDouble() * 20 : 5 + random.nextDouble() * 4;
				start = LocalTime.of(9, 15);
			}
			case SUNDAY -> {
				name = "Long Run";
				km = 14 + random.nextDouble() * 8;
				start = LocalTime.of(8, 0);
			}
			default -> {
				return null;
			}
		}
		// Skips about one in seven, so weeks differ.
		if (random.nextInt(7) == 0) return null;

		double meters = Math.round(km * 1000);
		double secondsPerKm = sport.equals("Ride") ? 120 : 300 + random.nextInt(60) - (name.equals("Intervals") ? 25 : 0);
		int moving = (int) Math.round(meters / 1000 * secondsPerKm);
		LocalDateTime local = day.atTime(start);
		return new Activity(
				day.toEpochDay() * 10 + 1,
				name,
				sport,
				local.atZone(zone).toInstant(),
				local,
				meters,
				moving,
				moving + random.nextInt(240),
				(double) Math.round(km * (5 + random.nextInt(10))),
				sport.equals("Ride") ? null : (double) (138 + random.nextInt(25)));
	}
}
