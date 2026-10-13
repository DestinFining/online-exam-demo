import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

/**
 * Rejected promise type for every failed request. It carries the backend business
 * code so a caller can branch on it (for example the 409 conflict of dynamic
 * paper generation) without re-parsing the axios error object.
 *
 * `response` is kept for the checks that predate this class
 * (`error.response.status === 404`); it is a plain object, not an AxiosResponse.
 */
export class ApiError extends Error {
  readonly code: number
  readonly status: number
  readonly url: string
  readonly response: { status: number; data: { code: number; message: string } }
  /** True when the API layer has already shown this message, so it must not be toasted twice. */
  readonly reported: boolean

  constructor(message: string, code: number, url = '', reported = false) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = code
    this.url = url
    this.reported = reported
    this.response = { status: code, data: { code, message } }
  }
}

/**
 * True when the failure was already surfaced by the API layer. Views use it as
 * `if (!isReported(error)) ElMessage.error(...)` so a handled-elsewhere conflict
 * stays quiet while a genuine view error is still shown.
 */
export function isReported(error: unknown): boolean {
  return error instanceof ApiError && error.reported
}

/** Backend message when there is one, otherwise the transport message, otherwise `fallback`. */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message || fallback
  const value = error as { response?: { data?: { message?: string } }; message?: string }
  return value?.response?.data?.message || value?.message || fallback
}

export const http = axios.create({
  baseURL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('oe_access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = Number(error?.response?.status) || 0
    const message = error?.response?.data?.message || error?.message || '请求失败，请检查服务状态'
    const requestUrl = String(error?.config?.url || '')
    // Dynamic paper generation handles 409 in PapersView so it can show
    // the shortage details and actionable suggestions without duplicate toasts.
    const isPaperGenerationConflict = status === 409 && requestUrl.includes('/papers/generate')
    let reported = false
    if (status === 401 && !window.location.pathname.startsWith('/login')) {
      sessionStorage.removeItem('oe_access_token')
      sessionStorage.removeItem('oe_profile')
      window.location.assign('/login')
    } else if (!isPaperGenerationConflict) {
      // 403 is reported too: it means the UI offered an action this account may
      // not perform, and swallowing it left the user with a silent no-op.
      ElMessage.error(message)
      reported = true
    }
    return Promise.reject(new ApiError(message, status, requestUrl, reported))
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResponse<T>>(config)
  const body = response.data
  if (body && typeof body.code === 'number' && body.code !== 200) {
    // No toast has been shown for a business-code failure, so the caller still reports it.
    throw new ApiError(body.message || '请求失败', body.code, String(config.url || ''), false)
  }
  return body?.data as T
}

export function get<T>(url: string, params?: Record<string, unknown>) {
  return request<T>({ method: 'GET', url, params })
}
export function post<T>(url: string, data?: unknown, params?: Record<string, unknown>) {
  return request<T>({ method: 'POST', url, data, params })
}
export function put<T>(url: string, data?: unknown) {
  return request<T>({ method: 'PUT', url, data })
}
export function del<T>(url: string) {
  return request<T>({ method: 'DELETE', url })
}
