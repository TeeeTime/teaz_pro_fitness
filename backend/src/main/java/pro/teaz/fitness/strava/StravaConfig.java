package pro.teaz.fitness.strava;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

import pro.teaz.fitness.FitnessProperties;

/** strava.com when a client id is configured, made-up sample runs otherwise. */
@Configuration
class StravaConfig {

	private static final Logger log = LoggerFactory.getLogger(StravaConfig.class);

	@Bean
	StravaApi stravaApi(FitnessProperties properties, Clock clock, JsonMapper json) {
		FitnessProperties.Strava strava = properties.strava();
		if (strava.useSample()) {
			log.info("No fitness.strava.client-id set: using made-up sample runs instead of Strava.");
			return new SampleStravaApi(clock, properties.timezone(), strava.redirectUri());
		}
		return new HttpStravaApi(strava, json);
	}
}
