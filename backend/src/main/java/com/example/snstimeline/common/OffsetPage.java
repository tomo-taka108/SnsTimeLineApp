package com.example.snstimeline.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * オフセットページネーションの共通レスポンス（docs/05_api_design.md 4章 OffsetPage）。
 *
 * <p><b>本アプリで唯一これを使うのは #20 ユーザー検索だけ</b>（docs/05_api_design.md 2.2）。 タイムラインやコメントのように新着が絶えず挿入される一覧では
 * {@link CursorPage} を使う。 挿入で順序がずれると、オフセット方式はページ境界で重複・欠落を起こすため。
 *
 * <p>検索結果は並びが安定しており「3ページ目に飛ぶ」操作が自然なため、こちらを採る。 <b>ページネーション方式は機能ごとに選ぶものであり、アプリ全体で統一する必要はない。</b>
 *
 * <p><b>{@code totalPages} が 0 でもキー自体を出す必要があるため、このrecordに {@code @JsonInclude(NON_NULL)}
 * を付けてはいけない</b>（{@link CursorPage} と同じ）。
 */
@Schema(description = "オフセットページネーションのレスポンス。**ユーザー検索でのみ使う**（ページ番号を直接指定したいため）")
public record OffsetPage<T>(
    @Schema(description = "取得した要素") List<T> items,
    @Schema(description = "現在のページ番号（0始まり）", example = "0") int page,
    @Schema(description = "1ページあたりの件数", example = "20") int size,
    @Schema(description = "検索条件に一致した総件数", example = "42") long totalElements,
    @Schema(description = "総ページ数。一致0件なら0", example = "3") int totalPages) {

  /**
   * {@code totalPages} を件数から算出して組み立てる。
   *
   * @param page 0始まりのページ番号
   * @param size 1ページあたりの件数（0以下は渡されない前提。呼び出し側でバリデーション済み）
   * @param totalElements 検索条件に一致した総件数
   */
  public static <T> OffsetPage<T> of(List<T> items, int page, int size, long totalElements) {
    // 切り上げ除算。totalElements が 0 なら totalPages も 0 になる
    int totalPages = (int) ((totalElements + size - 1) / size);
    return new OffsetPage<>(items, page, size, totalElements, totalPages);
  }

  /** 一致0件。総件数の問い合わせだけで結果が確定した場合に使う。 */
  public static <T> OffsetPage<T> empty(int page, int size) {
    return new OffsetPage<>(List.of(), page, size, 0L, 0);
  }
}
