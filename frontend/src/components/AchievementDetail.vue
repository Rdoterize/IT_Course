<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { UploadFilled, Download, View, Delete, Edit, Promotion, Document, Refresh, CircleCheck, Loading, InfoFilled } from '@element-plus/icons-vue'
import { api, download, preview, write } from '../api'
import { useAuth } from '../stores/auth'
import { materialValidation, useMaterialUpload, type MaterialTask } from '../composables/useMaterialUpload'
import { typeLabels, statusLabels, statusColors, levelLabels, participantLabels, detailFields, formatDate, formatSize } from '../constants'
import type { Achievement, Attachment, Indicator, Review } from '../types'

const props = defineProps<{ modelValue: boolean; achievementId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: []; edit: [achievement: Achievement] }>()
const auth = useAuth()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const item = ref<Achievement | null>(null), files = ref<Attachment[]>([]), reviews = ref<Review[]>([]), indicators = ref<Indicator[]>([])
const loading = ref(false), error = ref(''), comment = ref(''), reviewError = ref(''), materialRefreshing = ref(false)
const action = ref<'submit' | 'approve' | 'reject' | null>(null)
const deletingIds = ref(new Set<number>()), downloadingIds = ref(new Set<number>())
const uploader = useMaterialUpload()
const { tasks: uploadTasks, busy: uploading, pending: pendingUploads } = uploader
let generation = 0, loadController: AbortController | null = null, closingConfirm = false
const editable = computed(() => !!item.value && ['DRAFT', 'REJECTED'].includes(item.value.status) && (auth.isAdmin || item.value.ownerId === auth.user?.id))
const canReview = computed(() => auth.isAdmin && item.value?.status === 'PENDING' && item.value.ownerId !== auth.user?.id && !item.value.participants.some(p => p.userId === auth.user?.id))
const linkedIndicators = computed(() => indicators.value.filter(i => item.value?.indicatorIds.includes(i.id)))
const materialSlots = computed(() => Math.max(0, 50 - files.value.length - pendingUploads.value))
const controlsBusy = computed(() => !!action.value || deletingIds.value.size > 0)
const statusNote = computed(() => item.value?.status === 'APPROVED' ? '审核已通过，这项成果已纳入正式统计。' : item.value?.status === 'PENDING' ? '已提交审核，成果信息与支撑材料暂时锁定。' : item.value?.status === 'REJECTED' ? '请根据审核意见完善信息与材料，然后重新提交。' : '当前为草稿，可完善信息、上传材料，再提交审核。')
const previewOpen = ref(false), previewUrl = ref(''), previewType = ref(''), previewName = ref(''), previewLoading = ref(false), previewError = ref('')
const previewedFile = ref<Attachment | null>(null)
let previewGeneration = 0

async function load() {
  if (!props.achievementId || !visible.value) return
  const id = props.achievementId, current = generation
  loadController?.abort(); const controller = new AbortController(); loadController = controller
  loading.value = true; error.value = ''
  try {
    const result = await Promise.all([api<Achievement>(`/achievements/${id}`, { signal: controller.signal }), api<Attachment[]>(`/achievements/${id}/attachments`, { signal: controller.signal }), api<Review[]>(`/achievements/${id}/reviews`, { signal: controller.signal }), api<Indicator[]>('/indicators', { signal: controller.signal })])
    if (current === generation && visible.value && id === props.achievementId) [item.value, files.value, reviews.value, indicators.value] = result
  } catch (e) { if (current === generation && (e as Error).name !== 'AbortError') error.value = (e as Error).message }
  finally { if (current === generation && loadController === controller) loading.value = false }
}
watch([() => props.modelValue, () => props.achievementId], ([open]) => {
  generation++; loadController?.abort(); uploader.reset(); previewOpen.value = false; clearPreview(); materialRefreshing.value = false
  if (open) { item.value = null; files.value = []; reviews.value = []; comment.value = ''; reviewError.value = ''; action.value = null; error.value = ''; void load() }
}, { immediate: true })
watch(comment, () => { reviewError.value = '' })
async function mayClose() {
  if (controlsBusy.value || closingConfirm) return false
  if (!uploading.value) return true
  closingConfirm = true
  try {
    await ElMessageBox.confirm('已保存的材料会保留；关闭后，当前上传和等待中的文件将取消。', '材料仍在上传', { confirmButtonText: '取消上传并关闭', cancelButtonText: '继续上传', type: 'warning' })
    uploader.reset(); return true
  } catch { return false } finally { closingConfirm = false }
}
async function beforeClose(done: () => void) { if (await mayClose()) done() }
async function requestClose() { if (await mayClose()) visible.value = false }
onBeforeRouteLeave(async to => {
  if (!visible.value || to.path === '/login') return true
  return await mayClose()
})
async function submit() {
  if (!item.value || !editable.value || controlsBusy.value || uploading.value) return
  const id = item.value.id; action.value = 'submit'
  try {
    await ElMessageBox.confirm('提交后，成果信息和支撑材料将锁定，等待管理员审核。', '确认提交审核？', { confirmButtonText: '确认提交', cancelButtonText: '继续完善', type: 'info' })
    await write(`/achievements/${id}/submit`, 'POST'); ElMessage.success('已提交审核，可在审核记录中查看后续结果。')
    if (id === props.achievementId && visible.value) await load()
    emit('changed')
  } catch (e) { if (e instanceof Error) ElMessage.error(e.message) } finally { action.value = null }
}
async function review(approved: boolean) {
  if (!item.value || !canReview.value || controlsBusy.value) return
  const value = comment.value.trim()
  if (!approved && !value) { reviewError.value = '请填写具体退回原因，帮助录入人完成修改。'; return }
  if (value.length > 2000) { reviewError.value = '审核意见最多2000个字符，请精简后再提交。'; return }
  const id = item.value.id; action.value = approved ? 'approve' : 'reject'
  try {
    await ElMessageBox.confirm(approved ? '确认已核对成果信息和支撑材料？通过后，这项成果将纳入正式统计。' : '确认按当前审核意见退回？录入人将可以修改内容与材料。', approved ? '通过此项成果？' : '退回修改？', { confirmButtonText: approved ? '确认通过' : '确认退回', cancelButtonText: '再检查一下', type: approved ? 'success' : 'warning' })
    await write(`/achievements/${id}/review`, 'POST', { approved, comment: value }); comment.value = ''
    ElMessage.success(approved ? '审核通过，已纳入正式统计。' : '成果已退回，录入人可根据意见修改。')
    if (id === props.achievementId && visible.value) await load()
    emit('changed')
  } catch (e) { if (e instanceof Error) ElMessage.error(e.message) } finally { action.value = null }
}
function beforeUpload(file: File) {
  if (!editable.value || controlsBusy.value || materialRefreshing.value) return false
  const message = materialValidation(file)
  if (message) { ElMessage.warning(message); return false }
  if (!materialSlots.value) { ElMessage.warning('每项成果最多50份材料，请先移除不需要的文件。'); return false }
  return true
}
function addUploadedFile(file: Attachment, current: number) {
  if (current !== generation || !visible.value) return
  if (!files.value.some(existing => existing.id === file.id)) files.value.push(file)
  emit('changed')
}
async function upload(options: UploadRequestOptions) {
  if (!item.value || !beforeUpload(options.file)) throw new Error('当前文件未加入上传队列。')
  const current = generation
  try { const result = await uploader.enqueue(options.file, item.value.id); addUploadedFile(result, current); return result }
  catch (e) { if ((e as Error).name !== 'AbortError' && current === generation) ElMessage.error((e as Error).message); throw e }
}
async function retryUpload(task: MaterialTask) {
  if (!editable.value || controlsBusy.value || materialRefreshing.value || !materialSlots.value || task.status !== 'error') { if (!materialSlots.value) ElMessage.warning('材料数量已达上限，请先移除不需要的文件。'); return }
  const current = generation
  try { const result = await uploader.retry(task); addUploadedFile(result, current) }
  catch (e) { if ((e as Error).name !== 'AbortError' && current === generation) ElMessage.error((e as Error).message) }
}
async function refreshMaterials() {
  if (!item.value || materialRefreshing.value || uploading.value) return
  const id = item.value.id, current = generation; materialRefreshing.value = true
  try { const result = await api<Attachment[]>(`/achievements/${id}/attachments`); if (current === generation) files.value = result }
  catch (e) { if (current === generation) ElMessage.error((e as Error).message) } finally { if (current === generation) materialRefreshing.value = false }
}
async function removeFile(file: Attachment) {
  if (!editable.value || controlsBusy.value || uploading.value) return
  deletingIds.value.add(file.id)
  try {
    await ElMessageBox.confirm(`材料“${file.originalName}”删除后需要重新上传。`, '确认删除材料？', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '保留材料' })
    await write(`/attachments/${file.id}`, 'DELETE'); files.value = files.value.filter(f => f.id !== file.id); ElMessage.success('材料已删除。'); emit('changed')
  } catch (e) { if (e instanceof Error) ElMessage.error(e.message) } finally { deletingIds.value.delete(file.id) }
}
async function downloadFile(file: Attachment) {
  if (downloadingIds.value.has(file.id)) return
  downloadingIds.value.add(file.id)
  try { await download(`/attachments/${file.id}/download`, file.originalName) }
  catch (e) { ElMessage.error((e as Error).message) } finally { downloadingIds.value.delete(file.id) }
}
function clearPreview() { previewGeneration++; if (previewUrl.value) URL.revokeObjectURL(previewUrl.value); previewUrl.value = ''; previewLoading.value = false }
async function previewFile(file: Attachment) {
  clearPreview(); const current = previewGeneration
  previewedFile.value = file; previewName.value = file.originalName; previewType.value = file.contentType; previewError.value = ''; previewLoading.value = true; previewOpen.value = true
  try {
    const result = await preview(`/attachments/${file.id}/download`)
    if (current !== previewGeneration || !previewOpen.value) { URL.revokeObjectURL(result.url); return }
    previewUrl.value = result.url; previewType.value = result.type
  } catch (e) { if (current === previewGeneration) { previewError.value = (e as Error).message; previewLoading.value = false } }
}
function previewFailed() { previewLoading.value = false; previewError.value = '当前浏览器无法显示此材料，请下载原文件查看。' }
watch(previewOpen, open => { if (!open) clearPreview() })
function edit() { if (item.value && editable.value && !controlsBusy.value && !uploading.value) emit('edit', item.value) }
onBeforeUnmount(() => { generation++; loadController?.abort(); uploader.reset(); clearPreview() })
</script>

<template>
  <el-drawer v-model="visible" title="成果详情" size="min(900px, 100%)" class="detail-drawer" destroy-on-close :before-close="beforeClose" :close-on-press-escape="!controlsBusy" :show-close="!controlsBusy">
    <div v-loading="loading" element-loading-text="正在读取成果记录…" class="detail-body"><el-alert v-if="error" :title="error" type="error" show-icon :closable="false"><el-button text type="primary" :icon="Refresh" :loading="loading" @click="load">重新加载</el-button></el-alert>
      <template v-if="item"><div class="detail-heading"><div class="detail-tags"><el-tag effect="plain">{{ typeLabels[item.type] }}</el-tag><el-tag :type="statusColors[item.status]" round>{{ statusLabels[item.status] }}</el-tag><span class="record-number">成果 #{{ item.id }}</span></div><h2>{{ item.title }}</h2><p>由 {{ item.ownerName }} 录入 <span>·</span> 更新于 {{ formatDate(item.updatedAt) }}</p></div>
        <div class="status-note" :class="`status-${item.status.toLowerCase()}`"><el-icon><CircleCheck v-if="item.status === 'APPROVED'" /><InfoFilled v-else /></el-icon><span>{{ statusNote }}</span></div>
        <section class="detail-section"><div class="section-heading"><h3>基本信息</h3><small>以正式成果材料为准</small></div><el-descriptions :column="2" border><el-descriptions-item label="成果级别">{{ levelLabels[item.level] }}</el-descriptions-item><el-descriptions-item label="发生日期">{{ item.achievedOn }}</el-descriptions-item><el-descriptions-item v-for="field in detailFields[item.type]" :key="field.key" :label="field.label">{{ item.details[field.key] || '—' }}</el-descriptions-item><el-descriptions-item label="成果说明" :span="2"><div class="preserve-space">{{ item.description || '暂无补充说明' }}</div></el-descriptions-item></el-descriptions></section>
        <section class="detail-section"><div class="section-heading"><h3>参与与指导</h3><small>{{ item.participants.length }} 条关系</small></div><div v-if="item.participants.length" class="people-chips"><div v-for="(person, index) in item.participants" :key="`${person.userId}-${person.role}`" class="person-chip"><span class="person-order">{{ String(index + 1).padStart(2, '0') }}</span><strong>{{ person.name }}</strong><small>{{ participantLabels[person.role] }}</small></div></div><el-empty v-else description="暂无参与人" :image-size="50" /></section>
        <section class="detail-section"><div class="section-heading"><h3>认证支撑项</h3><small>{{ linkedIndicators.length ? `${linkedIndicators.length} 个已关联` : '暂未关联' }}</small></div><div v-if="linkedIndicators.length" class="linked-indicators"><div v-for="indicator in linkedIndicators" :key="indicator.id"><el-tag effect="plain">{{ indicator.code }}</el-tag><span>{{ indicator.name }}</span></div></div><p v-else class="muted">可在编辑信息时补充相关的认证或评估支撑项。</p></section>
        <section class="detail-section materials-section"><div class="section-heading"><h3>支撑材料 <small>{{ files.length }} / 50 份</small></h3><el-button :icon="Refresh" text :loading="materialRefreshing" :disabled="uploading || !!action" @click="refreshMaterials">刷新列表</el-button></div>
          <el-upload v-if="editable" class="material-dropzone" drag multiple :http-request="upload" :before-upload="beforeUpload" :show-file-list="false" accept=".pdf,.png,.jpg,.jpeg" :disabled="controlsBusy || materialRefreshing || !materialSlots"><el-icon :size="35" class="dropzone-icon"><UploadFilled /></el-icon><strong>{{ materialSlots ? '将材料拖到这里，或点击选择文件' : '材料数量已达到上限' }}</strong><p>支持 PDF、PNG、JPEG · 单份不超过20MB · 还可添加 {{ materialSlots }} 份</p><small>可选择多个文件，将依次上传；证明材料随成果权限保存。</small></el-upload>
          <p v-else class="material-readonly-note"><el-icon><InfoFilled /></el-icon>{{ ['PENDING', 'APPROVED'].includes(item.status) ? '当前状态下材料已锁定，可预览和下载。' : '您可以预览和下载参与成果的材料，修改由录入人或管理员完成。' }}</p>
          <div v-if="uploadTasks.length" class="upload-queue" aria-live="polite"><div class="upload-queue-heading"><strong>{{ uploading ? `正在处理 ${pendingUploads} 份材料` : '本次上传' }}</strong><el-button text size="small" @click="uploader.clearFinished()">收起已完成</el-button></div><div v-for="task in uploadTasks" :key="task.id" class="upload-task" :class="`task-${task.status}`"><div class="upload-task-top"><el-icon v-if="task.status === 'success'"><CircleCheck /></el-icon><el-icon v-else-if="['checking', 'uploading'].includes(task.status)" class="is-loading"><Loading /></el-icon><el-icon v-else><Document /></el-icon><strong>{{ task.file.name }}</strong><small>{{ formatSize(task.file.size) }}</small><span>{{ task.status === 'waiting' ? '等待上传' : task.status === 'checking' ? '校验文件中…' : task.status === 'success' ? '已保存' : task.status === 'error' ? '未完成' : task.status === 'cancelled' ? '已取消' : task.progress === 100 ? '文件已发送，等待保存…' : task.progress === null ? '正在上传…' : `${task.progress}%` }}</span></div><el-progress v-if="task.status === 'uploading' && task.progress !== null" :percentage="task.progress" :show-text="false" :stroke-width="4" /><div v-if="task.status === 'error'" class="upload-task-error"><p>{{ task.error }}</p><div class="upload-error-actions"><el-button size="small" type="primary" plain :disabled="!editable || controlsBusy || materialRefreshing || !materialSlots" @click="retryUpload(task)">重试上传</el-button><el-button size="small" text @click="uploader.dismiss(task.id)">移除</el-button></div></div></div><p v-if="uploadTasks.some(task => task.status === 'error')" class="upload-retry-help">连接异常时，请先刷新材料列表确认是否已保存，再重试未完成的文件。</p></div>
          <div v-for="file in files" :key="file.id" class="file-row"><div class="file-icon"><el-icon :size="22"><Document /></el-icon></div><div class="file-info"><strong>{{ file.originalName }}</strong><small>{{ file.contentType === 'application/pdf' ? 'PDF 文档' : file.contentType === 'image/png' ? 'PNG 图片' : 'JPEG 图片' }} <span>·</span> {{ formatSize(file.size) }} <span>·</span> {{ formatDate(file.createdAt) }}</small></div><div class="file-actions"><el-button :icon="View" text :loading="previewLoading && previewedFile?.id === file.id" @click="previewFile(file)">预览</el-button><el-button :icon="Download" text :loading="downloadingIds.has(file.id)" @click="downloadFile(file)">下载</el-button><el-button v-if="editable" :icon="Delete" text type="danger" :loading="deletingIds.has(file.id)" :disabled="controlsBusy && !deletingIds.has(file.id) || uploading" :aria-label="`删除材料${file.originalName}`" @click="removeFile(file)" /></div></div>
          <el-empty v-if="!files.length && !uploading" description="尚未上传证明材料" :image-size="65"><span class="empty-material-help">{{ editable ? '可上传证书、论文、项目通知等支撑材料。' : '录入人尚未添加支撑材料。' }}</span></el-empty>
        </section>
        <section class="detail-section"><div class="section-heading"><h3>审核记录</h3><small>{{ reviews.length ? `${reviews.length} 次审核` : '提交后在此查看结果' }}</small></div><el-timeline v-if="reviews.length"><el-timeline-item v-for="record in reviews" :key="record.id" :timestamp="formatDate(record.createdAt)" :type="record.approved ? 'success' : 'warning'" placement="top"><strong>{{ record.reviewerName }} · {{ record.approved ? '审核通过' : '退回修改' }}</strong><p class="review-comment">{{ record.comment || '审核通过，无补充意见。' }}</p></el-timeline-item></el-timeline><p v-else class="muted">暂无审核记录。审核意见会保留在这里，便于后续追溯。</p></section>
        <section v-if="canReview" class="review-box"><h3>审核此成果</h3><p>核对基本信息与支撑材料后作出决定。退回时请说明具体修改原因。</p><el-input v-model="comment" type="textarea" :rows="4" maxlength="2000" show-word-limit :disabled="!!action" placeholder="填写审核意见，退回时必填（最多2000字）" :class="{ 'review-input-error': reviewError }" /><p v-if="reviewError" class="review-error" role="alert">{{ reviewError }}</p><div class="review-actions"><el-button type="warning" plain :loading="action === 'reject'" :disabled="!!action && action !== 'reject'" @click="review(false)">退回修改</el-button><el-button type="success" :loading="action === 'approve'" :disabled="!!action && action !== 'approve'" @click="review(true)">通过审核</el-button></div></section><el-alert v-else-if="auth.isAdmin && item.status === 'PENDING'" title="您拥有或参与此成果，需由其他管理员审核。" type="info" :closable="false" show-icon />
      </template>
    </div>
    <template #footer><div class="drawer-footer"><el-button :disabled="controlsBusy" @click="requestClose">关闭</el-button><div v-if="editable"><el-button :icon="Edit" :disabled="controlsBusy || uploading" @click="edit">编辑信息</el-button><el-button type="primary" :icon="Promotion" :loading="action === 'submit'" :disabled="uploading || controlsBusy && action !== 'submit'" @click="submit">提交审核</el-button></div><span v-else-if="item?.status === 'APPROVED'" class="footer-status"><el-icon><CircleCheck /></el-icon>已纳入正式统计</span></div></template>
  </el-drawer>
  <el-dialog v-model="previewOpen" :title="previewName" width="min(1050px, 94vw)" top="4vh" destroy-on-close append-to-body><div class="preview-toolbar"><span>{{ previewType.includes('pdf') ? 'PDF 文档预览' : '图片预览' }}</span><el-button v-if="previewedFile" :icon="Download" size="small" :loading="downloadingIds.has(previewedFile.id)" @click="downloadFile(previewedFile)">下载原文件</el-button></div><div v-loading="previewLoading" element-loading-text="正在读取材料…" class="preview-content"><el-alert v-if="previewError" :title="previewError" type="error" :closable="false" show-icon><el-button v-if="previewedFile" text type="primary" @click="previewFile(previewedFile)">重新预览</el-button></el-alert><template v-else-if="previewUrl"><iframe v-if="previewType.includes('pdf')" :key="previewUrl" :src="previewUrl" class="pdf-preview" title="PDF材料预览" @load="previewLoading = false" @error="previewFailed"></iframe><img v-else :key="previewUrl" :src="previewUrl" :alt="previewName" class="image-preview" @load="previewLoading = false" @error="previewFailed" /></template></div></el-dialog>
</template>

<style scoped>
.upload-error-actions{display:flex;align-items:center;flex-shrink:0;gap:6px}.upload-error-actions .el-button+.el-button{margin-left:0}
.detail-heading h2{letter-spacing:-.2px}.detail-heading>p span{margin:0 7px;color:var(--el-text-color-placeholder)}.record-number{margin-left:auto;color:var(--el-text-color-placeholder);font-size:11px}.status-note{display:flex;align-items:flex-start;gap:9px;padding:14px 16px;border-radius:11px;background:var(--el-color-primary-light-9);color:var(--el-color-primary);font-size:12px;line-height:1.8;margin-bottom:26px}.status-note>.el-icon{margin-top:4px;flex-shrink:0}.status-approved{background:var(--el-color-success-light-9);color:var(--el-color-success)}.status-rejected{background:var(--el-color-warning-light-9);color:var(--el-color-warning-dark-2)}.section-heading>small{font-size:11px;color:var(--el-text-color-secondary);line-height:1.6}.material-dropzone{margin:0 0 15px}.material-dropzone :deep(.el-upload){width:100%}.material-dropzone :deep(.el-upload-dragger){padding:25px 18px;border-radius:14px;border-color:var(--el-color-primary-light-5);background:var(--el-color-primary-light-9);transition:background .2s,border-color .2s}.material-dropzone :deep(.el-upload-dragger:hover){border-color:var(--el-color-primary);background:var(--el-color-primary-light-8)}.dropzone-icon{display:block;margin:0 auto 10px;color:var(--el-color-primary)}.material-dropzone strong{display:block;color:var(--el-text-color-primary);font-size:14px;line-height:1.7;font-weight:500}.material-dropzone p{margin:8px 0 4px;color:var(--el-text-color-regular);font-size:12px;line-height:1.8}.material-dropzone small{font-size:11px;color:var(--el-text-color-secondary);line-height:1.8}.material-readonly-note{display:flex;align-items:flex-start;gap:7px;color:var(--el-text-color-secondary);font-size:12px;line-height:1.9;padding:13px 15px;background:var(--el-fill-color-light);border-radius:9px;margin-bottom:8px}.material-readonly-note>.el-icon{margin-top:4px;flex-shrink:0}.upload-queue{border:1px solid var(--el-border-color-lighter);border-radius:12px;overflow:hidden;margin:15px 0;background:var(--el-fill-color-lighter)}.upload-queue-heading{display:flex;align-items:center;justify-content:space-between;padding:8px 14px;border-bottom:1px solid var(--el-border-color-lighter)}.upload-queue-heading strong{font-size:12px;color:var(--el-text-color-regular);font-weight:500}.upload-task{padding:13px 14px;border-bottom:1px solid var(--el-border-color-lighter)}.upload-task:last-child{border-bottom:0}.upload-task-top{display:flex;align-items:center;gap:9px;font-size:11px;color:var(--el-text-color-secondary)}.upload-task-top strong{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;color:var(--el-text-color-regular);font-weight:500}.upload-task-top>span{white-space:nowrap;margin-left:auto}.upload-task-top>.el-icon{color:var(--el-color-primary)}.task-success .upload-task-top>.el-icon,.task-success .upload-task-top>span{color:var(--el-color-success)}.task-error .upload-task-top>span{color:var(--el-color-danger)}.upload-task>.el-progress{margin-top:11px}.upload-task-error{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:9px 0 0 22px}.upload-task-error p,.upload-retry-help{font-size:11px;line-height:1.8;color:var(--el-color-danger)}.upload-task-error>.el-button{flex-shrink:0}.upload-retry-help{padding:9px 14px;color:var(--el-text-color-secondary);background:var(--el-fill-color-light)}.file-info small span{margin:0 5px;color:var(--el-text-color-placeholder)}.empty-material-help{font-size:12px;color:var(--el-text-color-secondary)}.review-error{font-size:12px;line-height:1.8;color:var(--el-color-danger);margin-top:9px}.review-input-error :deep(.el-textarea__inner){box-shadow:0 0 0 1px var(--el-color-danger) inset}.footer-status{display:flex;align-items:center;gap:7px;font-size:12px;color:var(--el-color-success)}.preview-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:14px;font-size:12px;color:var(--el-text-color-secondary)}.preview-content{min-height:300px;border-radius:10px;background:var(--el-fill-color-light);padding:8px}.pdf-preview{height:70vh;border:0;border-radius:6px}.image-preview{max-height:70vh;border-radius:6px}.review-actions .el-button+.el-button{margin-left:0}
@media(max-width:680px){.record-number{font-size:10px}.status-note{font-size:12px;padding:12px 13px}.section-heading{gap:10px}.section-heading>small{font-size:10px;text-align:right}.material-dropzone :deep(.el-upload-dragger){padding:20px 12px}.material-dropzone strong{font-size:13px}.material-dropzone p{font-size:11px}.material-dropzone small{font-size:10px}.upload-task-top{flex-wrap:wrap;gap:7px}.upload-task-top strong{font-size:11px;flex-basis:calc(100% - 30px)}.upload-task-top small{margin-left:22px}.upload-task-error{margin-left:22px;align-items:flex-start;flex-direction:column;gap:7px}.footer-status{font-size:11px}.detail-section .file-info strong{font-size:12px}.detail-section .file-info small{font-size:10px;line-height:1.8}.preview-content{min-height:260px}}
</style>
