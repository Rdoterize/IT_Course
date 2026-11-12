import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from './stores/auth'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('./views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/', component: () => import('./layouts/AppLayout.vue'), children: [
      { path: '', component: () => import('./views/DashboardView.vue'), meta: { title: '数据概览' } },
      { path: 'achievements', component: () => import('./views/AchievementsView.vue'), meta: { title: '成果管理' } },
      { path: 'reviews', component: () => import('./views/AchievementsView.vue'), props: { review: true }, meta: { title: '审核工作台', admin: true } },
      { path: 'archive', component: () => import('./views/AchievementsView.vue'), props: { archive: true }, meta: { title: '材料归档' } },
      { path: 'users', component: () => import('./views/UsersView.vue'), meta: { title: '账号管理', admin: true } },
      { path: 'indicators', component: () => import('./views/IndicatorsView.vue'), meta: { title: '认证支撑项', admin: true } },
    ] },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
router.beforeEach(async to => {
  const auth = useAuth()
  if (!auth.loaded) { try { await auth.load() } catch { if (to.path !== '/login') return '/login' } }
  if (!auth.user && to.path !== '/login') return { path: '/login', query: { redirect: to.fullPath } }
  if (auth.user && to.path === '/login') return '/'
  if (to.meta.admin && !auth.isAdmin) return '/'
  document.title = `${to.meta.title || '知绩'} · 师生业绩管理`
})
export default router
