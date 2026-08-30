package com.example.snstimeline.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** バリデーションエラーの1フィールド分（docs/05_api_design.md 1.3 の errors[]）。 */
@Schema(description = "バリデーションエラーの1フィールド分。画面ではこの `field` の直下にメッセージを表示する")
public record FieldErrorItem(
    @Schema(description = "エラーが起きたフィールド名", example = "body") String field,
    @Schema(description = "そのフィールドのエラーメッセージ", example = "本文は280文字以内で入力してください") String message) {}
