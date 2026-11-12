<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Delete, DocumentChecked, InfoFilled, Refresh } from '@element-plus/icons-vue'
import { api, write } from '../api'
import { useAuth } from '../stores/auth'
import { detailFields, typeLabels, levelLabels, localToday } from '../constants'
import type { Achievement, AchievementInput, Indicator, User } from '../types'

const props = defineProps<{ modelValue: boolean; achievement?: Achievement | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [achievement: Achievement] }>()
const auth = useAuth()
const formRef = ref<FormInstance>()
const saving = ref(false), loading = ref(false), error = ref(''), referenceError = ref(''), attempted = ref(false)
const people = ref<User[]>([]), indicators = ref<Indicator[]>([]), baseline = ref('')
let generation = 0, confirmingClose = false
const form = reactive<AchievementInput>({ title: '', type: 'COMPETITION', level: 'NATIONAL', achievedOn: localToday(), description: '', details: {}, participants: [], indicatorIds: [] })
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const fields = computed(() => detailFields[form.type] || [])
const recorder = computed(() => props.achievement?.ownerName || auth.user?.name || '当前账号')
const dateHelp = computed(() => form.type === 'PAPER' ? '填写正式录用或发表日期，并在论文状态中注明。' : form.type === 'PATENT' ? '填写申请或授权日期，并与专利状态保持一致。' : '以证书、立项通知或正式文件记载的实际发生日期为准。')
const detailHelp = computed(() => ({ TEACHING: '对应课程建设、教学改革或教学奖励文件填写。', RESEARCH: '填写正式立项信息；项目经费单位为万元。', COMPETITION: '填写竞赛全称、获奖等次及主办单位，便于按类别归档。', SCHOLARSHIP: '区分获奖日期和所属学年；奖学金金额单位为元。', PATENT: '申请与授权属于不同状态，请如实填写专利号及当前状态。', PAPER: '请区分正式录用与已经发表，DOI 或检索号可暂留空。', SOFTWARE: '填写登记证书上的登记号与对应的软件版本。', CERTIFICATION: '填写发证机构与证书有效期，长期有效的证书可直接注明。' })[form.type])
const filledDetails = computed(() => fields.value.filter(field => form.details[field.key]?.trim()).length)
function validDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return false
  const [year = 0, month = 0, day = 0] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return year >= 1900 && year <= 2100 && date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}
const completed = computed(() => [!!form.title.trim(), !!form.type, !!form.level, validDate(form.achievedOn)].filter(Boolean).length)
function snapshot() { return JSON.stringify({ ...form, participants: form.participants.map(p => ({ userId: p.userId, role: p.role, sortOrder: p.sortOrder })) }) }
const dirty = computed(() => !!baseline.value && snapshot() !== baseline.value)
const participantIssues = computed(() => form.participants.map((p, index) => {
  if (!p.userId) return attempted.value ? '请选择人员，或移除这条空行。' : ''
  const person = people.value.find(u => u.id === p.userId)
  if (!loading.value && !referenceError.value && !person) return '该账号当前不可选，请重新选择人员或移除此行。'
  if (person && p.role === 'ADVISOR' && person.role !== 'TEACHER') return '指导关系只能选择教师账号。'
  if (form.participants.some((other, at) => at !== index && other.userId === p.userId && other.role === p.role)) return `${person?.name || p.name || '此人员'}已存在相同角色，请保留一条关系。`
  return ''
}))
const rules: FormRules = {
  title: [{ validator: (_rule, value: string, callback) => callback(!value?.trim() ? new Error('请填写成果名称，不能只输入空格。') : value.length > 250 ? new Error('成果名称最多250个字符。') : undefined), trigger: 'blur' }],
  type: [{ required: true, message: '请选择成果类型。', trigger: 'change' }],
  level: [{ required: true, message: '请选择成果级别。', trigger: 'change' }],
  achievedOn: [{ validator: (_rule, value: string, callback) => callback(validDate(value) ? undefined : new Error('请选择有效日期，年份范围为1900—2100年。')), trigger: 'change' }],
}
async function loadReferences(current = generation) {
  loading.value = true; referenceError.value = ''
  try {
    const [users, items] = await Promise.all([api<User[]>('/users/options'), api<Indicator[]>('/indicators')])
    if (current === generation) { people.value = users; indicators.value = items }
  } catch (e) { if (current === generation) referenceError.value = (e as Error).message }
  finally { if (current === generation) loading.value = false }
}
watch([() => props.modelValue, () => props.achievement?.id], async ([open]) => {
  const current = ++generation
  if (!open) return
  error.value = ''; referenceError.value = ''; attempted.value = false; loading.value = true
  const a = props.achievement
  Object.assign(form, a ? { title: a.title, type: a.type, level: a.level, achievedOn: a.achievedOn, description: a.description || '', details: { ...a.details }, participants: a.participants.map(p => ({ ...p })), indicatorIds: [...a.indicatorIds] } : { title: '', type: 'COMPETITION', level: 'NATIONAL', achievedOn: localToday(), description: '', details: {}, participants: auth.user ? [{ userId: auth.user.id, role: 'AUTHOR', sortOrder: 1 }] : [], indicatorIds: [] })
  baseline.value = snapshot()
  await nextTick()
  if (current !== generation) return
  formRef.value?.clearValidate()
  void loadReferences(current)
}, { immediate: true })
function addParticipant() { if (form.participants.length < 100) form.participants.push({ userId: 0, role: 'MEMBER', sortOrder: form.participants.length + 1 }) }
function roleChange(index: number) {
  const p = form.participants[index]
  if (p?.role === 'ADVISOR' && p.userId && people.value.find(u => u.id === p.userId)?.role !== 'TEACHER') { p.userId = 0; ElMessage.info('指导教师请从教师账号中重新选择。') }
}
function duplicateOption(index: number, userId: number) { return form.participants.some((p, at) => at !== index && p.userId === userId && p.role === form.participants[index]?.role) }
async function mayClose() {
  if (saving.value || confirmingClose) return false
  if (!dirty.value) return true
  confirmingClose = true
  try { await ElMessageBox.confirm('有尚未保存的修改，关闭后这些修改将丢失。', '放弃本次修改？', { confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning', autofocus: false }); return true }
  catch { return false } finally { confirmingClose = false }
}
async function beforeClose(done: () => void) { if (await mayClose()) done() }
async function requestClose() { if (await mayClose()) visible.value = false }
onBeforeRouteLeave(async to => {
  if (!visible.value || to.path === '/login') return true
  return await mayClose()
})
function beforeUnload(event: BeforeUnloadEvent) { if (visible.value && dirty.value && !saving.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => { generation++; window.removeEventListener('beforeunload', beforeUnload) })
async function save() {
  if (saving.value || loading.value || referenceError.value) return
  saving.value = true; attempted.value = true; error.value = ''
  try {
    if (!await formRef.value?.validate().catch(() => false)) { ElMessage.warning('请先检查标出的必填信息。'); return }
    if (participantIssues.value.some(Boolean)) { error.value = '参与关系尚未填写完整，请检查人员列表中的提示。'; await nextTick(); (formRef.value?.$el as HTMLElement | undefined)?.querySelector('.participant-entry.invalid')?.scrollIntoView({ behavior: 'smooth', block: 'center' }); return }
    const details: Record<string, string> = props.achievement?.type === form.type ? { ...form.details } : {}
    fields.value.forEach(field => { details[field.key] = (form.details[field.key] || '').trim() })
    const result = await write<Achievement>(props.achievement ? `/achievements/${props.achievement.id}` : '/achievements', props.achievement ? 'PUT' : 'POST', { ...form, title: form.title.trim(), description: form.description.trim(), details, participants: form.participants.map((p, i) => ({ userId: p.userId, role: p.role, sortOrder: i + 1 })) })
    baseline.value = snapshot(); ElMessage.success(props.achievement ? '修改已保存，可继续完善材料。' : '草稿已保存，接下来可上传材料并提交审核。')
    visible.value = false; emit('saved', result)
  } catch (e) { error.value = (e as Error).message }
  finally { saving.value = false }
}
</script>

<template>
  <el-dialog v-model="visible" :title="achievement ? '编辑成果信息' : '录入新成果'" width="min(900px, calc(100vw - 28px))" top="4vh" :before-close="beforeClose" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close class="achievement-editor">
    <div class="editor-intro"><div class="editor-intro-icon"><el-icon :size="24"><DocumentChecked /></el-icon></div><div><strong>{{ achievement ? '完善这项成果的记录' : '从一份完整、可追溯的记录开始' }}</strong><p>基本信息 → 参与与指导 → 认证支撑；保存后可继续上传证明材料。</p></div><el-tag :type="dirty ? 'warning' : 'info'" effect="plain" size="small">{{ dirty ? '有未保存修改' : '尚无新修改' }}</el-tag></div>
    <div class="editor-completion"><span>必填信息 <strong>{{ completed }} / 4</strong></span><el-progress :percentage="completed * 25" :show-text="false" :stroke-width="5" /><small>{{ recorder }} 录入</small></div>
    <div class="editor-scroll" v-loading="loading" element-loading-text="正在加载可选人员与支撑项…">
      <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" class="editor-alert" role="alert" />
      <el-alert v-if="referenceError" type="error" title="人员与支撑项暂未加载完成" :description="referenceError" show-icon :closable="false" class="editor-alert"><el-button text type="primary" :icon="Refresh" @click="loadReferences()">重新加载</el-button></el-alert>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" scroll-to-error>
        <section class="editor-section"><div class="editor-section-heading"><span class="editor-step">01</span><div><h3>基本信息</h3><p>标有 * 的信息用于成果检索和年度统计。</p></div></div>
          <el-form-item label="成果名称" prop="title" required><el-input v-model="form.title" maxlength="250" show-word-limit placeholder="填写项目、竞赛、论文或其他成果的完整名称" /></el-form-item>
          <div class="form-grid three"><el-form-item label="成果类型" prop="type"><el-select v-model="form.type" placeholder="选择成果类型"><el-option v-for="(label, key) in typeLabels" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item label="成果级别" prop="level"><el-select v-model="form.level" placeholder="选择认定级别"><el-option v-for="(label, key) in levelLabels" :key="key" :label="label" :value="key" /></el-select></el-form-item><el-form-item label="成果发生日期" prop="achievedOn" required><el-date-picker v-model="form.achievedOn" type="date" value-format="YYYY-MM-DD" :disabled-date="(date: Date) => date.getFullYear() < 1900 || date.getFullYear() > 2100" placeholder="选择实际发生日期" /></el-form-item></div>
          <p class="editor-context"><el-icon><InfoFilled /></el-icon>{{ dateHelp }}</p>
          <div class="category-card"><div class="category-card-heading"><strong>{{ typeLabels[form.type] }} · 分类信息</strong><small>选填 · 已填 {{ filledDetails }} / {{ fields.length }}</small></div><p>{{ detailHelp }} 不适用的项目可以留空。</p><div class="form-grid two"><el-form-item v-for="field in fields" :key="`${form.type}-${field.key}`" :label="field.label"><el-input v-model="form.details[field.key]" :placeholder="field.placeholder || `按正式材料填写${field.label}`" maxlength="500" /></el-form-item></div></div>
          <el-form-item label="成果说明（选填）" class="editor-description"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="10000" show-word-limit placeholder="补充成果内容、个人贡献，以及审核时需要了解的信息" /></el-form-item>
        </section>
        <section class="editor-section"><div class="editor-section-heading"><span class="editor-step">02</span><div><h3>参与与指导关系</h3><p>同一成果只录入一份，按实际顺序记录成员与指导教师。</p></div><el-button text type="primary" :icon="Plus" :disabled="form.participants.length >= 100" @click="addParticipant">添加人员</el-button></div>
          <div v-for="(participant, index) in form.participants" :key="index" class="participant-entry" :class="{ invalid: participantIssues[index] }"><div class="participant-row"><span class="participant-index">{{ String(index + 1).padStart(2, '0') }}</span><el-select v-model="participant.role" :aria-label="`第${index + 1}位人员的参与角色`" @change="roleChange(index)"><el-option label="作者 / 负责人" value="AUTHOR" /><el-option label="参与成员" value="MEMBER" /><el-option label="指导教师" value="ADVISOR" /></el-select><el-select :model-value="participant.userId || undefined" @update:model-value="participant.userId = Number($event)" filterable :placeholder="participant.role === 'ADVISOR' ? '搜索并选择教师' : '搜索姓名、学工号或账号'" :aria-label="`选择第${index + 1}位参与人员`"><el-option v-for="person in people.filter(p => participant.role !== 'ADVISOR' || p.role === 'TEACHER')" :key="person.id" :label="`${person.name} · ${person.number || person.username}${person.number ? ' · ' + person.username : ''}`" :value="person.id" :disabled="duplicateOption(index, person.id)" /></el-select><el-tooltip content="移除此条参与关系" placement="top"><el-button :icon="Delete" text type="danger" :aria-label="`移除第${index + 1}位参与人`" @click="form.participants.splice(index, 1)" /></el-tooltip></div><p v-if="participantIssues[index]" class="participant-error" role="alert">{{ participantIssues[index] }}</p></div>
          <p class="editor-context">录入人 {{ recorder }} 会自动关联；同一人员可以承担不同角色，相同角色仅需填写一次。最多100条关系。</p>
          <el-button v-if="!form.participants.length" :icon="Plus" plain @click="addParticipant">添加第一位参与人</el-button>
        </section>
        <section class="editor-section"><div class="editor-section-heading"><span class="editor-step">03</span><div><h3>认证支撑关系</h3><p>按专业要求关联支撑项，也可以在后续完善时补充。</p></div><span class="optional-mark">选填</span></div><el-form-item label="关联支撑项"><el-select v-model="form.indicatorIds" multiple filterable clearable collapse-tags collapse-tags-tooltip :max-collapse-tags="3" placeholder="搜索并选择支撑项（可多选）"><el-option v-for="indicator in indicators" :key="indicator.id" :label="`${indicator.code} · ${indicator.name}`" :value="indicator.id" /></el-select></el-form-item><div class="form-footnote">{{ achievement?.status === 'REJECTED' ? '保存后仍可继续修改，确认内容与材料完整后，请在详情中重新提交审核。' : '草稿保存后不会立即进入正式统计。请在详情中上传证书、论文等证明材料，再提交审核。' }}</div></section>
      </el-form>
    </div>
    <template #footer><div class="editor-footer"><span>{{ saving ? '正在保存，请稍候…' : '修改仅在保存后生效' }}</span><div><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" :disabled="loading || !!referenceError" @click="save">{{ achievement ? '保存修改' : '保存草稿' }}</el-button></div></div></template>
  </el-dialog>
</template>

<style scoped>
.editor-intro{display:flex;align-items:center;gap:14px;padding:2px 0 18px}.editor-intro-icon{width:46px;height:46px;flex-shrink:0;display:grid;place-items:center;border-radius:13px;background:var(--el-color-primary-light-9);color:var(--el-color-primary)}.editor-intro strong{font-size:16px;color:var(--el-text-color-primary);font-weight:600}.editor-intro p{font-size:12px;line-height:1.8;color:var(--el-text-color-secondary);margin-top:5px}.editor-intro>.el-tag{margin-left:auto;flex-shrink:0}.editor-completion{display:flex;align-items:center;gap:15px;border:1px solid var(--el-border-color-lighter);background:var(--el-fill-color-light);padding:12px 15px;border-radius:9px;margin-bottom:20px;font-size:12px;color:var(--el-text-color-regular)}.editor-completion strong{margin-left:6px;color:var(--el-color-primary)}.editor-completion>.el-progress{flex:1;max-width:240px}.editor-completion small{margin-left:auto;font-size:11px;color:var(--el-text-color-secondary)}.editor-scroll{max-height:calc(92dvh - 260px);overflow:auto;padding:0 8px 0 1px;scrollbar-gutter:stable}.editor-alert{margin-bottom:18px}.editor-section+.editor-section{border-top:1px solid var(--el-border-color-lighter);padding-top:24px;margin-top:24px}.editor-section-heading{display:flex;align-items:center;gap:12px;margin-bottom:20px}.editor-step{width:31px;height:31px;flex-shrink:0;display:grid;place-items:center;color:var(--el-color-primary);background:var(--el-color-primary-light-9);font-size:12px;border-radius:8px;font-weight:600}.editor-section-heading h3{font-size:14px;color:var(--el-text-color-primary)}.editor-section-heading p{font-size:11px;color:var(--el-text-color-secondary);line-height:1.7;margin-top:5px}.editor-section-heading>.el-button,.optional-mark{margin-left:auto;flex-shrink:0}.optional-mark{font-size:11px;color:var(--el-text-color-placeholder)}.editor-context{display:flex;align-items:flex-start;gap:6px;font-size:11px;color:var(--el-text-color-secondary);line-height:1.8;margin:-5px 0 18px}.editor-context>.el-icon{margin-top:3px;flex-shrink:0}.category-card{padding:18px 18px 0;border:1px solid var(--el-border-color-lighter);background:var(--el-fill-color-lighter);border-radius:10px;margin:20px 0}.category-card-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:12px}.category-card-heading strong{color:var(--el-text-color-regular)}.category-card-heading small{font-size:10px;color:var(--el-text-color-secondary)}.category-card>p{font-size:11px;line-height:1.9;color:var(--el-text-color-secondary);margin:8px 0 17px}.participant-entry{border-radius:8px;margin-bottom:12px}.participant-entry .participant-row{margin-bottom:0}.participant-entry.invalid{padding:9px;background:var(--el-color-danger-light-9);outline:1px solid var(--el-color-danger-light-7)}.participant-error{margin:7px 0 0 28px;color:var(--el-color-danger);font-size:11px;line-height:1.7}.editor-section>.editor-context{margin-top:13px}.editor-footer{display:flex;align-items:center;justify-content:space-between;gap:12px}.editor-footer>span{font-size:11px;color:var(--el-text-color-secondary)}.editor-footer>div{display:flex;gap:9px}.editor-footer :deep(.el-button+.el-button){margin-left:0}
@media(max-width:680px){.editor-intro{gap:10px;flex-wrap:wrap}.editor-intro>div:nth-child(2){flex:1}.editor-intro strong{font-size:14px}.editor-intro p{font-size:11px}.editor-intro>.el-tag{margin-left:56px}.editor-completion{gap:10px;padding:11px}.editor-completion small{display:none}.editor-scroll{max-height:calc(92dvh - 300px);padding-right:3px}.editor-section-heading{gap:9px;align-items:flex-start}.editor-section-heading p{font-size:10px}.editor-section-heading>.el-button{font-size:11px;padding:5px}.category-card{padding:14px 12px 0}.category-card-heading{align-items:flex-start}.category-card-heading small{white-space:nowrap}.participant-entry.invalid{padding:3px}.participant-error{margin-left:11px}.editor-footer>span{display:none}.editor-footer{justify-content:flex-end}}
</style>
