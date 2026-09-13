<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  getRecordDetail,
  getRecordSessions,
  getRecordEvents,
  getRecordFaceChecks,
  type RecordAudience,
  type RecordDetail,
  type SessionRecord,
  type EventRecord,
  type FaceRecord,
} from '@/api/records'
import RecordPhoto from './RecordPhoto.vue'
import AppTable from '@/components/AppTable/AppTable.vue'
import { recordDuration, recordLabel, recordTime } from '@/utils/recordDisplay'

const props = defineProps<{ taskId: string; audience: RecordAudience }>()
const router = useRouter()
const detail = ref<RecordDetail>()
const loading = ref(false)
const error = ref('')
const sessions = ref<SessionRecord[]>([])
const sessionTotal = ref(0)
const sessionPage = ref(1)
const sessionStatus = ref('')
const sessionDates = ref<string[]>([])
const sessionLoading = ref(false)
const selectedSession = ref<SessionRecord>()
const events = ref<EventRecord[]>([])
const eventPage = ref(1)
const eventSize = ref(10)
const eventTotal = ref(0)
const faces = ref<FaceRecord[]>([])
const facePage = ref(1)
const faceSize = ref(10)
const faceTotal = ref(0)
const evidenceLoading = ref(false)
let version = 0
let sessionVersion = 0
let evidenceVersion = 0

/** 切换档案时清理旧内容并读取已经冻结的培训详情。 */
async function load() {
  const current = ++version
  ++sessionVersion
  ++evidenceVersion
  detail.value = undefined
  sessions.value = []
  selectedSession.value = undefined
  error.value = ''
  loading.value = true
  sessionPage.value = 1
  sessionStatus.value = ''
  sessionDates.value = []
  try {
    const result = await getRecordDetail(props.taskId, props.audience)
    if (current !== version) return
    detail.value = result
    await loadSessions()
  } catch (reason) {
    if (current === version) showError(reason)
  } finally {
    if (current === version) loading.value = false
  }
}

/** 分页读取会话，查询失败时清除旧页以免误认筛选结果。 */
async function loadSessions() {
  const current = ++sessionVersion
  const parent = version
  sessionLoading.value = true
  error.value = ''
  sessions.value = []
  selectedSession.value = undefined
  ++evidenceVersion
  try {
    const result = await getRecordSessions(props.audience, {
      taskId: props.taskId,
      pageNumber: sessionPage.value,
      pageSize: 10,
      status: sessionStatus.value,
      fromTime: sessionDates.value?.[0],
      toTime: sessionDates.value?.[1],
    })
    if (current !== sessionVersion || parent !== version) return
    sessions.value = result.records
    sessionTotal.value = result.total
  } catch (reason) {
    if (current === sessionVersion && parent === version) showError(reason)
  } finally {
    if (current === sessionVersion && parent === version) sessionLoading.value = false
  }
}

/** 筛选会话时回到第一页。 */
function searchSessions() {
  sessionPage.value = 1
  void loadSessions()
}
/** 切换会话分页。 */
function changeSessionPage(page: number) {
  sessionPage.value = page
  void loadSessions()
}
/** 查看所选会话的事件及抽验记录。 */
function selectSession(row: SessionRecord) {
  selectedSession.value = row
  eventPage.value = 1
  facePage.value = 1
  void loadEvidence()
}
/** 并行读取两类证据；过期响应不覆盖当前会话。 */
async function loadEvidence() {
  const session = selectedSession.value
  if (!session) return
  const current = ++evidenceVersion
  evidenceLoading.value = true
  error.value = ''
  events.value = []
  faces.value = []
  try {
    const [eventResult, faceResult] = await Promise.all([
      getRecordEvents(props.audience, session.id, eventPage.value, eventSize.value),
      getRecordFaceChecks(props.audience, session.id, facePage.value, faceSize.value),
    ])
    if (current !== evidenceVersion) return
    events.value = eventResult.records
    eventTotal.value = eventResult.total
    faces.value = faceResult.records
    faceTotal.value = faceResult.total
  } catch (reason) {
    if (current === evidenceVersion) showError(reason)
  } finally {
    if (current === evidenceVersion) evidenceLoading.value = false
  }
}
/** 切换已受理事件页码。 */
function changeEventPage(page: number) {
  eventPage.value = page
  void loadEvidence()
}
/** 切换抽验任务页码。 */
function changeFacePage(page: number) {
  facePage.value = page
  void loadEvidence()
}
/** 调整事件页容量并返回第一页。 */
function changeEventSize(size: number) {
  eventSize.value = size
  changeEventPage(1)
}
/** 调整抽验页容量并返回第一页。 */
function changeFaceSize(size: number) {
  faceSize.value = size
  changeFacePage(1)
}
/** 展示查询失败原因。 */
function showError(reason: unknown) {
  error.value = reason instanceof Error ? reason.message : '学习档案加载失败'
}
/** 从档案返回既有培训任务入口，由原有页面判断学习和考试准入。 */
function openPlan() {
  if (detail.value) void router.push(`/student/plans/${detail.value.training.planId}`)
}
watch(() => [props.taskId, props.audience], load, { immediate: true })
</script>

<template>
  <section v-loading="loading" class="record-detail">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <template v-if="detail">
      <header class="record-heading">
        <div>
          <h2>{{ detail.training.planName }}</h2>
          <p>
            {{ detail.training.displayName }} · {{ recordTime(detail.training.startAt) }} —
            {{ recordTime(detail.training.endAt) }}
          </p>
        </div>
        <el-button v-if="audience === 'student'" @click="openPlan">查看培训任务</el-button>
      </header>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="学习状态">{{
          recordLabel(detail.training.studyStatus)
        }}</el-descriptions-item>
        <el-descriptions-item label="考试状态">{{
          recordLabel(detail.training.examStatus)
        }}</el-descriptions-item>
        <el-descriptions-item label="培训完成">{{
          recordLabel(detail.training.completionStatus)
        }}</el-descriptions-item>
        <el-descriptions-item label="考试成绩">{{
          detail.training.examScore ?? '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="计划状态">{{
          recordLabel(detail.training.planStatus)
        }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{
          recordTime(detail.training.completedAt)
        }}</el-descriptions-item>
      </el-descriptions>
      <h3>课程学时</h3>
      <el-table :data="detail.courses" row-key="planCourseId">
        <el-table-column label="课程" prop="courseName" min-width="160" />
        <el-table-column label="规定学时" min-width="145"
          ><template #default="{ row }">{{
            recordDuration(row.requiredDurationMillis)
          }}</template></el-table-column
        >
        <el-table-column label="有效学时" min-width="145"
          ><template #default="{ row }">{{
            recordDuration(row.effectiveDurationMillis)
          }}</template></el-table-column
        >
        <el-table-column label="完成状态"
          ><template #default="{ row }">{{ recordLabel(row.status) }}</template></el-table-column
        >
        <el-table-column label="完成时间" min-width="180"
          ><template #default="{ row }">{{
            recordTime(row.completedAt)
          }}</template></el-table-column
        >
      </el-table>
      <h3>学习会话</h3>
      <div class="session-filters">
        <el-select
          v-model="sessionStatus"
          clearable
          placeholder="全部会话状态"
          aria-label="会话状态"
        >
          <el-option
            v-for="state in ['STUDYING', 'PAUSED', 'FACE_PENDING', 'SIGNED_OUT', 'TERMINATED']"
            :key="state"
            :value="state"
            :label="recordLabel(state)"
          />
        </el-select>
        <el-date-picker
          v-model="sessionDates"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          start-placeholder="会话开始时间"
          end-placeholder="会话截止时间"
        />
        <el-button @click="searchSessions">查询会话</el-button>
      </div>
      <el-table v-loading="sessionLoading" :data="sessions" row-key="id">
        <el-table-column label="课程" prop="courseName" min-width="150" />
        <el-table-column label="状态"
          ><template #default="{ row }">{{ recordLabel(row.status) }}</template></el-table-column
        >
        <el-table-column label="签到时间" min-width="180"
          ><template #default="{ row }">{{ recordTime(row.signedInAt) }}</template></el-table-column
        >
        <el-table-column label="签退 / 终止时间" min-width="180"
          ><template #default="{ row }">{{
            recordTime(row.signedOutAt || row.terminatedAt)
          }}</template></el-table-column
        >
        <el-table-column label="签到照片" width="110">
          <template #default="{ row }"
            ><RecordPhoto :src="row.signInPhotoUrl" label="签到照片"
          /></template>
        </el-table-column>
        <el-table-column label="签退照片" width="110">
          <template #default="{ row }"
            ><RecordPhoto :src="row.signOutPhotoUrl" label="签退照片"
          /></template>
        </el-table-column>
        <el-table-column label="终止原因" min-width="140"
          ><template #default="{ row }">{{
            recordLabel(row.terminationReason)
          }}</template></el-table-column
        >
        <el-table-column label="操作" width="100"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="selectSession(row)"
              >查看过程</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        :current-page="sessionPage"
        :page-size="10"
        :total="sessionTotal"
        layout="total, prev, pager, next"
        @current-change="changeSessionPage"
      />
      <section v-if="selectedSession" v-loading="evidenceLoading">
        <h3>{{ selectedSession.courseName }} · {{ recordTime(selectedSession.createdAt) }}</h3>
        <el-tabs>
          <el-tab-pane label="学习事件">
            <p>展示服务端已受理的事件与本次计入学时。</p>
            <AppTable
              :data="events"
              :total="eventTotal"
              :page-number="eventPage"
              :page-size="eventSize"
              @page-change="changeEventPage"
              @size-change="changeEventSize"
            >
              <el-table-column label="序号" prop="sequence" width="75" />
              <el-table-column label="事件"
                ><template #default="{ row }">{{
                  recordLabel(row.eventType)
                }}</template></el-table-column
              >
              <el-table-column label="状态变化" min-width="160"
                ><template #default="{ row }"
                  >{{ recordLabel(row.fromStatus) }} → {{ recordLabel(row.toStatus) }}</template
                ></el-table-column
              >
              <el-table-column label="上报位置 / 确认位置" min-width="180"
                ><template #default="{ row }"
                  >{{ row.reportedPositionMillis / 1000 }} /
                  {{ row.confirmedPositionMillis / 1000 }} 秒</template
                ></el-table-column
              >
              <el-table-column label="本次有效学时" min-width="150"
                ><template #default="{ row }">{{
                  recordDuration(row.creditedDurationMillis)
                }}</template></el-table-column
              >
              <el-table-column label="服务器时间" min-width="180"
                ><template #default="{ row }">{{
                  recordTime(row.serverTime)
                }}</template></el-table-column
              >
            </AppTable>
          </el-tab-pane>
          <el-tab-pane label="人脸抽验">
            <AppTable
              :data="faces"
              :total="faceTotal"
              :page-number="facePage"
              :page-size="faceSize"
              @page-change="changeFacePage"
              @size-change="changeFaceSize"
            >
              <el-table-column type="expand"
                ><template #default="{ row }">
                  <el-empty
                    v-if="!row.attempts.length"
                    description="本次抽验没有提交照片"
                    :image-size="45"
                  />
                  <el-table v-else :data="row.attempts">
                    <el-table-column label="抽验照片" width="110">
                      <template #default="{ row: attempt }"
                        ><RecordPhoto :src="attempt.photoUrl" label="抽验照片"
                      /></template>
                    </el-table-column>
                    <el-table-column label="提交时间" min-width="180">
                      <template #default="{ row: attempt }">{{
                        recordTime(attempt.createdAt)
                      }}</template>
                    </el-table-column>
                    <el-table-column label="提交次数" prop="attemptNo" /><el-table-column
                      label="结果"
                      ><template #default="{ row: attempt }">{{
                        recordLabel(attempt.result)
                      }}</template></el-table-column
                    >
                    <el-table-column label="失败原因"
                      ><template #default="{ row: attempt }">{{
                        recordLabel(attempt.failureReason)
                      }}</template></el-table-column
                    >
                    <el-table-column label="相似度" prop="similarity" /><el-table-column
                      label="耗时（毫秒）"
                      prop="elapsedMillis"
                    />
                  </el-table> </template
              ></el-table-column>
              <el-table-column label="抽验状态"
                ><template #default="{ row }">{{
                  recordLabel(row.status)
                }}</template></el-table-column
              >
              <el-table-column label="触发时间" min-width="180"
                ><template #default="{ row }">{{
                  recordTime(row.triggeredAt)
                }}</template></el-table-column
              >
              <el-table-column label="截止时间" min-width="180"
                ><template #default="{ row }">{{
                  recordTime(row.deadlineAt)
                }}</template></el-table-column
              >
              <el-table-column label="提交次数" prop="attemptCount" /><el-table-column
                label="原因"
                min-width="150"
                ><template #default="{ row }">{{
                  recordLabel(row.failureReason)
                }}</template></el-table-column
              >
            </AppTable>
          </el-tab-pane>
        </el-tabs>
      </section>
    </template>
  </section>
</template>

<style scoped>
.record-detail {
  display: grid;
  gap: 18px;
  min-height: 150px;
}
.record-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
h2,
h3 {
  margin: 0;
}
p {
  color: var(--app-text-muted);
}
.session-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.session-filters .el-select {
  width: 180px;
}
</style>
