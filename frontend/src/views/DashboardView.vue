<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Collection, User, School, Clock, Plus, ArrowRight, Refresh, FolderOpened, CircleCheck, TrendCharts, Calendar, InfoFilled } from '@element-plus/icons-vue'
import { api, query } from '../api'
import { useAuth } from '../stores/auth'
import { levelLabels, typeLabels, statusLabels, statusColors, localToday } from '../constants'
import type { Achievement, Page, Stats, StatItem } from '../types'
import ChartPanel from '../components/ChartPanel.vue'

const router = useRouter()
const auth = useAuth()
const loading = ref(true)
const error = ref('')
const year = ref('')
const trend = ref<'month' | 'year'>('month')
const stats = ref<Stats | null>(null)
const recent = ref<Achievement[]>([])
const refreshedAt = ref('')
let controller: AbortController | undefined
let requestId = 0
const palette = ['#5968ed', '#12a88e', '#f2b35a', '#9181df', '#50a5ce', '#dd8a9e', '#85aa81', '#8b9ab5']

async function load() {
  controller?.abort()
  controller = new AbortController()
  const current = ++requestId
  loading.value = true
  error.value = ''
  try {
    const [summary, records] = await Promise.all([
      api<Stats>('/stats' + query({ year: year.value }), { signal: controller.signal }),
      api<Page<Achievement>>('/achievements' + query({ year: year.value, page: 1, size: 5 }), { signal: controller.signal }),
    ])
    if (current !== requestId) return
    stats.value = summary
    recent.value = records.items
    refreshedAt.value = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Shanghai', hour12: false }).format(new Date())
  } catch (e) {
    if (current === requestId && (e as Error).name !== 'AbortError') error.value = (e as Error).message
  } finally {
    if (current === requestId) loading.value = false
  }
}

function openRecords(params: Record<string, string> = {}) {
  void router.push({ path: '/achievements', query: { ...params, ...(year.value ? { year: year.value } : {}) } })
}
const cards = computed(() => [
  { key: 'total', label: '已通过成果', value: stats.value?.total, unit: '项', icon: Collection, color: 'blue', note: '每项共同成果仅计一次', link: true },
  { key: 'teachers', label: '参与教师', value: stats.value?.teachers, unit: '人', icon: School, color: 'green', note: '已通过成果中的教师人数', link: false },
  { key: 'students', label: '参与学生', value: stats.value?.students, unit: '人', icon: User, color: 'purple', note: '已通过成果中的学生人数', link: false },
  { key: 'pending', label: '待审核成果', value: stats.value?.pending, unit: '项', icon: Clock, color: 'orange', note: auth.isAdmin ? '点击查看审核队列' : '点击查看待审记录', link: true },
])
function openCard(key: string) {
  if (key === 'total') openRecords({ status: 'APPROVED' })
  if (key === 'pending') void router.push({ path: auth.isAdmin ? '/reviews' : '/achievements', query: { status: 'PENDING', ...(year.value ? { year: year.value } : {}) } })
}
const summary = computed(() => {
  const types = stats.value?.byType.length || 0
  return stats.value?.total ? '已覆盖 ' + types + ' 类成果，累计 ' + stats.value.participants + ' 次参与与指导记录。' : '从录入第一项成果开始，逐步建立专业成果档案。'
})
const total = computed(() => stats.value?.total || 0)
const trendData = computed<StatItem[]>(() => {
  if (trend.value === 'year') return [...(stats.value?.byYear || [])].sort((a, b) => a.name.localeCompare(b.name))
  const values = new Map((stats.value?.byMonth || []).map(item => [item.name, item.value]))
  const keys = [...values.keys()].sort()
  if (!keys.length) return []
  const first = year.value ? year.value + '-01' : keys[0]!
  const last = year.value ? year.value + '-12' : keys[keys.length - 1]!
  const start = Number(first.slice(0, 4)) * 12 + Number(first.slice(5, 7)) - 1
  const end = Number(last.slice(0, 4)) * 12 + Number(last.slice(5, 7)) - 1
  if (end - start > 240) return keys.map(name => ({ name, value: values.get(name) || 0 }))
  return Array.from({ length: end - start + 1 }, (_, index) => {
    const value = start + index
    const name = String(Math.floor(value / 12)) + '-' + String(value % 12 + 1).padStart(2, '0')
    return { name, value: values.get(name) || 0 }
  })
})
const typeOption = computed(() => ({
  color: palette, tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 项 · {d}%' },
  legend: { bottom: 3, icon: 'circle', itemWidth: 9, itemGap: 14, textStyle: { color: '#6e7c93', fontSize: 12 } },
  title: { text: String(total.value), subtext: '已通过成果', left: 'center', top: '32%', itemGap: 6, textStyle: { fontSize: 31, fontWeight: 650, color: '#22334e' }, subtextStyle: { fontSize: 12, color: '#8390a4' } },
  series: [{ type: 'pie', radius: ['48%', '68%'], center: ['50%', '43%'], avoidLabelOverlap: true, cursor: 'pointer', itemStyle: { borderRadius: 7, borderColor: '#fff', borderWidth: 4 }, label: { show: false }, emphasis: { scaleSize: 6 }, data: (stats.value?.byType || []).map(item => ({ name: typeLabels[item.name] || item.name, value: item.value })) }],
}))
const trendOption = computed(() => ({
  color: ['#5968ed'], tooltip: { trigger: 'axis', backgroundColor: '#fff', borderColor: '#e6eaf4', padding: [12, 16] },
  grid: { left: 40, right: 24, top: 30, bottom: 38 },
  xAxis: { type: 'category', boundaryGap: false, data: trendData.value.map(item => year.value && trend.value === 'month' ? Number(item.name.slice(5)) + '月' : item.name), axisLine: { lineStyle: { color: '#e7ebf2' } }, axisTick: { show: false }, axisLabel: { color: '#7e8ba0', fontSize: 11, hideOverlap: true } },
  yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#7e8ba0', fontSize: 11 }, splitLine: { lineStyle: { color: '#eef1f7', type: 'dashed' } } },
  series: [{ name: '已通过成果', type: 'line', smooth: 0.3, showSymbol: trendData.value.length <= 16, symbolSize: 7, lineStyle: { width: 3 }, itemStyle: { borderWidth: 3, borderColor: '#fff' }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(89,104,237,0.17)' }, { offset: 1, color: 'rgba(89,104,237,0.005)' }] } }, data: trendData.value.map(item => item.value) }],
}))
const levelOption = computed(() => ({
  color: ['#1caa92'], tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 70, right: 35, top: 15, bottom: 25 },
  xAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#8792a3' }, splitLine: { lineStyle: { color: '#eef2f7', type: 'dashed' } } },
  yAxis: { type: 'category', data: (stats.value?.byLevel || []).map(item => levelLabels[item.name] || item.name).reverse(), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#65748a', fontSize: 12 } },
  series: [{ name: '成果数量', type: 'bar', barMaxWidth: 18, cursor: 'pointer', itemStyle: { borderRadius: [0, 6, 6, 0] }, label: { show: true, position: 'right', color: '#5d7f79' }, data: (stats.value?.byLevel || []).map(item => item.value).reverse() }],
}))
function chartSelect(kind: 'type' | 'level', event: { name?: string }) {
  const dictionary = kind === 'type' ? typeLabels : levelLabels
  const selected = Object.entries(dictionary).find(([, label]) => label === event.name)?.[0]
  if (selected) openRecords({ status: 'APPROVED', [kind]: selected })
}
const years = Array.from({ length: 18 }, (_, index) => Number(localToday().slice(0, 4)) - index)
onMounted(load)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <div class="dashboard-welcome welcome-banner">
    <div class="welcome-copy">
      <div class="eyebrow">ACHIEVEMENT WORKSPACE <span class="welcome-dot"></span> 专业建设工作台</div>
      <h1>专业成果概览</h1>
      <p>您好，{{ auth.user?.name }}。教学、科研与学生成长，在这里汇聚。</p>
      <span class="welcome-summary">{{ summary }}</span>
    </div>
    <div class="welcome-actions">
      <div class="period-label"><el-icon><Calendar /></el-icon><span>统计范围</span></div>
      <div class="heading-actions">
        <el-select v-model="year" aria-label="统计年度" placeholder="全部年度" clearable class="year-select" @change="load">
          <el-option v-for="value in years" :key="value" :label="value + ' 年'" :value="String(value)" />
        </el-select>
        <el-tooltip content="刷新统计" placement="bottom"><el-button :icon="Refresh" circle :loading="loading" aria-label="刷新统计" @click="load" /></el-tooltip>
        <el-button type="primary" :icon="Plus" @click="router.push('/achievements?create=1')">录入成果</el-button>
      </div>
      <span class="refresh-control" aria-live="polite">{{ loading ? '正在更新数据…' : refreshedAt ? '更新于 ' + refreshedAt : '准备加载数据' }}</span>
    </div>
  </div>

  <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-20"><el-button size="small" @click="load">重新加载</el-button></el-alert>

  <div v-if="!stats && loading" class="stats-grid dashboard-skeleton" aria-label="正在加载成果统计">
    <article v-for="index in 4" :key="index" class="stat-card"><el-skeleton :rows="2" animated /></article>
  </div>
  <div v-else class="stats-grid">
    <article v-for="card in cards" :key="card.key" class="stat-card" :class="{ 'stat-card-link': card.link }">
      <div class="stat-top"><span>{{ card.label }}</span><div class="stat-icon" :class="card.color"><el-icon :size="22"><component :is="card.icon" /></el-icon></div></div>
      <div class="stat-value">{{ card.value ?? '—' }}<small>{{ card.unit }}</small></div>
      <div class="stat-bottom"><p>{{ card.note }}</p><button v-if="card.link" class="stat-jump" :aria-label="'查看' + card.label" @click="openCard(card.key)"><el-icon><ArrowRight /></el-icon></button></div>
    </article>
  </div>

  <div v-if="stats" class="task-strip">
    <div class="task-strip-icon"><el-icon :size="21"><component :is="stats?.pending ? Clock : CircleCheck" /></el-icon></div>
    <div class="task-strip-copy"><strong>{{ stats?.pending ? (auth.isAdmin ? '有 ' + stats.pending + ' 项成果等待您审核' : '有 ' + stats.pending + ' 项关联成果正在审核') : '当前没有待审核成果' }}</strong><span>{{ auth.isAdmin ? '核对成果信息与支撑材料，保持专业台账及时更新。' : '审核通过后，成果将计入正式统计与认证支撑材料。' }}</span></div>
    <el-button text type="primary" @click="openCard('pending')">{{ auth.isAdmin ? '前往审核' : '查看进度' }}<el-icon><ArrowRight /></el-icon></el-button>
  </div>

  <div class="charts-grid">
    <section class="panel trend-panel">
      <div class="panel-heading"><div><h2><el-icon><TrendCharts /></el-icon> 成果产出趋势</h2><p>按成果发生时间汇总，空缺月份计为 0</p></div><div class="trend-switch" aria-label="趋势统计粒度"><button :class="{ active: trend === 'month' }" :aria-pressed="trend === 'month'" @click="trend = 'month'">按月</button><button :class="{ active: trend === 'year' }" :aria-pressed="trend === 'year'" @click="trend = 'year'">按年</button></div></div>
      <ChartPanel v-if="trendData.length" :option="trendOption" :label="trend === 'month' ? '按月份统计的已通过成果折线图' : '按年度统计的已通过成果折线图'" />
      <el-empty v-else description="当前范围暂无已通过成果" :image-size="75"><el-button plain type="primary" @click="router.push('/achievements?create=1')">录入第一项成果</el-button></el-empty>
      <div class="chart-footer"><span><span class="chart-legend-dot"></span> 已通过成果</span><span>{{ year ? year + ' 年' : '全部年度' }}</span></div>
    </section>
    <section class="panel">
      <div class="panel-heading"><div><h2>成果类型分布</h2><p>点击图表，可查看对应成果</p></div><span class="subtle-badge">{{ stats?.byType.length || 0 }} 类</span></div>
      <ChartPanel v-if="stats?.byType.length" :option="typeOption" label="成果类型分布环形图，点击类别查看明细" @select="chartSelect('type', $event)" />
      <el-empty v-else description="暂无分类数据" :image-size="75" />
      <div class="chart-footer"><span>教学 · 科研 · 学生成长</span><button @click="openRecords({ status: 'APPROVED' })">查看全部<el-icon><ArrowRight /></el-icon></button></div>
    </section>
  </div>

  <div class="charts-grid second-row">
    <section class="panel"><div class="panel-heading"><div><h2>成果级别分布</h2><p>了解成果的影响范围，点击可查看明细</p></div></div><ChartPanel v-if="stats?.byLevel.length" :option="levelOption" label="成果级别分布条形图" @select="chartSelect('level', $event)" /><el-empty v-else description="暂无级别数据" :image-size="75" /></section>
    <section class="panel"><div class="panel-heading"><div><h2>成果贡献榜</h2><p>同一成果中的同一人员只计一次</p></div><span class="subtle-badge">TOP 5</span></div><div v-if="stats?.topContributors.length" class="contributors"><div v-for="(item, index) in stats.topContributors.slice(0, 5)" :key="item.name + index" class="contributor"><span class="rank" :class="{ first: index === 0 }">{{ String(index + 1).padStart(2, '0') }}</span><span class="contributor-name">{{ item.name }}</span><div class="contributor-track"><div :style="{ width: Math.max(5, item.value / (stats.topContributors[0]?.value || 1) * 100) + '%' }"></div></div><strong>{{ item.value }} <small>项</small></strong></div></div><el-empty v-else description="暂无贡献记录" :image-size="75" /></section>
  </div>

  <section class="panel recent-panel">
    <div class="panel-heading"><div><h2>近期成果</h2><p>{{ year ? year + ' 年 · ' : '' }}按成果发生日期排序</p></div><el-button text type="primary" @click="openRecords()">全部成果<el-icon><ArrowRight /></el-icon></el-button></div>
    <el-table v-loading="loading" :data="recent" class="clean-table recent-desktop">
      <el-table-column label="成果名称" min-width="260"><template #default="{ row }"><button class="table-title title-button" @click="router.push('/achievements?detail=' + row.id)">{{ row.title }}</button></template></el-table-column>
      <el-table-column label="类型" width="130"><template #default="{ row }">{{ typeLabels[row.type] }}</template></el-table-column>
      <el-table-column label="发生日期" prop="achievedOn" width="125" />
      <el-table-column label="录入人" prop="ownerName" width="110" />
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusColors[row.status]" effect="light" round>{{ statusLabels[row.status] }}</el-tag></template></el-table-column>
      <template #empty><el-empty description="还没有成果，点击「录入成果」开始记录" :image-size="70" /></template>
    </el-table>
    <div class="recent-mobile"><button v-for="row in recent" :key="row.id" @click="router.push('/achievements?detail=' + row.id)"><span class="recent-mobile-title">{{ row.title }}</span><span class="recent-mobile-meta">{{ typeLabels[row.type] }} · {{ row.achievedOn }}</span><el-tag :type="statusColors[row.status]" size="small" round>{{ statusLabels[row.status] }}</el-tag></button><el-empty v-if="!recent.length" description="当前范围暂无成果" :image-size="65" /></div>
  </section>
  <div class="stats-note"><el-icon><InfoFilled /></el-icon><span>正式统计仅包含审核通过成果；多人共同成果计为 1 项。{{ !auth.isAdmin ? '当前展示您有权查看的数据。' : '' }}</span><router-link to="/archive"><el-icon><FolderOpened /></el-icon> 整理认证材料</router-link></div>
</template>

<style scoped>
.recent-mobile{display:none}
@media(max-width:700px){.recent-desktop{display:none}.recent-mobile{display:grid;padding:0 20px 12px}.recent-mobile>button{display:grid;grid-template-columns:1fr auto;gap:7px 10px;border:0;background:none;text-align:left;padding:17px 0;border-bottom:1px solid #eff2f7;align-items:center}.recent-mobile>button:last-child{border-bottom:0}.recent-mobile-title{grid-column:1/-1;color:#42536e;font-size:13px;line-height:1.8;font-weight:500}.recent-mobile-meta{font-size:11px;color:#8996aa}}
.dashboard-welcome{display:flex;justify-content:space-between;gap:28px;align-items:center;padding:28px 30px;margin-bottom:24px;background:linear-gradient(110deg,#f0f2ff 0%,#f7f9ff 62%,#effaf7 100%);border:1px solid #e4e9fb;border-radius:18px;position:relative;overflow:hidden}
.welcome-copy,.welcome-actions{position:relative;z-index:1}.welcome-copy h1{font-size:30px;line-height:1.4;letter-spacing:-.7px}.welcome-copy p{font-size:14px;color:#66758e;margin-top:10px;line-height:1.8}.welcome-summary{font-size:12px;color:#7b88a0;display:block;margin-top:12px}.welcome-dot{display:inline-block;width:5px;height:5px;background:#14a38b;border-radius:50%;margin:0 9px;vertical-align:middle}.welcome-actions{flex-shrink:0}.period-label{display:flex;align-items:center;gap:6px;color:#7a87a0;font-size:12px;margin-bottom:10px}.refresh-control{display:block;text-align:right;font-size:11px;color:#8d9ab0;margin-top:10px}.stat-bottom{display:flex;align-items:center;justify-content:space-between;gap:7px}.stat-bottom p{margin:0}.stat-jump{display:flex;align-items:center;justify-content:center;width:28px;height:28px;background:#f0f3ff;border:0;border-radius:50%;color:#5968ed;flex-shrink:0}.stat-jump:hover{background:#5968ed;color:#fff}.stat-card-link{transition:transform .18s,border-color .18s}.stat-card-link:hover{transform:translateY(-2px);border-color:#ccd4fb}
.task-strip{display:flex;align-items:center;gap:14px;background:#fff;border:1px solid #e7ebf4;border-radius:13px;margin:18px 0 24px;padding:15px 20px}.task-strip-icon{width:39px;height:39px;display:flex;align-items:center;justify-content:center;background:#fff5e7;color:#d89839;border-radius:11px;flex-shrink:0}.task-strip-copy{flex:1;min-width:0}.task-strip-copy strong{display:block;font-size:13px;color:#42526d;font-weight:600}.task-strip-copy span{display:block;font-size:12px;color:#8a96aa;margin-top:6px;line-height:1.6}.trend-switch{display:flex;background:#f1f3f9;padding:3px;border-radius:8px;flex-shrink:0}.trend-switch button{border:0;background:none;padding:6px 11px;color:#8290a6;font-size:12px;border-radius:6px}.trend-switch button.active{background:#fff;color:#5968ed;box-shadow:0 2px 6px #1d315b10;font-weight:600}.panel-heading h2 .el-icon{vertical-align:-2px;margin-right:4px;color:#6c7ade}.chart-footer{border-top:1px solid #f0f2f7;padding:14px 23px;display:flex;align-items:center;justify-content:space-between;gap:10px;color:#8a96aa;font-size:11px}.chart-footer button{background:none;border:0;color:#5968ed;display:flex;align-items:center;gap:4px;padding:0;font-size:12px}.chart-legend-dot{display:inline-block;width:7px;height:7px;border-radius:50%;background:#5968ed;margin-right:6px}.stats-note .el-icon{flex-shrink:0}.stats-note a{margin-left:auto;display:flex;align-items:center;gap:5px;color:#7c87b4;white-space:nowrap}.dashboard-skeleton .stat-card{min-height:150px}
@media(max-width:1100px){.dashboard-welcome{padding:24px;align-items:flex-start;flex-direction:column;gap:20px}.welcome-actions{width:100%}.welcome-actions .heading-actions{justify-content:flex-start}.refresh-control{text-align:left}.period-label{display:none}}
@media(max-width:640px){.dashboard-welcome{padding:22px 19px;border-radius:14px}.welcome-copy h1{font-size:25px}.welcome-copy .eyebrow{font-size:9px;letter-spacing:1px}.welcome-copy p{font-size:13px}.welcome-summary{font-size:11px}.welcome-actions .heading-actions{flex-wrap:wrap}.task-strip{padding:14px;gap:11px;align-items:flex-start}.task-strip-copy span{font-size:11px}.task-strip>.el-button{font-size:12px;padding:3px;white-space:nowrap;align-self:center}.task-strip-icon{width:32px;height:32px}.stats-note{flex-wrap:wrap}.stats-note a{margin-left:0}.trend-switch button{padding:6px 9px}.chart-footer{padding:12px 18px}}
@media(prefers-reduced-motion:reduce){.stat-card-link{transition:none}.stat-card-link:hover{transform:none}}
</style>

