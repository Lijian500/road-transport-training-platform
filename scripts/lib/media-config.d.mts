export const repositoryRoot: string
export const mediaScenarios: string[]
export const loadGroups: number[]
export function mediaEnvironment(): Record<string, string | undefined>
export function mediaStudentKeys(): string[]
export function requireMediaConfiguration(
  env: Record<string, string | undefined>, options?: { faces?: boolean },
): { files: Record<string, string>; seconds: number }
export function readDemoState(env: Record<string, string | undefined>): {
  path: string; state: unknown
}
export function requireMediaState(state: unknown, options?: { load?: boolean }): void
