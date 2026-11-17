package com.course.demo.memory.embeddings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CosineSimilarityResponse {
    private String text1;
    private String text2;
    private double cosineSimilarity;
}
