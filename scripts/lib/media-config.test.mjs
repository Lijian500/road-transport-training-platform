import assert from 'node:assert/strict'
import { mkdtempSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { mediaStudentKeys, requireMediaConfiguration, requireMediaState } from './media-config.mjs'

test('缺配置时明确失败且不输出密钥内容', () => {
  assert.throws(() => requireMediaConfiguration({ OSS_ACCESS_KEY_SECRET: 'secret-for-unit-test' }), (error) => {
    assert.match(error.message, /OSS_BUCKET/)
    assert.match(error.message, /TRAIN_FACE_SAME_PERSON/)
    assert.doesNotMatch(error.message, /secret-for-unit-test/)
    return true
  })
})

test('拒绝同一张照片改名充当第二张同人照片，接受不同内容的本地素材声明', () => {
  const directory = mkdtempSync(join(tmpdir(), 'train-media-config-'))
  const files = ['demo.mp4', 'detect.onnx', 'recognize.onnx', 'reference.jpg', 'same.jpg', 'different.jpg', 'multiple.jpg']
  try {
    for (const file of files) writeFileSync(join(directory, file), 'unit-fixture')
    const env = { OSS_ENABLED: 'true', FACE_ENABLED: 'true', OSS_BUCKET: 'unit-test',
      OSS_ACCESS_KEY_ID: 'unit-key', OSS_ACCESS_KEY_SECRET: 'unit-secret', TRAIN_DEMO_VIDEO_SECONDS: '60',
      TRAIN_DEMO_VIDEO: join(directory, 'demo.mp4'), FACE_DETECTION_MODEL_PATH: join(directory, 'detect.onnx'),
      FACE_RECOGNITION_MODEL_PATH: join(directory, 'recognize.onnx'), TRAIN_FACE_REFERENCE: join(directory, 'reference.jpg'),
      TRAIN_FACE_SAME_PERSON: join(directory, 'same.jpg'), TRAIN_FACE_DIFFERENT_PERSON: join(directory, 'different.jpg'),
      TRAIN_FACE_MULTIPLE: join(directory, 'multiple.jpg') }
    assert.throws(() => requireMediaConfiguration(env), /不能复制登记照/)
    writeFileSync(env.TRAIN_FACE_SAME_PERSON, 'another-unit-fixture')
    assert.equal(requireMediaConfiguration(env).seconds, 60)
  } finally {
    for (const file of files) unlinkSync(join(directory, file))
    rmdirSync(directory)
  }
})

test('业务场景及1、5、10并发组账号互不重复', () => {
  const keys = mediaStudentKeys()
  assert.equal(new Set(keys).size, keys.length)
  assert.equal(keys.filter((key) => key.startsWith('load_')).length, 16)
  assert.ok(keys.includes('face_timeout'))
})

test('已播种但尚未发布的空计划必须在浏览器启动前失败', () => {
  assert.throws(() => requireMediaState({ media: { students: {}, plans: {} } }), /计划 face_pass/)
  assert.throws(() => requireMediaState({ media: { students: {}, plans: {} } }, { load: true }), /学员 load_10_10/)
})
