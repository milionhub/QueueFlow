interface ImportMetaEnv {
  /** Backend origin, e.g. http://localhost:8080 (no trailing /api). */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
