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

function preferredWorkspace(userId: string) { return uni.getStorageSync(`workspace:${userId}`) as string | undefined }

export function applyIdentity(user: Identity) {
  currentUser.value = user
  workspace.refresh(user.id, user.grants, preferredWorkspace(user.id))
  rememberWorkspace()
}

export function acceptSession(session: Session, isActive: () => boolean = () => true) {
  if (!isActive()) return false
  sessionToken.set(session.token)
  applyIdentity(session.user)
  pendingBinding.value = null
  pendingChallenge.value = null
  invalidationRedirected = false
  return true
}

export function clearLocalSession() {
  sessionToken.clear()
  currentUser.value = null
  pendingBinding.value = null
  pendingChallenge.value = null
  refreshPromise = null
  workspace.reset()
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
