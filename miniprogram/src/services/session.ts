let token: string | null = null
let invalidationHandler: (() => void) | null = null
let authorizationHandler: (() => void) | null = null
export const sessionToken = {
  get: () => token,
  set(value: string) { token = value },
  clear() { token = null },
}
export function setSessionInvalidationHandler(handler: (() => void) | null) { invalidationHandler = handler }
export function notifySessionInvalidated() { invalidationHandler?.() }
export function setAuthorizationChangeHandler(handler: () => void) { authorizationHandler = handler }
export function notifyAuthorizationChanged() { authorizationHandler?.() }
