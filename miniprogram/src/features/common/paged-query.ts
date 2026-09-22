import type { Page } from '../../services/types'
export class PagedQuery<T extends { id: string }> {
  items: T[] = []
  page = 0
  pageSize = 20
  total = 0
  loading = false
  loadingMore = false
  error = ''
  get hasMore() { return this.items.length < this.total }
  replace(result: Page<T>) { this.items = [...result.items]; this.applyPage(result) }
  append(result: Page<T>) {
    const merged = new Map(this.items.map(item => [item.id, item]))
    result.items.forEach(item => merged.set(item.id, item))
    this.items = [...merged.values()]
    this.applyPage(result)
  }
  fail(message: string, loadingMore = false) {
    this.error = message
    this.loading = false
    this.loadingMore = false
    if (!loadingMore) this.items = []
  }
  reset() {
    this.items = []
    this.page = 0
    this.pageSize = 20
    this.total = 0
    this.loading = false
    this.loadingMore = false
    this.error = ''
  }
  private applyPage(result: Page<T>) {
    this.page = result.page
    this.pageSize = result.pageSize
    this.total = result.total
    this.loading = false
    this.loadingMore = false
    this.error = ''
  }
}
