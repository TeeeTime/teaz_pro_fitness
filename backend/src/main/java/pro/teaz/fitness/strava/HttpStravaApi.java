package pro.teaz.fitness.strava;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pro.teaz.fitness.FitnessProperties;

/** {@link StravaApi} against strava.com (developers.strava.com/docs/reference). */
class HttpStravaApi implements StravaApi {

	private static final String OAUTH = "https://www.strava.com/oauth";
	private static final String API = "https://www.strava.com/api/v3";
	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	private final FitnessProperties.Strava settings;
	private final JsonMapper json;

	HttpStravaApi(FitnessProperties.Strava settings, JsonMapper json) {
		this.settings = settings;
		this.json = json;
	}

	@Override
	public String authorizeUrl(String state) {
		return OAUTH + "/authorize?" + query(
				"client_id", settings.clientId(),
				"redirect_uri", settings.redirectUri(),
				"response_type", "code",
				"approval_prompt", "auto",
				"scope", SCOPE,
				"state", state);
	}

	@Override
	public Tokens exchange(String code) {
		JsonNode body = postForm(OAUTH + "/token",
				"client_id", settings.clientId(),
				"client_secret", settings.clientSecret(),
				"code", code,
				"grant_type", "authorization_code");
		JsonNode athlete = body.path("athlete");
		String name = (athlete.path("firstname").asString("") + " " + athlete.path("lastname").asString("")).trim();
		return tokens(body, new Athlete(athlete.path("id").asLong(), name.isEmpty() ? null : name));
	}

	@Override
	public Tokens refresh(String refreshToken) {
		return tokens(postForm(OAUTH + "/token",
				"client_id", settings.clientId(),
				"client_secret", settings.clientSecret(),
				"refresh_token", refreshToken,
				"grant_type", "refresh_token"), null);
	}

	@Override
	public List<Activity> activities(String accessToken, Instant after, int page, int perPage) {
		String url = API + "/athlete/activities?" + query(
				"after", String.valueOf(after.getEpochSecond()),
				"page", String.valueOf(page),
				"per_page", String.valueOf(perPage));
		JsonNode body = send(HttpRequest.newBuilder(URI.create(url))
				.header("Authorization", "Bearer " + accessToken)
				.GET());
		List<Activity> activities = new ArrayList<>();
		for (JsonNode a : body) {
			activities.add(new Activity(
					a.path("id").asLong(),
					a.path("name").asString(""),
					a.path("sport_type").asString(a.path("type").asString("")),
					Instant.parse(a.path("start_date").asString()),
					// Local wall-clock time, but Strava writes it with a "Z" anyway.
					OffsetDateTime.parse(a.path("start_date_local").asString()).toLocalDateTime(),
					a.path("distance").asDouble(),
					a.path("moving_time").asInt(),
					a.path("elapsed_time").asInt(),
					a.hasNonNull("total_elevation_gain") ? a.get("total_elevation_gain").asDouble() : null,
					a.hasNonNull("average_heartrate") ? a.get("average_heartrate").asDouble() : null));
		}
		return activities;
	}

	@Override
	public void deauthorize(String accessToken) {
		postForm(OAUTH + "/deauthorize", "access_token", accessToken);
	}

	@Override
	public boolean sample() {
		return false;
	}

	private static Tokens tokens(JsonNode body, Athlete athlete) {
		return new Tokens(
				body.path("access_token").asString(),
				body.path("refresh_token").asString(),
				Instant.ofEpochSecond(body.path("expires_at").asLong()),
				athlete);
	}

	private JsonNode postForm(String url, String... form) {
		return send(HttpRequest.newBuilder(URI.create(url))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(query(form))));
	}

	private JsonNode send(HttpRequest.Builder request) {
		HttpResponse<String> response;
		try {
			response = http.send(request.timeout(TIMEOUT).header("Accept", "application/json").build(),
					HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new StravaException("Strava is not reachable right now.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new StravaException("Interrupted while calling Strava.", e);
		}
		int status = response.statusCode();
		// The token endpoint answers 400 for a used-up code or a revoked refresh token.
		if (status == 401 || (status == 400 && response.uri().getPath().startsWith("/oauth/"))) {
			throw new StravaException.Unauthorized("Strava did not accept the authorization (" + status + ").");
		}
		if (status == 429) throw new StravaException.RateLimited();
		if (status >= 400) throw new StravaException("Strava answered " + status + ".");
		try {
			return json.readTree(response.body());
		} catch (JacksonException e) {
			throw new StravaException("Strava sent an unreadable response.", e);
		}
	}

	/** {@code key=value&...}, URL-encoded, from alternating keys and values. */
	private static String query(String... keysAndValues) {
		var params = new LinkedHashMap<String, String>();
		for (int i = 0; i < keysAndValues.length; i += 2) params.put(keysAndValues[i], keysAndValues[i + 1]);
		return params.entrySet().stream()
				.map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
				.collect(Collectors.joining("&"));
	}
}
