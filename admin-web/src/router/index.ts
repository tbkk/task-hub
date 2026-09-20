import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const moduleRoutes: RouteRecordRaw[] = [
  { path: '', redirect: '/account' },
  { path: 'account', name: 'account', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '平台账户', description: '平台身份验证、登录状态与账户信息' } },
  { path: 'people', name: 'people', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '人员与权限', description: '员工准入、角色及逐角色数据范围' } },
  { path: 'master-data/warehouses', name: 'warehouses', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '仓库资料', description: '仓库定义、归属与启停状态' } },
  { path: 'master-data/stops', name: 'stops', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '停靠点资料', description: '配送停靠点及可用状态' } },
  { path: 'master-data/vehicles', name: 'vehicles', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '车辆资料', description: '车辆档案、归属及接入绑定' } },
  { path: 'master-data/compartments', name: 'compartments', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '硬件格口', description: '车辆格口定义与同步状态' } },
  { path: 'rules', name: 'rules', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '规则配置', description: '营业时段、预约容量、限制和阈值' } },
  { path: 'integrations', name: 'integrations', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '既有第三方接入', description: '九识、微信与验证码接入参数' } },
  { path: 'reports', name: 'reports', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '管理报表', description: '跨周期统计、只读明细与导出' } },
  { path: 'audit', name: 'audit', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '审计与运行维护', description: '配置变更、系统日志和接入监控' } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', component: () => import('@/layouts/AdminLayout.vue'), children: moduleRoutes },
    { path: '/:pathMatch(.*)*', redirect: '/account' },
  ],
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title || '管理平台')} | Task Hub`
})

export default router
