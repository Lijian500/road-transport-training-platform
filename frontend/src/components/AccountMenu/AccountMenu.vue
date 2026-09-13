<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { http } from '@/api/http'
import {
  type User,
  createFaceReferenceUploadSession,
  completeFaceReferenceUploadSession,
  getFaceReferencePreviewUrl,
} from '@/api/admin'
import CameraCapture from '@/components/CameraCapture/CameraCapture.vue'
import { uploadSignedBlob } from '@/utils/ossUpload'

const auth = useAuthStore()
const router = useRouter()
const visible = ref(false)
const busy = ref(false)
const profile = ref<User>()
const preview = ref('')

/** 打开本人资料并按需获取私有登记照预览。 */
async function openProfile() {
  try {
    profile.value = await http.get<User>('/auth/profile')
    preview.value = ''
    visible.value = true
    if (profile.value.faceReferenceEnrolled) {
      preview.value = (await getFaceReferencePreviewUrl(profile.value.id)).url
    }
  } catch (error) {
    showError(error)
  }
}

/** 保存姓名手机号，并同步右上角显示。 */
async function save() {
  if (!profile.value || busy.value) return
  busy.value = true
  try {
    profile.value = await http.put<User>('/auth/profile', {
      displayName: profile.value.displayName,
      phone: profile.value.phone,
    })
    if (auth.session) auth.session.displayName = profile.value.displayName
    ElMessage.success('个人信息已保存')
    visible.value = false
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

/** 选择照片后复用登记照上传与人脸校验。 */
function selectPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) void uploadPhoto(file)
}

/** 上传或拍照后立即替换本人登记照。 */
async function uploadPhoto(file: File) {
  if (!profile.value || busy.value) return
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    ElMessage.warning('请选择5MB以内的JPG或PNG照片')
    return
  }
  busy.value = true
  try {
    const userId = profile.value.id
    const upload = await createFaceReferenceUploadSession(userId, {
      originalFilename: file.name,
      contentType: file.type,
      fileSizeBytes: file.size,
      clientLastModified: file.lastModified,
    })
    await uploadSignedBlob(upload.uploadRequest, file)
    await completeFaceReferenceUploadSession(userId, upload.id)
    profile.value.faceReferenceEnrolled = true
    preview.value = (await getFaceReferencePreviewUrl(userId)).url
    ElMessage.success('登记照已更新')
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

/** 打开现有的密码修改页面。 */
function changePassword() {
  void router.push('/change-password')
}

/** 显示服务端返回的具体失败原因。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof Error ? error.message : '操作失败，请重试')
}
</script>

<template>
  <el-dropdown trigger="click">
    <button class="account-menu" type="button" aria-label="个人信息">
      <span class="account-menu__avatar">{{ auth.session?.displayName?.slice(0, 1) || '我' }}</span>
      <span class="account-menu__copy">
        <strong>{{ auth.session?.displayName }}</strong>
        <small>{{ auth.session?.username }}</small>
      </span>
      <span>个人信息 ▾</span>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item @click="openProfile">查看 / 修改个人信息</el-dropdown-item>
        <el-dropdown-item @click="changePassword">修改密码</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <el-dialog
    v-model="visible"
    title="个人信息"
    width="520px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    :show-close="!busy"
  >
    <el-form v-if="profile" label-width="80px" :disabled="busy">
      <el-form-item label="账号"
        ><el-input :model-value="profile.username" disabled
      /></el-form-item>
      <el-form-item label="姓名"
        ><el-input v-model="profile.displayName" maxlength="64"
      /></el-form-item>
      <el-form-item label="手机号"
        ><el-input v-model="profile.phone" maxlength="11"
      /></el-form-item>
      <el-form-item label="登记照">
        <div>
          <img v-if="preview" :src="preview" class="account-menu__photo" alt="本人登记照" />
          <p v-else>尚未登记照片</p>
          <input type="file" accept="image/jpeg,image/png" :disabled="busy" @change="selectPhoto" />
          <small>支持5MB以内的JPG、PNG，上传后立即保存。</small>
        </div>
      </el-form-item>
      <CameraCapture v-if="visible && !busy" @capture="uploadPhoto" />
    </el-form>
    <template #footer
      ><el-button type="primary" :loading="busy" @click="save">保存个人信息</el-button></template
    >
  </el-dialog>
</template>

<style scoped>
.account-menu {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  cursor: pointer;
  color: var(--app-text);
  padding: 5px;
}
.account-menu__avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  background: var(--app-primary);
  color: #fff;
  border-radius: 11px;
}
.account-menu__copy {
  display: grid;
  gap: 3px;
  text-align: left;
}
.account-menu__copy small {
  color: var(--app-text-muted);
}
.account-menu__photo {
  display: block;
  width: 120px;
  height: 150px;
  object-fit: cover;
  margin-bottom: 12px;
}
@media (width <= 560px) {
  .account-menu__copy {
    display: none;
  }
}
</style>
