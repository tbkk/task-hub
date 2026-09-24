import type { Grant } from '../workspace/model'
export type PlatformCapability = 'EMPLOYEE_MANAGE' | 'MASTERDATA_MANAGE' | 'RULE_MANAGE' | 'INTEGRATION_MANAGE' | 'REPORT_VIEW' | 'REPORT_EXPORT' | 'AUDIT_VIEW'
export interface PlatformGrant { capability: PlatformCapability; scope: 'WAREHOUSES' | 'ALL'; warehouseIds: string[] }
export interface Identity { id: string; name: string; verifiedPhone: string | null; grants: Grant[]; platformGrants: PlatformGrant[]; admin: boolean; mustChangePassword: boolean }
export interface Session { token: string; expiresAt: string; user: Identity }
export type WechatExchange = { status: 'AUTHENTICATED'; session: Session } | { status: 'CREDENTIALS_REQUIRED'; bindingToken: string; expiresAt: string }
