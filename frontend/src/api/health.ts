import { apiRequest } from './client'

/** Spring Boot Actuator's public health summary. */
export interface HealthResponse {
  status: string
}

export function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return apiRequest<HealthResponse>('/actuator/health', { signal })
}
