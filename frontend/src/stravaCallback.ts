const CALLBACK_PATH = '/strava/callback'
const STORAGE_KEY = 'fitness.stravaCallback'

/**
 * Strava sends the browser back to /strava/callback?code=…&state=…&scope=…
 * (or ?error=access_denied). Called before the login: keycloak-js would pass
 * that whole URL to Keycloak as its redirect_uri, which Keycloak rejects. So
 * keep the parameters for the view and continue at a clean /.
 */
export function stashStravaCallback() {
  if (window.location.pathname !== CALLBACK_PATH) return
  sessionStorage.setItem(STORAGE_KEY, window.location.search)
  window.history.replaceState(null, '', '/')
}

/** The parameters stashed by stashStravaCallback(), once. */
export function takeStravaCallback(): URLSearchParams | null {
  const search = sessionStorage.getItem(STORAGE_KEY)
  if (search === null) return null
  sessionStorage.removeItem(STORAGE_KEY)
  return new URLSearchParams(search)
}
