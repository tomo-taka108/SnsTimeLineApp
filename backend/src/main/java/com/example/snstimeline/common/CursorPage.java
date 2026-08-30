package com.example.snstimeline.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * カーソルページネーションの共通レスポンス（docs/05_api_design.md 4章 CursorPage）。
 *
 * <p>タイムラインのように新着が絶えず挿入される一覧では、オフセット方式だと ページ境界で重複・欠落が起きる。カーソル方式は「この投稿より古いものを20件」という
 * 指定になるため、間に何件挿入されても影響を受けない（docs/09_decision_log.md D-06）。
 *
 * <p><b>{@code nextCursor} が null でもキー自体を出す必要があるため、 このrecordに {@code @JsonInclude(NON_NULL)}
 * を付けてはいけない。</b>
 */
@Schema(description = "カーソルページネーションのレスポンス。タイムラインやコメント一覧など、新着が挿入され続ける一覧で使う")
public record CursorPage<T>(
    @Schema(description = "取得した要素") List<T> items,
    @Schema(
            description = "次ページを取得するためのカーソル。**最終ページでは null**。中身を解釈せず、そのまま次のリクエストに渡すこと",
            nullable = true)
        String nextCursor,
    @Schema(description = "次ページが存在するか。false なら終端") boolean hasNext) {

  /**
   * 次ページがある場合。
   *
   * @param items 返す要素（21件目は呼び出し側で除いておくこと）
   * @param nextCursor 次ページの起点
   */
  public static <T> CursorPage<T> hasNext(List<T> items, String nextCursor) {
    return new CursorPage<>(items, nextCursor, true);
  }

  /** 次ページが無い場合。{@code nextCursor} は null になる（docs/05_api_design.md 4章）。 */
  public static <T> CursorPage<T> last(List<T> items) {
    return new CursorPage<>(items, null, false);
  }
}
