import { onBeforeUnmount, shallowRef } from 'vue'

import { RealtimeClient } from '@/realtime/client'

export function useWebSocket(url: string) {
  const state = shallowRef<'idle' | 'connecting' | 'connected' | 'disconnected'>('idle')
  const client = new RealtimeClient({
    url,
    onStateChange: (nextState) => {
      state.value = nextState
    },
  })

  onBeforeUnmount(() => client.close())

  return {
    state,
    connect: () => client.connect(),
    close: () => client.close(),
    send: client.send.bind(client),
  }
}
