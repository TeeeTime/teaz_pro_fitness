package pro.teaz.fitness;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pro.teaz.fitness.strava.StravaException;

/** Failed Strava calls as problem details with a readable detail. */
@RestControllerAdvice
public class ApiErrors {

	/** A code from the redirect that Strava doesn't accept (used up, or the athlete revoked it already). */
	@ExceptionHandler(StravaException.Unauthorized.class)
	public ProblemDetail unauthorized(StravaException.Unauthorized e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Strava did not accept the authorization. Please connect again.");
	}

	@ExceptionHandler(StravaException.RateLimited.class)
	public ProblemDetail rateLimited(StravaException.RateLimited e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
	}

	@ExceptionHandler(StravaException.class)
	public ProblemDetail strava(StravaException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
	}
}
