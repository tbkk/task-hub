import { findDemoAccount, type AuthUser } from './fixtures'
export type { AuthUser } from './fixtures'

export const validPhone = (phone: string) => /^1[3-9]\d{9}$/.test(phone)

/** 本地演示服务，不向后端发放或传递任何认证凭据。 */
export function createMockAuth(options: { delay?: number; now?: () => number; code?: string } = {}) {
  const now = options.now ?? Date.now
  const codes = new Map<string, { expiresAt: number; retryAt: number }>()
  let bindingTicket: string | undefined
  let nextFailure: 'network' | 'cancel' | null = null
  const pause = () => new Promise<void>(resolve => setTimeout(resolve, options.delay ?? 450))
  function checkPhone(phone: string) {
    if (!validPhone(phone)) throw new Error('请输入正确的 11 位手机号')
  }
  return {
    async wechatLogin() {
      await pause()
      if (nextFailure) {
        const failure = nextFailure
        nextFailure = null
        throw new Error(failure === 'cancel' ? '已取消微信授权，可重新登录' : '网络连接失败，请重试')
      }
      bindingTicket = `mock-binding-${now()}`
      return bindingTicket
    },
    async sendCode(phone: string) {
      checkPhone(phone)
      if (nextFailure) { nextFailure = null; throw new Error('网络连接失败，请重试') }
      const previous = codes.get(phone)
      if (previous && previous.retryAt > now()) throw new Error('验证码已发送，请稍后再试')
      const result = { challengeId: `mock-challenge-${now()}`, expiresAt: now() + 300000, retryAt: now() + 60000 }
      codes.set(phone, result)
      await pause()
      return result
    },
    async verify(phone: string, code: string, ticket?: string): Promise<AuthUser> {
      checkPhone(phone)
      await pause()
      if (ticket !== undefined && (!bindingTicket || ticket !== bindingTicket)) {
        throw new Error('请返回重新进行微信登录')
      }
      const challenge = codes.get(phone)
      if (!challenge) throw new Error('请先获取验证码')
      if (challenge.expiresAt <= now()) throw new Error('验证码已过期，请重新获取')
      if (code !== (options.code ?? '123456')) throw new Error('验证码错误，请重新输入')
      if (phone === '13800000002') throw new Error('账号已停用，请联系管理员')
      const user = findDemoAccount(phone)
      if (!user) throw new Error('当前账号未开通使用权限，请联系管理员')
      codes.delete(phone)
      bindingTicket = undefined
      return user
    },
    failNext(failure: 'network' | 'cancel') { nextFailure = failure },
    retryAt(phone: string) { return codes.get(phone)?.retryAt ?? 0 },
    reset() {
      codes.clear()
      bindingTicket = undefined
      nextFailure = null
    },
  }
}
