package com.example.snstimeline.common;

/**
 * 文字数上限の共有定数（docs/06_non_functional.md 5.1「マジックナンバーは定数化する」）。
 *
 * <p>DBのカラム定義（docs/04_data_model.md 2.1）と一致させること。
 */
public final class ValidationConstants {

  private ValidationConstants() {}

  public static final int EMAIL_MAX = 255;
  public static final int USERNAME_MIN = 3;
  public static final int USERNAME_MAX = 30;
  public static final int DISPLAY_NAME_MIN = 1;
  public static final int DISPLAY_NAME_MAX = 50;
  public static final int BIO_MAX = 160;
  public static final int POST_BODY_MAX = 280;
  public static final int COMMENT_BODY_MAX = 280;

  /** 1投稿あたりの添付画像数。DBは4枚まで対応するが、MVPのAPIは1枚に制限する（docs/04_data_model.md 2.6）。 */
  public static final int POST_IMAGE_COUNT_MAX = 1;

  /**
   * #20 ユーザー検索のキーワード長（docs/05_api_design.md #20）。
   *
   * <p>下限を1文字にするのは、空文字での全件取得を防ぐため（docs/04_data_model.md 6.5 要求4）。
   */
  public static final int SEARCH_QUERY_MIN = 1;

  public static final int SEARCH_QUERY_MAX = 50;

  /** #20 ユーザー検索の1ページあたり件数（docs/05_api_design.md #20）。 */
  public static final int SEARCH_SIZE_DEFAULT = 20;

  public static final int SEARCH_SIZE_MAX = 50;

  /** ユーザー名は半角英数字とアンダースコアのみ。DBの ck_users_username_format と同じ。 */
  public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]+$";

  /** パスワードは8文字以上、英字と数字を各1文字以上（docs/06_non_functional.md 3.1）。 */
  public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";
}
