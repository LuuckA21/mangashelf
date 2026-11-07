import {
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type InputHTMLAttributes,
} from 'react'
import type { Publisher } from '../../api/types'
import { t } from '../../i18n/t'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange'> & {
  value: string
  onChange: (value: string) => void
  options: Publisher[]
}

const MAX = 20
const NONE: Publisher = { id: '', name: '' }

/**
 * Text field with its own suggestion list (ARIA combobox): mobile browsers show
 * <datalist> badly or not at all. Opening it shows every publisher; typing narrows
 * it to the names containing the text.
 */
export default function PublisherInput({
  value,
  onChange,
  options,
  ...input
}: Props) {
  const listId = useId()
  const field = useRef<HTMLInputElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const [open, setOpen] = useState(false)
  const [typed, setTyped] = useState(false)
  const [active, setActive] = useState(-1)
  const query = value.trim().toLowerCase()
  const shown =
    typed && query
      ? options.filter((p) => p.name.toLowerCase().includes(query))
      : options
  const first = shown.slice(0, MAX)
  // the current publisher is always listed, even past the first MAX names
  const current = shown.find((p) => p.name.toLowerCase() === query)
  const items = [
    NONE,
    ...first,
    ...(current && !first.includes(current) ? [current] : []),
  ]

  // On opening, centre the current publisher in the list (its own scroll, not the page's).
  useLayoutEffect(() => {
    const ul = list.current
    const li = ul?.querySelector<HTMLElement>('[aria-selected="true"]')
    if (!ul || !li) return
    ul.scrollTop +=
      li.getBoundingClientRect().top -
      ul.getBoundingClientRect().top -
      (ul.clientHeight - li.offsetHeight) / 2
  }, [open])

  // Arrow keys: keep the highlighted option inside the list's visible part.
  useLayoutEffect(() => {
    const ul = list.current
    const li = active >= 0 ? ul?.children[active] : null
    if (!ul || !li) return
    const box = ul.getBoundingClientRect()
    const row = li.getBoundingClientRect()
    if (row.top < box.top) ul.scrollTop -= box.top - row.top
    else if (row.bottom > box.bottom) ul.scrollTop += row.bottom - box.bottom
  }, [active])

  function show() {
    setOpen(true)
    setTyped(false)
    setActive(-1)
  }
  function choose(p: Publisher) {
    onChange(p.name)
    setOpen(false)
  }

  return (
    <div className="combo">
      <div className="combo-field">
        <input
          {...input}
          ref={field}
          role="combobox"
          aria-autocomplete="list"
          aria-expanded={open}
          aria-controls={listId}
          aria-activedescendant={
            open && active >= 0 ? `${listId}-${active}` : undefined
          }
          autoComplete="off"
          value={value}
          onFocus={show}
          onClick={() => open || show()}
          onBlur={() => setOpen(false)}
          onChange={(e) => {
            onChange(e.target.value)
            setOpen(true)
            setTyped(true)
            setActive(-1)
          }}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
              e.preventDefault()
              if (!open) return show()
              const step = e.key === 'ArrowDown' ? 1 : -1
              setActive((a) => (a + step + items.length) % items.length)
            } else if (e.key === 'Enter' && open && active >= 0) {
              e.preventDefault() // pick, do not submit the form
              choose(items[active])
            } else if (e.key === 'Escape') {
              setOpen(false)
            }
          }}
        />
        <button
          type="button"
          className="quiet combo-toggle"
          tabIndex={-1} // the field already opens it from the keyboard
          aria-label={t('edition.showPublishers')}
          aria-expanded={open}
          aria-controls={listId}
          // keep the focus in the field, so blur does not close the list first
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => {
            if (open) setOpen(false)
            else {
              field.current?.focus()
              show()
            }
          }}
        >
          ▾
        </button>
      </div>
      {open && (
        <ul ref={list} id={listId} role="listbox" className="combo-list">
          {items.map((p, i) => (
            // Keyboard lives on the input (arrows + Enter), as the combobox pattern expects.
            // eslint-disable-next-line jsx-a11y/click-events-have-key-events
            <li
              key={p.id || 'none'}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={p.name.toLowerCase() === query}
              className={i === active ? 'active' : undefined}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => choose(p)}
            >
              {p === NONE ? t('edition.noPublisher') : p.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
