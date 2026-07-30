package me.lj.train.face.adapter.model;

/**
 * 人脸比对业务状态。
 */
public enum FaceComparisonStatus {

    MATCH("两张图片为同一人"),
    NOT_MATCH("两张图片不是同一人"),
    REFERENCE_NO_FACE("登记照中未检测到人脸"),
    REFERENCE_MULTIPLE_FACES("登记照中检测到多张人脸"),
    CANDIDATE_NO_FACE("待核验照片中未检测到人脸"),
    CANDIDATE_MULTIPLE_FACES("待核验照片中检测到多张人脸");

    private final String message;

    FaceComparisonStatus(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
