<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, Download, FolderOpened, Document, ArrowRight, Filter, ArrowDown, ArrowUp, Close, Operation, Calendar, User, CircleCheck } from '@element-plus/icons-vue'
import { api, download, query, write } from '../api'
import { useAuth } from '../stores/auth'
import { typeLabels, levelLabels, statusLabels, statusColors, localToday } from '../constants'
import type { Achievement, Indicator, Page } from '../types'
import AchievementEditor from '../components/AchievementEditor.vue'
import AchievementDetail from '../components/AchievementDetail.vue'

const props = defineProps<{ archive?: boolean; review?: boolean }>()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
type Filters = { q: string; type: string; level: string; status: string; year: string; indicatorId: string; scope: string }
const defaultStatus = props.review ? 'PENDING' : props.archive ? 'APPROVED' : ''
const defaults = (): Filters => ({ q: '', type: '', level: '', status: defaultStatus, year: '', indicatorId: '', scope: 'all' })
const filters = reactive<Filters>(defaults())
const page = ref(1)
const size = ref(10)
const loading = ref(true)
const error = ref('')
const items = ref<Achievement[]>([])
const total = ref(0)
const indicators = ref<Indicator[]>([])
const advanced = ref(false)
const compact = ref(false)
const searchPending = ref(false)
const detailOpen = ref(false)
const selectedId = ref<number | null>(null)
const editorOpen = ref(false)
const editing = ref<Achievement | null>(null)
const exporting = ref('')
const deletingId = ref<number | null>(null)
let controller: AbortController | undefined
let activeRequest = 0
let searchTimer: ReturnType<typeof setTimeout> | undefined
let syncingQuery = false
let committedSignature = ''
const currentYear = Number(localToday().slice(0, 4))
const years = [...Array.from({ length: currentYear - 1899 }, (_, index) => currentYear - index), ...Array.from({ length: 2100 - currentYear }, (_, index) => currentYear + index + 1)]

const heading = computed(() => props.archive
  ? { eyebrow: 'EVIDENCE ARCHIVE', title: '支撑材料归档', description: '把每份成果整理成可检索、可追溯的专业建设材料。' }
  : props.review
    ? { eyebrow: 'REVIEW WORKSPACE', title: '审核工作台', description: '核对成果、查看材料，为每一次认定留下清楚记录。' }
    : { eyebrow: 'ACHIEVEMENT CENTER', title: '成果管理', description: '教学、科研与学生成果，一份台账统一管理。' })
const scopeOptions = computed(() => [
  { label: auth.isAdmin ? '全部成果' : '我可见的成果', value: 'all' },
  { label: '我的成果', value: 'mine' },
  ...(auth.user?.role !== 'STUDENT' ? [{ label: '我指导的', value: 'guided' }] : []),
])
const chips = computed(() => {
  const result: { key: keyof Filters; label: string }[] = []
  if (filters.q.trim()) result.push({ key: 'q', label: '搜索：' + filters.q.trim() })
  if (filters.type) result.push({ key: 'type', label: typeLabels[filters.type] || filters.type })
  if (filters.status) result.push({ key: 'status', label: statusLabels[filters.status] || filters.status })
  if (filters.level) result.push({ key: 'level', label: levelLabels[filters.level] || filters.level })
  if (filters.year) result.push({ key: 'year', label: filters.year + ' 年' })
  if (filters.indicatorId) result.push({ key: 'indicatorId', label: indicators.value.find(item => String(item.id) === filters.indicatorId)?.name || '支撑项 #' + filters.indicatorId })
  return result
})
const hasFilters = computed(() => chips.value.length > 0 || filters.scope !== 'all')
const activeExtraCount = computed(() => [filters.level, filters.year, filters.indicatorId].filter(Boolean).length)
const emptyDescription = computed(() => error.value ? '成果加载失败' : hasFilters.value ? '没有找到符合条件的成果' : props.review ? '当前没有待处理成果' : '您的成果档案，从这里开始')
const exportNote = computed(() => filters.status ? '导出范围：' + statusLabels[filters.status] + ' · 沿用当前筛选条件' : '未指定状态时，导出仅包含审核通过成果')

function queryText(key: string) { const value = route.query[key]; return typeof value === 'string' ? value : '' }
function readRoute() {
  const status = queryText('status')
  const valid = (dictionary: Record<string, string>, value: string) => Object.keys(dictionary).includes(value) ? value : ''
  const indicator = queryText('indicatorId')
  const year = queryText('year')
  const scope = queryText('scope')
  return {
    filters: {
      q: queryText('q').slice(0, 250),
      type: valid(typeLabels, queryText('type')),
      level: valid(levelLabels, queryText('level')),
      status: status === 'ALL' ? '' : status ? valid(statusLabels, status) || defaultStatus : defaultStatus,
      year: /^\d{4}$/.test(year) && Number(year) >= 1900 && Number(year) <= 2100 ? year : '',
      indicatorId: /^[1-9]\d*$/.test(indicator) && Number.isSafeInteger(Number(indicator)) ? indicator : '',
      scope: ['all', 'mine', 'guided'].includes(scope) && !(scope === 'guided' && auth.user?.role === 'STUDENT') ? scope : 'all',
    } as Filters,
    page: Math.max(1, Math.min(1000000, Number.parseInt(queryText('page'), 10) || 1)),
    size: [10, 20, 50].includes(Number(queryText('size'))) ? Number(queryText('size')) : 10,
  }
}
function signature(state: { filters: Filters; page: number; size: number }) { return JSON.stringify(state) }
function stopSearchTimer() { if (searchTimer) clearTimeout(searchTimer); searchTimer = undefined; searchPending.value = false }
function stateQuery() {
  const values: Record<string, string> = {}
  for (const key of ['q', 'type', 'level', 'year', 'indicatorId'] as const) { const value = filters[key].trim(); if (value) values[key] = value }
  if (filters.status && filters.status !== defaultStatus) values.status = filters.status
  if (!filters.status && defaultStatus) values.status = 'ALL'
  if (filters.scope !== 'all') values.scope = filters.scope
  if (page.value !== 1) values.page = String(page.value)
  if (size.value !== 10) values.size = String(size.value)
  return values
}
async function commitFilters(resetPage = true) {
  stopSearchTimer()
  if (resetPage) page.value = 1
  const sameState = signature({ filters: { ...filters, q: filters.q.trim() }, page: page.value, size: size.value }) === committedSignature
  await router.replace({ path: route.path, query: stateQuery() })
  if (sameState) await load()
}
async function load() {
  controller?.abort()
  controller = new AbortController()
  const request = ++activeRequest
  loading.value = true
  error.value = ''
  try {
    const data = await api<Page<Achievement>>('/achievements' + query({ ...filters, q: filters.q.trim(), page: page.value, size: size.value }), { signal: controller.signal })
    if (request !== activeRequest) return
    if (data.total > 0 && page.value > Math.ceil(data.total / size.value)) { page.value = Math.ceil(data.total / size.value); await commitFilters(false); return }
    items.value = data.items
    total.value = data.total
  } catch (e) {
    if (request === activeRequest && (e as Error).name !== 'AbortError') { error.value = (e as Error).message; items.value = []; total.value = 0 }
  } finally { if (request === activeRequest) loading.value = false }
}
function reset() {
  stopSearchTimer()
  syncingQuery = true
  Object.assign(filters, defaults())
  syncingQuery = false
  advanced.value = false
  void commitFilters()
}
function clearChip(key: keyof Filters) { filters[key] = ''; void commitFilters() }
function create() { editing.value = null; editorOpen.value = true }
function showDetail(row: Achievement) { selectedId.value = row.id; detailOpen.value = true }
function edit(row: Achievement) { editing.value = row; detailOpen.value = false; editorOpen.value = true }
function canEdit(row: Achievement) { return ['DRAFT', 'REJECTED'].includes(row.status) && (auth.isAdmin || row.ownerId === auth.user?.id) }
function peopleCount(row: Achievement) { return new Set(row.participants.map(item => item.userId)).size }
async function remove(row: Achievement) {
  if (deletingId.value !== null) return
  try { await ElMessageBox.confirm('确认删除“' + row.title + '”？关联的材料也会移除，此操作无法撤销。', '删除成果', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '保留成果', distinguishCancelAndClose: true }) } catch { return }
  deletingId.value = row.id
  try { await write('/achievements/' + row.id, 'DELETE'); ElMessage.success('成果及关联材料已删除'); await load() } catch (e) { ElMessage.error((e as Error).message) } finally { deletingId.value = null }
}
async function saved(item: Achievement) { await load(); selectedId.value = item.id; detailOpen.value = true }
async function exportFile(kind: 'xlsx' | 'zip') {
  if (exporting.value) return
  exporting.value = kind
  try {
    await download('/exports/' + (kind === 'xlsx' ? 'achievements.xlsx' : 'materials.zip') + query({ ...filters, q: filters.q.trim(), status: filters.status || 'APPROVED' }), kind === 'xlsx' ? '成果清单.xlsx' : '支撑材料.zip')
    ElMessage.success('文件已生成，浏览器将开始下载')
  } catch (e) { ElMessage.error((e as Error).message) } finally { exporting.value = '' }
}
watch(() => filters.q, () => {
  if (syncingQuery) return
  stopSearchTimer()
  searchPending.value = true
  searchTimer = setTimeout(() => void commitFilters(), 350)
}, { flush: 'sync' })
watch(() => route.query, () => {
  const state = readRoute()
  const nextSignature = signature(state)
  if (nextSignature !== committedSignature) {
    stopSearchTimer()
    syncingQuery = true
    Object.assign(filters, state.filters)
    syncingQuery = false
    page.value = state.page
    size.value = state.size
    if (state.filters.year || state.filters.level || state.filters.indicatorId) advanced.value = true
    committedSignature = nextSignature
    void load()
  }
  const detail = queryText('detail')
  const creating = queryText('create') === '1'
  if (creating && !props.archive && !props.review) create()
  if (/^[1-9]\d*$/.test(detail) && Number.isSafeInteger(Number(detail))) { selectedId.value = Number(detail); detailOpen.value = true }
  if (creating || detail) { const cleaned = { ...route.query }; delete cleaned.create; delete cleaned.detail; void router.replace({ path: route.path, query: cleaned }) }
}, { immediate: true })
onMounted(async () => { try { indicators.value = await api<Indicator[]>('/indicators') } catch (e) { ElMessage.error((e as Error).message) } })
onBeforeUnmount(() => { stopSearchTimer(); controller?.abort() })
</script>

<template>
  <div class="page-heading ledger-heading">
    <div><div class="eyebrow">{{ heading.eyebrow }}</div><h1>{{ heading.title }} <span class="ledger-title-count">{{ loading ? '…' : total }}</span></h1><p>{{ heading.description }}</p></div>
    <div class="heading-actions">
      <el-tooltip :content="exportNote" placement="bottom"><el-button v-if="!review" :icon="Download" :loading="exporting === 'xlsx'" :disabled="!!exporting || loading || searchPending || !total" @click="exportFile('xlsx')">导出清单</el-button></el-tooltip>
      <el-button v-if="archive" type="primary" :icon="FolderOpened" :loading="exporting === 'zip'" :disabled="!!exporting || loading || searchPending || !total" @click="exportFile('zip')">打包支撑材料</el-button>
      <el-button v-else-if="!review" type="primary" :icon="Plus" @click="create">录入成果</el-button>
    </div>
  </div>

  <div v-if="archive" class="archive-banner ledger-archive-banner"><div class="archive-banner-icon"><el-icon :size="27"><FolderOpened /></el-icon></div><div><h3>让成果与支撑材料，一一对应</h3><p>按年度、类型和认证支撑项筛选，下载成果台账与材料索引。附件按成果类型和编号归档。</p></div><span class="archive-step-note">筛选成果 <el-icon><ArrowRight /></el-icon> 生成材料包</span></div>
  <div v-if="review" class="review-hint"><el-icon><CircleCheck /></el-icon><span>默认展示待审核成果。进入详情核对材料后，可通过或填写原因退回。</span></div>

  <section class="panel list-panel ledger-panel">
    <div class="ledger-toolbar-top"><div class="scope-tabs" aria-label="成果范围"><button v-for="scope in scopeOptions" :key="scope.value" :class="{ selected: filters.scope === scope.value }" :aria-pressed="filters.scope === scope.value" @click="filters.scope = scope.value; commitFilters()">{{ scope.label }}</button></div><div class="ledger-view-controls"><el-tooltip :content="compact ? '切换舒适间距' : '切换紧凑间距'"><el-button :icon="Operation" circle size="small" :aria-label="compact ? '切换舒适间距' : '切换紧凑间距'" :aria-pressed="compact" @click="compact = !compact" /></el-tooltip><el-tooltip content="刷新成果"><el-button :icon="Refresh" circle size="small" :loading="loading" aria-label="刷新成果" @click="load" /></el-tooltip></div></div>
    <div class="ledger-filters">
      <el-input v-model="filters.q" :prefix-icon="Search" placeholder="搜索成果名称或说明" aria-label="搜索成果名称或说明" maxlength="250" clearable class="ledger-search" @keyup.enter="commitFilters()" @clear="commitFilters()" />
      <el-select v-model="filters.type" placeholder="全部类型" aria-label="成果类型" clearable @change="commitFilters()"><el-option v-for="(label, key) in typeLabels" :key="key" :label="label" :value="key" /></el-select>
      <el-select v-model="filters.status" placeholder="全部状态" aria-label="审核状态" clearable @change="commitFilters()"><el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" /></el-select>
      <el-button :class="{ 'filters-active': advanced || activeExtraCount }" :aria-expanded="advanced" aria-controls="advanced-ledger-filters" @click="advanced = !advanced"><el-icon><Filter /></el-icon>更多筛选<span v-if="activeExtraCount" class="filter-count">{{ activeExtraCount }}</span><el-icon><ArrowUp v-if="advanced" /><ArrowDown v-else /></el-icon></el-button>
      <el-button type="primary" :icon="Search" :loading="searchPending" @click="commitFilters()">查询</el-button>
    </div>
    <div v-show="advanced" id="advanced-ledger-filters" class="ledger-advanced">
      <label><span>成果级别</span><el-select v-model="filters.level" placeholder="全部级别" aria-label="成果级别" clearable @change="commitFilters()"><el-option v-for="(label, key) in levelLabels" :key="key" :label="label" :value="key" /></el-select></label>
      <label><span>发生年度</span><el-select v-model="filters.year" placeholder="全部年度" aria-label="发生年度" clearable filterable @change="commitFilters()"><el-option v-for="value in years" :key="value" :label="value + ' 年'" :value="String(value)" /></el-select></label>
      <label class="indicator-label"><span>认证支撑项</span><el-select v-model="filters.indicatorId" placeholder="全部支撑项" aria-label="认证支撑项" clearable filterable @change="commitFilters()"><el-option v-for="indicator in indicators" :key="indicator.id" :label="indicator.code + ' · ' + indicator.name" :value="String(indicator.id)" /></el-select></label>
    </div>
    <div v-if="chips.length" class="filter-chips"><span>当前筛选</span><el-tag v-for="chip in chips" :key="chip.key" closable effect="light" round @close="clearChip(chip.key)">{{ chip.label }}</el-tag><button @click="reset"><el-icon><Close /></el-icon>重置筛选</button></div>
    <div class="list-caption"><span aria-live="polite">{{ loading ? '正在读取成果台账…' : '找到 ' + total + ' 项成果' }}<small v-if="searchPending">等待搜索…</small></span><span class="muted">{{ archive ? exportNote : '按成果发生日期倒序 · 同一成果保留一份档案' }}</span></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb-20"><el-button size="small" @click="load">重新加载</el-button></el-alert>

    <div class="desktop-ledger">
      <el-table v-loading="loading" :data="items" class="clean-table" :class="{ 'compact-ledger': compact }" row-key="id">
        <el-table-column label="成果名称" min-width="320"><template #default="{ row }"><div class="achievement-cell"><span class="achievement-type-icon" :class="'type-' + row.type.toLowerCase()"><el-icon :size="21"><Document /></el-icon></span><div><button class="table-title title-button" @click="showDetail(row)">{{ row.title }}</button><small>{{ typeLabels[row.type] }} <span>·</span> {{ peopleCount(row) }} 位参与 / 指导人员 <span v-if="row.indicatorIds.length">· {{ row.indicatorIds.length }} 个支撑项</span></small></div></div></template></el-table-column>
        <el-table-column label="级别" width="100"><template #default="{ row }"><span class="level-text" :class="{ national: row.level === 'NATIONAL' || row.level === 'INTERNATIONAL' }">{{ levelLabels[row.level] }}</span></template></el-table-column>
        <el-table-column label="发生日期" prop="achievedOn" width="120" />
        <el-table-column label="录入人" prop="ownerName" width="100" />
        <el-table-column label="状态" width="115"><template #default="{ row }"><el-tag :type="statusColors[row.status]" round effect="light">{{ statusLabels[row.status] }}</el-tag></template></el-table-column>
        <el-table-column label="操作" :width="review || archive ? 115 : 180" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="showDetail(row)">{{ review && row.status === 'PENDING' ? '查看并审核' : '查看详情' }}<el-icon v-if="archive"><ArrowRight /></el-icon></el-button><template v-if="!review && !archive && canEdit(row)"><el-button link type="primary" :disabled="deletingId !== null" @click="edit(row)">编辑</el-button><el-button link type="danger" :loading="deletingId === row.id" :disabled="deletingId !== null" @click="remove(row)">删除</el-button></template></template></el-table-column>
        <template #empty><el-empty :description="loading ? '正在加载成果…' : emptyDescription" :image-size="90"><template v-if="!loading"><el-button v-if="error" type="primary" plain @click="load">重新加载</el-button><el-button v-else-if="hasFilters" plain @click="reset">重置筛选</el-button><el-button v-else-if="!review && !archive" type="primary" :icon="Plus" @click="create">录入第一项成果</el-button></template></el-empty></template>
      </el-table>
    </div>

    <div v-loading="loading" class="mobile-ledger">
      <article v-for="row in items" :key="row.id" class="mobile-achievement">
        <div class="mobile-achievement-tags"><span>{{ typeLabels[row.type] }}</span><el-tag :type="statusColors[row.status]" round>{{ statusLabels[row.status] }}</el-tag></div>
        <button class="mobile-achievement-title" @click="showDetail(row)">{{ row.title }}</button>
        <div class="mobile-achievement-meta"><span><el-icon><Calendar /></el-icon>{{ row.achievedOn }}</span><span><el-icon><User /></el-icon>{{ row.ownerName }}</span><span>{{ levelLabels[row.level] }}</span></div>
        <div class="mobile-achievement-foot"><span>{{ peopleCount(row) }} 位关联人员<template v-if="row.indicatorIds.length"> · {{ row.indicatorIds.length }} 个支撑项</template></span><div><el-button v-if="!review && !archive && canEdit(row)" link type="primary" :disabled="deletingId !== null" @click="edit(row)">编辑</el-button><el-button v-if="!review && !archive && canEdit(row)" link type="danger" :loading="deletingId === row.id" :disabled="deletingId !== null" @click="remove(row)">删除</el-button><el-button link type="primary" @click="showDetail(row)">{{ review && row.status === 'PENDING' ? '查看并审核' : '查看详情' }}<el-icon><ArrowRight /></el-icon></el-button></div></div>
      </article>
      <el-empty v-if="!items.length" :description="loading ? '正在加载成果…' : emptyDescription" :image-size="80"><el-button v-if="!loading && hasFilters" @click="reset">重置筛选</el-button></el-empty>
    </div>
    <div class="pagination-row"><span>共 {{ total }} 项 · 第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50]" :pager-count="5" layout="sizes, prev, pager, next" background @current-change="commitFilters(false)" @size-change="commitFilters()" /></div>
  </section>
  <AchievementEditor v-model="editorOpen" :achievement="editing" @saved="saved" />
  <AchievementDetail v-model="detailOpen" :achievement-id="selectedId" @changed="load" @edit="edit" />
</template>

<style scoped>
.ledger-heading h1{display:flex;align-items:center;gap:13px}.ledger-title-count{display:inline-flex;align-items:center;justify-content:center;min-width:38px;height:29px;padding:0 10px;border-radius:9px;color:#6571cf;background:#eceffc;font-size:14px;font-weight:600;letter-spacing:0}.ledger-toolbar-top{display:flex;align-items:center;justify-content:space-between;padding-right:22px;border-bottom:1px solid #ebeff6}.ledger-toolbar-top .scope-tabs{border:0;flex:1}.ledger-view-controls{display:flex;gap:7px}.ledger-view-controls .el-button+.el-button{margin:0}.ledger-filters{display:flex;gap:10px;align-items:center;flex-wrap:wrap;padding:22px 24px 17px}.ledger-filters>.el-select{width:145px}.ledger-search{flex:1;min-width:230px}.ledger-filters .el-button+.el-button{margin-left:0}.ledger-filters .el-button .el-icon{margin:0 5px}.filters-active{color:#5968ed;border-color:#d6dcff;background:#f5f6ff}.filter-count{font-size:11px;color:#5968ed;background:#e7ebff;padding:1px 6px;border-radius:5px;margin-left:6px}.ledger-advanced{display:flex;gap:16px;padding:17px 24px 20px;background:#f8faff;border-top:1px solid #edf1f8;border-bottom:1px solid #edf1f8;flex-wrap:wrap}.ledger-advanced>label{display:block;min-width:160px;flex:1}.ledger-advanced>label>span{display:block;font-size:12px;font-weight:500;color:#77859b;margin-bottom:8px}.ledger-advanced .indicator-label{flex:1.7;min-width:220px}.filter-chips{display:flex;align-items:center;flex-wrap:wrap;gap:8px;padding:0 24px 17px}.filter-chips>span{color:#8b97aa;font-size:12px;margin-right:2px}.filter-chips>button{display:flex;align-items:center;gap:3px;border:0;background:none;color:#8995aa;font-size:12px;padding:4px}.filter-chips>button:hover{color:#5968ed}.filter-chips .el-tag{max-width:280px}.list-caption{border-top:1px solid #edf1f7}.list-caption>span:first-child{color:#5f708a;font-weight:500}.list-caption small{font-size:11px;color:#9aa7bb;margin-left:8px}.archive-step-note{display:flex;align-items:center;gap:8px;color:#778ab3;font-size:12px;margin-left:auto;white-space:nowrap}.review-hint{display:flex;align-items:center;gap:9px;font-size:12px;line-height:1.8;color:#677f9b;margin:-4px 0 20px;padding:12px 16px;border:1px solid #e1eaf8;background:#f4f8ff;border-radius:10px}.review-hint>.el-icon{color:#678cce;flex-shrink:0}.desktop-ledger :deep(.compact-ledger .el-table__cell){padding-top:10px!important;padding-bottom:10px!important}.mobile-ledger{display:none}.achievement-type-icon.type-competition{color:#c18e39;background:#fff6e5}.achievement-type-icon.type-paper,.achievement-type-icon.type-research{color:#7f6cc6;background:#f3effc}.achievement-type-icon.type-scholarship,.achievement-type-icon.type-certification{color:#269a83;background:#ebf8f3}
@media(max-width:900px){.ledger-filters{gap:9px}.ledger-search{flex-basis:100%;width:100%}.ledger-filters>.el-select{flex:1;min-width:110px}.archive-step-note{display:none}.ledger-filters>.el-button{padding:8px 12px}.list-caption .muted{font-size:11px;max-width:48%;text-align:right;line-height:1.6}}
@media(max-width:700px){.desktop-ledger{display:none}.mobile-ledger{display:grid;gap:12px;background:#f6f8fc;padding:14px;min-height:210px}.mobile-achievement{border:1px solid #e7ecf4;border-radius:12px;background:#fff;padding:16px}.mobile-achievement-tags{display:flex;align-items:center;justify-content:space-between;gap:10px;font-size:11px;color:#8290a5}.mobile-achievement-title{font-size:14px;color:#354966;font-weight:600;line-height:1.7;background:none;border:0;padding:12px 0;text-align:left;width:100%}.mobile-achievement-meta{display:flex;align-items:center;flex-wrap:wrap;gap:12px;color:#8996aa;font-size:11px}.mobile-achievement-meta>span{display:flex;align-items:center;gap:4px}.mobile-achievement-foot{display:flex;align-items:center;justify-content:space-between;gap:6px;margin-top:14px;border-top:1px solid #f0f3f8;padding-top:10px}.mobile-achievement-foot>span{font-size:10px;color:#99a5b6}.mobile-achievement-foot .el-button{font-size:12px;padding:4px 2px}.ledger-toolbar-top{padding-right:14px}.ledger-toolbar-top .scope-tabs{padding-left:16px;gap:18px}.ledger-toolbar-top .scope-tabs button{font-size:12px}.ledger-view-controls .el-button:first-child{display:none}.ledger-filters{padding:17px 16px 14px;gap:8px}.ledger-filters>.el-select{min-width:100px}.ledger-filters>.el-button{font-size:12px}.ledger-advanced{padding:15px 16px;gap:14px}.ledger-advanced>label{min-width:120px}.filter-chips{padding:0 16px 14px}.filter-chips>span:first-child{display:none}.filter-chips .el-tag{max-width:240px}.list-caption{padding:14px 16px;align-items:flex-start}.list-caption .muted{display:none}.ledger-panel .pagination-row{padding:16px;flex-wrap:wrap;justify-content:center;gap:12px}.ledger-panel .pagination-row>span{font-size:11px;width:100%;text-align:center}.ledger-title-count{height:25px;font-size:12px;min-width:32px}.ledger-archive-banner{padding:17px;align-items:flex-start}.ledger-archive-banner .archive-banner-icon{width:40px;height:40px}.ledger-archive-banner p{font-size:11px}.ledger-heading .heading-actions{flex-wrap:wrap}.ledger-heading .heading-actions .el-button{font-size:12px}}
</style>

