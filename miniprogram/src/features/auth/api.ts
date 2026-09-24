import { request } from '../../services/request'
import type { Identity, Session, SmsChallenge, SmsPurpose, VerifySmsInput, WechatExchange } from './model'
export const loginWithPassword = (username: string, password: string) => request<Session>({ path: '/admin/auth/login', method: 'POST', data: { username, password }, workspace: false })
export const changePassword = (currentPassword: string, newPassword: string) => request<null>({ path: '/identity/password', method: 'POST', data: { currentPassword, newPassword }, workspace: false })
export const exchangeWechat = (code: string) => request<WechatExchange>({ path: '/mini/auth/wechat', method: 'POST', data: { code }, workspace: false })
export function requestSms(phone: string, purpose: SmsPurpose, bindingToken?: string) {
  return request<SmsChallenge>({ path: '/mini/auth/sms', method: 'POST', workspace: false, data: { phone, purpose, ...(bindingToken ? { bindingToken } : {}) } })
}
export const verifySms = (input: VerifySmsInput) => request<Session>({ path: '/mini/auth/verify', method: 'POST', data: input, workspace: false })
export const fetchIdentity = () => request<Identity>({ path: '/identity/me', workspace: false })
export const logout = () => request<null>({ path: '/identity/logout', method: 'POST', workspace: false })
