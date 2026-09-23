import type { AuthResponse, CurrentUser, LoginRequest, RegisterRequest } from '../features/auth/types'
import { apiRequest } from './client'

export function register(request: RegisterRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/api/auth/register', { method: 'POST', body: request })
}

export function login(request: LoginRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/api/auth/login', { method: 'POST', body: request })
}

export function getCurrentUser(accessToken: string, signal?: AbortSignal): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/api/auth/me', { accessToken, signal })
}
