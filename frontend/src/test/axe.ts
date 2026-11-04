import { configureAxe } from 'vitest-axe'

// jsdom cannot compute colors; contrast is covered by styles.test.ts
export const axe = configureAxe({
  rules: { 'color-contrast': { enabled: false } },
})
