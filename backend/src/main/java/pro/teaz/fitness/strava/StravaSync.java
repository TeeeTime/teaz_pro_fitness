package pro.teaz.fitness.strava;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import pro.teaz.fitness.FitnessProperties;
import pro.teaz.fitness.run.Runs;
import pro.teaz.fitness.strava.StravaConnections.Connection;

/**
 * Links users to Strava and keeps their runs current.
 *
 * The first sync after linking fetches {@code backfill-weeks} of activities;
 * every later one fetches again from {@code resync-window} before the
 * previous sync and replaces what's stored from then on, so runs edited or
 * deleted on Strava in that window change here too. Every connected user is
 * synced each {@code sync-interval} in the background, so other apps' tiles
 * stay current without anyone opening Fitness. Only runs are kept (Run,
 * TrailRun, VirtualRun), rides and the rest are skipped.
 *
 * When Strava rejects a user's tokens (access revoked in their Strava
 * settings), the link and their stored runs are deleted, as the Strava API
 * agreement asks for.
 */
@Service
public class StravaSync {

	private static final Logger log = LoggerFactory.getLogger(StravaSync.class);

	static final Set<String> RUN_TYPES = Set.of("Run", "TrailRun", "VirtualRun");
	// Refresh tokens that expire within this, so a sync never starts with one about to run out.
	private static final Duration REFRESH_BEFORE = Duration.ofMinutes(5);
	// 10,000 activities per sync window; more means something is off, and a partial list must not replace runs.
	private static final int MAX_PAGES = 50;

	private final StravaApi strava;
	private final StravaConnections connections;
	private final Runs runs;
	private final FitnessProperties properties;
	private final Clock clock;
	private final Set<String> running = ConcurrentHashMap.newKeySet();
	// Last background attempt per user, so a failing sync isn't retried every minute.
	private final Map<String, Instant> attempted = new ConcurrentHashMap<>();

	StravaSync(StravaApi strava, StravaConnections connections, Runs runs, FitnessProperties properties, Clock clock) {
		this.strava = strava;
		this.connections = connections;
		this.runs = runs;
		this.properties = properties;
		this.clock = clock;
	}

	/** Links the user to the athlete the tokens belong to and runs the first sync (a failure is kept on the link). */
	public void link(String userId, StravaApi.Tokens tokens) {
		runs.deleteAll(userId);
		connections.connect(userId, tokens, clock.instant());
		try {
			sync(userId);
		} catch (StravaException e) {
			log.warn("First sync for {} failed: {}", userId, e.getMessage());
		}
	}

	/** Revokes the app's access on Strava (best effort) and deletes the link and the user's runs. */
	public void unlink(String userId) {
		connections.find(userId).ifPresent(connection -> {
			try {
				strava.deauthorize(accessToken(connection));
			} catch (StravaException e) {
				log.info("Deauthorizing {} on Strava failed, deleting the link anyway: {}", userId, e.getMessage());
			}
		});
		forget(userId);
	}

	public boolean syncing(String userId) {
		return running.contains(userId);
	}

	/**
	 * Fetches the user's runs from Strava now. Does nothing without a link or
	 * while a sync for the user is already running. A failure is stored on the
	 * link and thrown; {@link StravaException.Unauthorized} means the link is gone.
	 */
	public void sync(String userId) {
		if (!running.add(userId)) return;
		try {
			connections.find(userId).ifPresent(this::sync);
		} finally {
			running.remove(userId);
		}
	}

	/** Syncs each user whose last sync is older than {@code sync-interval}; stops at Strava's rate limit. */
	@Scheduled(fixedDelayString = "${fitness.strava.sync-check:60s}", initialDelayString = "${fitness.strava.sync-check:60s}")
	void syncDue() {
		Instant now = clock.instant();
		Duration interval = properties.strava().syncInterval();
		for (Connection connection : connections.all()) {
			Instant last = attempted.getOrDefault(connection.userId(), connection.syncedAt());
			if (last != null && last.plus(interval).isAfter(now)) continue;
			attempted.put(connection.userId(), now);
			try {
				sync(connection.userId());
			} catch (StravaException.RateLimited e) {
				log.info("Strava rate limit reached, continuing with the next round.");
				return;
			} catch (StravaException e) {
				log.warn("Sync for {} failed: {}", connection.userId(), e.getMessage());
			}
		}
	}

	private void sync(Connection connection) {
		String userId = connection.userId();
		Instant now = clock.instant();
		Instant from = connection.syncedAt() == null
				? backfillStart()
				: connection.syncedAt().minus(properties.strava().resyncWindow());
		try {
			String token = accessToken(connection);
			List<StravaApi.Activity> fetched = new ArrayList<>();
			for (int page = 1; ; page++) {
				if (page > MAX_PAGES) throw new StravaException("Strava listed more activities than expected.");
				List<StravaApi.Activity> batch = strava.activities(token, from, page, StravaApi.MAX_PER_PAGE);
				batch.stream().filter(a -> RUN_TYPES.contains(a.sportType())).forEach(fetched::add);
				if (batch.size() < StravaApi.MAX_PER_PAGE) break;
			}
			runs.replaceFrom(userId, from, fetched);
			connections.markSynced(userId, now);
		} catch (StravaException.Unauthorized e) {
			log.info("Strava rejected the tokens of {} (access revoked?), deleting the link and its runs.", userId);
			forget(userId);
			throw e;
		} catch (StravaException e) {
			connections.markFailed(userId, e.getMessage());
			throw e;
		}
	}

	/** The stored access token, or a fresh one if it's (about to be) expired. */
	private String accessToken(Connection connection) {
		if (connection.expiresAt().isAfter(clock.instant().plus(REFRESH_BEFORE))) return connection.accessToken();
		StravaApi.Tokens tokens = strava.refresh(connection.refreshToken());
		connections.updateTokens(connection.userId(), tokens);
		return tokens.accessToken();
	}

	/** Monday {@code backfill-weeks} ago, so the oldest week is complete. */
	private Instant backfillStart() {
		return LocalDate.now(clock.withZone(properties.timezone()))
				.minusWeeks(properties.strava().backfillWeeks())
				.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
				.atStartOfDay(properties.timezone())
				.toInstant();
	}

	private void forget(String userId) {
		runs.deleteAll(userId);
		connections.delete(userId);
		attempted.remove(userId);
	}
}
