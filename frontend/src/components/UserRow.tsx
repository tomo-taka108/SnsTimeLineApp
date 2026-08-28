import { Link } from "react-router-dom";
import type { UserListItem } from "../api/types";
import { Avatar } from "./Avatar";
import { FollowButton } from "./FollowButton";

type Props = {
  user: UserListItem;
  onChange: (updated: UserListItem) => void;
};

/**
 * ユーザー行（SC-07 / SC-08 / SC-09 / SC-10 共通コンポーネント、mockup/mock.js userRowHtml）。
 *
 * 今回はSC-08 / SC-09（フォロー中一覧・フォロワー一覧）でのみ使用する。
 * 自分自身の行にはフォローボタンを出さない（docs/03_screen_design.md SC-08/09）。
 */
export function UserRow({ user, onChange }: Props) {
  return (
    <div className="user-row">
      <Link to={`/users/${user.id}`}>
        <Avatar user={user} />
      </Link>
      <div className="user-main">
        <Link className="user-name" to={`/users/${user.id}`}>
          {user.displayName}
        </Link>
        <span className="user-handle">@{user.username}</span>
        <div className="user-bio">{user.bio}</div>
      </div>
      {!user.isMe && <FollowButton user={user} onChange={onChange} size="sm" />}
    </div>
  );
}
