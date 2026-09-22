import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from "vue-router";
import { ElMessage } from "element-plus";
import {
  can,
  firstAllowedPath,
  moduleEntries,
  refreshIdentity,
  session,
  type Capability,
} from "@/features/auth/session";

const moduleRoutes: RouteRecordRaw[] = [
  { path: "", redirect: () => firstAllowedPath() },
  {
    path: "account",
    name: "account",
    component: () => import("@/features/auth/AccountPage.vue"),
    meta: { title: "平台账户" },
  },
  ...moduleEntries.map((entry) => ({
    path: entry.path.slice(1),
    component:
      entry.path === "/people"
        ? () => import("@/features/people/PeoplePage.vue")
        : [
              "/master-data/warehouses",
              "/master-data/stops",
              "/master-data/vehicles",
            ].includes(entry.path)
          ? () => import("@/features/masterdata/MasterdataPage.vue")
          : entry.path === "/master-data/compartments"
            ? () => import("@/features/masterdata/CompartmentPage.vue")
              : entry.path === "/integrations"
                ? () => import("@/features/integration/IntegrationPage.vue")
                : entry.path === "/reports"
                  ? () => import("@/features/reports/ReportsPage.vue")
                  : entry.path === "/audit"
                    ? () => import("@/features/audit/AuditPage.vue")
              : entry.path === "/rules"
              ? () => import("@/features/masterdata/RulesPage.vue")
              : () => import("@/views/ModulePlaceholder.vue"),
    meta: {
      title: entry.title,
      capability: entry.capability,
      description: "模块接入中",
    },
  })),
];
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/login",
      component: () => import("@/features/auth/LoginPage.vue"),
      meta: { title: "内部账号登录", public: true },
    },
    {
      path: "/",
      component: () => import("@/layouts/AdminLayout.vue"),
      children: moduleRoutes,
    },
    { path: "/:pathMatch(.*)*", redirect: "/account" },
  ],
});
router.beforeEach(async (to) => {
  if (to.meta.public) return session.value ? firstAllowedPath() : true;
  if (!session.value) return "/login";
  try {
    await refreshIdentity();
  } catch (cause) {
    if (!session.value) return "/login";
    ElMessage.error(
      cause instanceof Error ? cause.message : "身份校验失败，请重试",
    );
    return false;
  }
  if (!session.value) return "/login";
  if (session.value.user.mustChangePassword && to.path !== "/account")
    return "/account";
  if (to.meta.capability && !can(to.meta.capability as Capability))
    return firstAllowedPath();
  return true;
});
router.afterEach((to) => {
  document.title = `${String(to.meta.title || "管理平台")} | Task Hub`;
});
export default router;
