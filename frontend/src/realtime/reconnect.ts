const DEFAULT_DELAYS = [1_000, 2_000, 5_000, 10_000]

export function reconnectDelay(attempt: number, delays = DEFAULT_DELAYS) {
  const index = Math.min(Math.max(attempt, 0), delays.length - 1)
  return delays[index]
}
