package com.example.snstimeline.common;

/** バリデーションエラーの1フィールド分（docs/05_api_design.md 1.3 の errors[]）。 */
public record FieldErrorItem(String field, String message) {}
