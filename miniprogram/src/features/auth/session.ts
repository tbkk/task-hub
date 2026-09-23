import { shallowRef } from 'vue'
import { exchangeWechat, fetchIdentity, logout, requestSms, verifySms } from './api'
import { createMockAuth } from './mock'
import type { Identity, Session, SmsPurpose } from './model'
import { workspace, rememberWorkspace } from '../workspace/store'
import { sessionToken, setSessionInvalidationHandler, setAuthorizationChangeHandler } from '../../services/session'

const env = import.meta.env ?? {}
export const mockEnabled = env.VITE_AUTH_MOCK === 'true'
export const mockCode = env.VITE_AUTH_MOCK_CODE || ''
if (mockEnabled && !mockCode) throw new Error('演示模式必须配置 VITE_AUTH_MOCK_CODE')
const mock = createMockAuth({ code: mockCode || undefined })
export const currentUser = shallowRef<Identity | null>(null)
export const pendingBinding = shallowRef<string | null>(null)
const pendingChallenge = shallowRef<{ id: string; phone: string; purpose: SmsPurpose; retryAt: number } | null>(null)
let refreshPromise: Promise<Identity> | null = null
let invalidationRedirected = false

export const AUTH_SESSION_STORAGE_KEY = 'task-hub:session'
type StorageRuntime = {
  getStorageSync?: (key: string) => unknown
  setStorageSync?: (key: string, value: unknown) => void
  removeStorageSync?: (key: string) => void
}

function storageRuntime() {
  return (globalThis as typeof globalThis & { uni?: StorageRuntime }).uni
}

function clearStoredSession() {
  const runtime = storageRuntime()
  try {
    if (runtime?.removeStorageSync) runtime.removeStorageSync(AUTH_SESSION_STORAGE_KEY)
    else runtime?.setStorageSync?.(AUTH_SESSION_STORAGE_KEY, '')
  } catch { /* 本地存储不可用时仍清除内存会话。 */ }
}

function persistStoredSession(session: Session) {
  try { storageRuntime()?.setStorageSync?.(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session)) }
  catch { /* 本地存储不可用时仍保持当前会话可用。 */ }
}

function isIdentity(value: unknown): value is Identity {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<Identity>
  return typeof candidate.id === 'string' && typeof candidate.name === 'string' &&
    (candidate.verifiedPhone === null || typeof candidate.verifiedPhone === 'string') &&
    Array.isArray(candidate.grants) && Array.isArray(candidate.platformGrants) &&
    typeof candidate.admin === 'boolean' && typeof candidate.mustChangePassword === 'boolean'
}

function readStoredSession(): Session | null {
  try {
    const raw = storageRuntime()?.getStorageSync?.(AUTH_SESSION_STORAGE_KEY)
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!parsed || typeof parsed !== 'object') return null
    const candidate = parsed as Partial<Session>
    if (typeof candidate.token !== 'string' || !candidate.token || typeof candidate.expiresAt !== 'string' || !isIdentity(candidate.user)) return null
    return { token: candidate.token, expiresAt: candidate.expiresAt, user: candidate.user }
  } catch { return null }
}

function preferredWorkspace(userId: string) { return uni.getStorageSync(`workspace:${userId}`) as string | undefined }

export function applyIdentity(user: Identity) {
  currentUser.value = user
  workspace.refresh(user.id, user.grants, preferredWorkspace(user.id))
  rememberWorkspace()
}

export function acceptSession(session: Session, isActive: () => boolean = () => true) {
  if (!isActive()) return false
  sessionToken.set(session.token, session.expiresAt)
  persistStoredSession(session)
  applyIdentity(session.user)
  pendingBinding.value = null
  pendingChallenge.value = null
  invalidationRedirected = false
  return true
}

export function clearLocalSession() {
  sessionToken.clear()
  clearStoredSession()
  currentUser.value = null
  pendingBinding.value = null
  pendingChallenge.value = null
  refreshPromise = null
  workspace.reset()
}

export function restoreSession(now = Date.now()) {
  const stored = readStoredSession()
  const restoredToken = sessionToken.restore(now)
  if (!stored || !restoredToken || stored.token !== restoredToken.token || stored.expiresAt !== restoredToken.expiresAt || Date.parse(stored.expiresAt) <= now) {
    clearLocalSession()
    return false
  }
  sessionToken.set(stored.token, stored.expiresAt)
  applyIdentity(stored.user)
  pendingBinding.value = null
  pendingChallenge.value = null
  refreshPromise = null
  invalidationRedirected = false
  return true
}

function invalidateSession() {
  clearLocalSession()
  if (invalidationRedirected) return
  invalidationRedirected = true
  uni.reLaunch({ url: '/pages/login/index' })
}
setSessionInvalidationHandler(invalidateSession)
setAuthorizationChangeHandler(() => {
  const previousRole = workspace.activeRole
  void refreshIdentity().then(() => {
    if (!workspace.activeRole) uni.reLaunch({ url: '/pages/workspace/index' })
    else if (previousRole !== workspace.activeRole) uni.reLaunch({ url: `/subpackages/${workspace.activeRole}/index` })
  }).catch(() => { /* 原请求展示失败，网络恢复后页面再次核对授权。 */ })
})

function wechatCode() {
  return new Promise<string>((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success(result) { result.code ? resolve(result.code) : reject(new Error('微信登录未返回有效凭证')) },
      fail(error) { reject(new Error(error.errMsg || '微信登录失败，请重试')) },
    })
  })
}

export async function startWechatLogin(isActive: () => boolean = () => true): Promise<'AUTHENTICATED' | 'PHONE_REQUIRED'> {
  if (mockEnabled) {
    const bindingToken = await mock.wechatLogin()
    if (!isActive()) throw new Error('登录已取消，请重新登录')
    pendingBinding.value = bindingToken
    return 'PHONE_REQUIRED'
  }
  const result = await exchangeWechat(await wechatCode())
  if (result.status === 'AUTHENTICATED') {
    if (!acceptSession(result.session, isActive)) throw new Error('登录已取消，请重新登录')
    return result.status
  }
  if (!isActive()) throw new Error('登录已取消，请重新登录')
  pendingBinding.value = result.bindingToken
  return result.status
}

export async function sendCode(phone: string, purpose: SmsPurpose) {
  if (purpose === 'BIND' && !pendingBinding.value) throw new Error('请返回重新进行微信登录')
  if (mockEnabled) {
    const result = await mock.sendCode(phone)
    pendingChallenge.value = { id: result.challengeId, phone, purpose, retryAt: result.retryAt }
    return result
  }
  const result = await requestSms(phone, purpose, purpose === 'BIND' ? pendingBinding.value ?? undefined : undefined)
  pendingChallenge.value = { id: result.challengeId, phone, purpose, retryAt: Date.now() + result.retryAfterSeconds * 1000 }
  return result
}

export async function signIn(phone: string, code: string, purpose: SmsPurpose, isActive: () => boolean = () => true) {
  const challenge = pendingChallenge.value
  if (!challenge || challenge.phone !== phone || challenge.purpose !== purpose) throw new Error('请先获取验证码')
  let session: Session
  if (mockEnabled) {
    const user = await mock.verify(phone, code, purpose === 'BIND' ? pendingBinding.value ?? undefined : undefined)
    session = { token: `mock-session-${user.id}`, expiresAt: new Date(Date.now() + 8 * 3600000).toISOString(), user }
  } else {
    session = await verifySms({ challengeId: challenge.id, phone, code, ...(purpose === 'BIND' && pendingBinding.value ? { bindingToken: pendingBinding.value } : {}) })
  }
  if (!acceptSession(session, isActive)) throw new Error('登录已取消，请重新获取验证码')
}

export async function refreshIdentity() {
  if (!currentUser.value || !sessionToken.get()) throw new Error('请重新登录')
  if (mockEnabled) return currentUser.value
  if (!refreshPromise) {
    const token = sessionToken.get()
    const pending = fetchIdentity().then(user => {
      if (sessionToken.get() !== token) throw new Error('会话已变化')
      applyIdentity(user)
      return user
    }).finally(() => { if (refreshPromise === pending) refreshPromise = null })
    refreshPromise = pending
  }
  return refreshPromise
}

export async function signOut() {
  const token = sessionToken.get()
  try { if (!mockEnabled && sessionToken.get()) await logout() }
  finally {
    if (sessionToken.get() && sessionToken.get() !== token) return
    const needsRedirect = !invalidationRedirected
    clearLocalSession()
    mock.reset()
    invalidationRedirected = true
    if (needsRedirect) uni.reLaunch({ url: '/pages/login/index' })
  }
}

export function getCodeRetryAt(phone: string) { return pendingChallenge.value?.phone === phone ? pendingChallenge.value.retryAt : 0 }
export function failNextMockRequest(failure: 'network' | 'cancel') {
  if (!mockEnabled) throw new Error('仅演示模式支持失败场景')
  mock.failNext(failure)
}
export function revokeMockRole(all = false) {
  if (!mockEnabled) return
  const user = currentUser.value
  if (!user) return
  applyIdentity({ ...user, grants: all ? [] : user.grants.filter(grant => grant.role !== workspace.activeRole) })
  uni.reLaunch({ url: '/pages/workspace/index' })
}
