<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getExamPaper, type ExamPaper } from '@/api/exam'

const route = useRoute()
const paper = ref<ExamPaper>()
const loading = ref(false)
const error = ref('')

/** 独立加载试卷及题目快照，刷新或直接访问详情地址时同样可用。 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    paper.value = await getExamPaper(String(route.params.id))
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '试卷详情加载失败'
  } finally {
    loading.value = false
  }
}

/** 将判断题的答案与选项转换为中文。 */
function answerLabel(value: string) {
  return value === 'TRUE' ? '正确' : value === 'FALSE' ? '错误' : value
}

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="paper-detail">
    <div class="page-toolbar">
      <h1>{{ paper?.name || '试卷详情' }}</h1>
      <el-tag v-if="paper" :type="paper.status === 'ENABLED' ? 'success' : 'warning'">
        {{ paper.status === 'ENABLED' ? '已启用' : '草稿' }}
      </el-tag>
    </div>
    <el-result v-if="error" icon="error" :title="error">
      <template #extra><el-button @click="load">重试</el-button></template>
    </el-result>
    <template v-else-if="paper">
      <el-card shadow="never">
        <template #header
          ><strong>{{ paper.name }}</strong></template
        >
        <el-descriptions :column="2" border>
          <el-descriptions-item label="考试时长"
            >{{ paper.durationMinutes }} 分钟</el-descriptions-item
          >
          <el-descriptions-item label="题目数量"
            >{{ paper.manualQuestionCount + paper.randomQuestionCount }} 题</el-descriptions-item
          >
          <el-descriptions-item label="总分 / 及格分"
            >{{ paper.totalScore }} / {{ paper.passScore }} 分</el-descriptions-item
          >
          <el-descriptions-item label="每题分值">{{ paper.questionScore }} 分</el-descriptions-item>
          <el-descriptions-item label="组卷方式"
            >手工 {{ paper.manualQuestionCount }} 题，随机
            {{ paper.randomQuestionCount }} 题</el-descriptions-item
          >
          <el-descriptions-item label="试卷说明">{{
            paper.description || '无'
          }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
      <el-alert
        v-if="paper.status === 'DRAFT' && paper.randomQuestionCount"
        :closable="false"
        type="info"
        :title="`还有 ${paper.randomQuestionCount} 道随机题将在启用时抽取，下方展示已选题目。`"
      />
      <el-card v-for="(question, index) in paper.questions" :key="question.id" shadow="never">
        <template #header>
          <strong
            >第 {{ index + 1 }} 题 ·
            {{ question.questionType === 'JUDGMENT' ? '判断题' : '单选题' }}</strong
          >
          <span class="question-meta"
            >{{ question.score }} 分 ·
            {{ question.selectionType === 'MANUAL' ? '手工选题' : '随机选题' }}</span
          >
        </template>
        <p class="question-text">{{ question.content }}</p>
        <div
          v-for="(option, optionIndex) in question.options"
          :key="optionIndex"
          class="question-option"
        >
          <strong v-if="question.questionType !== 'JUDGMENT'"
            >{{ String.fromCharCode(65 + optionIndex) }}.</strong
          >
          {{ answerLabel(option) }}
        </div>
        <p><strong>正确答案：</strong>{{ answerLabel(question.correctAnswer) }}</p>
        <p class="question-text"><strong>解析：</strong>{{ question.analysis || '暂无解析' }}</p>
      </el-card>
      <el-empty v-if="!paper.questions.length" description="暂无已选题目" />
    </template>
  </section>
</template>

<style scoped>
.paper-detail {
  display: grid;
  gap: 16px;
}
.question-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.8;
}
.question-option {
  padding: 8px 12px;
  margin: 6px 0;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  white-space: pre-wrap;
}
.question-meta {
  margin-left: 16px;
  color: var(--app-text-muted);
  font-size: 13px;
}
</style>
