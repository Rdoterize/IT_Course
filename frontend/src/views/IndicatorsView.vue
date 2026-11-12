<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { onBeforeRouteLeave } from 'vue-router'
import { Plus, Edit, Connection, Search, Refresh, ArrowRight, FolderOpened } from '@element-plus/icons-vue'
import { api, write } from '../api'
import { useAuth } from '../stores/auth'
import type { Indicator } from '../types'

const auth = useAuth()
const items = ref<Indicator[]>([]); const loading = ref(false); const loaded = ref(false)
const saving = ref(false); const error = ref(''); const formError = ref('')
const open = ref(false); const editing = ref<number | null>(null); const formRef = ref<FormInstance>()
const form = reactive({ code: '', name: '', description: '' }); const initialForm = ref(''); const confirmingClose = ref(false)
const searchInput = ref(''); const searchTerm = ref('')
const dirty = computed(() => JSON.stringify(form) !== initialForm.value)
const visibleItems = computed(() => {
  const keyword = searchTerm.value.toLocaleLowerCase()
  return keyword ? items.value.filter(item => `${item.code} ${item.name}`.toLocaleLowerCase().includes(keyword)) : items.value
})
let searchTimer: number | undefined
let requestNumber = 0
let requestController: AbortController | undefined
const rules: FormRules = {
  code: [{ required: true, whitespace: true, message: '请输入支撑项编码', trigger: 'blur' }, { max: 60, message: '编码最多60字', trigger: 'blur' }],
  name: [{ required: true, whitespace: true, message: '请输入支撑项名称', trigger: 'blur' }, { max: 150, message: '名称最多150字', trigger: 'blur' }],
  description: [{ max: 3000, message: '说明最多3000字', trigger: 'blur' }],
}
async function load() {
  const currentRequest = ++requestNumber
  requestController?.abort()
  const controller = new AbortController(); requestController = controller
  loading.value = true; error.value = ''
  try {
    const result = await api<Indicator[]>('/indicators', { signal: controller.signal })
    if (currentRequest !== requestNumber) return
    items.value = result; loaded.value = true
  } catch (cause) { if (!controller.signal.aborted && currentRequest === requestNumber) error.value = (cause as Error).message }
  finally { if (currentRequest === requestNumber) loading.value = false }
}
function search() { window.clearTimeout(searchTimer); searchTerm.value = searchInput.value.trim() }
function scheduleSearch() { window.clearTimeout(searchTimer); searchTimer = window.setTimeout(search, 300) }
function clearSearch() { searchInput.value = ''; search() }
async function edit(item?: Indicator) {
  if (saving.value) return
  editing.value = item?.id ?? null
  Object.assign(form, { code: item?.code ?? '', name: item?.name ?? '', description: item?.description ?? '' })
  initialForm.value = JSON.stringify(form); formError.value = ''; open.value = true
  await nextTick(); formRef.value?.clearValidate()
}
async function closeForm(done?: () => void) {
  if (saving.value || confirmingClose.value) return
  if (dirty.value) {
    confirmingClose.value = true
    try {
      await ElMessageBox.confirm('本次支撑项修改尚未保存，关闭后将丢失。', '放弃未保存的修改？', {
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
    await write(editing.value !== null ? `/indicators/${editing.value}` : '/indicators', editing.value !== null ? 'PUT' : 'POST', { code: form.code.trim(), name: form.name.trim(), description: form.description.trim() })
    ElMessage.success(editing.value !== null ? '支撑项已更新' : '支撑项已创建')
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
  <div class="page-heading"><div><div class="eyebrow">ACCREDITATION SUPPORT</div><h1>认证支撑项</h1><p>建立专业建设的支撑目录，让成果与材料有迹可循。</p></div><el-button type="primary" :icon="Plus" :disabled="saving" @click="edit()">新增支撑项</el-button></div>

  <div class="archive-banner support-guide"><div class="archive-banner-icon"><el-icon :size="25"><Connection /></el-icon></div><div class="support-guide-copy"><h3>从专业目标，连接到具体成果</h3><p>成果可关联一个或多个支撑项，再到材料归档中筛选、核对并导出对应材料。</p></div><router-link class="support-guide-link" to="/archive"><el-icon><FolderOpened /></el-icon>进入材料归档<el-icon><ArrowRight /></el-icon></router-link></div>

  <section class="panel support-toolbar">
    <div><h2>专业支撑目录 <span>{{ loaded ? items.length : '—' }}</span></h2><p>编码、名称与支撑要求由专业统一维护。</p></div>
    <div class="support-search"><el-input v-model="searchInput" :prefix-icon="Search" clearable placeholder="搜索编码或名称" aria-label="搜索支撑项编码或名称" @input="scheduleSearch" @keyup.enter="search" @clear="search" /><el-button :icon="Refresh" :loading="loading" aria-label="刷新支撑目录" @click="load">刷新</el-button></div>
  </section>
  <div class="support-result-note" aria-live="polite"><span v-if="searchTerm">找到 <strong>{{ visibleItems.length }}</strong> 个匹配支撑项<span class="support-search-term">“{{ searchTerm }}”</span></span><span v-else>{{ error && items.length ? '以下保留上次加载的目录' : '点击卡片中的材料入口，查看对应成果与附件。' }}</span><el-button v-if="searchTerm" type="primary" link @click="clearSearch">清除搜索</el-button></div>
  <el-alert v-if="error && items.length" title="目录更新失败，当前显示上次成功加载的内容" :description="error" type="error" show-icon :closable="false" class="mb-20" />

  <div v-if="loading && !loaded" class="indicator-grid support-grid" aria-label="正在加载支撑目录"><div v-for="index in 3" :key="index" class="panel support-skeleton"><el-skeleton :rows="4" animated /></div></div>
  <section v-else-if="error && !items.length" class="panel support-empty"><el-result icon="warning" title="支撑目录暂时无法加载" :sub-title="error"><template #extra><el-button type="primary" :icon="Refresh" @click="load">重新加载</el-button></template></el-result></section>
  <div v-else v-loading="loading" element-loading-text="正在更新支撑目录…" class="indicator-grid support-grid">
    <article v-for="item in visibleItems" :key="item.id" class="panel indicator-card support-card">
      <div class="indicator-card-top"><span class="indicator-code">{{ item.code }}</span><el-button :icon="Edit" text type="primary" :disabled="saving || loading" :aria-label="`编辑支撑项${item.name}`" @click="edit(item)">编辑</el-button></div>
      <h2>{{ item.name }}</h2><p :class="{ 'support-description-empty': !item.description }">{{ item.description || '尚未补充支撑要求，可编辑说明适用的成果与材料范围。' }}</p>
      <router-link class="support-material-link" :to="{ path: '/archive', query: { indicatorId: item.id } }"><span><el-icon><FolderOpened /></el-icon>查看关联材料</span><el-icon><ArrowRight /></el-icon></router-link>
    </article>
    <section v-if="!visibleItems.length" class="panel support-empty">
      <el-empty :description="searchTerm ? '没有找到匹配的支撑项' : '建立专业的第一项支撑目录'" :image-size="92"><p>{{ searchTerm ? '试试其他编码或名称，也可以清除搜索查看全部目录。' : '先设置编码、名称和支撑要求，再将成果关联到相应目录。' }}</p><el-button v-if="searchTerm" @click="clearSearch">清除搜索</el-button><el-button v-else type="primary" :icon="Plus" @click="edit()">新增支撑项</el-button></el-empty>
    </section>
  </div>

  <el-dialog v-model="open" :title="editing !== null ? '编辑支撑项' : '新增支撑项'" width="580px" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="beforeClose" destroy-on-close>
    <p class="support-form-intro">填写专业统一使用的编码与要求，便于后续成果关联和材料检索。</p>
    <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="mb-20" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <el-form-item label="支撑项编码" prop="code"><el-input v-model.trim="form.code" maxlength="60" placeholder="例如：BY-03" /></el-form-item>
      <el-form-item label="支撑项名称" prop="name"><el-input v-model.trim="form.name" maxlength="150" placeholder="例如：设计与开发解决方案" /></el-form-item>
      <el-form-item label="支撑要求 / 说明" prop="description"><el-input v-model="form.description" type="textarea" :rows="5" maxlength="3000" show-word-limit placeholder="说明该支撑项涵盖哪些成果，需要哪些证明材料（可选）" /></el-form-item>
    </el-form>
    <div class="support-form-tip"><el-icon><Connection /></el-icon><span>保存后，可在成果录入中选择此支撑项。指标关联用于整理证据，不自动计算达成度。</span></div>
    <template #footer><el-button :disabled="saving" @click="closeForm()">取消</el-button><el-button type="primary" :loading="saving" :disabled="confirmingClose" @click="save">{{ editing !== null ? '保存修改' : '创建支撑项' }}</el-button></template>
  </el-dialog>
</template>

<style scoped>
.support-guide{gap:17px}.support-guide-copy{flex:1;min-width:0}.support-guide-link{display:flex;align-items:center;gap:7px;flex-shrink:0;font-size:12px;font-weight:600;color:var(--app-primary);padding:10px 0 10px 12px}.support-guide-link:hover{color:var(--el-color-primary-dark-2)}
.support-toolbar{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:20px 24px}.support-toolbar h2{display:flex;align-items:center;gap:10px;font-size:16px}.support-toolbar h2 span{display:grid;place-items:center;min-width:28px;padding:3px 8px;border-radius:7px;font-size:12px;font-variant-numeric:tabular-nums;background:var(--el-color-primary-light-9);color:var(--app-primary)}.support-toolbar p{font-size:12px;line-height:1.7;color:var(--app-muted);margin-top:6px}.support-search{display:flex;align-items:center;gap:10px;width:365px;max-width:100%}.support-search .el-input{min-width:0}.support-search .el-button{flex-shrink:0}
.support-result-note{display:flex;align-items:center;justify-content:space-between;gap:12px;min-height:52px;padding:8px 3px;color:var(--app-muted);font-size:12px}.support-result-note strong{color:var(--app-primary);font-weight:600}.support-search-term{margin-left:8px;overflow-wrap:anywhere}.support-grid{align-items:stretch}.support-card{display:flex;flex-direction:column;padding:24px;border-top:3px solid var(--el-color-primary-light-7);transition:box-shadow .2s,border-color .2s}.support-card:hover{border-top-color:var(--app-primary);box-shadow:var(--app-shadow)}.support-card .indicator-card-top{gap:10px;margin-bottom:19px}.support-card .indicator-code{font-variant-numeric:tabular-nums;overflow-wrap:anywhere;max-width:calc(100% - 62px);background:var(--el-color-primary-light-9);color:var(--app-primary)}.support-card h2{font-size:17px;font-weight:600;line-height:1.65;overflow-wrap:anywhere;margin-bottom:12px}.support-card>p{line-height:1.85;color:var(--el-text-color-regular);margin-bottom:24px;display:-webkit-box;-webkit-line-clamp:4;-webkit-box-orient:vertical;overflow:hidden;min-height:66px}.support-card>p.support-description-empty{color:var(--app-muted)}
.support-card .support-material-link{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:auto;padding-top:17px;border-top:1px solid var(--app-border);font-size:12px;font-weight:500;color:var(--app-primary)}.support-material-link>span{display:flex;align-items:center;gap:7px}.support-material-link:hover{color:var(--el-color-primary-dark-2)}.support-skeleton{padding:27px;min-height:270px}.support-empty{grid-column:1/-1;text-align:center;padding:24px;min-height:280px}.support-empty p{font-size:12px;line-height:1.9;color:var(--app-muted);margin:-3px 0 20px;max-width:430px}
.support-form-intro{font-size:13px;line-height:1.8;color:var(--app-muted);margin:-3px 0 21px}.support-form-tip{display:flex;align-items:flex-start;gap:9px;border-radius:9px;padding:13px 15px;background:var(--app-bg);color:var(--app-muted);font-size:12px;line-height:1.8}.support-form-tip>.el-icon{color:var(--app-primary);margin-top:4px;flex-shrink:0}
@media(max-width:900px){.support-guide{flex-wrap:wrap}.support-guide-link{margin-left:69px;padding:0}.support-toolbar{align-items:flex-start;flex-direction:column;gap:16px}.support-search{width:100%}}
@media(max-width:680px){.support-guide-link{margin-left:50px;font-size:12px}.support-toolbar{padding:18px}.support-toolbar p{font-size:12px}.support-result-note{font-size:12px;align-items:flex-start;padding:14px 2px}.support-card{padding:21px}.support-card h2{font-size:16px}.support-empty{padding:18px}.support-empty p{font-size:12px}}
</style>
