<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { submitFaceCheck, type FaceCheckTask } from '@/api/learning'
import { ApiError } from '@/api/http'
import { createRequestId } from '@/learning/session'

const props = defineProps<{
  faceCheck?: FaceCheckTask | null
}>()

const emit = defineEmits<{
  result: [faceCheck: FaceCheckTask]
  expired: []
  acknowledged: []
}>()

type CameraState = 'IDLE' | 'REQUESTING' | 'READY' | 'DENIED' | 'UNAVAILABLE'

const video = ref<HTMLVideoElement>()
const cameraState = ref<CameraState>('IDLE')
const capturedBlob = ref<Blob>()
const capturedUrl = ref('')
const submitting = ref(false)
const remainingSeconds = ref(0)
let stream: MediaStream | undefined
let countdownTimer: number | undefined
let expiredTaskId: string | undefined
let submissionRequestId = ''
let cameraRequestVersion = 0
let disposed = false

const visible = computed(() => Boolean(props.faceCheck))
const pending = computed(() => props.faceCheck?.status === 'PENDING')
const canSubmit = computed(
  () =>
    pending.value && remainingSeconds.value > 0 && Boolean(capturedBlob.value) && !submitting.value,
)
const countdownLabel = computed(() => {
  const minutes = Math.floor(remainingSeconds.value / 60)
  const seconds = remainingSeconds.value % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

/** 根据抽验任务切换清理摄像头、照片和倒计时。 */
watch(
  () => props.faceCheck,
  (current, previous) => {
    if (current?.taskId !== previous?.taskId) {
      stopCamera()
      resetCapture()
      cameraState.value = 'IDLE'
      expiredTaskId = undefined
    }
    if (!current || current.status !== 'PENDING') {
      stopCamera()
      resetCapture()
    }
    startCountdown()
  },
  { immediate: true },
)

/** 请求浏览器摄像头权限并把实时画面绑定到video。 */
async function startCamera() {
  const taskId = props.faceCheck?.taskId
  if (!taskId || !pending.value || remainingSeconds.value <= 0) return
  if (!navigator.mediaDevices?.getUserMedia) {
    cameraState.value = 'UNAVAILABLE'
    return
  }
  stopCamera()
  cameraState.value = 'REQUESTING'
  const requestVersion = ++cameraRequestVersion
  try {
    const requestedStream = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: { facingMode: 'user', width: { ideal: 960 }, height: { ideal: 720 } },
    })
    if (!isCameraRequestActive(requestVersion, taskId)) {
      requestedStream.getTracks().forEach((track) => track.stop())
      return
    }
    stream = requestedStream
    cameraState.value = 'READY'
    await nextTick()
    if (!isCameraRequestActive(requestVersion, taskId)) {
      requestedStream.getTracks().forEach((track) => track.stop())
      if (stream === requestedStream) stream = undefined
      return
    }
    if (video.value) {
      video.value.srcObject = stream
      await video.value.play()
    }
  } catch (error) {
    if (requestVersion !== cameraRequestVersion || disposed) return
    cameraState.value = isPermissionDenied(error) ? 'DENIED' : 'UNAVAILABLE'
    stopCamera(false)
  }
}

/** 把当前视频帧压缩为JPEG抽验照片。 */
async function capturePhoto() {
  const player = video.value
  if (!player || cameraState.value !== 'READY') return
  const width = player.videoWidth || 960
  const height = player.videoHeight || 720
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) {
    ElMessage.error('当前浏览器无法生成抽验照片，请更换浏览器后重试')
    return
  }
  context.drawImage(player, 0, 0, width, height)
  const blob = await canvasToBlob(canvas)
  resetCapture()
  capturedBlob.value = blob
  capturedUrl.value = URL.createObjectURL(blob)
  stopCamera()
}

/** 清除当前照片并重新开启摄像头供学员重拍。 */
async function retakePhoto() {
  resetCapture()
  await startCamera()
}

/** 以唯一请求ID提交当前照片，并将服务端任务快照交给学习页。 */
async function submitPhoto() {
  const task = props.faceCheck
  const photo = capturedBlob.value
  if (!task || !photo || !canSubmit.value) return
  submitting.value = true
  try {
    submissionRequestId ||= createRequestId()
    const result = await submitFaceCheck(task.taskId, photo, submissionRequestId)
    emit('result', result)
    if (result.status === 'PENDING') {
      resetCapture()
      ElMessage.warning(result.failureReason || '核验未通过，请在时限内重新拍照')
      await startCamera()
    } else {
      stopCamera()
    }
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '抽验照片提交失败，请重试')
  } finally {
    submitting.value = false
  }
}

/** 按服务端截止时间刷新剩余秒数并只通知一次超时。 */
function updateCountdown() {
  const task = props.faceCheck
  if (!task || task.status !== 'PENDING') {
    remainingSeconds.value = 0
    return
  }
  remainingSeconds.value = Math.max(0, Math.ceil((Date.parse(task.deadlineAt) - Date.now()) / 1000))
  if (remainingSeconds.value === 0 && expiredTaskId !== task.taskId) {
    expiredTaskId = task.taskId
    stopCountdown()
    stopCamera()
    emit('expired')
  }
}

/** 启动当前任务的本地倒计时，事实截止时间始终来自服务端。 */
function startCountdown() {
  stopCountdown()
  updateCountdown()
  if (props.faceCheck?.status === 'PENDING' && remainingSeconds.value > 0) {
    countdownTimer = window.setInterval(updateCountdown, 1000)
  }
}

/** 停止倒计时定时器。 */
function stopCountdown() {
  if (countdownTimer !== undefined) window.clearInterval(countdownTimer)
  countdownTimer = undefined
}

/** 停止摄像头的全部轨道，避免弹窗关闭后摄像头仍被占用。 */
function stopCamera(resetState = true) {
  cameraRequestVersion += 1
  stream?.getTracks().forEach((track) => track.stop())
  stream = undefined
  if (video.value) video.value.srcObject = null
  if (resetState && cameraState.value === 'READY') cameraState.value = 'IDLE'
}

/** 判断异步摄像头授权结果是否仍属于当前待处理任务。 */
function isCameraRequestActive(requestVersion: number, taskId: string) {
  return (
    !disposed &&
    requestVersion === cameraRequestVersion &&
    props.faceCheck?.taskId === taskId &&
    pending.value &&
    remainingSeconds.value > 0
  )
}

/** 释放已拍照片的对象URL和二进制引用。 */
function resetCapture() {
  if (capturedUrl.value) URL.revokeObjectURL(capturedUrl.value)
  capturedUrl.value = ''
  capturedBlob.value = undefined
  submissionRequestId = ''
}

/** 判断摄像头异常是否源于用户或系统拒绝授权。 */
function isPermissionDenied(error: unknown) {
  return error instanceof DOMException && ['NotAllowedError', 'SecurityError'].includes(error.name)
}

/** 将Canvas异步转换为JPEG Blob。 */
function canvasToBlob(canvas: HTMLCanvasElement) {
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('照片生成失败'))),
      'image/jpeg',
      0.9,
    )
  })
}

/** 返回抽验终态提示标题。 */
function resultTitle(faceCheck: FaceCheckTask) {
  if (faceCheck.status === 'PASSED') return '人脸抽验已通过'
  if (faceCheck.status === 'TIMED_OUT')
    return '未在规定时间完成抽验，学习已强制停止，请重新签到学习'
  return '人脸抽验未通过，学习会话已终止'
}

/** 将服务端人脸核验结果码转换为学员可理解的提示。 */
function resultMessage(faceCheck: FaceCheckTask) {
  const code = faceCheck.failureReason || faceCheck.result
  if (!code) return undefined
  return (
    (
      {
        MATCH: '当前照片与登记照匹配',
        MATCHED: '当前照片与登记照匹配',
        NOT_MATCH: '当前照片与登记照不匹配，请正对摄像头重拍',
        REFERENCE_NO_FACE: '登记照中未检测到人脸，请联系管理员重新登记',
        REFERENCE_MULTIPLE_FACES: '登记照包含多张人脸，请联系管理员重新登记',
        CANDIDATE_NO_FACE: '当前照片中未检测到人脸，请正对摄像头重拍',
        CANDIDATE_MULTIPLE_FACES: '当前照片包含多张人脸，请确保画面中只有本人',
        INVALID_IMAGE: '照片无法识别，请重新拍摄',
        DEADLINE_EXCEEDED: '未在规定时间内完成人脸抽验',
        SESSION_TERMINATED: '学习会话已终止',
        TIMEOUT: '未在规定时间内完成人脸抽验',
      } as Record<string, string>
    )[code] || code
  )
}

onBeforeUnmount(() => {
  disposed = true
  stopCountdown()
  stopCamera()
  resetCapture()
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    align-center
    title="学习人脸抽验"
    width="620px"
  >
    <template v-if="faceCheck">
      <template v-if="pending">
        <el-alert
          :closable="false"
          title="播放器和学时累计已暂停，请在倒计时结束前完成拍照核验。"
          type="warning"
        />
        <div class="face-check-summary">
          <strong>剩余时间 {{ countdownLabel }}</strong>
          <span>剩余提交次数 {{ faceCheck.remainingAttempts }} / {{ faceCheck.maxAttempts }}</span>
        </div>
        <el-alert
          v-if="resultMessage(faceCheck)"
          :closable="false"
          :title="resultMessage(faceCheck)"
          type="error"
        />

        <div class="camera-panel">
          <img v-if="capturedUrl" :src="capturedUrl" alt="待提交的抽验照片" />
          <video v-else-if="cameraState === 'READY'" ref="video" autoplay muted playsinline />
          <div v-else class="camera-placeholder">
            <p v-if="cameraState === 'DENIED'">摄像头权限被拒绝，请在浏览器设置中允许后重试。</p>
            <p v-else-if="cameraState === 'UNAVAILABLE'">未检测到可用摄像头，请更换设备后重试。</p>
            <p v-else>请正对摄像头，确保面部完整且光线充足。</p>
            <el-button
              type="primary"
              :loading="cameraState === 'REQUESTING'"
              :disabled="remainingSeconds <= 0"
              @click="startCamera"
            >
              {{
                cameraState === 'DENIED' || cameraState === 'UNAVAILABLE'
                  ? '重新检测摄像头'
                  : '启用摄像头'
              }}
            </el-button>
          </div>
        </div>
      </template>

      <el-result
        v-else
        :icon="faceCheck.status === 'PASSED' ? 'success' : 'error'"
        :title="resultTitle(faceCheck)"
        :sub-title="faceCheck.status === 'TIMED_OUT' ? undefined : resultMessage(faceCheck)"
      />
    </template>

    <template #footer>
      <template v-if="faceCheck && pending">
        <el-button v-if="capturedBlob" :disabled="submitting" @click="retakePhoto">重拍</el-button>
        <el-button
          v-else-if="cameraState === 'READY'"
          type="primary"
          :disabled="remainingSeconds <= 0"
          @click="capturePhoto"
        >
          拍照
        </el-button>
        <el-button
          v-if="capturedBlob"
          type="primary"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="submitPhoto"
        >
          提交核验
        </el-button>
      </template>
      <el-button v-else type="primary" @click="emit('acknowledged')">知道了</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.face-check-summary {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 4px;
}

.face-check-summary strong {
  color: #d92d20;
  font-size: 20px;
}

.camera-panel {
  display: grid;
  min-height: 340px;
  margin-top: 16px;
  overflow: hidden;
  color: #d0d5dd;
  background: #101828;
  border-radius: 10px;
  place-items: center;
}

.camera-panel video,
.camera-panel img {
  width: 100%;
  max-height: 440px;
  object-fit: contain;
  transform: scaleX(-1);
}

.camera-placeholder {
  display: grid;
  max-width: 420px;
  gap: 12px;
  padding: 40px;
  text-align: center;
}

@media (width <= 680px) {
  .face-check-summary {
    flex-direction: column;
  }

  .camera-panel {
    min-height: 260px;
  }
}
</style>
