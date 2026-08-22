import js from '@eslint/js'
import { defineConfig, globalIgnores } from 'eslint/config'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig([
  globalIgnores(['dist', 'coverage']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 'latest',
      globals: globals.browser,
    },
    rules: {
      // Data fetching legitimately sets loading/error state from effects.
      // The dependency rule still guards stale closures around those calls.
      'react-hooks/set-state-in-effect': 'off',
      'react-refresh/only-export-components': ['error', {
        allowExportNames: ['isExpiredSession', 'useI18n', 'useSession'],
      }],
    },
  },
])
