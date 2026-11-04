import '@testing-library/jest-dom/vitest'
import 'vitest-axe/extend-expect'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// Vitest has no globals here, so Testing Library cannot register this itself.
afterEach(cleanup)

// axe probes canvas support; jsdom has none and would print a notice.
HTMLCanvasElement.prototype.getContext = () => null

// Pages are lazy chunks: the first load can exceed the 1 s default when the machine is busy.
configure({ asyncUtilTimeout: 5000 })
