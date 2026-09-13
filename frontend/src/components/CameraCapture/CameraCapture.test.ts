import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CameraCapture from './CameraCapture.vue'

let wrapper: VueWrapper | undefined
const mediaDescriptor = Object.getOwnPropertyDescriptor(navigator, 'mediaDevices')

/** 注入可控摄像头设备并挂载采集组件。 */
function setup(getUserMedia: ReturnType<typeof vi.fn>) {
  Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia } })
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
  vi.spyOn(HTMLVideoElement.prototype, 'videoWidth', 'get').mockReturnValue(640)
  vi.spyOn(HTMLVideoElement.prototype, 'videoHeight', 'get').mockReturnValue(480)
  wrapper = mount(CameraCapture, {
    global: {
      stubs: {
        ElButton: { template: '<button><slot /></button>' },
        ElAlert: { props: ['title'], template: '<p>{{ title }}</p>' },
      },
    },
  })
  return wrapper
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  vi.restoreAllMocks()
  if (mediaDescriptor) Object.defineProperty(navigator, 'mediaDevices', mediaDescriptor)
  else delete (navigator as { mediaDevices?: MediaDevices }).mediaDevices
})

describe('登记照摄像头采集', () => {
  it('仅点击后请求视频，拍照输出JPEG文件并释放设备', async () => {
    const stop = vi.fn()
    const getUserMedia = vi.fn().mockResolvedValue({ getTracks: () => [{ stop }] })
    const view = setup(getUserMedia)
    const drawImage = vi.fn()
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage,
    } as unknown as CanvasRenderingContext2D)
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) =>
      callback(new Blob(['photo'], { type: 'image/jpeg' })),
    )
    expect(getUserMedia).not.toHaveBeenCalled()
    await view.get('button').trigger('click')
    await flushPromises()
    expect(getUserMedia).toHaveBeenCalledWith(expect.objectContaining({ audio: false }))
    await view
      .findAll('button')
      .find((button) => button.text() === '拍照')!
      .trigger('click')
    expect(drawImage).toHaveBeenCalled()
    const file = view.emitted('capture')?.[0]?.[0] as File
    expect(file.type).toBe('image/jpeg')
    expect(stop).toHaveBeenCalled()
  })

  it('离开页面后仍会释放迟到的摄像头授权结果', async () => {
    const stop = vi.fn()
    let resolveMedia!: (value: unknown) => void
    const view = setup(
      vi.fn().mockReturnValue(
        new Promise((resolve) => {
          resolveMedia = resolve
        }),
      ),
    )
    await view.get('button').trigger('click')
    view.unmount()
    wrapper = undefined
    resolveMedia({ getTracks: () => [{ stop }] })
    await flushPromises()
    expect(stop).toHaveBeenCalled()
  })

  it('权限被拒绝时展示中文提示并允许重试', async () => {
    const view = setup(vi.fn().mockRejectedValue(new DOMException('denied', 'NotAllowedError')))
    await view.get('button').trigger('click')
    await flushPromises()
    expect(view.text()).toContain('摄像头权限被拒绝')
    expect(view.get('button').text()).toBe('摄像头采集')
  })
})
