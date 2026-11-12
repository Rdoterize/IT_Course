import { computed, ref } from 'vue'
import { api, ApiError } from '../api'
import type { Attachment } from '../types'

export interface MaterialTask {
  id: number
  achievementId: number
  file: File
  status: 'waiting' | 'checking' | 'uploading' | 'success' | 'error' | 'cancelled'
  progress: number | null
  error: string
}
interface Job { task: MaterialTask; resolve: (file: Attachment) => void; reject: (error: Error) => void }
export function materialValidation(file: File): string {
  if (!/\.(pdf|png|jpe?g)$/i.test(file.name)) return '仅支持 PDF、PNG、JPG 或 JPEG 文件。'
  if (!file.size || file.size > 20 * 1024 * 1024) return '文件不能为空，单份文件不能超过20MB。'
  if (file.name.length > 240 || /[\\/:\x00-\x1f\x7f]/.test(file.name) || file.name.includes('..')) return '文件名过长或含特殊路径字符，请重命名后再上传。'
  return ''
}
async function checkSignature(file: File) {
  const bytes = new Uint8Array(await file.slice(0, 8).arrayBuffer())
  const pdf = bytes.length >= 5 && String.fromCharCode(...bytes.slice(0, 5)) === '%PDF-'
  const png = [137, 80, 78, 71, 13, 10, 26, 10].every((byte, index) => bytes[index] === byte)
  const jpeg = bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255
  if (!(/\.pdf$/i.test(file.name) && pdf || /\.png$/i.test(file.name) && png || /\.jpe?g$/i.test(file.name) && jpeg)) throw new Error('文件内容与扩展名不一致，请选择原始PDF或图片文件。')
}
export function useMaterialUpload() {
  const tasks = ref<MaterialTask[]>([])
  const pending = computed(() => tasks.value.filter(task => ['waiting', 'checking', 'uploading'].includes(task.status)).length)
  const busy = computed(() => pending.value > 0)
  const queue: Job[] = []
  let active: Job | null = null, xhr: XMLHttpRequest | null = null, preparation: AbortController | null = null, nextId = 0, epoch = 0
  async function pump() {
    if (active || !queue.length) return
    const job = queue.shift()!
    active = job
    const current = epoch, task = job.task
    try {
      task.status = 'checking'; await checkSignature(task.file)
      if (current !== epoch) throw new DOMException('上传已取消', 'AbortError')
      preparation = new AbortController()
      const csrf = await api<{ token: string; headerName: string }>('/auth/csrf', { signal: AbortSignal.any([preparation.signal, AbortSignal.timeout(30000)]) })
      if (current !== epoch) throw new DOMException('上传已取消', 'AbortError')
      const attachment = await new Promise<Attachment>((resolve, reject) => {
        const request = new XMLHttpRequest(); xhr = request
        request.open('POST', `/api/achievements/${task.achievementId}/attachments`)
        request.withCredentials = true; request.responseType = 'json'; request.timeout = 180000
        request.setRequestHeader(csrf.headerName, csrf.token)
        request.upload.onprogress = event => { if (current === epoch && event.lengthComputable) task.progress = Math.min(100, Math.round(event.loaded / event.total * 100)) }
        request.onload = () => {
          if (request.status >= 200 && request.status < 300 && typeof request.response?.id === 'number') resolve(request.response as Attachment)
          else { if (request.status === 401) window.dispatchEvent(new Event('session-expired')); reject(new ApiError(request.response?.message || `上传未完成（${request.status}），请重试。`, request.status)) }
        }
        request.onerror = () => reject(new Error('连接中断，请先刷新材料列表，确认结果后再重试。'))
        request.ontimeout = () => reject(new Error('上传超时，请先刷新材料列表，确认结果后再重试。'))
        request.onabort = () => reject(new DOMException('上传已取消', 'AbortError'))
        const data = new FormData(); data.append('file', task.file); task.status = 'uploading'; request.send(data)
      })
      if (current !== epoch) throw new DOMException('上传已取消', 'AbortError')
      task.status = 'success'; task.progress = 100; job.resolve(attachment)
    } catch (e) {
      const original = e as Error
      const error = original.name === 'TimeoutError' ? new Error('等待上传连接超时，请检查网络后重试。') : original
      task.status = error.name === 'AbortError' ? 'cancelled' : 'error'; task.error = error.name === 'AbortError' ? '' : error.message
      job.reject(error)
    } finally {
      if (active === job) { active = null; xhr = null; preparation = null }
      void pump()
    }
  }
  function enqueue(file: File, achievementId: number, existingTask?: MaterialTask) {
    const task = existingTask || { id: ++nextId, achievementId, file, status: 'waiting' as const, progress: null, error: '' }
    task.status = 'waiting'; task.error = ''; task.progress = null
    if (!existingTask) tasks.value.push(task)
    const reactiveTask = tasks.value.find(item => item.id === task.id)!
    const result = new Promise<Attachment>((resolve, reject) => queue.push({ task: reactiveTask, resolve, reject }))
    void pump(); return result
  }
  function retry(task: MaterialTask) { if (task.status !== 'error') return Promise.reject(new Error('当前材料不需要重试。')); return enqueue(task.file, task.achievementId, task) }
  function cancelAll() {
    epoch++
    for (const job of queue.splice(0)) { job.task.status = 'cancelled'; job.reject(new DOMException('上传已取消', 'AbortError')) }
    if (active) { active.task.status = 'cancelled'; active.reject(new DOMException('上传已取消', 'AbortError')) }
    preparation?.abort(); xhr?.abort()
  }
  function clearFinished() { tasks.value = tasks.value.filter(task => !['success', 'cancelled'].includes(task.status)) }
  function dismiss(id: number) { tasks.value = tasks.value.filter(task => task.id !== id || !['error', 'success', 'cancelled'].includes(task.status)) }
  function reset() { cancelAll(); tasks.value = [] }
  return { tasks, pending, busy, enqueue, retry, cancelAll, clearFinished, dismiss, reset }
}
