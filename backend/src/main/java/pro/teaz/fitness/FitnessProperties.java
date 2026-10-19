package pro.teaz.fitness;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code fitness.*}, see application.properties.
 *
 * @param timezone time zone of every time in API responses and of "today" for the current week
 * @param strava   the Strava API app and how often runs are synced
 * @param cors     other Teaz apps allowed to read the tile endpoints from the browser
 */
@ConfigurationProperties("fitness")
public record FitnessProperties(
		@DefaultValue("Europe/Berlin") ZoneId timezone,
		@DefaultValue Strava strava,
		@DefaultValue Cors cors) {

	/**
	 * @param clientId      Strava API app (strava.com/settings/api); unset or blank = made-up sample data
	 * @param clientSecret  that app's secret
	 * @param redirectUri   where Strava sends the user back to after authorizing: the frontend's
	 *                      {@code /strava/callback}; its host must be the app's "Authorization Callback Domain"
	 * @param syncInterval  how often each connected user's runs are fetched again
	 * @param backfillWeeks how far back the first sync after connecting goes
	 * @param resyncWindow  every sync fetches again from this long before the previous one, so edited
	 *                      and deleted activities are picked up
	 */
	public record Strava(
			String clientId,
			String clientSecret,
			@DefaultValue("http://localhost:5173/strava/callback") String redirectUri,
			@DefaultValue("15m") Duration syncInterval,
			@DefaultValue("52") int backfillWeeks,
			@DefaultValue("14d") Duration resyncWindow) {

		public boolean useSample() {
			return clientId == null || clientId.isBlank();
		}
	}

	public record Cors(@DefaultValue List<String> allowedOrigins) {
	}
}
