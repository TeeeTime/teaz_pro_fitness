package pro.teaz.fitness.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class SecurityConfigTests {

	@Autowired
	MockMvc mvc;

	@Test
	void rejectsRequestWithoutToken() throws Exception {
		mvc.perform(get("/api/strava/connection")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/runs/latest")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/runs/weekly")).andExpect(status().isUnauthorized());
	}

	@Test
	void anyLoggedInUserMayUseIt() throws Exception {
		mvc.perform(get("/api/strava/connection").with(jwt())).andExpect(status().isOk());
		mvc.perform(get("/api/runs").with(jwt())).andExpect(status().isOk());
	}

	@Test
	void onlyTileEndpointsAreSharedCrossOrigin() throws Exception {
		for (String path : new String[] {"/api/runs/latest", "/api/runs/weekly"}) {
			mvc.perform(options(path)
					.header("Origin", "https://portal.teaz.pro")
					.header("Access-Control-Request-Method", "GET")
					.header("Access-Control-Request-Headers", "Authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://portal.teaz.pro"));
		}

		mvc.perform(options("/api/runs")
				.header("Origin", "https://portal.teaz.pro")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		mvc.perform(options("/api/runs/latest")
				.header("Origin", "https://evil.example")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}
}
