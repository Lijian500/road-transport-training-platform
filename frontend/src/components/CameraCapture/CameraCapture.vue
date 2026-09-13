<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'

const emit = defineEmits<{ capture: [file: File]; activeChange: [active: boolean] }>()
const video = ref<HTMLVideoElement>()
const active = ref(false)
const loading = ref(false)
const ready = ref(false)
const error = ref('')
let stream: MediaStream | undefined
let requestVersion = 0

/** 关闭采集时立即释放摄像头，同时使尚未返回的权限请求失效。 */
function stop() {
  requestVersion += 1
  stream?.getTracks().forEach((track) => track.stop())
  stream = undefined
  if (video.value) video.value.srcObject = null
  active.value = false
  emit('activeChange', false)
  loading.value = false
  ready.value = false
}

/** 由用户点击启用摄像头，只采集视频，不请求麦克风。 */
async function start() {
  stop()
  error.value = ''
  if (!navigator.mediaDevices?.getUserMedia) {
    error.value = '当前浏览器无法调用摄像头，请使用 HTTPS 或 localhost 地址访问，也可选择本地照片。'
    return
  }
  const version = requestVersion
  active.value = true
  emit('activeChange', true)
  loading.value = true
  try {
    const media = await navigator.mediaDevices.getUserMedia({
      audio: false,
      video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } },
    })
    if (version !== requestVersion) {
      media.getTracks().forEach((track) => track.stop())
      return
    }
    stream = media
    await nextTick()
    if (!video.value) {
      stop()
      return
    }
    video.value.srcObject = media
    await video.value.play()
    ready.value = video.value.videoWidth > 0
  } catch (reason) {
    if (version !== requestVersion) return
    stop()
    error.value =
      reason instanceof DOMException && reason.name === 'NotAllowedError'
        ? '摄像头权限被拒绝，请在浏览器设置中允许访问后重试。'
        : '摄像头打开失败，请检查设备是否连接或被其他程序占用。'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

/** 拍照后交由原有登记照上传流程处理，采集完成立即释放设备。 */
function capture() {
  const source = video.value
  if (!ready.value || !source?.videoWidth) return
  const canvas = document.createElement('canvas')
  canvas.width = source.videoWidth
  canvas.height = source.videoHeight
  const context = canvas.getContext('2d')
  if (!context) {
    error.value = '浏览器无法生成照片，请选择本地图片。'
    return
  }
  context.drawImage(source, 0, 0)
  const version = requestVersion
  canvas.toBlob(
    (blob) => {
      if (version !== requestVersion) return
      if (!blob) {
        error.value = '照片生成失败，请重新拍摄。'
        return
      }
      emit('capture', new File([blob], `人脸登记照-${Date.now()}.jpg`, { type: 'image/jpeg' }))
      stop()
    },
    'image/jpeg',
    0.9,
  )
}

onBeforeUnmount(stop)
</script>

<template>
  <div class="camera-capture">
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <el-button v-if="!active" @click="start">摄像头采集</el-button>
    <template v-else>
      <video ref="video" autoplay muted playsinline aria-label="摄像头实时预览" />
      <div>
        <el-button type="primary" :loading="loading" :disabled="!ready" @click="capture"
          >拍照</el-button
        >
        <el-button @click="stop">关闭摄像头</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.camera-capture {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}
video {
  width: 100%;
  max-height: 360px;
  background: #182449;
  border-radius: 8px;
}
</style>
