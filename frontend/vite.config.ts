import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // The repository keeps one .env at its root (see /.env.example). Vite only
  // exposes VITE_-prefixed variables to the browser bundle; the backend's
  // secrets in the same file are never included.
  envDir: '..',
})
