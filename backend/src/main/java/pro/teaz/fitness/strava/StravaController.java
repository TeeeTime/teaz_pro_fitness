package pro.teaz.fitness.strava;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pro.teaz.fitness.FitnessProperties;
import pro.teaz.fitness.security.CurrentUser;

/**
 * Linking the user's Strava account. Strava redirects the browser back to
 * the frontend ({@code /strava/callback}), which hands code and state to
 * {@code POST /api/strava/callback} with the user's token; so every endpoint
 * here, like everywhere else, needs a logged-in user.
 */
@RestController
@RequestMapping("/api/strava")
class StravaController {

	/**
	 * @param sample    made-up runs, no real Strava behind it (local dev)
	 * @param syncedAt  null until the first sync succeeded
	 * @param syncError why the last sync failed, null if it didn't
	 */
	record Status(boolean connected, boolean sample, String athleteName, OffsetDateTime connectedAt,
			OffsetDateTime syncedAt, boolean syncing, String syncError) {
	}

	record Authorization(String url) {
	}

	record Callback(String code, String state, String scope) {
	}

	private final StravaApi strava;
	private final StravaSync sync;
	private final StravaConnections connections;
	private final OAuthStates states;
	private final CurrentUser currentUser;
	private final FitnessProperties properties;

	StravaController(StravaApi strava, StravaSync sync, StravaConnections connections, OAuthStates states,
			CurrentUser currentUser, FitnessProperties properties) {
		this.strava = strava;
		this.sync = sync;
		this.connections = connections;
		this.states = states;
		this.currentUser = currentUser;
		this.properties = properties;
	}

	@GetMapping("/connection")
	Status connection() {
		return status(currentUser.id());
	}

	/** Starts linking: the URL to send the browser to. */
	@PostMapping("/connect")
	Authorization connect() {
		return new Authorization(strava.authorizeUrl(states.create(currentUser.id())));
	}

	/** Finishes linking with what Strava appended to the redirect, then runs the first sync. */
	@PostMapping("/callback")
	Status callback(@RequestBody Callback callback) {
		String user = currentUser.id();
		if (!states.consume(callback.state(), user)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"This Strava authorization expired or was started in another session. Please connect again.");
		}
		if (!grantsActivities(callback.scope())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Fitness needs to read your activities. Please connect again and leave activity access checked.");
		}
		if (callback.code() == null || callback.code().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Strava sent no authorization code.");
		}
		StravaApi.Tokens tokens = strava.exchange(callback.code());
		connections.userOfAthlete(tokens.athlete().id())
				.filter(other -> !other.equals(user))
				.ifPresent(other -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT,
							"This Strava account is already linked to another Teaz user.");
				});
		sync.link(user, tokens);
		return status(user);
	}

	/** Syncs now, e.g. right after a run. A failure other than revoked access shows up in {@code syncError}. */
	@PostMapping("/sync")
	Status syncNow() {
		String user = currentUser.id();
		try {
			sync.sync(user);
		} catch (StravaException.Unauthorized e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Strava no longer grants access, so your runs were removed here. Connect again to sync them.");
		} catch (StravaException e) {
			// Stored on the link, part of the status.
		}
		return status(user);
	}

	/** Unlinks: revokes access on Strava and deletes the user's runs here. */
	@DeleteMapping("/connection")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void disconnect() {
		sync.unlink(currentUser.id());
	}

	private Status status(String user) {
		return connections.find(user)
				.map(c -> new Status(true, strava.sample(), c.athleteName(), local(c.connectedAt()), local(c.syncedAt()),
						sync.syncing(user), c.syncError()))
				.orElseGet(() -> new Status(false, strava.sample(), null, null, null, false, null));
	}

	private OffsetDateTime local(Instant instant) {
		return instant == null ? null : instant.atZone(properties.timezone()).toOffsetDateTime();
	}

	/** activity:read (activities others may see) or activity:read_all; the athlete can uncheck them. */
	private static boolean grantsActivities(String scope) {
		return scope != null && Arrays.stream(scope.split(","))
				.map(String::trim)
				.anyMatch(s -> s.equals("activity:read") || s.equals("activity:read_all"));
	}
}
