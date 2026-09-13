/** 培训周期只展示年月日，保持后端本地日期语义。 */
export function formatTrainingDate(value?: string) {
  return value ? value.slice(0, 10) : '-'
}

/** 将毫秒转换为精确到秒的已学或要求时长。 */
export function formatLearningDuration(value: number) {
  const seconds = Math.max(0, Math.floor(value / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours ? `${hours}小时` : ''}${minutes ? `${minutes}分` : ''}${seconds % 60}秒`
}

/** 按已确认时长计算进度，限制在百分之零至一百。 */
export function learningPercentage(completed: number, required: number) {
  return required > 0 ? Math.min(100, Math.max(0, Math.floor((completed / required) * 100))) : 0
}
