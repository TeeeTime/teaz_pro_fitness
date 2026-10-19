package pro.teaz.fitness.strava;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import pro.teaz.fitness.TestClock;

class SampleStravaApiTests {

	private final TestClock clock = new TestClock();
	private final SampleStravaApi sample = new SampleStravaApi(clock, TestClock.BERLIN, "http://localhost:5173/strava/callback");

	@Test
	void authorizingComesStraightBackWithTheState() {
		assertThat(sample.authorizeUrl("abc"))
				.startsWith("http://localhost:5173/strava/callback?code=sample&scope=read%2Cactivity%3Aread_all")
				.endsWith("&state=abc");
	}

	@Test
	void runsAreTheSameEverySyncAndNeverInTheFuture() {
		clock.set(LocalDateTime.of(2026, 10, 21, 12, 0));
		Instant from = clock.instant().minus(70, ChronoUnit.DAYS);

		List<StravaApi.Activity> first = sample.activities("t", from, 1, StravaApi.MAX_PER_PAGE);
		assertThat(first).isEqualTo(sample.activities("t", from, 1, StravaApi.MAX_PER_PAGE));
		assertThat(first).hasSizeBetween(25, 40);
		assertThat(first).allMatch(a -> !a.startTime().isBefore(from) && a.startTime().isBefore(clock.instant()));
		assertThat(first).extracting(StravaApi.Activity::sportType).contains("Run", "TrailRun");

		// Paging: page 2 continues where page 1 of size 10 ended.
		List<StravaApi.Activity> page2 = sample.activities("t", from, 2, 10);
		assertThat(page2).isEqualTo(first.subList(10, 20));
	}
}
