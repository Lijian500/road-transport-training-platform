export interface RealtimeEnvelope<T = unknown> {
  type: string
  requestId: string
  sessionId?: string
  sequence?: number
  timestamp: number
  payload: T
}

export function encodeMessage<T>(message: RealtimeEnvelope<T>) {
  return JSON.stringify(message)
}

export function decodeMessage(data: string): RealtimeEnvelope {
  return JSON.parse(data) as RealtimeEnvelope
}
