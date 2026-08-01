const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

type CsrfToken = { headerName: string; token: string }
let csrfToken: CsrfToken | null = null

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function requestCsrfToken() {
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/csrf`, { credentials: 'include' })
  if (!response.ok) throw new ApiError('보안 토큰을 발급하지 못했습니다.', response.status)
  csrfToken = await response.json() as CsrfToken
  return csrfToken
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null
    throw new ApiError(body?.message ?? (response.status === 401 ? '로그인이 필요합니다.' : '요청을 처리하지 못했습니다.'), response.status)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method?.toUpperCase() ?? 'GET'
  const changesState = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  const token = changesState ? (csrfToken ?? await requestCsrfToken()) : null
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { [token.headerName]: token.token } : {}),
      ...init?.headers,
    },
  })
  return parseResponse<T>(response)
}

export async function loginRequest(username: string, password: string) {
  const token = csrfToken ?? await requestCsrfToken()
  const body = new URLSearchParams({ username, password })
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/login`, {
    method: 'POST', credentials: 'include', body,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', [token.headerName]: token.token },
  })
  const result = await parseResponse<{ username: string }>(response)
  csrfToken = null
  return result
}

export async function logoutRequest() {
  await apiRequest<void>('/api/v1/auth/logout', { method: 'POST' })
  csrfToken = null
}
