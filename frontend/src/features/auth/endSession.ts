import type { QueryClient } from '@tanstack/react-query'

// Drop everything cached for the previous user, not just `me`.
export function endSession(client: QueryClient) {
  client.clear()
  client.setQueryData(['me'], null)
}
