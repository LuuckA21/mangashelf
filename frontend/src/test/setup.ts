import '@testing-library/jest-dom/vitest'
import 'vitest-axe/extend-expect'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Vitest has no globals here, so Testing Library cannot register this itself.
afterEach(cleanup)

// axe probes canvas support; jsdom has none and would print a notice.
HTMLCanvasElement.prototype.getContext = () => null
