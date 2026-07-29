import { ref } from 'vue'
import { defineStore } from 'pinia'

export type StudyConnectionState = 'idle' | 'connecting' | 'connected' | 'disconnected'

export const useLearningStore = defineStore('learning', () => {
  const sessionId = ref<string | null>(null)
  const connectionState = ref<StudyConnectionState>('idle')
  const confirmedPositionSeconds = ref(0)

  function reset() {
    sessionId.value = null
    connectionState.value = 'idle'
    confirmedPositionSeconds.value = 0
  }

  return {
    sessionId,
    connectionState,
    confirmedPositionSeconds,
    reset,
  }
})
