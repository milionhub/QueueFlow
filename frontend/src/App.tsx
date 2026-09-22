import { useEffect, useState } from 'react'

type BackendStatus = 'checking' | 'connected' | 'unavailable'

const STATUS_TEXT: Record<BackendStatus, string> = {
  checking: 'Checking backend...',
  connected: 'Backend connected',
  unavailable: 'Backend unavailable',
}

function App() {
  const [status, setStatus] = useState<BackendStatus>('checking')

  useEffect(() => {
    const baseUrl = import.meta.env.VITE_API_BASE_URL as string | undefined
    if (!baseUrl) {
      setStatus('unavailable')
      return
    }

    let cancelled = false

    fetch(`${baseUrl}/actuator/health`)
      .then((response) => {
        if (!response.ok) throw new Error('Health check request failed')
        return response.json()
      })
      .then((data: { status?: string }) => {
        if (!cancelled) setStatus(data.status === 'UP' ? 'connected' : 'unavailable')
      })
      .catch(() => {
        if (!cancelled) setStatus('unavailable')
      })

    return () => {
      cancelled = true
    }
  }, [])

  return (
    <main className="flex min-h-screen flex-col items-center justify-center">
      <h1 className="text-2xl font-semibold">QueueFlow</h1>
      <p className="mt-2 text-sm">Frontend foundation is running.</p>
      <p className="mt-4 text-sm">{STATUS_TEXT[status]}</p>
    </main>
  )
}

export default App
