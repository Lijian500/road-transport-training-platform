/** 将档案状态转成统一中文文案，保留未知值以便定位问题。 */
export function recordLabel(value?: string) {
  const labels: Record<string, string> = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    NOT_COMPLETED: '未完成',
    NOT_REQUIRED: '无需考试',
    PASSED: '已通过',
    FAILED: '未通过',
    PUBLISHED: '待开始',
    FINISHED: '已结束',
    CANCELLED: '已取消',
    CREATED: '已创建',
    SIGNED_IN: '已签到',
    STUDYING: '学习中',
    PAUSED: '已暂停',
    DISCONNECTED: '连接中断',
    FACE_PENDING: '等待抽验',
    SIGNED_OUT: '已签退',
    TERMINATED: '已终止',
    PENDING: '待处理',
    TIMED_OUT: '已超时',
    FACE_CHECK_FAILED: '抽验失败',
    FACE_CHECK_TIMEOUT: '抽验超时',
    DEADLINE_EXCEEDED: '超过抽验时限',
    SESSION_TERMINATED: '会话已终止',
    USER_TERMINATED: '学员主动终止',
    SIGN_IN: '签到',
    PLAY: '播放',
    PROGRESS: '进度上报',
    PAUSE: '暂停',
    RESUME: '恢复',
    SIGN_OUT: '签退',
    DISCONNECT: '断开连接',
    SYNC: '状态同步',
    INVALID_IMAGE: '图片无效',
    DIFFERENT_PERSON: '人脸不匹配',
    NO_FACE: '未检测到人脸',
    MULTIPLE_FACES: '检测到多张人脸',
    SAME_PERSON: '人脸匹配',
    SUCCESS: '成功',
  }
  return value ? labels[value] || value : '—'
}

/** 精确到秒展示服务端确认学时，不把不足一分钟进位为一分钟。 */
export function recordDuration(milliseconds: number) {
  const seconds = Math.floor(Math.max(0, milliseconds) / 1000)
  return `${Math.floor(seconds / 3600)}小时${Math.floor((seconds % 3600) / 60)}分${seconds % 60}秒`
}

/** 格式化档案时间，空值使用统一占位。 */
export function recordTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}
