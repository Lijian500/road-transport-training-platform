<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import {
  cancelUploadSession,
  changeCourseStatus,
  completeUploadSession,
  createCoursewareUploadSession,
  createCoverUploadSession,
  deleteCourseCover,
  deleteCourseware,
  getCourse,
  getCoursewarePreviewUrl,
  getCoverPreviewUrl,
  getStorageCapability,
  reorderCoursewares,
  updateCourse,
  updateCourseware,
  type Course,
  type CourseStatus,
  type Courseware,
  type StorageCapability,
  type UploadSession,
} from '@/api/training'
import { ApiError } from '@/api/http'
import AppDialog from '@/components/AppDialog/AppDialog.vue'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'
import { usePermissionStore } from '@/stores/permission'
import {
  matchesResumableFile,
  uploadMultipartFile,
  uploadSignedBlob,
  type ResumableUploadRecord,
} from '@/utils/ossUpload'

interface PersistedVideoUpload extends ResumableUploadRecord {
  session: UploadSession
}

const route = useRoute()
const router = useRouter()
const permissionStore = usePermissionStore()
const courseId = String(route.params.id)
const resumeStorageKey = `training:course-upload:${courseId}`

const loading = ref(false)
const saving = ref(false)
const course = ref<Course>()
const capability = ref<StorageCapability>()
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  description: '',
  requiredDurationMinutes: 1,
  allowSeek: false,
  progressReportIntervalSeconds: 20,
  studyToleranceSeconds: 30,
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  requiredDurationMinutes: [{ required: true, message: '请输入规定时长', trigger: 'blur' }],
}

const coverInput = ref<HTMLInputElement>()
const coverUploading = ref(false)
const coverProgress = ref(0)
const coverUrl = ref('')
const videoInput = ref<HTMLInputElement>()
const videoUploading = ref(false)
const videoProgress = ref(0)
const activeVideoSessionId = ref('')
const uploadController = ref<AbortController>()
const resumeRecord = ref<PersistedVideoUpload>()
const coursewareTitle = ref('')

const renameVisible = ref(false)
const renameSaving = ref(false)
const renamingCourseware = ref<Courseware>()
const renameTitle = ref('')
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('')
const previewUrl = ref('')

const enabled = computed(() => course.value?.status === 'ENABLED')
const canUpdate = computed(
  () => !enabled.value && permissionStore.has('admin:course:update'),
)
const canManage = computed(
  () => !enabled.value && permissionStore.has('admin:courseware:manage'),
)
const storageEnabled = computed(() => capability.value?.enabled === true)

/** 加载课程详情、OSS能力和可恢复会话。 */
async function load() {
  loading.value = true
  try {
    const [courseResult, capabilityResult] = await Promise.all([
      getCourse(courseId),
      getStorageCapability(),
    ])
    course.value = courseResult
    capability.value = capabilityResult
    Object.assign(form, {
      name: courseResult.name,
      description: courseResult.description || '',
      requiredDurationMinutes: courseResult.requiredDurationSeconds / 60,
      allowSeek: courseResult.allowSeek,
      progressReportIntervalSeconds: courseResult.progressReportIntervalSeconds,
      studyToleranceSeconds: courseResult.studyToleranceSeconds,
    })
    loadResumeRecord()
    await loadCoverPreview()
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

/** 保存草稿或已禁用课程的业务规则。 */
async function saveCourse() {
  if (!(await formRef.value?.validate().catch(() => false))) {
    return
  }
  saving.value = true
  try {
    course.value = await updateCourse(courseId, {
      name: form.name,
      description: form.description,
      requiredDurationSeconds: Math.round(form.requiredDurationMinutes * 60),
      allowSeek: form.allowSeek,
      progressReportIntervalSeconds: form.progressReportIntervalSeconds,
      studyToleranceSeconds: form.studyToleranceSeconds,
    })
    ElMessage.success('课程规则已保存')
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 启用或禁用当前课程。 */
async function toggleStatus() {
  if (!course.value) {
    return
  }
  const status = course.value.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await ElMessageBox.confirm(
    `确定${status === 'ENABLED' ? '启用' : '禁用'}课程“${course.value.name}”吗？`,
    '状态确认',
    { type: 'warning' },
  )
  try {
    course.value = await changeCourseStatus(courseId, status)
    ElMessage.success('课程状态已更新')
  } catch (error) {
    showError(error)
  }
}

/** 打开系统文件选择器选择封面。 */
function chooseCover() {
  coverInput.value?.click()
}

/** 校验并直传封面到OSS，完成后重新加载课程。 */
async function handleCoverFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !capability.value) {
    return
  }
  const contentType = inferContentType(file)
  if (!capability.value.coverContentTypes.includes(contentType)) {
    ElMessage.error('封面仅支持JPG、PNG或WebP格式')
    return
  }
  if (file.size > capability.value.maxCoverBytes) {
    ElMessage.error(`封面不能超过${formatBytes(capability.value.maxCoverBytes)}`)
    return
  }
  let session: UploadSession | undefined
  coverUploading.value = true
  coverProgress.value = 0
  try {
    session = await createCoverUploadSession(courseId, {
      originalFilename: file.name,
      contentType,
      fileSizeBytes: file.size,
      clientLastModified: file.lastModified,
    })
    if (!session.uploadRequest) {
      throw new Error('服务端未返回封面上传地址')
    }
    await uploadSignedBlob(session.uploadRequest, file, (loaded) => {
      coverProgress.value = Math.round((loaded / file.size) * 100)
    })
    await completeUploadSession(session.id)
    ElMessage.success('课程封面已更新')
    await refreshCourse()
  } catch (error) {
    if (session) {
      await cancelUploadSession(session.id).catch(() => undefined)
    }
    showError(error)
  } finally {
    coverUploading.value = false
  }
}

/** 删除当前课程封面并按课程历史状态处理OSS对象。 */
async function removeCover() {
  await ElMessageBox.confirm('确定删除当前课程封面吗？', '删除确认', { type: 'warning' })
  try {
    await deleteCourseCover(courseId)
    coverUrl.value = ''
    ElMessage.success('封面已删除')
    await refreshCourse()
  } catch (error) {
    showError(error)
  }
}

/** 打开系统文件选择器选择MP4视频。 */
function chooseVideo() {
  videoInput.value?.click()
}

/** 创建或恢复视频会话并执行最多4片并发上传。 */
async function handleVideoFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !capability.value) {
    return
  }
  const contentType = inferContentType(file)
  if (contentType !== 'video/mp4') {
    ElMessage.error('视频仅支持MP4格式')
    return
  }
  if (file.size > capability.value.maxVideoBytes) {
    ElMessage.error(`视频不能超过${formatBytes(capability.value.maxVideoBytes)}`)
    return
  }

  videoUploading.value = true
  videoProgress.value = 0
  uploadController.value = new AbortController()
  try {
    let session: UploadSession
    if (resumeRecord.value && !matchesResumableFile(file, resumeRecord.value)) {
      ElMessage.error('存在未完成任务，请选择名称、大小和修改时间完全相同的文件，或先取消任务')
      return
    }
    if (resumeRecord.value) {
      session = resumeRecord.value.session
      ElMessage.info('已匹配本地上传记录，正在查询OSS分片并续传')
    } else {
      const durationSeconds = await readVideoDuration(file)
      const title = coursewareTitle.value.trim() || filenameWithoutExtension(file.name)
      session = await createCoursewareUploadSession(courseId, {
        title,
        originalFilename: file.name,
        contentType,
        fileSizeBytes: file.size,
        clientLastModified: file.lastModified,
        durationSeconds,
      })
      persistVideoSession(file, session)
    }
    activeVideoSessionId.value = session.id
    await uploadMultipartFile(file, session, {
      signal: uploadController.value.signal,
      onProgress: (progress) => {
        videoProgress.value = progress.percentage
      },
    })
    await completeUploadSession(session.id)
    clearResumeRecord()
    coursewareTitle.value = ''
    ElMessage.success('视频课件上传完成')
    await refreshCourse()
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      ElMessage.info('本次上传已停止')
    } else {
      showError(error, '上传中断，重新选择相同文件可继续上传')
    }
  } finally {
    activeVideoSessionId.value = ''
    uploadController.value = undefined
    videoUploading.value = false
  }
}

/** 主动终止浏览器请求并取消OSS分片任务。 */
async function cancelVideoUpload() {
  const sessionId = activeVideoSessionId.value || resumeRecord.value?.sessionId
  if (!sessionId) {
    return
  }
  uploadController.value?.abort()
  try {
    await cancelUploadSession(sessionId)
    clearResumeRecord()
    ElMessage.success('上传任务已取消')
  } catch (error) {
    showError(error)
  }
}

/** 打开课件改名弹窗。 */
function openRename(row: Courseware) {
  renamingCourseware.value = row
  renameTitle.value = row.title
  renameVisible.value = true
}

/** 保存课件标题。 */
async function saveRename() {
  if (!renamingCourseware.value || !renameTitle.value.trim()) {
    ElMessage.warning('请输入课件标题')
    return
  }
  renameSaving.value = true
  try {
    await updateCourseware(courseId, renamingCourseware.value.id, renameTitle.value)
    renameVisible.value = false
    ElMessage.success('课件标题已更新')
    await refreshCourse()
  } catch (error) {
    showError(error)
  } finally {
    renameSaving.value = false
  }
}

/** 删除课件并按课程历史状态保留或清理OSS对象。 */
async function removeCourseware(row: Courseware) {
  await ElMessageBox.confirm(`确定删除课件“${row.title}”吗？`, '删除确认', {
    type: 'warning',
  })
  try {
    await deleteCourseware(courseId, row.id)
    ElMessage.success('课件已删除')
    await refreshCourse()
  } catch (error) {
    showError(error)
  }
}

/** 将课件向上或向下移动一位并保存完整顺序。 */
async function moveCourseware(row: Courseware, offset: -1 | 1) {
  const coursewares = [...(course.value?.coursewares || [])]
  const index = coursewares.findIndex((item) => item.id === row.id)
  const targetIndex = index + offset
  if (index < 0 || targetIndex < 0 || targetIndex >= coursewares.length) {
    return
  }
  ;[coursewares[index], coursewares[targetIndex]] = [
    coursewares[targetIndex]!,
    coursewares[index]!,
  ]
  try {
    await reorderCoursewares(courseId, coursewares.map((item) => item.id))
    await refreshCourse()
  } catch (error) {
    showError(error)
  }
}

/** 获取短期签名并打开视频预览弹窗。 */
async function previewCourseware(row: Courseware) {
  previewVisible.value = true
  previewLoading.value = true
  previewTitle.value = row.title
  previewUrl.value = ''
  try {
    previewUrl.value = (await getCoursewarePreviewUrl(courseId, row.id)).url
  } catch (error) {
    previewVisible.value = false
    showError(error)
  } finally {
    previewLoading.value = false
  }
}

/** 关闭预览后释放视频地址引用。 */
function closePreview() {
  previewUrl.value = ''
}

/** 重新加载课程数据并刷新封面签名。 */
async function refreshCourse() {
  course.value = await getCourse(courseId)
  await loadCoverPreview()
}

/** 有封面且OSS可用时获取短期预览地址。 */
async function loadCoverPreview() {
  coverUrl.value = ''
  if (!course.value?.coverObjectId || !storageEnabled.value) {
    return
  }
  try {
    coverUrl.value = (await getCoverPreviewUrl(courseId)).url
  } catch {
    coverUrl.value = ''
  }
}

/** 从浏览器视频元数据读取时长并向上取整为秒。 */
function readVideoDuration(file: File): Promise<number> {
  return new Promise((resolve, reject) => {
    const video = document.createElement('video')
    const objectUrl = URL.createObjectURL(file)
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      URL.revokeObjectURL(objectUrl)
      const seconds = Math.ceil(video.duration)
      if (!Number.isFinite(seconds) || seconds <= 0 || seconds > 86_400) {
        reject(new Error('无法读取有效视频时长，或视频超过24小时'))
        return
      }
      resolve(seconds)
    }
    video.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('无法读取MP4视频元数据'))
    }
    video.src = objectUrl
  })
}

/** 保存续传所需的文件指纹和会话参数，不保存签名或凭证。 */
function persistVideoSession(file: File, session: UploadSession) {
  const record: PersistedVideoUpload = {
    courseId,
    sessionId: session.id,
    fileName: file.name,
    fileSize: file.size,
    lastModified: file.lastModified,
    expiresAt: session.expiresAt,
    session: { ...session, uploadRequest: undefined },
  }
  localStorage.setItem(resumeStorageKey, JSON.stringify(record))
  resumeRecord.value = record
}

/** 读取当前课程仍在有效期内的本地续传记录。 */
function loadResumeRecord() {
  const raw = localStorage.getItem(resumeStorageKey)
  if (!raw) {
    resumeRecord.value = undefined
    return
  }
  try {
    const record = JSON.parse(raw) as PersistedVideoUpload
    if (record.courseId !== courseId || new Date(record.expiresAt).getTime() <= Date.now()) {
      clearResumeRecord()
      return
    }
    if (
      record.session.coursewareId &&
      course.value?.coursewares.some((item) => item.id === record.session.coursewareId)
    ) {
      clearResumeRecord()
      return
    }
    resumeRecord.value = record
  } catch {
    clearResumeRecord()
  }
}

/** 清除已完成、取消或失效的本地续传记录。 */
function clearResumeRecord() {
  localStorage.removeItem(resumeStorageKey)
  resumeRecord.value = undefined
}

/** 按扩展名补齐浏览器可能缺失的文件内容类型。 */
function inferContentType(file: File) {
  if (file.type) {
    return file.type.toLowerCase()
  }
  const extension = file.name.split('.').pop()?.toLowerCase()
  return {
    mp4: 'video/mp4',
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    png: 'image/png',
    webp: 'image/webp',
  }[extension || ''] || ''
}

/** 去掉文件扩展名作为默认课件标题。 */
function filenameWithoutExtension(filename: string) {
  return filename.replace(/\.[^.]+$/, '')
}

/** 将字节数格式化为易读大小。 */
function formatBytes(bytes: number) {
  if (bytes >= 1024 ** 3) {
    return `${(bytes / 1024 ** 3).toFixed(2)} GiB`
  }
  return `${(bytes / 1024 ** 2).toFixed(2)} MiB`
}

/** 将秒数格式化为时分秒。 */
function formatDuration(seconds: number) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  return `${hours ? `${hours}小时` : ''}${minutes ? `${minutes}分` : ''}${rest}秒`
}

/** 返回课程状态中文文案。 */
function statusLabel(status?: CourseStatus) {
  return status ? { DRAFT: '草稿', ENABLED: '启用', DISABLED: '已禁用' }[status] : ''
}

/** 返回课程状态标签颜色。 */
function statusType(status?: CourseStatus) {
  return status === 'ENABLED' ? 'success' : status === 'DRAFT' ? 'warning' : 'info'
}

/** 统一展示接口或上传错误。 */
function showError(error: unknown, fallback = '操作失败，请稍后重试') {
  const message = error instanceof ApiError || error instanceof Error ? error.message : fallback
  ElMessage.error(message || fallback)
}

onMounted(load)

onBeforeUnmount(() => {
  uploadController.value?.abort()
})
</script>

<template>
  <section v-loading="loading">
    <header class="page-title page-title--actions">
      <div>
        <el-button link type="primary" @click="router.push('/admin/courses')">← 返回课程列表</el-button>
        <h1>{{ course?.name || '课程详情' }}</h1>
        <p>课程规则、封面及视频文件均按当前组织隔离管理。</p>
      </div>
      <div v-if="course" class="header-actions">
        <el-tag :type="statusType(course.status)" size="large">
          {{ statusLabel(course.status) }}
        </el-tag>
        <PermissionButton permission="admin:course:status" type="primary" @click="toggleStatus">
          {{ course.status === 'ENABLED' ? '禁用课程' : '启用课程' }}
        </PermissionButton>
      </div>
    </header>

    <el-alert
      v-if="capability && !capability.enabled"
      :closable="false"
      :title="capability.message"
      show-icon
      type="warning"
    />
    <el-alert
      v-if="enabled"
      :closable="false"
      class="detail-alert"
      title="启用中的课程只允许预览和禁用；禁用后可再次编辑。"
      show-icon
      type="info"
    />

    <section class="detail-card">
      <header class="detail-card__title">
        <div>
          <h2>课程规则</h2>
          <p>规定时长不能超过所有有效视频的总时长。</p>
        </div>
        <PermissionButton
          permission="admin:course:update"
          :disabled="!canUpdate"
          :loading="saving"
          type="primary"
          @click="saveCourse"
        >
          保存规则
        </PermissionButton>
      </header>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="24">
          <el-col :md="12" :xs="24">
            <el-form-item label="课程名称" prop="name">
              <el-input v-model="form.name" :disabled="!canUpdate" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="规定时长" prop="requiredDurationMinutes">
              <el-input-number
                v-model="form.requiredDurationMinutes"
                :disabled="!canUpdate"
                :min="1 / 60"
                :max="1440"
                :precision="2"
              />
              <span class="form-unit">分钟</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="课程简介">
          <el-input
            v-model="form.description"
            :disabled="!canUpdate"
            maxlength="1000"
            rows="3"
            type="textarea"
          />
        </el-form-item>
        <el-row :gutter="24">
          <el-col :md="8" :xs="24">
            <el-form-item label="允许拖动">
              <el-switch v-model="form.allowSeek" :disabled="!canUpdate" />
            </el-form-item>
          </el-col>
          <el-col :md="8" :xs="24">
            <el-form-item label="进度上报">
              <el-input-number
                v-model="form.progressReportIntervalSeconds"
                :disabled="!canUpdate"
                :min="10"
                :max="30"
              />
              <span class="form-unit">秒</span>
            </el-form-item>
          </el-col>
          <el-col :md="8" :xs="24">
            <el-form-item label="学时误差">
              <el-input-number
                v-model="form.studyToleranceSeconds"
                :disabled="!canUpdate"
                :min="0"
                :max="300"
              />
              <span class="form-unit">秒</span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </section>

    <section class="detail-card">
      <header class="detail-card__title">
        <div>
          <h2>课程封面</h2>
          <p>支持JPG、PNG、WebP，最大{{ capability ? formatBytes(capability.maxCoverBytes) : '5 MiB' }}。</p>
        </div>
        <div class="header-actions">
          <PermissionButton
            permission="admin:courseware:manage"
            :disabled="!canManage || !storageEnabled || coverUploading"
            @click="chooseCover"
          >
            {{ course?.coverObjectId ? '替换封面' : '上传封面' }}
          </PermissionButton>
          <PermissionButton
            v-if="course?.coverObjectId"
            permission="admin:courseware:manage"
            :disabled="!canManage"
            type="danger"
            @click="removeCover"
          >
            删除封面
          </PermissionButton>
        </div>
      </header>
      <input
        ref="coverInput"
        accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
        hidden
        type="file"
        @change="handleCoverFile"
      />
      <el-progress v-if="coverUploading" :percentage="coverProgress" />
      <div v-else-if="coverUrl" class="cover-preview">
        <img :alt="course?.coverFilename || '课程封面'" :src="coverUrl" />
        <span>{{ course?.coverFilename }} · {{ formatBytes(course?.coverSizeBytes || 0) }}</span>
      </div>
      <el-empty v-else description="暂未设置课程封面" :image-size="80" />
    </section>

    <section class="detail-card">
      <header class="detail-card__title">
        <div>
          <h2>视频课件</h2>
          <p>
            MP4最大{{ capability ? formatBytes(capability.maxVideoBytes) : '5 GiB' }}，8 MiB分片、最多4片并发。
          </p>
        </div>
        <div class="video-upload-actions">
          <el-input
            v-model="coursewareTitle"
            :disabled="!canManage || videoUploading"
            maxlength="128"
            placeholder="课件标题（不填则使用文件名）"
          />
          <PermissionButton
            permission="admin:courseware:manage"
            :disabled="!canManage || !storageEnabled || videoUploading"
            type="primary"
            @click="chooseVideo"
          >
            {{ resumeRecord ? '选择相同文件续传' : '上传MP4' }}
          </PermissionButton>
        </div>
      </header>
      <input
        ref="videoInput"
        accept=".mp4,video/mp4"
        hidden
        type="file"
        @change="handleVideoFile"
      />
      <el-alert
        v-if="resumeRecord && !videoUploading"
        :closable="false"
        class="resume-alert"
        show-icon
        type="info"
      >
        <template #title>
          存在未完成任务：{{ resumeRecord.fileName }}（{{ formatBytes(resumeRecord.fileSize) }}），请重新选择完全相同的文件续传。
        </template>
        <el-button link type="danger" @click="cancelVideoUpload">取消该任务</el-button>
      </el-alert>
      <div v-if="videoUploading" class="upload-progress">
        <el-progress :percentage="videoProgress" />
        <el-button link type="danger" @click="cancelVideoUpload">取消上传</el-button>
      </div>
      <el-table :data="course?.coursewares || []" row-key="id">
        <el-table-column label="排序" width="110">
          <template #default="{ row, $index }">
            <el-button
              :disabled="!canManage || $index === 0"
              link
              @click="moveCourseware(row, -1)"
            >↑</el-button>
            <el-button
              :disabled="!canManage || $index === (course?.coursewares.length || 0) - 1"
              link
              @click="moveCourseware(row, 1)"
            >↓</el-button>
          </template>
        </el-table-column>
        <el-table-column label="课件标题" min-width="190" prop="title" />
        <el-table-column label="原文件名" min-width="180" prop="originalFilename" />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatBytes(row.fileSizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="视频时长" width="130">
          <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="210">
          <template #default="{ row }">
            <el-button :disabled="!storageEnabled" link type="primary" @click="previewCourseware(row)">
              预览
            </el-button>
            <PermissionButton
              permission="admin:courseware:manage"
              :disabled="!canManage"
              link
              @click="openRename(row)"
            >
              改名
            </PermissionButton>
            <PermissionButton
              permission="admin:courseware:manage"
              :disabled="!canManage"
              link
              type="danger"
              @click="removeCourseware(row)"
            >
              删除
            </PermissionButton>
          </template>
        </el-table-column>
      </el-table>
      <div class="duration-summary">
        有效视频总时长：{{ formatDuration(course?.totalVideoDurationSeconds || 0) }}；规定时长：{{ formatDuration(course?.requiredDurationSeconds || 0) }}
      </div>
    </section>

    <AppDialog
      v-model="renameVisible"
      title="修改课件标题"
      :loading="renameSaving"
      @confirm="saveRename"
    >
      <el-form label-width="90px">
        <el-form-item label="课件标题" required>
          <el-input v-model="renameTitle" maxlength="128" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <el-dialog
      v-model="previewVisible"
      :title="`预览：${previewTitle}`"
      width="min(900px, 90vw)"
      @closed="closePreview"
    >
      <div v-loading="previewLoading" class="video-preview">
        <video v-if="previewUrl" :src="previewUrl" controls preload="metadata" />
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.detail-alert,
.detail-card {
  margin-top: 20px;
}

.detail-card {
  padding: 22px;
  background: #fff;
  border: 1px solid #e5eaf2;
  border-radius: 14px;
}

.detail-card__title,
.header-actions,
.video-upload-actions,
.upload-progress {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.detail-card__title {
  margin-bottom: 20px;
}

.detail-card__title h2 {
  margin: 0 0 5px;
  font-size: 19px;
}

.detail-card__title p {
  margin: 0;
  color: #71809a;
  font-size: 13px;
}

.form-unit {
  margin-left: 8px;
  color: #71809a;
}

.cover-preview {
  display: grid;
  gap: 10px;
  justify-items: start;
}

.cover-preview img {
  width: min(420px, 100%);
  max-height: 240px;
  object-fit: cover;
  border-radius: 10px;
}

.cover-preview span,
.duration-summary {
  color: #71809a;
  font-size: 13px;
}

.video-upload-actions {
  width: min(520px, 55%);
}

.resume-alert,
.upload-progress {
  margin-bottom: 16px;
}

.upload-progress :deep(.el-progress) {
  flex: 1;
}

.duration-summary {
  margin-top: 14px;
  text-align: right;
}

.video-preview {
  min-height: 220px;
}

.video-preview video {
  width: 100%;
  max-height: 70vh;
  background: #000;
}

@media (width <= 760px) {
  .detail-card__title,
  .header-actions,
  .video-upload-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .video-upload-actions {
    width: 100%;
  }
}
</style>
