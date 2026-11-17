package com.course.demo.structured;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/structured-demo")
@RequiredArgsConstructor
public class StructuredDemo {

    private final ChatClient.Builder chatClientBuilder;

    @GetMapping("/course-summary")
    public CourseSummary courseSummary(@RequestParam(defaultValue = "Spring AI") String topic) {
        CourseSummary courseSummary = chatClientBuilder.build()
                .prompt()
                .user("Generate a course summary for a course on " + topic
                        + ". Include a concise title, a difficulty level (Beginner, Intermediate, or Advanced), "
                        + "a list of 3 to 5 learning objectives, and the estimated hours to complete it.")
                .call()
                .entity(CourseSummary.class);

        System.out.println(courseSummary.title());

        return courseSummary;
    }
}
