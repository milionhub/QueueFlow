import { SkeletonFrame } from '../../../components/ui/States'

/** The dashboard's shape while it loads: your tickets and recent changes, then status and projects. */
export function DashboardSkeleton() {
  return (
    <SkeletonFrame label="Loading dashboard…">
      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="flex flex-col gap-5">
          <SkeletonCard rows={4} />
          <SkeletonCard rows={3} />
        </div>
        <div className="flex flex-col gap-5">
          <div className="rounded-lg border border-line bg-surface p-4 shadow-xs">
            <div className="skeleton h-3.5 w-32" />
            <div className="skeleton mt-5 h-2 w-full rounded-full" />
            <div className="mt-4 flex flex-col gap-3">
              {[1, 2, 3].map((row) => (
                <div key={row} className="skeleton h-3 w-2/3" />
              ))}
            </div>
          </div>
          <SkeletonCard rows={2} />
        </div>
      </div>
    </SkeletonFrame>
  )
}

function SkeletonCard({ rows }: { rows: number }) {
  return (
    <div className="rounded-lg border border-line bg-surface shadow-xs">
      <div className="flex h-12 items-center border-b border-line px-4">
        <div className="skeleton h-3.5 w-32" />
      </div>
      <div className="divide-y divide-line">
        {Array.from({ length: rows }, (_, index) => (
          <div key={index} className="flex h-11 items-center gap-3 px-4">
            <div className="skeleton h-3 w-12 shrink-0" />
            <div className="skeleton h-3 flex-1" style={{ maxWidth: `${60 - index * 8}%` }} />
          </div>
        ))}
      </div>
    </div>
  )
}
