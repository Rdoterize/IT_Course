<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataAnalysis, Collection, FolderOpened, CircleCheck, User, Setting, ArrowDown, ArrowRight, Menu, Lock, SwitchButton, School, Close, Search, Plus, Connection, Calendar } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules, type InputInstance } from 'element-plus'
import { useAuth } from '../stores/auth'
import { write } from '../api'
import { roleLabels, localToday } from '../constants'

const auth = useAuth(); const route = useRoute(); const router = useRouter()
const mobileOpen = ref(false); const mobileView = ref(window.matchMedia('(max-width: 760px)').matches)
const sidebar = ref<HTMLElement>(); const mobileTrigger = ref<HTMLButtonElement>(); const mainArea = ref<HTMLElement>()
const passwordOpen = ref(false); const saving = ref(false)
const passwordForm = ref<FormInstance>(); const password = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 8, max: 72, message: '新密码需为8至72位', trigger: 'blur' }, { validator: (_, value, done) => /[A-Za-z]/.test(value) && /\d/.test(value) ? done() : done(new Error('请同时包含字母和数字')), trigger: 'blur' }],
  confirm: [{ validator: (_, value, done) => value === password.newPassword ? done() : done(new Error('两次输入的密码不一致')), trigger: 'blur' }],
}
const menus = computed(() => [
  { path: '/', label: '数据概览', description: '专业成果统计与工作待办', icon: DataAnalysis, keyword: 'dashboard overview tongji shuju' },
  { path: '/achievements', label: '成果管理', description: '查看、筛选和维护师生成果', icon: Collection, keyword: 'achievement chengguo' },
  ...(auth.isAdmin ? [{ path: '/reviews', label: '审核工作台', description: '核对成果与支撑材料', icon: CircleCheck, keyword: 'review shenhe' }] : []),
  { path: '/archive', label: '材料归档', description: '整理认证材料与导出台账', icon: FolderOpened, keyword: 'archive export cailiao daochu' },
])
const adminMenus = computed(() => auth.isAdmin ? [
  { path: '/users', label: '账号管理', description: '维护师生账号与访问角色', icon: User, keyword: 'users account zhanghao' },
  { path: '/indicators', label: '认证支撑项', description: '配置专业认证与评估目录', icon: Setting, keyword: 'indicator renzheng' },
] : [])
const activeSection = computed(() => route.meta.admin && ['/users', '/indicators'].includes(route.path) ? '系统管理' : '工作空间')
const commandOpen = ref(false); const commandQuery = ref(''); const commandIndex = ref(0); const commandInput = ref<InputInstance>()
let focusBeforeCommand: HTMLElement | null = null
const commandItems = computed(() => [...menus.value, ...adminMenus.value, { path: '/achievements?create=1', label: '录入新成果', description: '创建成果草稿并补充支撑材料', icon: Plus, keyword: 'create new xinzeng luru' }])
const commandResults = computed(() => {
  const text = commandQuery.value.trim().toLowerCase()
  return text ? commandItems.value.filter(item => `${item.label} ${item.description} ${item.keyword}`.toLowerCase().includes(text)) : commandItems.value
})
watch(commandQuery, () => { commandIndex.value = 0 })
function closeMobile(restore = true) { mobileOpen.value = false; if (restore) void nextTick(() => mobileTrigger.value?.focus()) }
async function openMobile() { mobileOpen.value = true; await nextTick(); sidebar.value?.querySelector<HTMLButtonElement>('.mobile-close')?.focus() }
function openCommand() { focusBeforeCommand = document.activeElement instanceof HTMLElement ? document.activeElement : null; if (mobileOpen.value) closeMobile(false); commandQuery.value = ''; commandIndex.value = 0; commandOpen.value = true }
function focusCommandInput() { void nextTick(() => commandInput.value?.focus()) }
function restoreCommandFocus() { if (focusBeforeCommand?.isConnected && !focusBeforeCommand.closest('[inert]')) focusBeforeCommand.focus(); else if (mobileView.value) mobileTrigger.value?.focus(); focusBeforeCommand = null }
async function chooseCommand(index: number) { const item = commandResults.value[index]; if (!item) return; commandOpen.value = false; await router.push(item.path) }
function commandKey(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault(); const count = commandResults.value.length
    if (count) commandIndex.value = (commandIndex.value + (event.key === 'ArrowDown' ? 1 : -1) + count) % count
    void nextTick(() => document.getElementById(`command-item-${commandIndex.value}`)?.scrollIntoView({ block: 'nearest' }))
  } else if (event.key === 'Enter') { event.preventDefault(); void chooseCommand(commandIndex.value) }
}
function globalKeys(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && !event.altKey && !event.isComposing) { event.preventDefault(); if (commandOpen.value) commandOpen.value = false; else openCommand(); return }
  if (event.key === 'Escape' && mobileOpen.value) { event.preventDefault(); closeMobile(); return }
  if (mobileOpen.value && event.key === 'Tab') {
    const focusable = sidebar.value?.querySelectorAll<HTMLElement>('a[href], button:not([disabled])')
    if (!focusable?.length) return
    const first = focusable[0]; const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
  }
}
const media = window.matchMedia('(max-width: 760px)')
function viewportChange() { mobileView.value = media.matches; if (!media.matches) mobileOpen.value = false }
watch(() => route.fullPath, () => { if (mobileOpen.value) { closeMobile(false); void nextTick(() => mainArea.value?.focus()) }; commandOpen.value = false })
watch(mobileOpen, value => { document.body.classList.toggle('mobile-nav-open', value) })
async function logout() { try { await auth.logout(); await router.push('/login') } catch (error) { ElMessage.error((error as Error).message) } }
function openPassword() { Object.assign(password, { oldPassword: '', newPassword: '', confirm: '' }); passwordOpen.value = true; void nextTick(() => passwordForm.value?.clearValidate()) }
async function changePassword() {
  if (!await passwordForm.value?.validate().catch(() => false)) return
  saving.value = true
  try { await write('/auth/password', 'PUT', { oldPassword: password.oldPassword, newPassword: password.newPassword }); passwordOpen.value = false; auth.user = null; ElMessage.success('密码已更新，请使用新密码登录'); await router.push('/login') } catch (error) { ElMessage.error((error as Error).message) } finally { saving.value = false }
}
function expired() { auth.user = null; ElMessage.warning('登录已过期，请重新登录'); void router.push('/login') }
onMounted(() => { window.addEventListener('session-expired', expired); window.addEventListener('keydown', globalKeys); media.addEventListener('change', viewportChange) })
onUnmounted(() => { window.removeEventListener('session-expired', expired); window.removeEventListener('keydown', globalKeys); media.removeEventListener('change', viewportChange); document.body.classList.remove('mobile-nav-open') })
</script>
<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳转到页面内容</a>
    <div v-if="mobileOpen" class="mobile-shade" @click="closeMobile()" aria-hidden="true"></div>
    <aside id="workspace-sidebar" ref="sidebar" class="sidebar" :class="{ open: mobileOpen }" :inert="mobileView && !mobileOpen" aria-label="主导航">
      <router-link class="brand" to="/"><span class="brand-mark"><el-icon :size="27"><School /></el-icon></span><span><strong>知绩<span class="brand-dot">.</span></strong><small>师生业绩管理平台</small></span></router-link>
      <button class="mobile-close" aria-label="关闭导航菜单" @click="closeMobile()"><el-icon :size="22"><Close /></el-icon></button>
      <div class="workspace-badge"><span class="workspace-badge-icon"><el-icon :size="16"><Connection /></el-icon></span><div><strong>专业成果工作台</strong><small>{{ auth.user?.department || '教学 · 科研 · 学生成长' }}</small></div></div>
      <div class="sidebar-label">工作空间 <span>WORKSPACE</span></div>
      <nav class="nav-list" aria-label="工作空间"><router-link v-for="item in menus" :key="item.path" :to="item.path" :class="{ active: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined"><el-icon :size="20"><component :is="item.icon" /></el-icon><span>{{ item.label }}</span><span v-if="route.path === item.path" class="nav-active-mark"></span></router-link></nav>
      <template v-if="auth.isAdmin"><div class="sidebar-label section-gap">系统管理 <span>MANAGE</span></div><nav class="nav-list" aria-label="系统管理"><router-link v-for="item in adminMenus" :key="item.path" :to="item.path" :class="{ active: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined"><el-icon :size="20"><component :is="item.icon" /></el-icon><span>{{ item.label }}</span><span v-if="route.path === item.path" class="nav-active-mark"></span></router-link></nav></template>
      <button class="sidebar-shortcut" @click="openCommand"><el-icon :size="18"><Search /></el-icon><span>快速前往</span><kbd>⌘ / Ctrl K</kbd></button>
      <div class="sidebar-footer"><span class="online-dot"></span><span>让每一份成果，有据可循</span><small>教学 · 科研 · 成长</small></div>
    </aside>
    <div class="main-shell" :inert="mobileView && mobileOpen">
      <header class="topbar"><div class="topbar-left"><button ref="mobileTrigger" class="menu-toggle" aria-label="展开导航菜单" aria-controls="workspace-sidebar" :aria-expanded="mobileOpen" @click="openMobile"><el-icon :size="22"><Menu /></el-icon></button><nav class="app-breadcrumb" aria-label="当前位置"><span>{{ activeSection }}</span><el-icon :size="11"><ArrowRight /></el-icon><strong>{{ route.meta.title }}</strong></nav></div><div class="topbar-right"><button class="quick-search-trigger" @click="openCommand" aria-label="搜索页面，快捷键 Control 或 Command 加 K"><el-icon :size="17"><Search /></el-icon><span>搜索页面与操作</span><kbd>⌘ K</kbd></button><span class="today"><el-icon :size="15"><Calendar /></el-icon>{{ localToday() }}</span><span class="header-separator"></span><el-dropdown trigger="click"><button class="user-menu" aria-label="个人账户菜单"><span class="avatar">{{ auth.user?.name?.slice(-2) }}</span><span class="user-info"><strong>{{ auth.user?.name }}</strong><small>{{ roleLabels[auth.user?.role || ''] }}</small></span><el-icon :size="12"><ArrowDown /></el-icon></button><template #dropdown><el-dropdown-menu><el-dropdown-item :icon="Lock" @click="openPassword">修改密码</el-dropdown-item><el-dropdown-item divided :icon="SwitchButton" @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></header>
      <main id="main-content" ref="mainArea" tabindex="-1" class="page-main"><router-view :key="route.path" /></main>
      <footer class="app-footer"><span><strong>知绩</strong> · 师生业绩管理系统</span><span>以成果见证成长，以数据支撑发展</span></footer>
    </div>
    <el-dialog v-model="commandOpen" title="快速前往" class="command-dialog" width="600px" top="15vh" :show-close="false" :close-on-click-modal="true" @opened="focusCommandInput" @closed="restoreCommandFocus" destroy-on-close>
      <div class="command-search"><el-icon :size="21"><Search /></el-icon><el-input ref="commandInput" v-model="commandQuery" placeholder="搜索页面或操作…" aria-label="搜索页面或操作" aria-controls="command-results" :aria-activedescendant="commandResults.length ? `command-item-${commandIndex}` : undefined" @keydown="commandKey" clearable /><button aria-label="关闭快速导航" class="command-escape" @click="commandOpen = false">Esc</button></div>
      <div class="command-section-title">{{ commandQuery ? '搜索结果' : '页面与常用操作' }}<span>{{ commandResults.length }}</span></div>
      <div id="command-results" class="command-results" role="listbox" aria-label="可前往的页面"><button v-for="(item, index) in commandResults" :id="`command-item-${index}`" :key="item.path" class="command-result" :class="{ selected: index === commandIndex }" role="option" :aria-selected="index === commandIndex" @mouseenter="commandIndex = index" @click="chooseCommand(index)"><span class="command-result-icon"><el-icon :size="21"><component :is="item.icon" /></el-icon></span><span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span><el-icon class="command-result-arrow"><ArrowRight /></el-icon></button><div v-if="!commandResults.length" class="command-empty"><el-icon :size="30"><Search /></el-icon><strong>没有找到相关页面</strong><span>试试「成果」「材料」或「账号」</span></div></div>
      <div class="command-footer"><span><kbd>↑</kbd><kbd>↓</kbd> 选择</span><span><kbd>Enter</kbd> 前往</span><span><kbd>Esc</kbd> 关闭</span></div>
    </el-dialog>
    <el-dialog v-model="passwordOpen" title="修改密码" width="460px" :close-on-click-modal="false"><p class="password-dialog-intro">定期更新密码，保护您的个人成果档案。</p><el-form ref="passwordForm" :model="password" :rules="rules" label-position="top"><el-form-item label="当前密码" prop="oldPassword"><el-input v-model="password.oldPassword" type="password" show-password autocomplete="current-password" /></el-form-item><el-form-item label="新密码" prop="newPassword"><el-input v-model="password.newPassword" type="password" show-password autocomplete="new-password" placeholder="8至72位，包含字母和数字" /></el-form-item><el-form-item label="确认新密码" prop="confirm"><el-input v-model="password.confirm" type="password" show-password autocomplete="new-password" /></el-form-item></el-form><template #footer><el-button @click="passwordOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="changePassword">更新密码</el-button></template></el-dialog>
  </div>
</template>
