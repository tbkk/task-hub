import { reactive } from 'vue'
import { WorkspaceState } from './model'
export const workspace = reactive(new WorkspaceState())
export function rememberWorkspace() {
  if (workspace.userId && workspace.activeRole) {
    uni.setStorageSync(`workspace:${workspace.userId}`, workspace.activeRole)
  }
}
