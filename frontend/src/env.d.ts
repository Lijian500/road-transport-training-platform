/// <reference types="vite/client" />

declare global {
  interface ImportMetaEnv {
    readonly VITE_APP_TITLE?: string
    readonly VITE_API_BASE_URL?: string
    readonly VITE_WS_PATH?: string
    readonly VITE_GATEWAY_TARGET?: string
  }

  interface ImportMeta {
    readonly env: ImportMetaEnv
  }
}

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    workspace?: 'admin' | 'student'
    permission?: string
  }
}

export {}
