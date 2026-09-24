import { demoAccounts, type AuthUser } from './fixtures'
export type { AuthUser } from './fixtures'

/** 本地演示服务，只模拟微信绑定和网络异常，不模拟短信认证。 */
export function createMockAuth(options: { delay?: number; now?: () => number } = {}) {
  const now = options.now ?? Date.now
  let bindingTicket: string | undefined
  let nextFailure: 'network' | 'cancel' | null = null
  const pause = () => new Promise<void>(resolve => setTimeout(resolve, options.delay ?? 50))
  return {
    async wechatLogin() {
      await pause()
      if (nextFailure) {
        const failure = nextFailure; nextFailure = null
        throw new Error(failure === 'cancel' ? '已取消微信授权，可重新登录' : '网络连接失败，请重试')
      }
      bindingTicket = `mock-binding-${now()}`
      return bindingTicket
    },
    async bindCredentials(username: string, password: string): Promise<AuthUser> {
      await pause()
      if (!bindingTicket) throw new Error('请返回重新进行微信登录')
      if (!username.trim() || !password) throw new Error('请输入账号和密码')
      bindingTicket = undefined
      return JSON.parse(JSON.stringify({ ...demoAccounts[0], id: 'mock-user-1', platformGrants: [], admin: false, mustChangePassword: false })) as AuthUser
    },
    failNext(failure: 'network' | 'cancel') { nextFailure = failure },
    reset() { bindingTicket = undefined; nextFailure = null },
  }
}
