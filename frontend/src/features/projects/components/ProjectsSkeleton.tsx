const BLOCK = 'rounded bg-line motion-safe:animate-pulse'

/** The page's shape while the projects load: context line and a few rows. */
export function ProjectsSkeleton() {
  return (
    <div aria-busy="true" className="flex flex-col gap-4">
      <span className="sr-only" role="status">
        Loading projects…
      </span>
      <div aria-hidden="true" className="flex flex-col gap-4">
        <div className={`h-4 w-48 max-w-full ${BLOCK}`} />
        <div className="divide-y divide-line rounded-md border border-line bg-surface">
          {[64, 48, 56].map((width) => (
            <div key={width} className="flex items-start gap-3 px-4 py-3.5">
              <div className={`h-3 w-12 shrink-0 ${BLOCK}`} />
              <div className="flex flex-1 flex-col gap-2">
                <div className={`h-3 ${BLOCK}`} style={{ width: `${width}%` }} />
                <div className={`h-3 ${BLOCK}`} style={{ width: `${width - 16}%` }} />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
