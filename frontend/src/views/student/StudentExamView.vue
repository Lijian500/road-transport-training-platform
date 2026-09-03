<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { openExam, saveExamAnswers, submitExam, type ExamRecord } from '@/api/exam'
import { ApiError } from '@/api/http'

const route = useRoute()
const router = useRouter()
const planId = String(route.params.planId)

const loading = ref(false)
const submitting = ref(false)
const timeoutSettling = ref(false)
const savingCount = ref(0)
const saveFailed = ref(false)
const record = ref<ExamRecord>()
const answers = reactive<Record<string, string>>({})
const remainingSeconds = ref(0)

const unsavedAnswers = new Map<string, string>()
let answerSaveChain: Promise<void> = Promise.resolve()
let countdownTimer: number | undefined
let timeoutRetryTimer: number | undefined

const isInProgress = computed(() => record.value?.status === 'IN_PROGRESS')
const answeredCount = computed(
  () =>
    record.value?.questions.filter((question) => Boolean(answers[question.paperQuestionId]))
      .length ?? 0,
)
const questionCount = computed(() => record.value?.questions.length ?? 0)
const answerPercentage = computed(() =>
  questionCount.value ? Math.round((answeredCount.value / questionCount.value) * 100) : 0,
)
const countdownLabel = computed(() => {
  const hours = Math.floor(remainingSeconds.value / 3600)
  const minutes = Math.floor((remainingSeconds.value % 3600) / 60)
  const seconds = remainingSeconds.value % 60
  return [hours, minutes, seconds].map((value) => String(value).padStart(2, '0')).join(':')
})
const countdownDanger = computed(() => remainingSeconds.value <= 5 * 60)
const saveStatusLabel = computed(() => {
  if (saveFailed.value) return '部分答案尚未保存'
  if (savingCount.value) return '答案保存中…'
  return '答案已保存'
})

/** 创建或恢复当前计划下的唯一考试记录。 */
async function initialize() {
  loading.value = true
  try {
    applyRecord(await openExam(planId))
  } catch (error) {
    showError(error, '考试加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/** 应用服务端考试状态，并恢复已经保存的答案。 */
function applyRecord(nextRecord: ExamRecord) {
  record.value = nextRecord
  nextRecord.questions.forEach((question) => {
    if (question.answer) answers[question.paperQuestionId] = question.answer
  })
  if (nextRecord.status === 'IN_PROGRESS') {
    refreshCountdown()
    startCountdown()
    return
  }
  unsavedAnswers.clear()
  saveFailed.value = false
  stopCountdown()
}

/** 将一次答题变化追加到保存队列，避免并发响应覆盖较新的答案。 */
function handleAnswerChange(
  paperQuestionId: string,
  answer: string | number | boolean | undefined,
) {
  if (!isInProgress.value || submitting.value || timeoutSettling.value) return
  const normalizedAnswer = String(answer ?? '')
  if (!normalizedAnswer) return
  answers[paperQuestionId] = normalizedAnswer
  unsavedAnswers.set(paperQuestionId, normalizedAnswer)
  savingCount.value += 1
  answerSaveChain = answerSaveChain
    .then(() => persistAnswer(paperQuestionId, normalizedAnswer))
    .catch((error) => {
      saveFailed.value = true
      showError(error, '答案自动保存失败，请检查网络后再交卷')
    })
    .finally(() => {
      savingCount.value = Math.max(0, savingCount.value - 1)
    })
}

/** 保存队列中的单道题答案，并识别服务端触发的超时终态。 */
async function persistAnswer(paperQuestionId: string, answer: string) {
  if (!record.value || record.value.status !== 'IN_PROGRESS') return
  const nextRecord = await saveExamAnswers(record.value.id, [{ paperQuestionId, answer }])
  if (unsavedAnswers.get(paperQuestionId) === answer) {
    unsavedAnswers.delete(paperQuestionId)
  }
  if (!unsavedAnswers.size) saveFailed.value = false
  if (nextRecord.status !== 'IN_PROGRESS') applyRecord(nextRecord)
}

/** 等待自动保存完成，并集中重试仍未成功保存的答案。 */
async function flushPendingAnswers() {
  await answerSaveChain
  if (!record.value || record.value.status !== 'IN_PROGRESS' || !unsavedAnswers.size) return true
  const snapshot = Array.from(unsavedAnswers, ([paperQuestionId, answer]) => ({
    paperQuestionId,
    answer,
  }))
  try {
    const nextRecord = await saveExamAnswers(record.value.id, snapshot)
    snapshot.forEach(({ paperQuestionId, answer }) => {
      if (unsavedAnswers.get(paperQuestionId) === answer) unsavedAnswers.delete(paperQuestionId)
    })
    saveFailed.value = unsavedAnswers.size > 0
    if (nextRecord.status !== 'IN_PROGRESS') applyRecord(nextRecord)
    return !unsavedAnswers.size || nextRecord.status !== 'IN_PROGRESS'
  } catch (error) {
    saveFailed.value = true
    showError(error, '答案保存失败，暂时无法交卷')
    return false
  }
}

/** 确认未答题提示，等待答案保存后手工提交考试。 */
async function handleSubmit() {
  if (!record.value || record.value.status !== 'IN_PROGRESS' || submitting.value) return
  const unanswered = questionCount.value - answeredCount.value
  const message = unanswered
    ? `还有 ${unanswered} 道题未作答，提交后不能修改，确定交卷吗？`
    : '提交后不能修改答案，确定交卷吗？'
  try {
    await ElMessageBox.confirm(message, '确认交卷', {
      type: 'warning',
      confirmButtonText: '确认交卷',
      cancelButtonText: '继续答题',
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    if (!(await flushPendingAnswers()) || record.value.status !== 'IN_PROGRESS') return
    applyRecord(await submitExam(record.value.id))
    ElMessage.success('试卷已提交')
  } catch (error) {
    showError(error, '交卷失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

/** 截止时间到达后等待在途保存，并调用服务端完成超时判分。 */
async function settleTimeout() {
  if (!record.value || record.value.status !== 'IN_PROGRESS' || timeoutSettling.value) return
  timeoutSettling.value = true
  stopCountdown()
  try {
    await answerSaveChain
    if (record.value.status === 'IN_PROGRESS') {
      applyRecord(await submitExam(record.value.id))
    }
    ElMessage.warning('考试时间已到，系统已自动交卷')
  } catch (error) {
    showError(error, '超时交卷失败，正在等待服务端自动结算')
    scheduleTimeoutRetry()
  } finally {
    timeoutSettling.value = false
  }
}

/** 按服务端截止时间刷新剩余秒数。 */
function refreshCountdown() {
  if (!record.value) return
  const deadline = new Date(record.value.deadlineAt).getTime()
  remainingSeconds.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
  if (!remainingSeconds.value) void settleTimeout()
}

/** 启动每秒更新一次的考试倒计时。 */
function startCountdown() {
  if (countdownTimer !== undefined) return
  countdownTimer = window.setInterval(refreshCountdown, 1000)
}

/** 超时结算请求失败后延迟重试，避免页面停留在可作答状态。 */
function scheduleTimeoutRetry() {
  if (timeoutRetryTimer !== undefined) return
  timeoutRetryTimer = window.setTimeout(() => {
    timeoutRetryTimer = undefined
    void settleTimeout()
  }, 5000)
}

/** 停止考试倒计时并释放定时器。 */
function stopCountdown() {
  if (countdownTimer !== undefined) {
    window.clearInterval(countdownTimer)
    countdownTimer = undefined
  }
  if (timeoutRetryTimer !== undefined) {
    window.clearTimeout(timeoutRetryTimer)
    timeoutRetryTimer = undefined
  }
}

/** 返回单选题选项的字母编号。 */
function optionValue(index: number) {
  return String.fromCharCode(65 + index)
}

/** 返回考试终态中文文案。 */
function resultStatusLabel(status: ExamRecord['status']) {
  return status === 'TIMEOUT' ? '超时交卷' : '已交卷'
}

/** 格式化服务端时间。 */
function formatDateTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

/** 返回培训任务详情页。 */
function backToPlan() {
  void router.push(`/student/plans/${planId}`)
}

/** 统一展示考试相关错误。 */
function showError(error: unknown, fallback: string) {
  ElMessage.error(error instanceof ApiError ? error.message : fallback)
}

onMounted(initialize)
onBeforeUnmount(stopCountdown)
</script>

<template>
  <section v-loading="loading" class="exam-page">
    <header class="exam-header">
      <div>
        <el-button link type="primary" @click="backToPlan">← 返回培训任务</el-button>
        <h1>{{ record?.paperName || '在线考试' }}</h1>
        <p v-if="record">满分 {{ record.totalScore }} 分，{{ record.passScore }} 分及格</p>
      </div>
      <div v-if="record?.status === 'IN_PROGRESS'" class="countdown-panel">
        <span>剩余时间</span>
        <strong :class="{ danger: countdownDanger }">{{ countdownLabel }}</strong>
      </div>
    </header>

    <el-empty v-if="!loading && !record" description="暂时无法加载考试">
      <el-button type="primary" @click="initialize">重新加载</el-button>
    </el-empty>

    <template v-else-if="record">
      <el-result
        v-if="record.status !== 'IN_PROGRESS'"
        :icon="record.passed ? 'success' : 'error'"
        :title="record.passed ? '考试通过' : '考试未通过'"
        :sub-title="`${resultStatusLabel(record.status)}，得分 ${record.score ?? 0} / ${record.totalScore}`"
      >
        <template #extra>
          <el-descriptions :column="2" border class="result-detail">
            <el-descriptions-item label="开始时间">
              {{ formatDateTime(record.startedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="交卷时间">
              {{ formatDateTime(record.submittedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="及格分数">{{ record.passScore }}</el-descriptions-item>
            <el-descriptions-item label="考试结果">
              <el-tag :type="record.passed ? 'success' : 'danger'">
                {{ record.passed ? '通过' : '未通过' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <el-button type="primary" @click="backToPlan">返回培训任务</el-button>
        </template>
      </el-result>

      <template v-else>
        <el-alert
          title="考试只有一次作答记录，答案会在选择后自动保存，请在倒计时结束前交卷。"
          type="warning"
          :closable="false"
          show-icon
        />

        <el-card class="progress-card" shadow="never">
          <div class="progress-summary">
            <span>答题进度：{{ answeredCount }} / {{ questionCount }}</span>
            <span :class="{ 'save-error': saveFailed }">{{ saveStatusLabel }}</span>
          </div>
          <el-progress :percentage="answerPercentage" :stroke-width="10" />
        </el-card>

        <div class="question-list">
          <el-card
            v-for="(question, index) in record.questions"
            :id="`question-${question.paperQuestionId}`"
            :key="question.paperQuestionId"
            class="question-card"
            shadow="never"
          >
            <template #header>
              <div class="question-title">
                <strong>{{ index + 1 }}. {{ question.content }}</strong>
                <el-tag size="small" type="info">
                  {{ question.questionType === 'JUDGMENT' ? '判断题' : '单选题' }} ·
                  {{ question.score }}分
                </el-tag>
              </div>
            </template>

            <el-radio-group
              v-if="question.questionType === 'SINGLE_CHOICE'"
              v-model="answers[question.paperQuestionId]"
              class="answer-options"
              :disabled="submitting || timeoutSettling"
              @change="handleAnswerChange(question.paperQuestionId, $event)"
            >
              <el-radio
                v-for="(option, optionIndex) in question.options"
                :key="optionValue(optionIndex)"
                :value="optionValue(optionIndex)"
                border
              >
                <span class="option-prefix">{{ optionValue(optionIndex) }}.</span>
                {{ option }}
              </el-radio>
            </el-radio-group>

            <el-radio-group
              v-else
              v-model="answers[question.paperQuestionId]"
              class="answer-options judgment-options"
              :disabled="submitting || timeoutSettling"
              @change="handleAnswerChange(question.paperQuestionId, $event)"
            >
              <el-radio value="TRUE" border>正确</el-radio>
              <el-radio value="FALSE" border>错误</el-radio>
            </el-radio-group>
          </el-card>
        </div>

        <footer class="submit-bar">
          <div>
            已答 {{ answeredCount }} 题，未答 {{ questionCount - answeredCount }} 题
            <span v-if="savingCount">，正在保存 {{ savingCount }} 项变化</span>
          </div>
          <el-button
            type="primary"
            size="large"
            :loading="submitting || timeoutSettling"
            @click="handleSubmit"
          >
            提交试卷
          </el-button>
        </footer>
      </template>
    </template>
  </section>
</template>

<style scoped>
.exam-page {
  max-width: 980px;
  margin: 0 auto;
  padding-bottom: 92px;
}

.exam-header,
.question-title,
.progress-summary,
.submit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.exam-header {
  align-items: flex-start;
  margin-bottom: 22px;
}

.exam-header h1 {
  margin: 12px 0 6px;
}

.exam-header p,
.progress-summary {
  color: #6f7c93;
}

.countdown-panel {
  min-width: 150px;
  padding: 14px 18px;
  border: 1px solid #dfe5ef;
  border-radius: 10px;
  background: #fff;
  text-align: center;
}

.countdown-panel span,
.countdown-panel strong {
  display: block;
}

.countdown-panel strong {
  margin-top: 4px;
  color: #3370ff;
  font-size: 26px;
  font-variant-numeric: tabular-nums;
}

.countdown-panel strong.danger,
.save-error {
  color: #e34d59;
}

.progress-card {
  margin: 18px 0;
}

.progress-summary {
  margin-bottom: 10px;
}

.question-card + .question-card {
  margin-top: 16px;
}

.question-title {
  align-items: flex-start;
}

.question-title strong {
  line-height: 1.7;
}

.answer-options {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  width: 100%;
}

.answer-options :deep(.el-radio) {
  width: 100%;
  height: auto;
  min-height: 42px;
  margin: 0;
  padding: 10px 14px;
  white-space: normal;
}

.answer-options :deep(.el-radio__label) {
  line-height: 1.6;
  white-space: normal;
}

.judgment-options {
  flex-direction: row;
}

.option-prefix {
  margin-right: 6px;
  font-weight: 600;
}

.submit-bar {
  position: fixed;
  bottom: 18px;
  left: 50%;
  z-index: 8;
  box-sizing: border-box;
  width: min(980px, calc(100% - 40px));
  padding: 14px 22px;
  border: 1px solid #dfe5ef;
  border-radius: 12px;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 8px 24px rgb(31 45 61 / 12%);
  color: #5f6c85;
  transform: translateX(-50%);
}

.result-detail {
  width: min(620px, 100%);
  margin-bottom: 20px;
}

@media (max-width: 640px) {
  .exam-header,
  .question-title,
  .submit-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .countdown-panel {
    align-self: stretch;
  }

  .judgment-options {
    flex-direction: column;
  }
}
</style>
