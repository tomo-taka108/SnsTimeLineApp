import { Link } from "react-router-dom";

type Props = {
  /**
   * 発生元ごとの文言（docs/03_screen_design.md SC-12）。
   * 省略時は「未定義のURL」向けの既定文言を出す。
   */
  message?: string;
};

/**
 * SC-12 NotFound（docs/03_screen_design.md）。
 *
 * 未定義のURLだけでなく、投稿404・ユーザー404の遷移先としても使う。
 */
export function NotFoundPage({ message = "お探しのページは見つかりませんでした" }: Props) {
  return (
    <div className="page-center">
      <h1>{message}</h1>
      <Link className="btn btn-outline" to="/">
        タイムラインへ戻る
      </Link>
    </div>
  );
}
