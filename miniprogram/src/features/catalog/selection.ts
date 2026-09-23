import { getCurrentInstance } from 'vue'
export function useSelectionResult() {
  const page = getCurrentInstance()?.proxy as unknown as { getOpenerEventChannel(): { emit(name:string,data:unknown):void } }
  return (value:unknown) => { page?.getOpenerEventChannel()?.emit('selected',value); uni.navigateBack() }
}
