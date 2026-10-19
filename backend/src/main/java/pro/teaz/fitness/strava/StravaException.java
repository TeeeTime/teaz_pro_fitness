package pro.teaz.fitness.strava;

/** A failed call to Strava. The message is meant for the user. */
public class StravaException extends RuntimeException {

	public StravaException(String message) {
		super(message);
	}

	public StravaException(String message, Throwable cause) {
		super(message, cause);
	}

	/** Strava rejected the token or code: the athlete revoked access, or the code is used up. */
	public static class Unauthorized extends StravaException {

		public Unauthorized(String message) {
			super(message);
		}
	}

	/** Strava's rate limit for the app (all users together) is used up for now. */
	public static class RateLimited extends StravaException {

		public RateLimited() {
			super("Strava's request limit is reached. Runs are synced again in a few minutes.");
		}
	}
}
