# train-face-adapter

本模块使用开源 OpenCV 完成本地人脸检测和一对一核验，不上传图片到第三方：

- YuNet：检测图片中是否有人脸、是否有多张人脸，并返回位置与置信度；
- SFace：对两张单人脸图片进行五点对齐和特征比对，返回是否同一人及余弦相似度；
- Spring Boot 自动配置默认关闭，只有显式设置 `FACE_ENABLED=true` 才加载约 37MB 的模型。

OpenCV Zoo 的 YuNet 目录使用 MIT License，SFace 目录使用 Apache-2.0 License。模型来源和许可说明：

- <https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet>
- <https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface>

## 1. 准备模型

模型权重不提交到Git。请在仓库根目录执行：

```powershell
.\scripts\download-face-models.ps1
```

脚本固定使用官方模型地址并校验SHA-256，执行结束后会输出两个模型的绝对路径。将路径填写到本机环境变量：

```text
FACE_ENABLED=true
FACE_DETECTION_MODEL_PATH=<绝对路径>\face_detection_yunet_2023mar.onnx
FACE_RECOGNITION_MODEL_PATH=<绝对路径>\face_recognition_sface_2021dec.onnx
```

## 2. 接口

`FaceVerifier`提供两个方法：

```java
Result<FaceDetectionResult> detect(byte[] imageBytes);

Result<FaceComparisonResult> compare(
        byte[] referenceImageBytes,
        byte[] candidateImageBytes
);
```

检测结果包含：

- `faceCount`：人脸数量；
- `hasFace`：是否存在人脸；
- `multipleFaces`：是否包含多张人脸；
- `faces`：每张脸的位置和检测置信度；
- `elapsedMillis`：处理耗时。

比对只在两张图片都恰好检测到一张人脸时执行。否则通过
`REFERENCE_NO_FACE`、`REFERENCE_MULTIPLE_FACES`、`CANDIDATE_NO_FACE`
或`CANDIDATE_MULTIPLE_FACES`说明不可比原因。

可比对结果中的`similarity`是SFace余弦相似度原始值，默认以OpenCV官方示例的
`0.363`作为同一人阈值；`similarityPercent`只是便于展示的数值，不是身份概率。
业务上线前应使用获得授权、符合实际采集环境的数据重新评估阈值。

## 3. Spring Boot配置

`train-learning-service`已经提供以下配置项：

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `FACE_ENABLED` | `false` | 是否启用本地人脸核验 |
| `FACE_DETECTION_MODEL_PATH` | 见`application.yml` | YuNet模型路径 |
| `FACE_RECOGNITION_MODEL_PATH` | 见`application.yml` | SFace模型路径 |
| `FACE_DETECTION_SCORE_THRESHOLD` | `0.9` | 人脸检测置信度阈值 |
| `FACE_NMS_THRESHOLD` | `0.3` | 检测框NMS阈值 |
| `FACE_TOP_K` | `5000` | 最大候选检测框数 |
| `FACE_SIMILARITY_THRESHOLD` | `0.363` | 同一人余弦相似度阈值 |
| `FACE_MAX_IMAGE_BYTES` | `10485760` | 单张图片最大字节数 |
| `FACE_MAX_IMAGE_PIXELS` | `25000000` | 解码后最大像素数 |

启用后，Spring容器中会自动注册一个`FaceVerifier`。OpenCV模型对象由内部锁保护，
同一实例会串行执行模型推理；如果后续并发量较大，应通过实例池或独立推理服务扩容。

## 4. 使用边界

- 当前实现是1:1身份核验，不是从人员库中进行1:N检索；
- 当前实现不包含活体检测，照片、屏幕翻拍等攻击需要后续单独防护；
- 不要在日志中输出图片字节、Base64、人脸特征或可识别身份信息；
- 人脸信息属于敏感个人信息，采集、存储和使用前需取得合法授权，并设置最小留存期限。
