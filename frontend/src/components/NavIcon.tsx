/** Small line icons for the phone tab bar; decorative, the label names the link. */
const PATHS = {
  collection: 'M4 5h4v14H4zM10 5h4v14h-4zM16.5 5.5l3.5 1-3.6 13-3.4-1z',
  catalog: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  purchases: 'M6 8h12l-1 12H7zM9 8a3 3 0 0 1 6 0',
  account: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 20a8 8 0 0 1 16 0',
} as const

export default function NavIcon({ name }: { name: keyof typeof PATHS }) {
  return (
    <svg
      className="nav-icon"
      viewBox="0 0 24 24"
      width="22"
      height="22"
      aria-hidden="true"
      focusable="false"
    >
      <path
        d={PATHS[name]}
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
    </svg>
  )
}
