type Params = Record<string, string | number | boolean | undefined | null>
export class ApiError extends Error { constructor(message: string, public status: number) { super(message) } }
async function connect(path: string, options: RequestInit = {}) {
  try { return await fetch(path, options) }
  catch (error) {
    if ((error as Error).name === 'AbortError') throw error
    throw new ApiError('暂时无法连接系统，请确认启动窗口仍在运行后重试', 0)
  }
}
export function query(params: Params = {}) {
  const values = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => { if (value !== '' && value !== undefined && value !== null) values.set(key, String(value)) })
  return values.size ? `?${values.toString()}` : ''
}
async function check(response: Response) {
  if (!response.ok) {
    let message = response.status === 401 ? '登录已过期，请重新登录' : `请求失败（${response.status}）`
    try { const body = await response.json(); message = body.message || message } catch { /* non-JSON error */ }
    if (response.status === 401 && !response.url.includes('/auth/')) window.dispatchEvent(new Event('session-expired'))
    throw new ApiError(message, response.status)
  }
  return response
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  const method = (options.method || 'GET').toUpperCase()
  if (method !== 'GET' && method !== 'HEAD') {
    const csrf = await connect('/api/auth/csrf', { credentials: 'include', signal: options.signal }).then(check).then(r => r.json())
    headers.set(csrf.headerName, csrf.token)
    if (!(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  }
  const response = await connect(`/api${path}`, { ...options, headers, credentials: 'include' })
  await check(response)
  return response.status === 204 ? undefined as T : response.json()
}
export const write = <T>(path: string, method: string, body?: unknown) => api<T>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) })
export async function download(path: string, fallbackName: string) {
  const response = await check(await connect(`/api${path}`, { credentials: 'include' }))
  const blob = await response.blob()
  const disposition = response.headers.get('Content-Disposition') || ''
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const plain = disposition.match(/filename="?([^";]+)"?/i)?.[1]
  let filename = fallbackName
  try { filename = encoded ? decodeURIComponent(encoded) : plain || fallbackName } catch { /* fallback */ }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a'); link.href = url; link.download = filename; link.style.display = 'none'; document.body.appendChild(link); link.click(); link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export async function preview(path: string) {
  const response = await check(await connect(`/api${path}`, { credentials: 'include' }))
  const blob = await response.blob()
  return { url: URL.createObjectURL(blob), type: blob.type }
}
