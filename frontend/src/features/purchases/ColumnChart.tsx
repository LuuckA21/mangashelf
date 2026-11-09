import { useEffect, useId, useRef, useState } from 'react'
import { t } from '../../i18n/t'
import { formatChfGrouped } from './money'

interface ChartColumn {
  short: string
  full: string
  spentCents: number
  upcomingCents: number
  aboveAverage: boolean
  hasLists: boolean
}

const W = 360
const H = 200
const TOP = 28 // room for the ▲ markers and the average label
const BASE = 176 // baseline; short labels sit below
const PLOT = BASE - TOP

export default function ColumnChart({
  label,
  columns,
  averageCents,
}: {
  label: string
  columns: ChartColumn[]
  averageCents: number
}) {
  const stripes = useId()
  const box = useRef<HTMLDivElement>(null)
  const [shown, setShown] = useState<number | null>(null)

  useEffect(() => {
    if (shown === null) return
    const away = (e: Event) => {
      if (!box.current?.contains(e.target as Node)) setShown(null)
    }
    document.addEventListener('pointerdown', away)
    return () => document.removeEventListener('pointerdown', away)
  }, [shown])

  const max = Math.max(
    averageCents,
    ...columns.map((c) => c.spentCents + c.upcomingCents),
  )
  const h = (cents: number) => (max > 0 ? (cents / max) * PLOT : 0)
  const slot = W / columns.length
  const bar = Math.min(slot * 0.6, 40)
  const avgY = BASE - h(averageCents)
  const tip = shown === null ? null : columns[shown]

  return (
    <div className="summary-chart" ref={box}>
      <div className="chart-legend">
        <span>
          <i className="swatch spent" /> {t('summary.spent')}
        </span>
        <span>
          <i className="swatch upcoming" /> {t('summary.upcoming')}
        </span>
      </div>
      <div className="chart-tip">
        {tip &&
          t('summary.tip', {
            name: tip.full,
            spent: tip.hasLists ? formatChfGrouped(tip.spentCents) : '—',
            upcoming: tip.hasLists ? formatChfGrouped(tip.upcomingCents) : '—',
          })}
      </div>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        width="100%"
        role="img"
        aria-label={label}
        className="chart-svg"
      >
        <defs>
          <pattern
            id={stripes}
            width="6"
            height="6"
            patternUnits="userSpaceOnUse"
            patternTransform="rotate(45)"
          >
            <rect width="6" height="6" fill="var(--indigo-wash)" />
            <rect width="3" height="6" fill="var(--indigo)" opacity="0.55" />
          </pattern>
        </defs>
        <line x1="0" x2={W} y1={BASE} y2={BASE} stroke="var(--ash-line)" />
        {columns.map((c, i) => {
          const x = i * slot + (slot - bar) / 2
          const hs = h(c.spentCents)
          const hu = h(c.upcomingCents)
          return (
            <g key={i}>
              <rect
                x={x}
                y={BASE - hs}
                width={bar}
                height={hs}
                fill="var(--indigo)"
              />
              <rect
                x={x}
                y={BASE - hs - hu}
                width={bar}
                height={hu}
                fill={`url(#${stripes})`}
                stroke="var(--indigo)"
                strokeWidth="0.5"
              />
              {c.aboveAverage && (
                <text
                  data-testid="above"
                  aria-hidden="true"
                  x={x + bar / 2}
                  y={BASE - hs - hu - 4}
                  textAnchor="middle"
                  fontSize="10"
                  fill="var(--indigo-deep)"
                >
                  ▲
                </text>
              )}
              <text
                x={x + bar / 2}
                y={BASE + 16}
                textAnchor="middle"
                fontSize="12"
                fill="var(--ash)"
              >
                {c.short}
              </text>
            </g>
          )
        })}
        {averageCents > 0 && (
          <g data-testid="average">
            <line
              x1="0"
              x2={W}
              y1={avgY}
              y2={avgY}
              stroke="var(--indigo-deep)"
              strokeDasharray="4 3"
            />
            <text
              x={W}
              y={avgY - 3}
              textAnchor="end"
              fontSize="10"
              fill="var(--indigo-deep)"
            >
              {formatChfGrouped(averageCents)}
            </text>
          </g>
        )}
        {columns.map((_, i) => (
          <rect
            key={i}
            data-testid="hit"
            className={shown === i ? 'hit on' : 'hit'}
            x={i * slot}
            y="0"
            width={slot}
            height={BASE}
            onPointerEnter={() => setShown(i)}
            onPointerLeave={() => setShown(null)}
            onClick={() => setShown(i)}
          />
        ))}
      </svg>
    </div>
  )
}
