package com.example.snstimeline.post;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.ErrorCode;

/** タイムラインのタブ（docs/03_screen_design.md SC-03、docs/05_api_design.md #5）。 */
public enum TimelineTab {
  /** すべて。全ユーザーの投稿（F-TL-01）。 */
  ALL,
  /**
   * フォロー中。<b>自分＋</b>フォロー中ユーザーの投稿（F-TL-02、D-11）。
   *
   * <p>自分の投稿を含めるのは、投稿直後にこのタブを見て「消えた」と 感じさせないため。
   */
  FOLLOWING;

  /**
   * クエリパラメータの値から変換する。
   *
   * <p>Springの標準変換は大文字の定数名を要求するが、APIの契約は小文字（{@code all} / {@code following}）なので自前で変換する。
   *
   * @throws ApiException 未知の値の場合（VALIDATION_ERROR → 400）
   */
  public static TimelineTab from(String value) {
    if ("all".equals(value)) {
      return ALL;
    }
    if ("following".equals(value)) {
      return FOLLOWING;
    }
    throw new ApiException(ErrorCode.VALIDATION_ERROR);
  }
}
