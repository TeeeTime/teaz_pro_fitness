package pro.teaz.fitness.strava;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * The {@code state} of each started Strava authorization: a random value tied
 * to the Teaz user who started it, single use, valid for ten minutes. Kept in
 * memory, since an authorization takes seconds; after a restart the user just
 * clicks "Connect" again.
 */
@Component
class OAuthStates {

	private static final Duration VALID_FOR = Duration.ofMinutes(10);

	private record Pending(String userId, Instant createdAt) {
	}

	private final SecureRandom random = new SecureRandom();
	private final Map<String, Pending> pending = new ConcurrentHashMap<>();
	private final Clock clock;

	OAuthStates(Clock clock) {
		this.clock = clock;
	}

	String create(String userId) {
		Instant now = clock.instant();
		pending.values().removeIf(p -> p.createdAt().plus(VALID_FOR).isBefore(now));
		byte[] bytes = new byte[24];
		random.nextBytes(bytes);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		pending.put(state, new Pending(userId, now));
		return state;
	}

	/** True if the state was created for this user and is still valid; it can't be used again either way. */
	boolean consume(String state, String userId) {
		Pending p = state == null ? null : pending.remove(state);
		return p != null && p.userId().equals(userId) && !p.createdAt().plus(VALID_FOR).isBefore(clock.instant());
	}
}
