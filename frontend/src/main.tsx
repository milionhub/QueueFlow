import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router/dom'

import { AuthProvider } from './features/auth/AuthProvider'
import { trackInputModality } from './lib/inputModality'
import { router } from './routes/router'
import './styles/global.css'

// For the life of the page: the focus ring follows how the user is working (see global.css).
trackInputModality()

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('Missing #root element in index.html')
}

createRoot(rootElement).render(
  <StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </StrictMode>,
)
