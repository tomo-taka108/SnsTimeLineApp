package com.example.snstimeline.common;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * サロゲートペア（絵文字）を1文字として数える文字数制約。
 *
 * <p>Bean Validation の {@code @Size} は {@code String.length()}（UTF-16単位）で数えるため、
 * 絵文字が2文字になってしまう。PostgreSQL の {@code char_length} はコードポイント数なので、 {@code @Size}
 * を使うとアプリとDBで数え方が食い違う（docs/05_api_design.md 8章）。
 */
@Documented
@Constraint(validatedBy = CodePointLengthValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface CodePointLength {

  int min() default 0;

  int max() default Integer.MAX_VALUE;

  String message() default "文字数が範囲外です";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
