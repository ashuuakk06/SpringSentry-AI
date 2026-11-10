import type { Analysis, ProviderStatus, User, Verification } from './types'

async function request<T>(path: string, token?: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, {
    ...init,
    headers,
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null) as { detail?: string; message?: string; title?: string } | null
    throw new Error(body?.detail || body?.message || body?.title || `Request failed (${response.status})`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  login: (email: string, password: string) => request<User>('/api/auth/login', undefined, {
    method: 'POST', body: JSON.stringify({ email, password }),
  }),
  register: (name: string, email: string, password: string) => request<User>('/api/auth/register', undefined, {
    method: 'POST', body: JSON.stringify({ name, email, password }),
  }),
  me: (token: string) => request<User>('/api/auth/me', token),
  history: (token: string) => request<Analysis[]>('/api/analyses', token),
  getAnalysis: (token: string, id: string) => request<Analysis>(`/api/analyses/${id}`, token),
  analyze: (token: string, data: { title: string; description: string; stackTrace: string; sourceCode: string }) =>
    request<Analysis>('/api/analyses', token, { method: 'POST', body: JSON.stringify(data) }),
  verifySample: (token: string) => request<Verification>('/api/verifications/sample', token, { method: 'POST' }),
  providerStatus: (token: string) => request<ProviderStatus>('/api/provider/status', token),
}
