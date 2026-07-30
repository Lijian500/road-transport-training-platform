package me.lj.train.face.adapter.config;

import me.lj.train.face.adapter.FaceVerifier;
import me.lj.train.face.adapter.opencv.OpenCvFaceVerifier;
import org.opencv.objdetect.FaceDetectorYN;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OpenCV人脸核验自动配置。
 */
@AutoConfiguration
@ConditionalOnClass(FaceDetectorYN.class)
@EnableConfigurationProperties(FaceVerifierProperties.class)
public class FaceAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FaceVerifier.class)
    @ConditionalOnProperty(prefix = "train.face", name = "enabled", havingValue = "true")
    public FaceVerifier faceVerifier(FaceVerifierProperties properties) {
        return new OpenCvFaceVerifier(properties.toConfig());
    }
}
