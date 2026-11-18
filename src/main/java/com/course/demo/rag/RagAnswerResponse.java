package com.course.demo.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagAnswerResponse {

    private String question;
    private String answer;
    private List<Citation> citations;
}
