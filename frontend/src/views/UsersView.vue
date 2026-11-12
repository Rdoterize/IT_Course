<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { onBeforeRouteLeave } from 'vue-router'
import { Plus, Search, Refresh, Lock, User as UserIcon } from '@element-plus/icons-vue'
import { api, query, write } from '../api'
import { useAuth } from '../stores/auth'
import { roleLabels } from '../constants'
import type { User, UserRole, Page } from '../types'

interface UserForm {
  username: string; name: string; role: UserRole; number: string; cohort: string
  department: string; enabled: boolean; password: string
}
const emptyForm = (): UserForm => ({ username: '', name: '', role: 'STUDENT', number: '', cohort: '', department: '', enabled: true, password: '' })
const auth = useAuth()
const loading = ref(false); const saving = ref(false); const error = ref(''); const formError = ref('')
const loaded = ref(false); const items = ref<User[]>([]); const total = ref(0)
const page = ref(1); const size = ref(10); const q = ref(''); const role = ref('')
const open = ref(false); const editing = ref<number | null>(null); const formRef = ref<FormInstance>()
const form = reactive<UserForm>(emptyForm()); const initialForm = ref(''); const confirmingClose = ref(false)
const editingSelf = computed(() => editing.value !== null && editing.value === auth.user?.id)
const hasFilters = computed(() => Boolean(q.value.trim() || role.value))
const pageEnabled = computed(() => items.value.filter(user => user.enabled).length)
const dirty = computed(() => JSON.stringify(form) !== initialForm.value)
let searchTimer: number | undefined
let requestNumber = 0
let requestController: AbortController | undefined

const rules: FormRules<UserForm> = {
  username: [{ required: true, whitespace: true, message: '请输入登录账号', trigger: 'blur' }, { pattern: /^[A-Za-z0-9_.-]{3,80}$/, message: '使用3至80位字母、数字、下划线、点或连字符', trigger: 'blur' }],
  name: [{ required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' }, { max: 100, message: '姓名最多100字', trigger: 'blur' }],
  role: [{ required: true, message: '请选择账号角色', trigger: 'change' }],
  number: [{ max: 80, message: '学号或工号最多80字', trigger: 'blur' }],
  cohort: [{ max: 40, message: '年级最多40字', trigger: 'blur' }],
  department: [{ max: 150, message: '专业或部门最多150字', trigger: 'blur' }],
  password: [{ validator: (_, value: string, done) => {
    const password = typeof value === 'string' ? value.trim() : ''
    if (!password && editing.value !== null) return done()
    if (password.length < 8 || password.length > 72 || !/[A-Za-z]/.test(password) || !/\d/.test(password)) {
      done(new Error('密码需为8至72位，同时包含字母和数字'))
    } else done()
  }, trigger: 'blur' }],
}

async function load() {
  const currentRequest = ++requestNumber
  requestController?.abort()
  const controller = new AbortController(); requestController = controller
  loading.value = true; error.value = ''
  try {
    const result = await api<Page<User>>(`/users${query({ page: page.value, size: size.value, q: q.value.trim(), role: role.value })}`, { signal: controller.signal })
    if (currentRequest !== requestNumber) return
    items.value = result.items; total.value = result.total; loaded.value = true
  } catch (cause) {
    if (!controller.signal.aborted && currentRequest === requestNumber) error.value = (cause as Error).message
  } finally { if (currentRequest === requestNumber) loading.value = false }
}
function search() { window.clearTimeout(searchTimer); page.value = 1; void load() }
function scheduleSearch() { window.clearTimeout(searchTimer); searchTimer = window.setTimeout(search, 350) }
function resetFilters() { q.value = ''; role.value = ''; search() }
function changePage(value: number) { if (value !== page.value) { page.value = value; void load() } }
function changeSize(value: number) { size.value = value; search() }

async function edit(user?: User) {
  if (saving.value) return
  editing.value = user?.id ?? null; formError.value = ''
  Object.assign(form, user ? {
    username: user.username, name: user.name, role: user.role, number: user.number ?? '',
    cohort: user.cohort ?? '', department: user.department ?? '', enabled: user.enabled, password: '',
  } : emptyForm())
  initialForm.value = JSON.stringify(form); open.value = true
  await nextTick(); formRef.value?.clearValidate()
}
async function closeForm(done?: () => void) {
  if (saving.value || confirmingClose.value) return
  if (dirty.value) {
    confirmingClose.value = true
    try {
      await ElMessageBox.confirm('本次修改尚未保存，关闭后将丢失。', '放弃未保存的修改？', {
        confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning',
      })
    } catch { return } finally { confirmingClose.value = false }
  }
  if (done) done(); else open.value = false
}
function beforeClose(done: () => void) { void closeForm(done) }
async function save() {
  if (saving.value) return
  saving.value = true; formError.value = ''
  try {
    if (!await formRef.value?.validate().catch(() => false)) return
    const payload = { ...form, username: form.username.trim(), name: form.name.trim(), number: form.number.trim(), cohort: form.cohort.trim(), department: form.department.trim(), password: form.password.trim() }
    const saved = await write<User>(editing.value !== null ? `/users/${editing.value}` : '/users', editing.value !== null ? 'PUT' : 'POST', payload)
    if (saved.id === auth.user?.id) auth.user = saved
    ElMessage.success(editing.value !== null ? '账号信息已更新' : '账号已创建')
    if (editing.value === null) page.value = 1
    open.value = false; await load()
  } catch (cause) { formError.value = (cause as Error).message }
  finally { saving.value = false }
}
onMounted(() => void load())
onBeforeRouteLeave(async () => {
  if (!open.value || !auth.user) return true
  let canLeave = false
  await closeForm(() => { canLeave = true })
  return canLeave
})
onBeforeUnmount(() => { window.clearTimeout(searchTimer); ++requestNumber; requestController?.abort() })
</script>

<template>
  <div class="page-heading">
    <div><div class="eyebrow">ACCESS MANAGEMENT</div><h1>账号管理</h1><p>管理师生身份与访问范围，让每一份成果找到对应的人。</p></div>
    <el-button type="primary" :icon="Plus" :disabled="saving" @click="edit()">新建账号</el-button>
  </div>

  <div class="account-policy"><span class="policy-icon"><el-icon><Lock /></el-icon></span><div><strong>角色决定操作权限，成果关系决定可见范围</strong><p>停用账号会阻止该用户继续访问；已有成果及参与记录仍会保留。</p></div></div>

  <section class="panel list-panel account-panel">
    <div class="filters account-filters">
      <el-input v-model="q" class="search-input" :prefix-icon="Search" placeholder="搜索姓名、账号或学工号" aria-label="搜索账号" clearable @input="scheduleSearch" @keyup.enter="search" @clear="search" />
      <el-select v-model="role" placeholder="全部角色" aria-label="按角色筛选" clearable @change="search"><el-option v-for="(label, key) in roleLabels" :key="key" :label="label" :value="key" /></el-select>
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Refresh" :disabled="!hasFilters && !error" @click="resetFilters">重置</el-button>
    </div>
    <div class="list-caption account-caption" aria-live="polite">
      <span>{{ error ? '上次加载结果' : hasFilters ? '筛选结果' : '全部账号' }} <strong>{{ loaded ? total : '—' }}</strong><span class="caption-unit">个账号</span></span>
      <div v-if="loaded && items.length" class="page-status"><span><i class="status-dot"></i>本页启用 {{ pageEnabled }}</span><span><i class="status-dot inactive"></i>本页停用 {{ items.length - pageEnabled }}</span></div>
      <span v-else class="muted">支持输入后自动搜索，也可按 Enter 查询</span>
    </div>
    <el-alert v-if="error && items.length" title="账号列表更新失败，以下保留上次成功加载的内容" type="error" show-icon :closable="false" class="mb-20"><p>{{ error }}</p><el-button size="small" @click="load">重新加载</el-button></el-alert>
    <el-table :data="items" v-loading="loading" element-loading-text="正在加载账号…" class="clean-table" row-key="id">
      <el-table-column label="姓名" min-width="180"><template #default="{ row }"><div class="user-table-name"><span class="small-avatar"><el-icon><UserIcon /></el-icon></span><strong>{{ row.name }}</strong><el-tag v-if="row.id === auth.user?.id" size="small" type="info" effect="plain">当前账号</el-tag></div></template></el-table-column>
      <el-table-column label="登录账号" min-width="140"><template #default="{ row }"><span class="account-username">{{ row.username }}</span></template></el-table-column>
      <el-table-column label="角色" width="110"><template #default="{ row }"><el-tag :type="row.role === 'ADMIN' ? 'primary' : row.role === 'TEACHER' ? 'success' : 'info'" effect="light">{{ roleLabels[row.role] }}</el-tag></template></el-table-column>
      <el-table-column label="学号 / 工号" min-width="135"><template #default="{ row }"><span class="account-secondary">{{ row.number || '—' }}</span></template></el-table-column>
      <el-table-column label="年级" width="100"><template #default="{ row }">{{ row.cohort || '—' }}</template></el-table-column>
      <el-table-column label="所属专业 / 部门" min-width="170" show-overflow-tooltip><template #default="{ row }">{{ row.department || '—' }}</template></el-table-column>
      <el-table-column label="账号状态" width="110"><template #default="{ row }"><span :class="['enabled-label', { disabled: !row.enabled }]"><i></i>{{ row.enabled ? '已启用' : '已停用' }}</span></template></el-table-column>
      <el-table-column label="操作" width="85" fixed="right"><template #default="{ row }"><el-button type="primary" link :disabled="loading || saving" :aria-label="`编辑${row.name}的账号`" @click="edit(row)">编辑</el-button></template></el-table-column>
      <template #empty>
        <div v-if="loading" class="account-empty-placeholder">正在读取账号信息…</div>
        <el-result v-else-if="error" icon="warning" title="账号列表暂时无法加载" :sub-title="error"><template #extra><el-button type="primary" :icon="Refresh" @click="load">重新加载</el-button></template></el-result>
        <el-empty v-else :description="hasFilters ? '没有找到匹配的账号' : '还没有师生账号'" :image-size="88"><p class="empty-help">{{ hasFilters ? '试试其他关键词，或清除筛选查看全部账号。' : '创建账号后，师生即可登录并填报各自成果。' }}</p><el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button><el-button v-else type="primary" :icon="Plus" @click="edit()">创建第一个账号</el-button></el-empty>
      </template>
    </el-table>
    <div class="pagination-row"><span>{{ loaded ? `共 ${total} 个账号 · 每页 ${size} 个` : '等待加载账号' }}</span><el-pagination :current-page="page" :page-size="size" :total="total" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" background :disabled="loading" @current-change="changePage" @size-change="changeSize" /></div>
  </section>

  <el-dialog v-model="open" :title="editing !== null ? '编辑账号' : '新建账号'" width="640px" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="beforeClose" destroy-on-close>
    <p class="account-dialog-intro">{{ editing !== null ? '更新账号资料；重置密码可留空，保留原密码。' : '填写登录信息与身份，创建后即可使用该账号登录。' }}</p>
    <el-alert v-if="editingSelf" title="当前账号受到保护" description="登录账号、角色和启用状态不可在此修改，避免失去当前管理权限。" type="info" show-icon :closable="false" class="mb-20" />
    <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="mb-20" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <div class="form-grid two">
        <el-form-item label="登录账号" prop="username"><el-input v-model.trim="form.username" maxlength="80" :disabled="editingSelf" autocomplete="off" placeholder="3位以上字母、数字等" /></el-form-item>
        <el-form-item label="姓名" prop="name"><el-input v-model.trim="form.name" maxlength="100" placeholder="填写真实姓名" /></el-form-item>
        <el-form-item label="账号角色" prop="role"><el-select v-model="form.role" :disabled="editingSelf"><el-option v-for="(label, key) in roleLabels" :key="key" :label="label" :value="key" /></el-select></el-form-item>
        <el-form-item label="学号 / 工号" prop="number"><el-input v-model.trim="form.number" maxlength="80" placeholder="可选" /></el-form-item>
        <el-form-item label="年级" prop="cohort"><el-input v-model.trim="form.cohort" maxlength="40" placeholder="例如：2025级" /></el-form-item>
        <el-form-item label="所属专业 / 部门" prop="department"><el-input v-model.trim="form.department" maxlength="150" placeholder="例如：计算机科学与技术" /></el-form-item>
      </div>
      <el-form-item :label="editing !== null ? '重置密码' : '初始密码'" prop="password"><el-input v-model.trim="form.password" type="password" show-password maxlength="72" autocomplete="new-password" :placeholder="editing !== null ? '留空保留原密码' : '8至72位，同时包含字母和数字'" /></el-form-item>
      <p class="account-field-help">{{ editing !== null ? '只有填写新密码时才会重置；新密码需为8至72位，包含字母和数字。' : '密码需为8至72位，包含字母和数字；首尾空格将被忽略。' }}</p>
      <div class="account-state-setting"><div><strong>账号访问状态</strong><p>{{ editingSelf ? '当前管理员账号保持启用。' : '停用后，当前会话和后续登录都会被阻止。' }}</p></div><el-switch v-model="form.enabled" :disabled="editingSelf" active-text="启用" inactive-text="停用" /></div>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="closeForm()">取消</el-button><el-button type="primary" :loading="saving" :disabled="confirmingClose" @click="save">{{ editing !== null ? '保存修改' : '创建账号' }}</el-button></template>
  </el-dialog>
</template>

<style scoped>
.account-policy{display:flex;align-items:center;gap:14px;padding:17px 20px;margin-bottom:22px;border:1px solid var(--app-border,#e7ecf3);border-radius:12px;background:var(--app-bg,#f5f8fc)}
.policy-icon{display:grid;place-items:center;width:38px;height:38px;flex-shrink:0;border-radius:10px;color:var(--el-color-primary);background:var(--el-color-primary-light-9)}
.account-policy strong{font-size:14px;font-weight:600;color:var(--app-text)}
.account-policy p{margin-top:5px;font-size:12px;line-height:1.7;color:var(--app-muted)}
.account-filters .search-input{width:320px}.account-caption{min-height:64px}.caption-unit{margin-left:7px;color:var(--app-muted);font-size:12px}
.page-status{display:flex;gap:18px;color:var(--app-muted);font-size:12px}.page-status>span{display:flex;align-items:center;gap:6px}.status-dot{width:6px;height:6px;border-radius:50%;background:var(--app-accent)}.status-dot.inactive{background:var(--el-text-color-placeholder)}
.account-username{font-size:13px;font-variant-numeric:tabular-nums;color:var(--el-text-color-regular)}.account-secondary{font-variant-numeric:tabular-nums}.account-empty-placeholder{padding:70px 20px;color:var(--app-muted)}
.account-panel :deep(.el-table__empty-text){width:100%;line-height:1.5}
.empty-help{font-size:12px;line-height:1.8;color:var(--app-muted);margin:-4px 0 18px}.account-dialog-intro{font-size:13px;color:var(--app-muted);line-height:1.8;margin:-3px 0 20px}.account-field-help{font-size:12px;line-height:1.7;color:var(--app-muted);margin:-9px 0 20px}
.account-state-setting{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:17px 18px;border:1px solid var(--app-border);background:var(--app-bg);border-radius:10px}.account-state-setting strong{font-size:14px;font-weight:600}.account-state-setting p{font-size:12px;line-height:1.7;color:var(--app-muted);margin-top:5px}.account-state-setting .el-switch{flex-shrink:0}
@media(max-width:680px){.account-policy{padding:15px;align-items:flex-start}.account-policy strong{font-size:13px}.account-policy p{font-size:12px}.account-filters .search-input{width:100%}.account-caption{align-items:flex-start;flex-direction:column;gap:10px}.page-status{font-size:12px}.account-state-setting{align-items:flex-start;flex-direction:column;gap:10px}.account-state-setting .el-switch{align-self:flex-end}}
</style>
