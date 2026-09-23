export type EntityId = string
export interface ApiEnvelope<T> { code: number; message: string; data: T | null }
export interface ApiErrorData {
  fieldErrors?: Record<string, string>
  blockers?: Array<{ code: string; message: string }>
  currentVersion?: number
}
export interface Page<T> { items: T[]; total: number; page: number; pageSize: number }
