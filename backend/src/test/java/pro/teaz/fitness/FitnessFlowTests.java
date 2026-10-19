package pro.teaz.fitness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Linking Strava, syncing, the run list and the two tile endpoints, against
 * {@link FakeStrava}, with the real prod security and a fixed clock:
 * Wednesday 2026-10-21, 12:00 in Berlin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
@Import({TestClock.Config.class, FakeStrava.Config.class})
class FitnessFlowTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	TestClock clock;

	@Autowired
	FakeStrava strava;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void reset() {
		jdbc.sql("DELETE FROM run_activity").update();
		jdbc.sql("DELETE FROM strava_connection").update();
		strava.reset();
		clock.set(LocalDateTime.of(2026, 10, 21, 12, 0));
	}

	private static JwtRequestPostProcessor user(String id) {
		return jwt().jwt(token -> token.subject(id));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode read(String path, String userId) throws Exception {
		return body(mvc.perform(get(path).with(user(userId))));
	}

	private String startConnect(String userId) throws Exception {
		String url = body(mvc.perform(post("/api/strava/connect").with(user(userId)))).path("url").asString();
		return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
	}

	private ResultActions callback(String userId, String code, String state, String scope) throws Exception {
		return mvc.perform(post("/api/strava/callback").with(user(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("code", code, "state", state, "scope", scope))));
	}

	private JsonNode connect(String userId, String code) throws Exception {
		return body(callback(userId, code, startConnect(userId), "read,activity:read_all"));
	}

	private static List<Long> ids(JsonNode runs) {
		List<Long> ids = new ArrayList<>();
		runs.forEach(run -> ids.add(run.path("id").asLong()));
		return ids;
	}

	@Test
	void withoutStravaEverythingIsEmpty() throws Exception {
		assertThat(read("/api/strava/connection", "a").path("connected").asBoolean()).isFalse();

		JsonNode latest = read("/api/runs/latest", "a");
		assertThat(latest.path("connected").asBoolean()).isFalse();
		assertThat(latest.path("run").isNull()).isTrue();
		assertThat(latest.path("attribution").asString()).isEqualTo("Powered by Strava");

		JsonNode weekly = read("/api/runs/weekly", "a");
		assertThat(weekly.path("weeks").size()).isEqualTo(12);
		assertThat(weekly.path("weeks").get(11).path("weekStart").asString()).isEqualTo("2026-10-19");
		assertThat(weekly.path("thisWeekKm").asDouble()).isZero();
	}

	@Test
	void connectingSyncsOnlyRuns() throws Exception {
		strava.activities.add(FakeStrava.run(101, LocalDateTime.of(2026, 10, 20, 7, 0), 10.0));
		strava.activities.add(FakeStrava.activity(102, "Ride", LocalDateTime.of(2026, 10, 19, 17, 0), TestClock.BERLIN, 40, 5400));
		strava.activities.add(FakeStrava.activity(103, "TrailRun", LocalDateTime.of(2026, 10, 18, 9, 0), TestClock.BERLIN, 21.1, 7600));
		strava.activities.add(FakeStrava.run(104, LocalDateTime.of(2026, 10, 12, 18, 30), 5.0));

		JsonNode status = connect("a", "athlete-7");
		assertThat(status.path("connected").asBoolean()).isTrue();
		assertThat(status.path("athleteName").asString()).isEqualTo("Athlete 7");
		assertThat(status.path("syncedAt").asString()).isEqualTo("2026-10-21T12:00:00+02:00");
		assertThat(status.path("syncError").isNull()).isTrue();
		// First sync: 52 weeks back, from a Monday.
		assertThat(strava.fetchedFrom).hasSize(1);
		assertThat(strava.fetchedFrom.getFirst().atZone(TestClock.BERLIN).toLocalDateTime())
				.isEqualTo(LocalDateTime.of(2025, 10, 20, 0, 0));

		assertThat(ids(read("/api/runs", "a").path("runs"))).containsExactly(101L, 103L, 104L);

		JsonNode latest = read("/api/runs/latest", "a");
		assertThat(latest.path("connected").asBoolean()).isTrue();
		JsonNode run = latest.path("run");
		assertThat(run.path("id").asLong()).isEqualTo(101);
		assertThat(run.path("sportType").asString()).isEqualTo("Run");
		assertThat(run.path("startTime").asString()).isEqualTo("2026-10-20T07:00:00+02:00");
		assertThat(run.path("distanceKm").asDouble()).isEqualTo(10.0);
		assertThat(run.path("movingTimeSeconds").asInt()).isEqualTo(3000);
		assertThat(run.path("paceSecondsPerKm").asInt()).isEqualTo(300);
		assertThat(run.path("stravaUrl").asString()).isEqualTo("https://www.strava.com/activities/101");

		// Another user sees none of it.
		assertThat(read("/api/runs", "b").path("runs").size()).isZero();
		assertThat(read("/api/runs/latest", "b").path("run").isNull()).isTrue();
	}

	@Test
	void weeksAreCountedInTheAthletesLocalTime() throws Exception {
		ZoneId newYork = ZoneId.of("America/New_York");
		// Week of 5 Oct: 8 km. Week of 12 Oct: 10 + 6 + 4 km, the last one Sunday night in New York
		// (Monday morning in Berlin). This week: 7 km so far.
		strava.activities.add(FakeStrava.run(1, LocalDateTime.of(2026, 10, 7, 18, 0), 8));
		strava.activities.add(FakeStrava.run(2, LocalDateTime.of(2026, 10, 13, 18, 0), 10));
		strava.activities.add(FakeStrava.run(3, LocalDateTime.of(2026, 10, 18, 23, 30), 6));
		strava.activities.add(FakeStrava.activity(4, "Run", LocalDateTime.of(2026, 10, 18, 23, 30), newYork, 4, 1500));
		strava.activities.add(FakeStrava.run(5, LocalDateTime.of(2026, 10, 19, 0, 15), 7));
		// Older than the three weeks asked for.
		strava.activities.add(FakeStrava.run(6, LocalDateTime.of(2026, 10, 4, 10, 0), 30));
		connect("a", "athlete-7");

		JsonNode weekly = read("/api/runs/weekly?weeks=3", "a");
		JsonNode weeks = weekly.path("weeks");
		assertThat(weeks.size()).isEqualTo(3);
		assertThat(weeks.get(0).path("weekStart").asString()).isEqualTo("2026-10-05");
		assertThat(weeks.get(0).path("distanceKm").asDouble()).isEqualTo(8.0);
		assertThat(weeks.get(1).path("weekStart").asString()).isEqualTo("2026-10-12");
		assertThat(weeks.get(1).path("distanceKm").asDouble()).isEqualTo(20.0);
		assertThat(weeks.get(1).path("runs").asInt()).isEqualTo(3);
		assertThat(weeks.get(2).path("weekStart").asString()).isEqualTo("2026-10-19");
		assertThat(weeks.get(2).path("distanceKm").asDouble()).isEqualTo(7.0);
		assertThat(weekly.path("thisWeekKm").asDouble()).isEqualTo(7.0);
		assertThat(weekly.path("lastWeekKm").asDouble()).isEqualTo(20.0);
		assertThat(weekly.path("averageKm").asDouble()).isEqualTo(14.0);

		JsonNode one = read("/api/runs/weekly?weeks=1", "a");
		assertThat(one.path("weeks").size()).isEqualTo(1);
		assertThat(one.path("averageKm").isNull()).isTrue();
	}

	@Test
	void resyncPicksUpEditsAndDeletionsWithinTheWindow() throws Exception {
		strava.activities.add(FakeStrava.run(1, LocalDateTime.of(2026, 8, 1, 9, 0), 12));
		strava.activities.add(FakeStrava.run(2, LocalDateTime.of(2026, 10, 18, 9, 0), 15));
		strava.activities.add(FakeStrava.run(3, LocalDateTime.of(2026, 10, 20, 7, 0), 8));
		connect("a", "athlete-7");

		clock.advance(Duration.ofHours(1));
		strava.activities.removeIf(a -> a.id() == 1 || a.id() == 3);
		strava.activities.removeIf(a -> a.id() == 2);
		strava.activities.add(FakeStrava.run(2, LocalDateTime.of(2026, 10, 18, 9, 0), 16));
		strava.activities.add(FakeStrava.run(4, LocalDateTime.of(2026, 10, 21, 12, 30), 5));

		JsonNode status = body(mvc.perform(post("/api/strava/sync").with(user("a"))));
		assertThat(status.path("syncedAt").asString()).isEqualTo("2026-10-21T13:00:00+02:00");
		// Fetched again from 14 days before the previous sync.
		assertThat(strava.fetchedFrom.getLast().atZone(TestClock.BERLIN).toLocalDateTime())
				.isEqualTo(LocalDateTime.of(2026, 10, 7, 12, 0));

		JsonNode runs = read("/api/runs", "a").path("runs");
		// Run 3 is gone, run 2 is updated, run 1 is outside the window and stays.
		assertThat(ids(runs)).containsExactly(4L, 2L, 1L);
		assertThat(runs.get(1).path("distanceKm").asDouble()).isEqualTo(16.0);
	}

	@Test
	void expiredAccessTokenIsRefreshed() throws Exception {
		strava.activities.add(FakeStrava.run(1, LocalDateTime.of(2026, 10, 20, 7, 0), 8));
		connect("a", "athlete-7");
		clock.advance(Duration.ofHours(7));

		JsonNode status = body(mvc.perform(post("/api/strava/sync").with(user("a"))));
		assertThat(status.path("syncError").isNull()).isTrue();
		assertThat(strava.refreshes).isEqualTo(1);
	}

	@Test
	void revokedAccessDeletesLinkAndRuns() throws Exception {
		strava.activities.add(FakeStrava.run(1, LocalDateTime.of(2026, 10, 20, 7, 0), 8));
		connect("a", "athlete-7");
		strava.revoked = true;
		clock.advance(Duration.ofHours(7));

		mvc.perform(post("/api/strava/sync").with(user("a"))).andExpect(status().isConflict());
		assertThat(read("/api/strava/connection", "a").path("connected").asBoolean()).isFalse();
		assertThat(read("/api/runs", "a").path("runs").size()).isZero();
	}

	@Test
	void stateIsSingleUseAndBelongsToOneUser() throws Exception {
		String state = startConnect("a");
		callback("b", "athlete-7", state, "read,activity:read_all").andExpect(status().isBadRequest());
		// Used up by the attempt above.
		callback("a", "athlete-7", state, "read,activity:read_all").andExpect(status().isBadRequest());
		callback("a", "athlete-7", "made-up", "read,activity:read_all").andExpect(status().isBadRequest());
		assertThat(read("/api/strava/connection", "a").path("connected").asBoolean()).isFalse();
	}

	@Test
	void activityAccessAndAValidCodeAreRequired() throws Exception {
		callback("a", "athlete-7", startConnect("a"), "read").andExpect(status().isBadRequest());
		callback("a", "bad", startConnect("a"), "read,activity:read").andExpect(status().isBadRequest());
		assertThat(read("/api/strava/connection", "a").path("connected").asBoolean()).isFalse();
	}

	@Test
	void anAthleteLinksToOneUserOnly() throws Exception {
		connect("a", "athlete-7");
		callback("b", "athlete-7", startConnect("b"), "read,activity:read_all").andExpect(status().isConflict());
		// Linking again as the same user is fine.
		connect("a", "athlete-7");
	}

	@Test
	void disconnectRevokesAccessAndDeletesRuns() throws Exception {
		strava.activities.add(FakeStrava.run(1, LocalDateTime.of(2026, 10, 20, 7, 0), 8));
		connect("a", "athlete-7");

		mvc.perform(delete("/api/strava/connection").with(user("a"))).andExpect(status().isNoContent());
		assertThat(strava.deauthorizations).isEqualTo(1);
		assertThat(read("/api/strava/connection", "a").path("connected").asBoolean()).isFalse();
		assertThat(read("/api/runs", "a").path("runs").size()).isZero();
	}
}
