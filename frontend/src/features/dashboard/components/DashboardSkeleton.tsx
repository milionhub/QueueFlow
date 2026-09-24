const BLOCK = 'rounded bg-line motion-safe:animate-pulse'

/** The dashboard's shape while it loads: context line, status strip, ticket rows, projects. */
export function DashboardSkeleton() {
  return (
    <div aria-busy="true" className="flex flex-col gap-6">
      <span className="sr-only" role="status">
        Loading dashboard…
      </span>
      <div aria-hidden="true" className="flex flex-col gap-6">
        <div className={`h-4 w-64 max-w-full ${BLOCK}`} />
        <div className="h-[46px] rounded-md border border-line bg-surface" />
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <SkeletonList rows={4} />
          <SkeletonList rows={3} />
        </div>
      </div>
    </div>
  )
}

function SkeletonList({ rows }: { rows: number }) {
  return (
    <div>
      <div className={`mb-2 h-4 w-32 ${BLOCK}`} />
      <div className="divide-y divide-line rounded-md border border-line bg-surface">
        {Array.from({ length: rows }, (_, index) => (
          <div key={index} className="flex items-center gap-3 px-4 py-3">
            <div className={`h-3 w-12 shrink-0 ${BLOCK}`} />
            <div className={`h-3 flex-1 ${BLOCK}`} style={{ maxWidth: `${60 - index * 8}%` }} />
          </div>
        ))}
      </div>
    </div>
  )
}
