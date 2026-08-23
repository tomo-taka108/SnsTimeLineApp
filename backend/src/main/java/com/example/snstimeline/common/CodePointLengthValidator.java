package com.example.snstimeline.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CodePointLengthValidator implements ConstraintValidator<CodePointLength, String> {

  private int min;
  private int max;

  @Override
  public void initialize(CodePointLength annotation) {
    this.min = annotation.min();
    this.max = annotation.max();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    // null は @NotBlank / @NotNull の責務。ここでは通す
    if (value == null) {
      return true;
    }
    // 限界: ZWJ絵文字（例 👨‍👩‍👧）は複数コードポイントのままなので、見た目の1文字にはならない。
    // 真の書記素単位で数えるには BreakIterator が必要だが、設計書の要求は
    // 「サロゲートペアを1文字として数える」なのでここまでとする。
    int length = value.codePointCount(0, value.length());
    return length >= min && length <= max;
  }
}
