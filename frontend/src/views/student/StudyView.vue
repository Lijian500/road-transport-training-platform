<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import {
  getActiveLearningSession,
  getLearningCourse,
  getLearningPlaybackUrl,
  getLearningSession,
  openLearningSession,
  terminateLearningSession,
  type CourseProgress,
  type CoursewareProgress,
  type FaceCheckTask,
  type LearningEventResult,
  type LearningEventType,
  type LearningSession,
} from '@/api/learning'
import { ApiError } from '@/api/http'
import FaceCheckDialog from '@/components/FaceCheckDialog/FaceCheckDialog.vue'
import { LearningRealtimeError, useLearningRealtime } from '@/composables/useLearningRealtime'
import {
  getOrCreateClientInstanceId,
  isCoursewareUnlocked,
  resolveCoursewareStatusAfterEvent,
} from '@/learning/session'

const route = useRoute()
const router = useRouter()
const planId = String(route.params.planId)
const planCourseId = String(route.params.planCourseId)
const clientInstanceId = getOrCreateClientInstanceId()

const loading = ref(false)
const eventBusy = ref(false)
const playbackLoading = ref(false)
const course = ref<CourseProgress>()
const session = ref<LearningSession>()
const conflictSession = ref<LearningSession | null>(null)
const selectedCoursewareId = ref<string>()
const videoUrl = ref('')
const video = ref<HTMLVideoElement>()
const currentFaceCheck = ref<FaceCheckTask | null>(null)

let progressTimer: number | undefined
let eventChain: Promise<unknown> = Promise.resolve()
let suppressPause = false
let approvedPlay = false
let handlingEnd = false
let restoringPosition = false
let playbackRetries = 0

const learningRealtime = useLearningRealtime({
  clientInstanceId,
  onStateSync: applySessionState,
  onProgressConfirmed: applyEventResult,
  onFaceCheckRequired: handleFaceCheckRequired,
  onFaceCheckResult: handleFaceCheckResult,
  onDisconnected: handleRealtimeDisconnected,
  onReplaced: handleRealtimeReplaced,
})
const realtimeReady = learningRealtime.ready
const realtimeState = learningRealtime.connectionState

const selectedCourseware = computed(() =>
  course.value?.coursewares.find(
    (value) => value.coursewareSnapshotId === selectedCoursewareId.value,
  ),
)

const completionPercentage = computed(() => {
  if (!course.value?.requiredDurationMillis) return 0
  return Math.min(
    100,
    Math.floor((course.value.effectiveDurationMillis / course.value.requiredDurationMillis) * 100),
  )
})

/** 初始化课程进度并创建或恢复当前浏览器学习会话。 */
async function initialize() {
  loading.value = true
  try {
    course.value = await getLearningCourse(planId, planCourseId)
    await openCurrentSession()
  } catch (error) {
    if (error instanceof ApiError && error.code === 'L3003') {
      conflictSession.value = await getActiveLearningSession()
    } else {
      showError(error, '学习课程加载失败')
    }
  } finally {
    loading.value = false
  }
}

/** 创建REST会话，再由实时通道完成绑定、同步及必要的安全暂停。 */
async function openCurrentSession() {
  session.value = await openLearningSession({ planId, planCourseId, clientInstanceId })
  selectInitialCourseware()
  session.value = await learningRealtime.bind(session.value.id)
  restoreFaceCheck(session.value)
  if (['SIGNED_IN', 'PAUSED'].includes(session.value.status)) {
    await loadPlaybackUrl()
  }
}

/** 优先恢复服务端当前课件，否则选择第一个未完成且已解锁课件。 */
function selectInitialCourseware() {
  if (!course.value || !session.value) return
  const current = course.value.coursewares.find(
    (value) => value.coursewareSnapshotId === session.value?.currentCoursewareSnapshotId,
  )
  const firstIncomplete = course.value.coursewares.find(
    (value) =>
      value.status !== 'COMPLETED' && isCoursewareUnlocked(course.value!.coursewares, value),
  )
  selectedCoursewareId.value =
    current?.coursewareSnapshotId ||
    firstIncomplete?.coursewareSnapshotId ||
    course.value.coursewares[0]?.coursewareSnapshotId
}

/** 完成学习签到并加载首个已解锁视频。 */
async function signIn() {
  if (!realtimeReady.value) return
  try {
    await enqueueEvent('SIGN_IN')
    await loadPlaybackUrl()
    ElMessage.success('签到成功，可以开始学习')
  } catch (error) {
    showError(error, '学习签到失败')
  }
}

/** 选择已解锁课件，学习中切换前先安全暂停。 */
async function selectCourseware(target: CoursewareProgress) {
  if (!course.value || !isCoursewareUnlocked(course.value.coursewares, target)) return
  try {
    if (session.value?.status === 'STUDYING') {
      await pauseCurrentVideo()
    }
    selectedCoursewareId.value = target.coursewareSnapshotId
    videoUrl.value = ''
    playbackRetries = 0
    if (session.value && ['SIGNED_IN', 'PAUSED'].includes(session.value.status)) {
      await loadPlaybackUrl()
    }
  } catch (error) {
    showError(error, '课件切换失败')
  }
}

/** 获取当前课件短期OSS地址，地址刷新后恢复服务端确认位置。 */
async function loadPlaybackUrl() {
  if (!session.value || !selectedCourseware.value) return
  playbackLoading.value = true
  try {
    const signed = await getLearningPlaybackUrl(
      session.value.id,
      selectedCourseware.value.coursewareSnapshotId,
      clientInstanceId,
    )
    videoUrl.value = signed.url
    await nextTick()
    if (video.value) video.value.load()
  } finally {
    playbackLoading.value = false
  }
}

/** 媒体元数据可用后恢复课件自身的服务端确认位置。 */
function onLoadedMetadata() {
  if (!video.value || !selectedCourseware.value) return
  playbackRetries = 0
  restoringPosition = true
  video.value.currentTime = selectedCourseware.value.confirmedPositionMillis / 1000
  window.setTimeout(() => {
    restoringPosition = false
  })
}

/** 程序化暂停播放器，并由随后触发的pause事件恢复抑制标记。 */
function pausePlayerSilently() {
  const player = video.value
  if (!player || player.paused) {
    suppressPause = false
    return
  }
  suppressPause = true
  player.pause()
}

/** 在浏览器真正播放前先获得服务端PLAY状态确认。 */
async function onVideoPlay() {
  if (session.value?.status === 'FACE_PENDING') {
    pausePlayerSilently()
    ElMessage.warning('请先完成人脸抽验，再手动继续播放')
    return
  }
  if (!realtimeReady.value) {
    pausePlayerSilently()
    ElMessage.warning('实时学习连接尚未恢复，请稍后手动继续播放')
    return
  }
  if (approvedPlay) {
    approvedPlay = false
    startProgressTimer()
    return
  }
  if (!session.value || session.value.status === 'STUDYING' || !selectedCourseware.value) return
  pausePlayerSilently()
  try {
    await enqueueEvent(
      'PLAY',
      selectedCourseware.value.coursewareSnapshotId,
      selectedCourseware.value.confirmedPositionMillis,
    )
    approvedPlay = true
    await video.value?.play()
  } catch (error) {
    stopProgressTimer()
    showError(error, '开始播放失败')
  }
}

/** 用户主动暂停时同步最终位置并停止定时上报。 */
async function onVideoPause() {
  stopProgressTimer()
  if (suppressPause) {
    suppressPause = false
    return
  }
  if (video.value?.ended || handlingEnd || session.value?.status !== 'STUDYING') return
  try {
    await pauseCurrentVideo()
  } catch (error) {
    showError(error, '暂停状态同步失败')
  }
}

/** 请求服务端从头播放已完成课件，补足有效学时而不清空历史进度。 */
async function supplementLearning() {
  if (!selectedCourseware.value || !video.value || !realtimeReady.value) return
  try {
    if (session.value?.status === 'STUDYING') await pauseCurrentVideo()
    const result = await enqueueEvent('PLAY', selectedCourseware.value.coursewareSnapshotId, 0)
    restoringPosition = true
    video.value.currentTime = result.confirmedPositionMillis / 1000
    window.setTimeout(() => {
      restoringPosition = false
    })
    approvedPlay = true
    await video.value.play()
  } catch (error) {
    stopProgressTimer()
    showError(error, '补学启动失败')
  }
}

/** 视频播放结束时使用PAUSE事件结算最后一段并解锁下一课件。 */
async function onVideoEnded() {
  if (!selectedCourseware.value) return
  if (session.value?.status === 'COMPLETED') {
    stopProgressTimer()
    return
  }
  handlingEnd = true
  stopProgressTimer()
  try {
    const result = await enqueueEvent(
      'PAUSE',
      selectedCourseware.value.coursewareSnapshotId,
      selectedCourseware.value.durationMillis,
    )
    if (result.coursewareCompleted) {
      ElMessage.success(result.courseCompleted ? '课程学习已完成' : '课件已完成')
      const next = course.value?.coursewares.find(
        (value) =>
          value.status !== 'COMPLETED' &&
          course.value &&
          isCoursewareUnlocked(course.value.coursewares, value),
      )
      if (next) await selectCourseware(next)
    }
  } catch (error) {
    showError(error, '课件完成状态同步失败')
  } finally {
    handlingEnd = false
  }
}

/** OSS地址过期或媒体加载失败时重新签名并恢复确认位置。 */
async function onVideoError() {
  if (!videoUrl.value || playbackRetries >= 2) {
    ElMessage.error('视频加载失败，请稍后重新进入课程')
    return
  }
  playbackRetries += 1
  try {
    if (session.value?.status === 'STUDYING') await pauseCurrentVideo()
    await loadPlaybackUrl()
  } catch (error) {
    showError(error, '视频地址续期失败')
  }
}

/** 不允许拖动时把播放器恢复到服务端最近确认位置。 */
function onSeeking() {
  if (!video.value || !course.value || !session.value || restoringPosition) return
  if (!course.value.allowSeek) {
    restoringPosition = true
    video.value.currentTime = session.value.confirmedPositionMillis / 1000
    window.setTimeout(() => {
      restoringPosition = false
    })
  }
}

/** 提交当前视频位置的暂停事件。 */
async function pauseCurrentVideo() {
  if (!session.value || !selectedCourseware.value) return
  pausePlayerSilently()
  if (!realtimeReady.value) {
    throw new LearningRealtimeError('实时学习连接已断开', 'REALTIME_DISCONNECTED', true)
  }
  await enqueueEvent(
    'PAUSE',
    selectedCourseware.value.coursewareSnapshotId,
    Math.floor((video.value?.currentTime || 0) * 1000),
  )
}

/** 正常签退当前会话并返回培训任务详情。 */
async function signOut() {
  if (!realtimeReady.value) return
  try {
    if (session.value?.status === 'STUDYING') await pauseCurrentVideo()
    await enqueueEvent(
      'SIGN_OUT',
      selectedCourseware.value?.coursewareSnapshotId,
      Math.floor((video.value?.currentTime || 0) * 1000),
    )
    ElMessage.success('学习签退成功')
    await router.replace(`/student/plans/${planId}`)
  } catch (error) {
    showError(error, '学习签退失败')
  }
}

/** 跳转到冲突会话所属课程，避免后台自动挤掉有效会话。 */
async function goToConflictSession() {
  if (!conflictSession.value) return
  await router.replace(
    `/student/plans/${conflictSession.value.planId}/courses/${conflictSession.value.planCourseId}/study`,
  )
}

/** 经学员明确确认后终止遗留会话并重新进入当前课程。 */
async function terminateConflictSession() {
  if (!conflictSession.value) return
  try {
    await terminateLearningSession(conflictSession.value.id)
    conflictSession.value = null
    await openCurrentSession()
    ElMessage.success('原学习会话已终止')
  } catch (error) {
    showError(error, '终止原学习会话失败')
  }
}

/** 将学习事件串行排队，确保浏览器不会并发占用同一序号。 */
function enqueueEvent(
  eventType: LearningEventType,
  coursewareSnapshotId?: string,
  videoPositionMillis = 0,
) {
  const action = eventChain.then(() =>
    sendEventNow(eventType, coursewareSnapshotId, videoPositionMillis),
  )
  eventChain = action.catch(() => undefined)
  return action
}

/** 将下一严格序号事件交给实时组合函数确认和断线恢复。 */
async function sendEventNow(
  eventType: LearningEventType,
  coursewareSnapshotId?: string,
  videoPositionMillis = 0,
) {
  if (!session.value) throw new ApiError('学习会话尚未创建')
  eventBusy.value = true
  try {
    return await learningRealtime.sendEvent(
      eventType,
      session.value.lastSequence + 1,
      coursewareSnapshotId,
      videoPositionMillis,
    )
  } finally {
    eventBusy.value = false
  }
}

/** 将服务端STATE_SYNC快照合并到学习页。 */
function applySessionState(state: LearningSession) {
  session.value = state
  restoreFaceCheck(state)
  if (!course.value) return
  course.value.effectiveDurationMillis = state.effectiveDurationMillis
  const current = course.value.coursewares.find(
    (value) => value.coursewareSnapshotId === state.currentCoursewareSnapshotId,
  )
  if (current) {
    current.confirmedPositionMillis = state.confirmedPositionMillis
    current.maxConfirmedPositionMillis = Math.max(
      current.maxConfirmedPositionMillis,
      state.confirmedPositionMillis,
    )
  }
}

/** 从STATE_SYNC恢复待处理抽验，或清理已由服务端结束的过期弹窗。 */
function restoreFaceCheck(state: LearningSession) {
  if (state.currentFaceCheck) {
    currentFaceCheck.value = state.currentFaceCheck
    if (state.status === 'FACE_PENDING') freezeForFaceCheck()
    return
  }
  if (state.status !== 'FACE_PENDING' && currentFaceCheck.value?.status === 'PENDING') {
    currentFaceCheck.value = null
  }
}

/** 处理实时抽验触发，立即冻结播放器和进度上报。 */
function handleFaceCheckRequired(faceCheck: FaceCheckTask) {
  if (faceCheck.sessionId !== session.value?.id) return
  currentFaceCheck.value = faceCheck
  if (session.value) {
    session.value = { ...session.value, status: 'FACE_PENDING', currentFaceCheck: faceCheck }
  }
  freezeForFaceCheck()
}

/** 合并HTTP或实时抽验结果，并获取最终会话状态。 */
function handleFaceCheckResult(faceCheck: FaceCheckTask) {
  if (faceCheck.sessionId !== session.value?.id) return
  currentFaceCheck.value = faceCheck
  freezeForFaceCheck()
  if (faceCheck.status !== 'PENDING') void refreshSessionAfterFaceCheck()
}

/** 停止所有学习计时入口并静默暂停播放器。 */
function freezeForFaceCheck() {
  stopProgressTimer()
  approvedPlay = false
  pausePlayerSilently()
}

/** 查询抽验后的服务端会话，确保通过后只恢复为手动播放的暂停态。 */
async function refreshSessionAfterFaceCheck() {
  if (!session.value) return
  try {
    applySessionState(await getLearningSession(session.value.id))
  } catch (error) {
    showError(error, '抽验后的学习状态同步失败')
  }
}

/** 倒计时结束后刷新服务端终态，不在浏览器本地擅自判定。 */
async function handleFaceCheckExpired() {
  freezeForFaceCheck()
  await refreshSessionAfterFaceCheck()
  if (session.value?.status === 'TERMINATED' && currentFaceCheck.value?.status === 'PENDING') {
    currentFaceCheck.value = {
      ...currentFaceCheck.value,
      status: 'TIMED_OUT',
      result: 'TIMEOUT',
      failureReason: '未在规定时间内完成人脸抽验',
    }
  }
}

/** 关闭抽验终态提示；通过后保持PAUSED并等待学员手动继续。 */
function acknowledgeFaceCheck() {
  const passed = currentFaceCheck.value?.status === 'PASSED'
  currentFaceCheck.value = null
  if (passed) ElMessage.success('人脸抽验已通过，请手动继续播放')
}

/** 将服务端确认结果同步到会话、课程和当前课件视图。 */
function applyEventResult(result: LearningEventResult) {
  if (!session.value || !course.value) return
  session.value = {
    ...session.value,
    status: result.status,
    lastSequence: result.acceptedSequence,
    currentCoursewareSnapshotId: result.currentCoursewareSnapshotId,
    confirmedPositionMillis: result.confirmedPositionMillis,
    effectiveDurationMillis: result.effectiveDurationMillis,
    requiredDurationMillis: result.requiredDurationMillis,
    lastEventAt: result.serverTime,
  }
  course.value.effectiveDurationMillis = result.effectiveDurationMillis
  if (result.courseCompleted) {
    course.value.status = 'COMPLETED'
    course.value.completedAt = result.serverTime
  } else if (result.effectiveDurationMillis > 0) {
    course.value.status = 'IN_PROGRESS'
  }
  const current = course.value.coursewares.find(
    (value) => value.coursewareSnapshotId === result.currentCoursewareSnapshotId,
  )
  if (current) {
    current.confirmedPositionMillis = result.confirmedPositionMillis
    current.maxConfirmedPositionMillis = Math.max(
      current.maxConfirmedPositionMillis,
      result.confirmedPositionMillis,
    )
    current.status = resolveCoursewareStatusAfterEvent(
      current.status,
      result.coursewareCompleted,
      result.status,
    )
    if (result.coursewareCompleted) {
      current.completedAt = result.serverTime
    }
  }
}

/** 启动课程规则规定间隔的进度上报。 */
function startProgressTimer() {
  stopProgressTimer()
  if (!course.value) return
  progressTimer = window.setInterval(async () => {
    if (
      !realtimeReady.value ||
      session.value?.status !== 'STUDYING' ||
      !selectedCourseware.value ||
      eventBusy.value
    ) {
      return
    }
    try {
      await enqueueEvent(
        'PROGRESS',
        selectedCourseware.value.coursewareSnapshotId,
        Math.floor((video.value?.currentTime || 0) * 1000),
      )
    } catch (error) {
      stopProgressTimer()
      pausePlayerSilently()
      showError(error, '学习进度上报失败，已暂停播放')
    }
  }, course.value.progressReportIntervalSeconds * 1000)
}

/** 停止进度定时器，避免暂停后继续提交事件。 */
function stopProgressTimer() {
  if (progressTimer !== undefined) {
    window.clearInterval(progressTimer)
    progressTimer = undefined
  }
}

/** 页面隐藏时立即暂停，返回页面后保持暂停等待手动恢复。 */
function onVisibilityChange() {
  if (!document.hidden || session.value?.status !== 'STUDYING') return
  stopProgressTimer()
  if (!realtimeReady.value) {
    pausePlayerSilently()
    return
  }
  void pauseCurrentVideo().catch((error) => {
    showError(error, '页面失焦暂停同步失败')
  })
}

/** 连接中断后立即停止计时和播放，等待同步完成后手动恢复。 */
function handleRealtimeDisconnected() {
  stopProgressTimer()
  pausePlayerSilently()
}

/** 连接被同浏览器其他页面接管后停止本页学习。 */
function handleRealtimeReplaced() {
  stopProgressTimer()
  pausePlayerSilently()
  ElMessage.warning('学习连接已被其他页面接管，本页面已停止计时')
}

/** 格式化毫秒学时。 */
function formatMillis(value: number) {
  const seconds = Math.floor(value / 1000)
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainingSeconds = seconds % 60
  return `${hours ? `${hours}小时` : ''}${minutes ? `${minutes}分` : ''}${remainingSeconds}秒`
}

/** 返回学习状态中文文案。 */
function sessionStatusLabel(status?: LearningSession['status']) {
  const labels: Record<string, string> = {
    CREATED: '待签到',
    SIGNED_IN: '已签到',
    STUDYING: '学习中',
    PAUSED: '已暂停',
    FACE_PENDING: '等待人脸抽验',
    COMPLETED: '课程已完成',
    SIGNED_OUT: '已签退',
    TERMINATED: '已终止',
  }
  return labels[status || ''] || status
}

/** 返回课件状态中文文案。 */
function coursewareStatusLabel(status: CoursewareProgress['status']) {
  return { NOT_STARTED: '未学习', IN_PROGRESS: '学习中', COMPLETED: '已完成' }[status]
}

/** 统一展示学习页错误。 */
function showError(error: unknown, fallback: string) {
  ElMessage.error(
    error instanceof ApiError || error instanceof LearningRealtimeError ? error.message : fallback,
  )
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  void initialize()
})

onBeforeUnmount(() => {
  stopProgressTimer()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <section v-loading="loading" class="study-page">
    <header class="study-header">
      <div>
        <el-button link type="primary" @click="router.push(`/student/plans/${planId}`)">
          ← 返回培训任务
        </el-button>
        <h1>{{ course?.courseName || '视频学习' }}</h1>
        <p>有效学时和播放进度以服务端确认结果为准。</p>
      </div>
      <el-tag v-if="session" size="large">{{ sessionStatusLabel(session.status) }}</el-tag>
    </header>

    <el-alert
      v-if="conflictSession"
      type="warning"
      :closable="false"
      title="当前账号已有其他活动学习会话"
      class="conflict-alert"
    >
      <template #default>
        <p>{{ conflictSession.courseName }}（{{ sessionStatusLabel(conflictSession.status) }}）</p>
        <el-button type="primary" @click="goToConflictSession">前往原课程</el-button>
        <el-button @click="terminateConflictSession">终止原会话</el-button>
      </template>
    </el-alert>

    <template v-else-if="course && session">
      <el-alert
        v-if="realtimeState === 'replaced'"
        type="warning"
        :closable="false"
        title="学习连接已被其他页面接管，本页面不会继续累计学时。"
        class="realtime-alert"
      />
      <el-alert
        v-else-if="!realtimeReady"
        type="info"
        :closable="false"
        title="实时学习连接正在恢复，播放器已暂停；同步完成后请手动继续。"
        class="realtime-alert"
      />
      <el-card shadow="never" class="progress-card">
        <div class="progress-summary">
          <div>
            <strong>有效学时</strong>
            <span>
              {{ formatMillis(course.effectiveDurationMillis) }} /
              {{ formatMillis(course.requiredDurationMillis) }}
            </span>
          </div>
          <el-progress :percentage="completionPercentage" :stroke-width="12" />
          <div class="rule-tags">
            <el-tag effect="plain">{{ course.allowSeek ? '允许拖动' : '禁止拖动' }}</el-tag>
            <el-tag effect="plain">每{{ course.progressReportIntervalSeconds }}秒上报</el-tag>
            <el-tag effect="plain">误差{{ course.studyToleranceSeconds }}秒</el-tag>
          </div>
        </div>
      </el-card>

      <el-alert
        v-if="
          course.coursewares.every((item) => item.status === 'COMPLETED') &&
          course.effectiveDurationMillis < course.requiredDurationMillis
        "
        title="课件已播放完成，有效学时仍不足，请从头补学。"
        type="warning"
        :closable="false"
        class="realtime-alert"
      >
        <el-button
          :disabled="
            !realtimeReady || !['SIGNED_IN', 'PAUSED', 'STUDYING'].includes(session.status)
          "
          :loading="eventBusy"
          @click="supplementLearning"
          >从头补学当前课件</el-button
        >
      </el-alert>

      <div class="study-grid">
        <el-card shadow="never" class="courseware-panel">
          <template #header><strong>课程课件</strong></template>
          <button
            v-for="item in course.coursewares"
            :key="item.coursewareSnapshotId"
            class="courseware-item"
            :class="{ active: selectedCoursewareId === item.coursewareSnapshotId }"
            :disabled="
              !isCoursewareUnlocked(course.coursewares, item) ||
              session.status === 'FACE_PENDING' ||
              (session.status === 'STUDYING' && !realtimeReady)
            "
            type="button"
            @click="selectCourseware(item)"
          >
            <span>{{ item.sortOrder }}. {{ item.title }}</span>
            <small>
              {{
                isCoursewareUnlocked(course.coursewares, item)
                  ? coursewareStatusLabel(item.status)
                  : '未解锁'
              }}
              · {{ formatMillis(item.durationMillis) }}
            </small>
          </button>
        </el-card>

        <el-card shadow="never" class="player-panel">
          <template #header>
            <div class="player-title">
              <strong>{{ selectedCourseware?.title || '请选择课件' }}</strong>
              <span v-if="selectedCourseware">
                已确认 {{ formatMillis(selectedCourseware.confirmedPositionMillis) }}
              </span>
            </div>
          </template>

          <div v-if="session.status === 'CREATED'" class="sign-in-panel">
            <p>开始播放前请先完成本次学习签到。</p>
            <el-button
              type="primary"
              size="large"
              :loading="eventBusy"
              :disabled="!realtimeReady"
              @click="signIn"
            >
              学习签到
            </el-button>
          </div>

          <div v-else v-loading="playbackLoading" class="video-wrapper">
            <video
              ref="video"
              :src="videoUrl"
              :class="{ 'connection-disabled': !realtimeReady }"
              controls
              controlslist="nodownload"
              preload="metadata"
              @loadedmetadata="onLoadedMetadata"
              @play="onVideoPlay"
              @pause="onVideoPause"
              @ended="onVideoEnded"
              @error="onVideoError"
              @seeking="onSeeking"
            />
            <el-alert
              v-if="session.status === 'FACE_PENDING'"
              title="人脸抽验处理中，播放器与有效学时累计已暂停。"
              type="warning"
              :closable="false"
            />
            <el-alert
              v-if="session.status === 'PAUSED'"
              title="学习已暂停，请点击播放器继续；页面返回时不会自动恢复计时。"
              type="info"
              :closable="false"
            />
            <el-alert
              v-if="session.status === 'COMPLETED'"
              title="本课程的视频和规定学时均已完成，请正常签退。"
              type="success"
              :closable="false"
            />
          </div>

          <footer class="player-actions">
            <span v-if="eventBusy">正在确认服务端学习状态…</span>
            <el-button
              v-if="!['CREATED', 'SIGNED_OUT', 'TERMINATED'].includes(session.status)"
              :loading="eventBusy"
              :disabled="!realtimeReady"
              @click="signOut"
            >
              正常签退
            </el-button>
          </footer>
        </el-card>
      </div>
    </template>
    <FaceCheckDialog
      :face-check="currentFaceCheck"
      @result="handleFaceCheckResult"
      @expired="handleFaceCheckExpired"
      @acknowledged="acknowledgeFaceCheck"
    />
  </section>
</template>

<style scoped>
.study-page {
  min-height: 560px;
}

.study-header,
.player-title,
.player-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.study-header {
  align-items: flex-start;
  margin-bottom: 22px;
}

.study-header h1 {
  margin: 12px 0 6px;
}

.study-header p,
.player-title span,
.player-actions span,
.courseware-item small {
  color: #6f7c93;
}

.conflict-alert,
.realtime-alert,
.progress-card {
  margin-bottom: 20px;
}

.progress-summary {
  display: grid;
  gap: 14px;
}

.progress-summary > div:first-child {
  display: flex;
  justify-content: space-between;
}

.rule-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.study-grid {
  display: grid;
  grid-template-columns: minmax(220px, 300px) minmax(0, 1fr);
  gap: 20px;
}

.courseware-item {
  display: grid;
  width: 100%;
  gap: 6px;
  padding: 14px;
  margin-bottom: 10px;
  text-align: left;
  background: #f8faff;
  border: 1px solid #dfe7f3;
  border-radius: 8px;
  cursor: pointer;
}

.courseware-item.active {
  color: #155eef;
  background: #edf3ff;
  border-color: #8bb2ff;
}

.courseware-item:disabled {
  color: #9aa5b5;
  cursor: not-allowed;
  background: #f4f5f7;
}

.video-wrapper {
  display: grid;
  gap: 14px;
}

video {
  width: 100%;
  max-height: 65vh;
  background: #101828;
  border-radius: 8px;
}

video.connection-disabled {
  opacity: 0.65;
  pointer-events: none;
}

.sign-in-panel {
  display: grid;
  min-height: 320px;
  place-content: center;
  text-align: center;
}

.player-actions {
  min-height: 42px;
  margin-top: 18px;
}

@media (width <= 900px) {
  .study-grid {
    grid-template-columns: 1fr;
  }
}
</style>
