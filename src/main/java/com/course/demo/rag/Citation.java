package com.course.demo.rag;

public record Citation(int number, String title, String source, Double score, String excerpt, boolean cited) {
}
