import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { FaceCheckTask } from '@/api/learning'

import FaceCheckDialog from './FaceCheckDialog.vue'

const apiMock = vi.hoisted(() => ({ submitFaceCheck: vi.fn() }))

vi.mock('@/api/learning', () => ({
  submitFaceCheck: apiMock.submitFaceCheck,
}))

let wrapper: VueWrapper | undefined
let mediaDevicesDescriptor: PropertyDescriptor | undefined
let createObjectUrlDescriptor: PropertyDescriptor | undefined
let revokeObjectUrlDescriptor: PropertyDescriptor | undefined

describe('FaceCheckDialog', () => {
  it('超时弹窗仅保留知道了按钮及指定提示', async () => {
    wrapper = mountDialog({ ...faceCheck('PENDING'), status: 'TIMED_OUT' })
    expect(wrapper.findComponent({ name: 'ElResult' }).attributes('title')).toBe(
      '未在规定时间完成抽验，学习已强制停止，请重新签到学习',
    )
    expect(wrapper.findAll('button').map((button) => button.text())).toEqual(['知道了'])
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('acknowledged')).toHaveLength(1)
  })

  beforeEach(() => {
    mediaDevicesDescriptor = Object.getOwnPropertyDescriptor(navigator, 'mediaDevices')
    createObjectUrlDescriptor = Object.getOwnPropertyDescriptor(URL, 'createObjectURL')
    revokeObjectUrlDescriptor = Object.getOwnPropertyDescriptor(URL, 'revokeObjectURL')
    vi.clearAllMocks()
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.restoreAllMocks()
    if (mediaDevicesDescriptor) {
      Object.defineProperty(navigator, 'mediaDevices', mediaDevicesDescriptor)
    } else {
      delete (navigator as { mediaDevices?: MediaDevices }).mediaDevices
    }
    restoreUrlMethod('createObjectURL', createObjectUrlDescriptor)
    restoreUrlMethod('revokeObjectURL', revokeObjectUrlDescriptor)
  })

  it('显式授权后启用摄像头，任务结束时释放视频轨道', async () => {
    const stop = vi.fn()
    const getUserMedia = vi.fn().mockResolvedValue({
      getTracks: () => [{ stop }],
    })
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia },
    })
    wrapper = mountDialog(faceCheck('PENDING'))

    const enableButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('启用摄像头'))
    await enableButton?.trigger('click')
    await flushPromises()

    expect(getUserMedia).toHaveBeenCalledWith(
      expect.objectContaining({ audio: false, video: expect.any(Object) }),
    )
    await wrapper.setProps({ faceCheck: faceCheck('PASSED') })
    expect(stop).toHaveBeenCalled()
  })

  it('任务结束早于摄像头授权时仍释放迟到的视频轨道', async () => {
    const stop = vi.fn()
    let resolveStream!: (stream: { getTracks: () => Array<{ stop: () => void }> }) => void
    const getUserMedia = vi.fn().mockReturnValue(
      new Promise((resolve) => {
        resolveStream = resolve
      }),
    )
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia },
    })
    wrapper = mountDialog(faceCheck('PENDING'))

    await findButton(wrapper, '启用摄像头')?.trigger('click')
    await wrapper.setProps({ faceCheck: null })
    resolveStream({ getTracks: () => [{ stop }] })
    await flushPromises()

    expect(stop).toHaveBeenCalled()
  })

  it('同一张照片网络重试时复用幂等请求ID', async () => {
    const stop = vi.fn()
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [{ stop }] }),
      },
    })
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn().mockReturnValue('blob:face-check'),
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    })
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(),
    } as unknown as CanvasRenderingContext2D)
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) => {
      callback(new Blob(['face-image'], { type: 'image/jpeg' }))
    })
    apiMock.submitFaceCheck
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(faceCheck('PASSED'))
    wrapper = mountDialog(faceCheck('PENDING'))

    await findButton(wrapper, '启用摄像头')?.trigger('click')
    await flushPromises()
    await findButton(wrapper, '拍照')?.trigger('click')
    await flushPromises()
    await findButton(wrapper, '提交核验')?.trigger('click')
    await flushPromises()
    await findButton(wrapper, '提交核验')?.trigger('click')
    await flushPromises()

    expect(apiMock.submitFaceCheck).toHaveBeenCalledTimes(2)
    expect(apiMock.submitFaceCheck.mock.calls[0]?.[2]).toBe(
      apiMock.submitFaceCheck.mock.calls[1]?.[2],
    )
  })
})

/** 按按钮文案查找Element Plus桩。 */
function findButton(target: VueWrapper, text: string) {
  return target.findAll('button').find((button) => button.text().includes(text))
}

/** 恢复测试前URL静态方法，兼容jsdom未实现对象URL的环境。 */
function restoreUrlMethod(
  name: 'createObjectURL' | 'revokeObjectURL',
  descriptor?: PropertyDescriptor,
) {
  if (descriptor) {
    Object.defineProperty(URL, name, descriptor)
  } else {
    delete (URL as unknown as Record<string, unknown>)[name]
  }
}

/** 使用必要的Element Plus桩挂载抽验弹窗。 */
function mountDialog(faceCheck: FaceCheckTask) {
  return mount(FaceCheckDialog, {
    props: { faceCheck },
    global: {
      stubs: {
        ElAlert: true,
        ElButton: {
          emits: ['click'],
          template: '<button @click="$emit(\'click\')"><slot /></button>',
        },
        ElDialog: { template: '<div><slot /><slot name="footer" /></div>' },
        ElResult: true,
      },
    },
  })
}

/** 构造指定状态的人脸抽验任务。 */
function faceCheck(status: 'PENDING' | 'PASSED'): FaceCheckTask {
  return {
    taskId: '700',
    sessionId: '900',
    status,
    triggeredAt: new Date().toISOString(),
    deadlineAt: new Date(Date.now() + 60_000).toISOString(),
    attemptCount: status === 'PENDING' ? 0 : 1,
    maxAttempts: 3,
    remainingAttempts: status === 'PENDING' ? 3 : 2,
    result: status === 'PASSED' ? 'MATCHED' : null,
    failureReason: null,
    similarity: status === 'PASSED' ? 0.92 : null,
    completedAt: status === 'PASSED' ? new Date().toISOString() : null,
  }
}
