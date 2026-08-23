import { Link } from "react-router-dom";

/** SC-12 NotFound（docs/03_screen_design.md）。未定義のURLに来たとき */
export function NotFoundPage() {
  return (
    <div className="page-center">
      <h1>お探しのページは見つかりませんでした</h1>
      <Link className="btn btn-outline" to="/">
        タイムラインへ戻る
      </Link>
    </div>
  );
}
