import { http, type PageResult } from '@/api/http'

export type QuestionType = 'SINGLE_CHOICE' | 'JUDGMENT'
export type QuestionStatus = 'ENABLED' | 'DISABLED'
export type PaperStatus = 'DRAFT' | 'ENABLED'
export type ExamStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'TIMEOUT'

export interface ExamQuestion {
  id: string
  questionType: QuestionType
  content: string
  options: string[]
  correctAnswer: string
  analysis?: string
  status: QuestionStatus
  createdAt: string
  updatedAt: string
}

export interface QuestionPayload {
  questionType: QuestionType
  content: string
  options: string[]
  correctAnswer: string
  analysis?: string
}

export interface PaperQuestion {
  id: string
  sourceQuestionId: string
  selectionType: 'MANUAL' | 'RANDOM'
  questionType: QuestionType
  content: string
  options: string[]
  correctAnswer: string
  analysis?: string
  score: number
  sortOrder: number
}

export interface ExamPaper {
  id: string
  name: string
  description?: string
  durationMinutes: number
  passScore: number
  questionScore: number
  manualQuestionCount: number
  randomQuestionCount: number
  totalScore: number
  status: PaperStatus
  questions: PaperQuestion[]
  enabledAt?: string
  createdAt: string
  updatedAt: string
}

export interface PaperPayload {
  name: string
  description?: string
  durationMinutes: number
  passScore: number
  questionScore: number
  manualQuestionIds: string[]
  randomFillCount: number
}

export interface PaperOption {
  id: string
  name: string
  durationMinutes: number
  passScore: number
  totalScore: number
  questionCount: number
}

export interface StudentExamQuestion {
  paperQuestionId: string
  questionType: QuestionType
  content: string
  options: string[]
  score: number
  sortOrder: number
  answer?: string
}

export interface ExamRecord {
  id: string
  taskId: string
  planId: string
  paperId: string
  paperName: string
  status: ExamStatus
  startedAt: string
  deadlineAt: string
  submittedAt?: string
  passScore: number
  totalScore: number
  score?: number
  passed?: boolean
  questions: StudentExamQuestion[]
}

export interface ExamAnswer {
  paperQuestionId: string
  answer: string
}

/** 分页查询当前企业题库。 */
export function getExamQuestions(params: {
  pageNumber: number
  pageSize: number
  keyword?: string
  questionType?: QuestionType | ''
  status?: QuestionStatus | ''
}) {
  return http.get<PageResult<ExamQuestion>>('/training/exam/questions', { params })
}

/** 创建单选题或判断题。 */
export function createExamQuestion(data: QuestionPayload) {
  return http.post<ExamQuestion>('/training/exam/questions', data)
}

/** 获取题目详情。 */
export function getExamQuestion(id: string) {
  return http.get<ExamQuestion>(`/training/exam/questions/${id}`)
}

/** 编辑题目内容。 */
export function updateExamQuestion(id: string, data: QuestionPayload) {
  return http.put<ExamQuestion>(`/training/exam/questions/${id}`, data)
}

/** 启用或停用题目。 */
export function changeExamQuestionStatus(id: string, status: QuestionStatus) {
  return http.patch<ExamQuestion>(`/training/exam/questions/${id}/status`, { status })
}

/** 删除不再使用的题目。 */
export function deleteExamQuestion(id: string) {
  return http.delete<void>(`/training/exam/questions/${id}`)
}

/** 分页查询当前企业试卷。 */
export function getExamPapers(params: {
  pageNumber: number
  pageSize: number
  keyword?: string
  status?: PaperStatus | ''
}) {
  return http.get<PageResult<ExamPaper>>('/training/exam/papers', { params })
}

/** 创建试卷草稿。 */
export function createExamPaper(data: PaperPayload) {
  return http.post<ExamPaper>('/training/exam/papers', data)
}

/** 获取试卷详情和固化题目。 */
export function getExamPaper(id: string) {
  return http.get<ExamPaper>(`/training/exam/papers/${id}`)
}

/** 编辑试卷草稿。 */
export function updateExamPaper(id: string, data: PaperPayload) {
  return http.put<ExamPaper>(`/training/exam/papers/${id}`, data)
}

/** 删除试卷草稿。 */
export function deleteExamPaper(id: string) {
  return http.delete<void>(`/training/exam/papers/${id}`)
}

/** 固化题目并启用试卷。 */
export function enableExamPaper(id: string) {
  return http.post<ExamPaper>(`/training/exam/papers/${id}/enable`)
}

/** 查询计划可关联的已启用试卷。 */
export function getEnabledExamPaperOptions(keyword?: string) {
  return http.get<PaperOption[]>('/training/exam/papers/options', { params: { keyword } })
}

/** 创建或恢复当前学员在计划下的唯一考试。 */
export function openExam(planId: string) {
  return http.post<ExamRecord>(`/exams/plans/${planId}/records`)
}

/** 查询本人考试详情。 */
export function getExamRecord(recordId: string) {
  return http.get<ExamRecord>(`/exams/records/${recordId}`)
}

/** 保存本次发生变化的答案。 */
export function saveExamAnswers(recordId: string, answers: ExamAnswer[]) {
  return http.put<ExamRecord>(`/exams/records/${recordId}/answers`, { answers })
}

/** 提交试卷并获取自动判分结果。 */
export function submitExam(recordId: string) {
  return http.post<ExamRecord>(`/exams/records/${recordId}/submit`)
}
