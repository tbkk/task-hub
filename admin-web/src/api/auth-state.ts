let token: string | null = null
let expired: () => void = () => {}
export function setAccessToken(value: string) { token = value }
export function getAccessToken() { return token }
export function clearAccessToken() { token = null }
export function onSessionExpired(handler: () => void) { expired = handler }
export function invalidateSession(expectedToken: string | null) {
  if (token !== expectedToken) return
  clearAccessToken()
  expired()
}
