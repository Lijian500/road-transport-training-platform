import { defineComponent, nextTick } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { ExamRecord } from '@/api/exam'

import StudentExamView from './StudentExamView.vue'

const apiMock = vi.hoisted(() => ({
  openExam: vi.fn(),
  saveExamAnswers: vi.fn(),
  submitExam: vi.fn(),
}))

const elementPlusMock = vi.hoisted(() => ({
  message: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
  confirm: vi.fn(),
}))

const routerMock = vi.hoisted(() => ({
  push: vi.fn(),
}))

vi.mock('@/api/exam', () => apiMock)

vi.mock('element-plus', () => ({
  ElMessage: elementPlusMock.message,
  ElMessageBox: { confirm: elementPlusMock.confirm },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { planId: '100' } }),
  useRouter: () => routerMock,
}))

const RadioGroupStub = defineComponent({
  name: 'ElRadioGroup',
  props: {
    modelValue: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue', 'change'],
  template: '<div class="radio-group"><slot /></div>',
})

let wrapper: VueWrapper | undefined
let dateNowSpy: ReturnType<typeof vi.spyOn> | undefined

describe('StudentExamView学员考试', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    dateNowSpy = vi.spyOn(Date, 'now').mockReturnValue(new Date('2030-01-01T00:00:00').getTime())
    apiMock.openExam.mockResolvedValue(inProgressRecord())
    apiMock.saveExamAnswers.mockResolvedValue(inProgressRecord())
    apiMock.submitExam.mockResolvedValue(submittedRecord())
    elementPlusMock.confirm.mockResolvedValue('confirm')
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    dateNowSpy?.mockRestore()
    dateNowSpy = undefined
  })

  it('恢复考试后展示题目和服务端截止时间倒计时', async () => {
    wrapper = mountStudentExamView()
    await flushPromises()

    expect(apiMock.openExam).toHaveBeenCalledWith('100')
    expect(wrapper.text()).toContain('道路运输安全考试')
    expect(wrapper.text()).toContain('1. 安全驾驶基础要求是什么？')
    expect(wrapper.text()).toContain('00:10:00')
    expect(wrapper.text()).toContain('答题进度：1 / 2')
  })

  it('连续改答时严格等待上一次保存完成后再发送下一次请求', async () => {
    const firstSave = deferred<ExamRecord>()
    apiMock.openExam.mockResolvedValue(inProgressRecord(''))
    apiMock.saveExamAnswers
      .mockImplementationOnce(() => firstSave.promise)
      .mockResolvedValueOnce(inProgressRecord('B'))
    wrapper = mountStudentExamView()
    await flushPromises()

    const firstQuestion = wrapper.findAllComponents(RadioGroupStub)[0]!
    firstQuestion.vm.$emit('update:modelValue', 'A')
    firstQuestion.vm.$emit('change', 'A')
    await nextTick()
    await vi.waitFor(() => expect(apiMock.saveExamAnswers).toHaveBeenCalledTimes(1))

    firstQuestion.vm.$emit('update:modelValue', 'B')
    firstQuestion.vm.$emit('change', 'B')
    await nextTick()
    await Promise.resolve()

    expect(apiMock.saveExamAnswers).toHaveBeenCalledTimes(1)
    expect(apiMock.saveExamAnswers).toHaveBeenNthCalledWith(1, '700', [
      { paperQuestionId: '801', answer: 'A' },
    ])

    firstSave.resolve(inProgressRecord('A'))
    await vi.waitFor(() => expect(apiMock.saveExamAnswers).toHaveBeenCalledTimes(2))
    expect(apiMock.saveExamAnswers).toHaveBeenNthCalledWith(2, '700', [
      { paperQuestionId: '801', answer: 'B' },
    ])
  })

  it('确认提交后展示自动判分结果', async () => {
    wrapper = mountStudentExamView()
    await flushPromises()

    const submitButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交试卷'))
    expect(submitButton).toBeDefined()
    await submitButton!.trigger('click')
    await flushPromises()

    expect(elementPlusMock.confirm).toHaveBeenCalled()
    expect(apiMock.submitExam).toHaveBeenCalledWith('700')
    expect(wrapper.text()).toContain('考试通过')
    expect(wrapper.text()).toContain('得分 90 / 100')
    expect(wrapper.text()).toContain('通过')
  })
})

/** 使用页面渲染所需的轻量Element Plus桩挂载考试页。 */
function mountStudentExamView() {
  return mount(StudentExamView, {
    global: {
      directives: { loading: () => undefined },
      stubs: {
        ElAlert: { template: '<div><slot /></div>' },
        ElButton: {
          emits: ['click'],
          template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
        },
        ElCard: { template: '<div><slot name="header" /><slot /></div>' },
        ElDescriptions: { template: '<div><slot /></div>' },
        ElDescriptionsItem: {
          props: ['label'],
          template: '<div>{{ label }}：<slot /></div>',
        },
        ElEmpty: { template: '<div><slot /></div>' },
        ElProgress: true,
        ElRadio: { template: '<span><slot /></span>' },
        ElRadioGroup: RadioGroupStub,
        ElResult: {
          props: ['title', 'subTitle'],
          template:
            '<div class="exam-result"><h2>{{ title }}</h2><p>{{ subTitle }}</p><slot name="extra" /></div>',
        },
        ElTag: { template: '<span><slot /></span>' },
      },
    },
  })
}

/** 创建可由测试手工完成的Promise。 */
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

/** 创建恢复中的考试记录，可指定第一题已保存答案。 */
function inProgressRecord(firstAnswer = 'A'): ExamRecord {
  return {
    id: '700',
    taskId: '500',
    planId: '100',
    paperId: '600',
    paperName: '道路运输安全考试',
    status: 'IN_PROGRESS',
    startedAt: '2030-01-01T00:00:00',
    deadlineAt: '2030-01-01T00:10:00',
    passScore: 60,
    totalScore: 100,
    questions: [
      {
        paperQuestionId: '801',
        questionType: 'SINGLE_CHOICE',
        content: '安全驾驶基础要求是什么？',
        options: ['谨慎驾驶', '超速通行'],
        score: 50,
        sortOrder: 1,
        answer: firstAnswer,
      },
      {
        paperQuestionId: '802',
        questionType: 'JUDGMENT',
        content: '疲劳时可以继续驾驶。',
        options: [],
        score: 50,
        sortOrder: 2,
      },
    ],
  }
}

/** 创建已经完成判分的考试结果。 */
function submittedRecord(): ExamRecord {
  return {
    ...inProgressRecord(),
    status: 'SUBMITTED',
    submittedAt: '2030-01-01T00:05:00',
    score: 90,
    passed: true,
  }
}
