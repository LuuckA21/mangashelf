import { useId, useState, type InputHTMLAttributes } from 'react'
import type { Publisher } from '../../api/types'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange'> & {
  value: string
  onChange: (value: string) => void
  options: Publisher[]
}

const MAX = 8

/**
 * Text field with its own suggestion list (ARIA combobox): mobile browsers show
 * <datalist> badly or not at all. Suggestions are names containing the typed text.
 */
export default function PublisherInput({
  value,
  onChange,
  options,
  ...input
}: Props) {
  const listId = useId()
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const query = value.trim().toLowerCase()
  const matches = query
    ? options
        .filter((p) => p.name.toLowerCase().includes(query))
        .filter((p) => p.name.toLowerCase() !== query)
        .slice(0, MAX)
    : []
  const shown = open && matches.length > 0

  function choose(p: Publisher) {
    onChange(p.name)
    setOpen(false)
    setActive(-1)
  }

  return (
    <div className="combo">
      <input
        {...input}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={shown}
        aria-controls={listId}
        aria-activedescendant={
          shown && active >= 0 ? `${listId}-${active}` : undefined
        }
        autoComplete="off"
        value={value}
        onChange={(e) => {
          onChange(e.target.value)
          setOpen(true)
          setActive(-1)
        }}
        onBlur={() => setOpen(false)}
        onKeyDown={(e) => {
          if (!shown) return
          if (e.key === 'ArrowDown') {
            e.preventDefault()
            setActive((a) => (a + 1) % matches.length)
          } else if (e.key === 'ArrowUp') {
            e.preventDefault()
            setActive((a) => (a <= 0 ? matches.length - 1 : a - 1))
          } else if (e.key === 'Enter' && active >= 0) {
            e.preventDefault() // pick, do not submit the form
            choose(matches[active])
          } else if (e.key === 'Escape') {
            setOpen(false)
          }
        }}
      />
      {shown && (
        <ul id={listId} role="listbox" className="combo-list">
          {matches.map((p, i) => (
            // Keyboard lives on the input (arrows + Enter), as the combobox pattern expects.
            // eslint-disable-next-line jsx-a11y/click-events-have-key-events
            <li
              key={p.id}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              // mousedown would blur the input and close the list before the click
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => choose(p)}
            >
              {p.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
