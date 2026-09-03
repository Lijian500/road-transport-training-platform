package me.lj.train.training.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/** 考试选项JSON转换。 */
@Component
class ExamJsonSupport {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<List<String>>() { };

    private final ObjectMapper objectMapper;

    ExamJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String writeOptions(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("考试选项序列化失败", exception);
        }
    }

    List<String> readOptions(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("考试选项反序列化失败", exception);
        }
    }
}
