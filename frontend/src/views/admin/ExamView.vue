<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  changeExamQuestionStatus,
  createExamPaper,
  createExamQuestion,
  deleteExamPaper,
  deleteExamQuestion,
  enableExamPaper,
  getExamPapers,
  getExamQuestions,
  updateExamPaper,
  updateExamQuestion,
  type ExamPaper,
  type ExamQuestion,
  type PaperStatus,
  type QuestionStatus,
  type QuestionType,
} from '@/api/exam'
import { ApiError } from '@/api/http'
import AppDialog from '@/components/AppDialog/AppDialog.vue'
import AppFilterField from '@/components/AppFilterField/AppFilterField.vue'
import AppTable from '@/components/AppTable/AppTable.vue'
import PermissionButton from '@/components/PermissionButton/PermissionButton.vue'

const route = useRoute()
const router = useRouter()
const activeTab = ref(route.query.tab === 'papers' ? 'papers' : 'questions')
const loading = ref(false)
const saving = ref(false)
const questionRows = ref<ExamQuestion[]>([])
const questionTotal = ref(0)
const paperRows = ref<ExamPaper[]>([])
const paperTotal = ref(0)
const questionCandidates = ref<ExamQuestion[]>([])
const questionDialogVisible = ref(false)
const paperDialogVisible = ref(false)
const questionQuery = reactive({
  pageNumber: 1,
  pageSize: 10,
  keyword: '',
  questionType: '' as QuestionType | '',
  status: '' as QuestionStatus | '',
})
const paperQuery = reactive({
  pageNumber: 1,
  pageSize: 10,
  keyword: '',
  status: '' as PaperStatus | '',
})
const questionForm = reactive({
  id: '',
  questionType: 'SINGLE_CHOICE' as QuestionType,
  content: '',
  options: ['', ''],
  correctAnswer: 'A',
  analysis: '',
})
const paperForm = reactive({
  id: '',
  name: '',
  description: '',
  durationMinutes: 60,
  passScore: 60,
  questionScore: 10,
  manualQuestionIds: [] as string[],
  randomFillCount: 0,
})
const paperQuestionCount = computed(
  () => paperForm.manualQuestionIds.length + paperForm.randomFillCount,
)
const paperTotalScore = computed(() => paperQuestionCount.value * paperForm.questionScore)

/** 加载当前标签页数据。 */
async function load() {
  if (activeTab.value === 'questions') await loadQuestions()
  else await loadPapers()
}

/** 分页加载题库。 */
async function loadQuestions() {
  loading.value = true
  try {
    const result = await getExamQuestions(questionQuery)
    questionRows.value = result.records
    questionTotal.value = result.total
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

/** 分页加载试卷。 */
async function loadPapers() {
  loading.value = true
  try {
    const result = await getExamPapers(paperQuery)
    paperRows.value = result.records
    paperTotal.value = result.total
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

/** 从第一页按当前条件查询题库。 */
function searchQuestions() {
  questionQuery.pageNumber = 1
  void loadQuestions()
}

/** 从第一页按当前条件查询试卷。 */
function searchPapers() {
  paperQuery.pageNumber = 1
  void loadPapers()
}

/** 加载全部已启用题目作为手工组卷候选。 */
async function loadQuestionCandidates() {
  const records: ExamQuestion[] = []
  let pageNumber = 1
  let total = 0
  do {
    const result = await getExamQuestions({
      pageNumber,
      pageSize: 100,
      status: 'ENABLED',
    })
    records.push(...result.records)
    total = result.total
    pageNumber += 1
  } while (records.length < total)
  questionCandidates.value = records
}

/** 打开新建或编辑题目弹窗。 */
function openQuestion(row?: ExamQuestion) {
  Object.assign(questionForm, {
    id: row?.id || '',
    questionType: row?.questionType || 'SINGLE_CHOICE',
    content: row?.content || '',
    options: row?.questionType === 'JUDGMENT' ? ['TRUE', 'FALSE'] : [...(row?.options || ['', ''])],
    correctAnswer: row?.correctAnswer || 'A',
    analysis: row?.analysis || '',
  })
  questionDialogVisible.value = true
}

/** 切换题型时重置题型特有选项和答案。 */
function changeQuestionType(type: QuestionType) {
  if (type === 'JUDGMENT') {
    questionForm.options = ['TRUE', 'FALSE']
    questionForm.correctAnswer = 'TRUE'
  } else {
    questionForm.options = ['', '']
    questionForm.correctAnswer = 'A'
  }
}

/** 为单选题追加一个选项。 */
function addOption() {
  if (questionForm.options.length < 6) questionForm.options.push('')
}

/** 删除单选题选项并修正可能失效的答案。 */
function removeOption(index: number) {
  if (questionForm.options.length <= 2) return
  const answerIndex = questionForm.correctAnswer.charCodeAt(0) - 65
  questionForm.options.splice(index, 1)
  if (index === answerIndex) {
    questionForm.correctAnswer = 'A'
  } else if (index < answerIndex) {
    questionForm.correctAnswer = String.fromCharCode(64 + answerIndex)
  }
}

/** 校验并保存题目。 */
async function saveQuestion() {
  const options = questionForm.options.map((item) => item.trim())
  if (!questionForm.content.trim()) {
    ElMessage.warning('请输入题干')
    return
  }
  if (questionForm.questionType === 'SINGLE_CHOICE' && options.some((item) => !item)) {
    ElMessage.warning('请填写全部单选题选项')
    return
  }
  if (questionForm.questionType === 'SINGLE_CHOICE' && new Set(options).size !== options.length) {
    ElMessage.warning('单选题选项不能重复')
    return
  }
  saving.value = true
  try {
    const payload = {
      questionType: questionForm.questionType,
      content: questionForm.content,
      options,
      correctAnswer: questionForm.correctAnswer,
      analysis: questionForm.analysis || undefined,
    }
    if (questionForm.id) await updateExamQuestion(questionForm.id, payload)
    else await createExamQuestion(payload)
    questionDialogVisible.value = false
    ElMessage.success(questionForm.id ? '题目已更新' : '题目已创建')
    await loadQuestions()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 启用或停用题目。 */
async function toggleQuestion(row: ExamQuestion) {
  try {
    await changeExamQuestionStatus(row.id, row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED')
    ElMessage.success(row.status === 'ENABLED' ? '题目已停用' : '题目已启用')
    await loadQuestions()
  } catch (error) {
    showError(error)
  }
}

/** 删除题目。 */
async function removeQuestion(row: ExamQuestion) {
  await ElMessageBox.confirm('确定删除该题目吗？已启用试卷中的题目快照不受影响。', '删除确认', {
    type: 'warning',
  })
  try {
    await deleteExamQuestion(row.id)
    ElMessage.success('题目已删除')
    await loadQuestions()
  } catch (error) {
    showError(error)
  }
}

/** 打开新建或编辑试卷弹窗并准备题目候选项。 */
async function openPaper(row?: ExamPaper) {
  try {
    await loadQuestionCandidates()
    Object.assign(paperForm, {
      id: row?.id || '',
      name: row?.name || '',
      description: row?.description || '',
      durationMinutes: row?.durationMinutes || 60,
      passScore: row?.passScore || 60,
      questionScore: row?.questionScore || 10,
      manualQuestionIds:
        row?.questions
          .filter((item) => item.selectionType === 'MANUAL')
          .map((item) => item.sourceQuestionId) || [],
      randomFillCount: row?.randomQuestionCount || 0,
    })
    paperDialogVisible.value = true
  } catch (error) {
    showError(error)
  }
}

/** 校验并保存试卷草稿。 */
async function savePaper() {
  if (!paperForm.name.trim()) {
    ElMessage.warning('请输入试卷名称')
    return
  }
  if (paperQuestionCount.value < 1) {
    ElMessage.warning('请手工选择题目或设置随机补题数')
    return
  }
  if (paperQuestionCount.value > 200) {
    ElMessage.warning('试卷题目总数不能超过200题')
    return
  }
  if (paperForm.passScore < 1 || paperForm.passScore > paperTotalScore.value) {
    ElMessage.warning(`及格分须在1至${paperTotalScore.value}分之间`)
    return
  }
  saving.value = true
  try {
    const payload = {
      name: paperForm.name.trim(),
      description: paperForm.description.trim() || undefined,
      durationMinutes: paperForm.durationMinutes,
      passScore: paperForm.passScore,
      questionScore: paperForm.questionScore,
      manualQuestionIds: paperForm.manualQuestionIds,
      randomFillCount: paperForm.randomFillCount,
    }
    if (paperForm.id) await updateExamPaper(paperForm.id, payload)
    else await createExamPaper(payload)
    paperDialogVisible.value = false
    ElMessage.success(paperForm.id ? '试卷草稿已更新' : '试卷草稿已创建')
    await loadPapers()
  } catch (error) {
    showError(error)
  } finally {
    saving.value = false
  }
}

/** 固化当前组卷结果并启用试卷。 */
async function enablePaper(row: ExamPaper) {
  await ElMessageBox.confirm('启用后题目和规则不可再修改，确定继续吗？', '启用确认', {
    type: 'warning',
  })
  try {
    await enableExamPaper(row.id)
    ElMessage.success('试卷已固化并启用')
    await loadPapers()
  } catch (error) {
    showError(error)
  }
}

/** 删除试卷草稿。 */
async function removePaper(row: ExamPaper) {
  await ElMessageBox.confirm(`确定删除试卷草稿“${row.name}”吗？`, '删除确认', {
    type: 'warning',
  })
  try {
    await deleteExamPaper(row.id)
    ElMessage.success('试卷草稿已删除')
    await loadPapers()
  } catch (error) {
    showError(error)
  }
}

/** 切换题库页码。 */
function changeQuestionPage(pageNumber: number) {
  questionQuery.pageNumber = pageNumber
  void loadQuestions()
}

/** 切换题库每页数量。 */
function changeQuestionPageSize(pageSize: number) {
  questionQuery.pageSize = pageSize
  questionQuery.pageNumber = 1
  void loadQuestions()
}

/** 切换试卷页码。 */
function changePaperPage(pageNumber: number) {
  paperQuery.pageNumber = pageNumber
  void loadPapers()
}

/** 切换试卷每页数量。 */
function changePaperPageSize(pageSize: number) {
  paperQuery.pageSize = pageSize
  paperQuery.pageNumber = 1
  void loadPapers()
}

/** 返回题型中文名。 */
function questionTypeLabel(type: QuestionType) {
  return type === 'SINGLE_CHOICE' ? '单选题' : '判断题'
}

/** 返回试卷状态中文名。 */
function paperStatusLabel(status: PaperStatus) {
  return status === 'DRAFT' ? '草稿' : '已启用'
}

/** 统一展示接口错误。 */
function showError(error: unknown) {
  ElMessage.error(error instanceof ApiError ? error.message : '操作失败，请稍后重试')
}

watch(activeTab, (tab) => { void router.replace({ path: '/admin/exam', query: { tab } }) })
watch(() => route.query.tab, (tab) => { activeTab.value = tab === 'papers' ? 'papers' : 'questions' })
onMounted(load)
</script>

<template>
  <section>


    <el-tabs v-model="activeTab" @tab-change="load">
      <el-tab-pane label="题库" name="questions">
        <AppTable
          :data="questionRows"
          :loading="loading"
          :page-number="questionQuery.pageNumber"
          :page-size="questionQuery.pageSize"
          :total="questionTotal"
          @page-change="changeQuestionPage"
          @size-change="changeQuestionPageSize"
        >
          <template #search>
            <AppFilterField label="题干">
              <el-input v-model="questionQuery.keyword" clearable @keyup.enter="searchQuestions" />
            </AppFilterField>
            <AppFilterField label="题型">
              <el-select v-model="questionQuery.questionType" clearable>
                <el-option label="单选题" value="SINGLE_CHOICE" />
                <el-option label="判断题" value="JUDGMENT" />
              </el-select>
            </AppFilterField>
            <AppFilterField label="状态">
              <el-select v-model="questionQuery.status" clearable>
                <el-option label="已启用" value="ENABLED" />
                <el-option label="已停用" value="DISABLED" />
              </el-select>
            </AppFilterField>
            <el-button type="primary" @click="searchQuestions">查询</el-button>
          </template>
          <template #actions>
            <PermissionButton permission="admin:exam:manage" type="primary" @click="openQuestion()">
              新建题目
            </PermissionButton>
          </template>
          <el-table-column label="题型" width="100">
            <template #default="{ row }">{{ questionTypeLabel(row.questionType) }}</template>
          </el-table-column>
          <el-table-column label="题干" min-width="300" prop="content" show-overflow-tooltip />
          <el-table-column label="答案" width="90" prop="correctAnswer" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
                {{ row.status === 'ENABLED' ? '已启用' : '已停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <PermissionButton permission="admin:exam:manage" link @click="openQuestion(row)">
                编辑
              </PermissionButton>
              <PermissionButton permission="admin:exam:manage" link @click="toggleQuestion(row)">
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </PermissionButton>
              <PermissionButton
                permission="admin:exam:manage"
                link
                type="danger"
                @click="removeQuestion(row)"
              >
                删除
              </PermissionButton>
            </template>
          </el-table-column>
        </AppTable>
      </el-tab-pane>

      <el-tab-pane label="试卷" name="papers">
        <AppTable
          :data="paperRows"
          :loading="loading"
          :page-number="paperQuery.pageNumber"
          :page-size="paperQuery.pageSize"
          :total="paperTotal"
          @page-change="changePaperPage"
          @size-change="changePaperPageSize"
        >
          <template #search>
            <AppFilterField label="试卷名称">
              <el-input v-model="paperQuery.keyword" clearable @keyup.enter="searchPapers" />
            </AppFilterField>
            <AppFilterField label="状态">
              <el-select v-model="paperQuery.status" clearable>
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已启用" value="ENABLED" />
              </el-select>
            </AppFilterField>
            <el-button type="primary" @click="searchPapers">查询</el-button>
          </template>
          <template #actions>
            <PermissionButton permission="admin:exam:manage" type="primary" @click="openPaper()">
              新建试卷
            </PermissionButton>
          </template>
          <el-table-column label="试卷名称" min-width="190">
            <template #default="{ row }"><el-button link type="primary" @click="router.push(`/admin/exam/papers/${row.id}`)">{{ row.name }}</el-button></template>
          </el-table-column>
          <el-table-column label="题目" width="100">
            <template #default="{ row }"
              >{{ row.manualQuestionCount + row.randomQuestionCount }}题</template
            >
          </el-table-column>
          <el-table-column label="分值" width="130">
            <template #default="{ row }">{{ row.passScore }} / {{ row.totalScore }}分</template>
          </el-table-column>
          <el-table-column label="时长" width="100">
            <template #default="{ row }">{{ row.durationMinutes }}分钟</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'warning'">
                {{ paperStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/admin/exam/papers/${row.id}`)">详情</el-button>
              <PermissionButton
                v-if="row.status === 'DRAFT'"
                permission="admin:exam:manage"
                link
                @click="openPaper(row)"
              >
                编辑
              </PermissionButton>
              <PermissionButton
                v-if="row.status === 'DRAFT'"
                permission="admin:exam:manage"
                link
                @click="enablePaper(row)"
              >
                启用
              </PermissionButton>
              <PermissionButton
                v-if="row.status === 'DRAFT'"
                permission="admin:exam:manage"
                link
                type="danger"
                @click="removePaper(row)"
              >
                删除
              </PermissionButton>
              <span v-else class="muted">题目已固化</span>
            </template>
          </el-table-column>
        </AppTable>
      </el-tab-pane>
    </el-tabs>

    <AppDialog
      v-model="questionDialogVisible"
      :title="questionForm.id ? '编辑题目' : '新建题目'"
      :loading="saving"
      @confirm="saveQuestion"
    >
      <el-form label-width="90px">
        <el-form-item label="题型">
          <el-radio-group v-model="questionForm.questionType" @change="changeQuestionType">
            <el-radio value="SINGLE_CHOICE">单选题</el-radio>
            <el-radio value="JUDGMENT">判断题</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题干">
          <el-input v-model="questionForm.content" maxlength="1000" rows="3" type="textarea" />
        </el-form-item>
        <el-form-item v-if="questionForm.questionType === 'SINGLE_CHOICE'" label="选项">
          <div class="option-editor">
            <div v-for="(_, index) in questionForm.options" :key="index" class="option-row">
              <strong>{{ String.fromCharCode(65 + index) }}</strong>
              <el-input v-model="questionForm.options[index]" maxlength="500" />
              <el-button
                :disabled="questionForm.options.length <= 2"
                link
                type="danger"
                @click="removeOption(index)"
              >
                删除
              </el-button>
            </div>
            <el-button :disabled="questionForm.options.length >= 6" @click="addOption">
              添加选项
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="正确答案">
          <el-select
            v-if="questionForm.questionType === 'SINGLE_CHOICE'"
            v-model="questionForm.correctAnswer"
          >
            <el-option
              v-for="(_, index) in questionForm.options"
              :key="index"
              :label="String.fromCharCode(65 + index)"
              :value="String.fromCharCode(65 + index)"
            />
          </el-select>
          <el-radio-group v-else v-model="questionForm.correctAnswer">
            <el-radio value="TRUE">正确</el-radio>
            <el-radio value="FALSE">错误</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="答案解析">
          <el-input v-model="questionForm.analysis" maxlength="1000" rows="2" type="textarea" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog
      v-model="paperDialogVisible"
      :title="paperForm.id ? '编辑试卷草稿' : '新建试卷'"
      :loading="saving"
      width="760px"
      @confirm="savePaper"
    >
      <el-form label-width="100px">
        <el-form-item label="试卷名称">
          <el-input v-model="paperForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="试卷说明">
          <el-input v-model="paperForm.description" maxlength="1000" rows="2" type="textarea" />
        </el-form-item>
        <div class="paper-rule-grid">
          <el-form-item label="考试时长">
            <el-input-number v-model="paperForm.durationMinutes" :min="1" :max="480" />
            <span class="unit">分钟</span>
          </el-form-item>
          <el-form-item label="每题分值">
            <el-input-number v-model="paperForm.questionScore" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="随机补题">
            <el-input-number v-model="paperForm.randomFillCount" :min="0" :max="200" />
            <span class="unit">题</span>
          </el-form-item>
          <el-form-item label="及格分">
            <el-input-number v-model="paperForm.passScore" :min="1" :max="paperTotalScore || 1" />
          </el-form-item>
        </div>
        <el-form-item label="手工选题">
          <el-select
            v-model="paperForm.manualQuestionIds"
            filterable
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="可不选，使用随机补题"
          >
            <el-option
              v-for="question in questionCandidates"
              :key="question.id"
              :label="`[${questionTypeLabel(question.questionType)}] ${question.content}`"
              :value="question.id"
            />
          </el-select>
        </el-form-item>
        <el-alert
          :closable="false"
          :title="`共 ${paperQuestionCount} 题，总分 ${paperTotalScore} 分；随机题在启用试卷时一次性抽取并固化。`"
          type="info"
        />
      </el-form>
    </AppDialog>
  </section>
</template>

<style scoped>
.option-editor {
  display: grid;
  width: 100%;
  gap: 10px;
}

.option-row {
  display: grid;
  align-items: center;
  grid-template-columns: 24px minmax(0, 1fr) 40px;
  gap: 8px;
}

.paper-rule-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.unit,
.muted {
  margin-left: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
}

@media (width <= 680px) {
  .paper-rule-grid {
    grid-template-columns: 1fr;
  }
}
</style>
