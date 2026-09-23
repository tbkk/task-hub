let token: string | null = null
let invalidationHandler: (() => void) | null = null
let authorizationHandler: (() => void) | null = null

export const SESSION_TOKEN_STORAGE_KEY = 'task-hub:session-token'
export interface StoredSessionToken { token: string; expiresAt: string }

type StorageRuntime = {
  getStorageSync?: (key: string) => unknown
  setStorageSync?: (key: string, value: unknown) => void
  removeStorageSync?: (key: string) => void
}

function storageRuntime() {
  return (globalThis as typeof globalThis & { uni?: StorageRuntime }).uni
}

function removeStoredToken() {
  const runtime = storageRuntime()
  try {
    if (runtime?.removeStorageSync) runtime.removeStorageSync(SESSION_TOKEN_STORAGE_KEY)
    else runtime?.setStorageSync?.(SESSION_TOKEN_STORAGE_KEY, '')
  } catch { /* 本地存储不可用时仍清除内存会话。 */ }
}

function persistStoredToken(value: StoredSessionToken) {
  try { storageRuntime()?.setStorageSync?.(SESSION_TOKEN_STORAGE_KEY, JSON.stringify(value)) }
  catch { /* 本地存储不可用时仍保持当前会话可用。 */ }
}

function parseStoredToken(value: unknown): StoredSessionToken | null {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    if (!parsed || typeof parsed !== 'object') return null
    const candidate = parsed as Partial<StoredSessionToken>
    if (typeof candidate.token !== 'string' || !candidate.token || typeof candidate.expiresAt !== 'string') return null
    return { token: candidate.token, expiresAt: candidate.expiresAt }
  } catch { return null }
}

export const sessionToken = {
  get: () => token,
  set(value: string, expiresAt?: string) {
    token = value
    if (expiresAt !== undefined) persistStoredToken({ token: value, expiresAt })
    else removeStoredToken()
  },
  restore(now = Date.now()): StoredSessionToken | null {
    let raw: unknown
    try { raw = storageRuntime()?.getStorageSync?.(SESSION_TOKEN_STORAGE_KEY) }
    catch { raw = null }
    const stored = parseStoredToken(raw)
    const expiry = stored ? Date.parse(stored.expiresAt) : NaN
    if (!stored || !Number.isFinite(expiry) || expiry <= now) {
      token = null
      removeStoredToken()
      return null
    }
    token = stored.token
    return stored
  },
  clear() { token = null; removeStoredToken() },
}
export function setSessionInvalidationHandler(handler: (() => void) | null) { invalidationHandler = handler }
export function notifySessionInvalidated() { invalidationHandler?.() }
export function setAuthorizationChangeHandler(handler: () => void) { authorizationHandler = handler }
export function notifyAuthorizationChanged() { authorizationHandler?.() }
