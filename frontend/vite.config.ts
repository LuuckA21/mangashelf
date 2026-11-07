import { readFileSync } from 'node:fs'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The app version shown in the footer is the project's, from backend/pom.xml.
const pom = readFileSync(new URL('../backend/pom.xml', import.meta.url), 'utf8')
const version = /<\/parent>[\s\S]*?<version>([^<]+)<\/version>/.exec(pom)?.[1]
if (!version) throw new Error('No <version> after <parent> in backend/pom.xml')
process.env.VITE_APP_VERSION = version

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': 'http://localhost:8080' }, // same origin in dev
  },
})
