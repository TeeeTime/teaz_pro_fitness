package pro.teaz.fitness.run;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pro.teaz.fitness.FitnessProperties;
import pro.teaz.fitness.security.CurrentUser;
import pro.teaz.fitness.strava.StravaConnections;
import pro.teaz.fitness.strava.StravaConnections.Connection;

/**
 * The logged-in user's runs. {@code /api/runs/latest} and
 * {@code /api/runs/weekly} are the API for other apps (Portal tiles); see README.
 */
@RestController
@RequestMapping("/api/runs")
class RunController {

	static final String ATTRIBUTION = "Powered by Strava";
	static final int MAX_LIMIT = 100;
	static final int MAX_WEEKS = 52;

	/** @param connected false: no Strava account linked yet, everything else is empty */
	record RunList(boolean connected, OffsetDateTime syncedAt, List<Run> runs, String attribution) {
	}

	record LatestRun(boolean connected, OffsetDateTime syncedAt, Run run, String attribution) {
	}

	/** @param weekStart the week's Monday */
	record Week(LocalDate weekStart, double distanceKm, int runs, int movingTimeSeconds) {
	}

	/**
	 * @param weeks       oldest first; the last one is the current week, so far
	 * @param averageKm   mean of the completed weeks (all but the current one); null for weeks=1
	 */
	record WeeklyDistance(boolean connected, OffsetDateTime syncedAt, List<Week> weeks, double thisWeekKm,
			double lastWeekKm, Double averageKm, String attribution) {
	}

	private final Runs runs;
	private final StravaConnections connections;
	private final CurrentUser currentUser;
	private final FitnessProperties properties;
	private final Clock clock;

	RunController(Runs runs, StravaConnections connections, CurrentUser currentUser, FitnessProperties properties,
			Clock clock) {
		this.runs = runs;
		this.connections = connections;
		this.currentUser = currentUser;
		this.properties = properties;
		this.clock = clock;
	}

	@GetMapping
	RunList recent(@RequestParam(defaultValue = "20") int limit) {
		String user = currentUser.id();
		Optional<Connection> connection = connections.find(user);
		return new RunList(connection.isPresent(), syncedAt(connection),
				runs.recent(user, Math.clamp(limit, 1, MAX_LIMIT), properties.timezone()), ATTRIBUTION);
	}

	@GetMapping("/latest")
	LatestRun latest() {
		String user = currentUser.id();
		Optional<Connection> connection = connections.find(user);
		Run run = runs.recent(user, 1, properties.timezone()).stream().findFirst().orElse(null);
		return new LatestRun(connection.isPresent(), syncedAt(connection), run, ATTRIBUTION);
	}

	@GetMapping("/weekly")
	WeeklyDistance weekly(@RequestParam(defaultValue = "12") int weeks) {
		String user = currentUser.id();
		int count = Math.clamp(weeks, 1, MAX_WEEKS);
		LocalDate thisMonday = LocalDate.now(clock.withZone(properties.timezone()))
				.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate firstMonday = thisMonday.minusWeeks(count - 1);

		double[] meters = new double[count];
		int[] runCounts = new int[count];
		int[] seconds = new int[count];
		for (Runs.Day day : runs.daysFrom(user, firstMonday)) {
			int week = (int) ChronoUnit.WEEKS.between(firstMonday, day.date());
			// Runs dated after this week (a far-east time zone around midnight) belong to no bar.
			if (week >= count) continue;
			meters[week] += day.distanceMeters();
			runCounts[week]++;
			seconds[week] += day.movingTimeSeconds();
		}

		List<Week> result = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			result.add(new Week(firstMonday.plusWeeks(i), Run.kilometers(meters[i]), runCounts[i], seconds[i]));
		}
		Double average = null;
		if (count > 1) {
			double completed = 0;
			for (int i = 0; i < count - 1; i++) completed += meters[i];
			average = Run.kilometers(completed / (count - 1));
		}
		Optional<Connection> connection = connections.find(user);
		return new WeeklyDistance(connection.isPresent(), syncedAt(connection), result,
				result.get(count - 1).distanceKm(), count > 1 ? result.get(count - 2).distanceKm() : 0,
				average, ATTRIBUTION);
	}

	private OffsetDateTime syncedAt(Optional<Connection> connection) {
		return connection
				.map(Connection::syncedAt)
				.map(t -> t.atZone(properties.timezone()).toOffsetDateTime())
				.orElse(null);
	}
}
