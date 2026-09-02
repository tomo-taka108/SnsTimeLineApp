package com.example.snstimeline.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CodePointLengthValidator} の単体テスト（docs/11_test_design.md 2章、ケース #20〜#29）。
 *
 * <p><b>Bean Validation のランタイムを起動せず、バリデータを直接呼ぶ。</b> このプロジェクトには EL 実装 （expressly など）が入っておらず、{@code
 * Validation.buildDefaultValidatorFactory()} は メッセージ補間に EL を要求するため動かない。ここで確かめたいのは「コードポイントで数えているか」
 * という一点なので、実装クラスを直接叩くほうが対象も絞れる。
 *
 * <p>アノテーションを実際に付けた DTO 経由の検証は、Controller の結合テスト（Stage 2）で行う。
 */
class CodePointLengthValidatorTest {

  /**
   * {@code @CodePointLength(min=..., max=...)} を付けた状態のバリデータを作る。
   *
   * <p>アノテーションは interface なので、匿名クラスで実装して {@code initialize} に渡す。 Mockito を使わずに済ませられる。
   */
  private static CodePointLengthValidator validator(int min, int max) {
    CodePointLengthValidator validator = new CodePointLengthValidator();
    validator.initialize(
        new CodePointLength() {
          @Override
          public Class<? extends Annotation> annotationType() {
            return CodePointLength.class;
          }

          @Override
          public int min() {
            return min;
          }

          @Override
          public int max() {
            return max;
          }

          @Override
          public String message() {
            return "文字数が範囲外です";
          }

          @Override
          public Class<?>[] groups() {
            return new Class<?>[0];
          }

          @Override
          public Class<? extends jakarta.validation.Payload>[] payload() {
            @SuppressWarnings("unchecked")
            Class<? extends jakarta.validation.Payload>[] empty = new Class[0];
            return empty;
          }
        });
    return validator;
  }

  /** {@code isValid} の第2引数は使われないため null を渡す。 */
  private static boolean isValid(CodePointLengthValidator validator, String value) {
    return validator.isValid(value, null);
  }

  @Nested
  @DisplayName("境界値（min=1, max=50。display_name 相当）")
  class Boundary {

    private final CodePointLengthValidator validator = validator(1, 50);

    @Test
    @DisplayName("#20 null は通す（@NotBlank / @NotNull の責務）")
    void nullは通す() {
      assertThat(isValid(validator, null)).isTrue();
    }

    @Test
    @DisplayName("#21 空文字（0文字 = min-1）は弾く")
    void 空文字を弾く() {
      assertThat(isValid(validator, "")).isFalse();
    }

    @Test
    @DisplayName("#22 1文字（= min）は通す")
    void 下限ちょうどを通す() {
      assertThat(isValid(validator, "a")).isTrue();
    }

    @Test
    @DisplayName("#23 範囲内の文字列を通す")
    void 範囲内を通す() {
      assertThat(isValid(validator, "abc")).isTrue();
    }

    @Test
    @DisplayName("#24 50文字（= max）は通す")
    void 上限ちょうどを通す() {
      assertThat(isValid(validator, "a".repeat(50))).isTrue();
    }

    @Test
    @DisplayName("#25 51文字（= max+1）は弾く")
    void 上限超過を弾く() {
      assertThat(isValid(validator, "a".repeat(51))).isFalse();
    }
  }

  /**
   * コードポイント数え（docs/05_api_design.md 8章）。
   *
   * <p><b>ここがこのクラスの存在理由。</b> {@code @Size} は {@code String.length()}（UTF-16単位）で
   * 数えるため絵文字が2文字になる。PostgreSQL の {@code char_length} はコードポイント数なので、 {@code @Size}
   * を使うとアプリとDBで数え方が食い違う。
   */
  @Nested
  @DisplayName("コードポイント数え")
  class CodePointCounting {

    /** サロゲートペアの絵文字。UTF-16では2単位、コードポイントでは1。 */
    private static final String EMOJI = "😀";

    @Test
    @DisplayName("#26 絵文字1個を1文字と数える（String.length() なら2）")
    void 絵文字を1文字と数える() {
      // 前提の確認: この文字列は UTF-16 では2単位である
      assertThat(EMOJI.length()).isEqualTo(2);

      assertThat(isValid(validator(1, 1), EMOJI)).isTrue();
    }

    @Test
    @DisplayName("#27 絵文字50個（UTF-16では100）は max=50 を通る")
    void 絵文字50個を通す() {
      String value = EMOJI.repeat(50);
      assertThat(value.length()).isEqualTo(100);

      assertThat(isValid(validator(1, 50), value)).isTrue();
    }

    @Test
    @DisplayName("#28 絵文字51個は max=50 で弾かれる")
    void 絵文字51個を弾く() {
      assertThat(isValid(validator(1, 50), EMOJI.repeat(51))).isFalse();
    }

    /**
     * #29 <b>バグではなく、割り切った仕様を固定するテスト。</b>
     *
     * <p>ZWJ（Zero Width Joiner）で結合された絵文字は、見た目は1文字だが複数コードポイントのまま。 真の書記素単位で数えるには {@code
     * BreakIterator} が必要だが、設計書の要求は 「サロゲートペアを1文字として数える」なのでここまでとしている（実装コメント）。
     *
     * <p>この判断をテストに書いておかないと、<b>後から見た人がバグと誤認して直してしまう。</b>
     */
    @Test
    @DisplayName("#29 ZWJ絵文字は5コードポイント扱い（1にはならない。意図的な割り切り）")
    void ZWJ絵文字は複数コードポイントのまま() {
      // 👨(1) + ZWJ + 👩(1) + ZWJ + 👧(1) = 絵文字3 + ZWJ2 = 5コードポイント
      String family = "👨‍👩‍👧";
      assertThat(family.codePointCount(0, family.length())).isEqualTo(5);

      // 見た目は1文字だが、max=1 では通らない
      assertThat(isValid(validator(1, 1), family)).isFalse();
      // 5コードポイントなので max=5 で通る
      assertThat(isValid(validator(1, 5), family)).isTrue();
    }
  }
}
