import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchProfile, updateProfile } from "../../api/users";
import type { UserProfile } from "../../api/types";
import { AppHeader } from "../../components/AppHeader";
import { useAuth } from "../../auth/useAuth";
import { useToast } from "../../components/useToast";
import { countChars, validateBio, validateProfileDisplayName } from "../validation";

/**
 * SC-06 プロフィール編集（docs/03_screen_design.md、mockup/profile-edit.html の挙動を踏襲）。
 *
 * モックアップは自己紹介欄に textarea maxlength="200" と書いているが、同じファイル内の
 * カウンタ・ヒントは160でありDB制約（VARCHAR(160)）とも一致する。200は誤りと判断し、
 * このページも設計書どおり160で統一する（モックアップ側も修正済み）。
 *
 * メールアドレスは GET /auth/me が返さない（アカウント列挙防止）ため表示しない。
 * モックアップにはある disabled 表示だが、この画面では意図的にユーザー名のみを disabled 表示する。
 *
 * プロフィール画像欄はモックアップと同じく「（Phase2）」の非活性プレースホルダのみを置く
 * （画像アップロードAPIが未実装のため、F-US-04 は次回に見送り）。
 */
export function ProfileEditPage() {
  const navigate = useNavigate();
  const { user, updateUser } = useAuth();
  const { showToast } = useToast();

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [displayName, setDisplayName] = useState("");
  const [bio, setBio] = useState("");
  const [original, setOriginal] = useState({ displayName: "", bio: "" });
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    async function load() {
      setStatus("loading");
      try {
        const result = await fetchProfile(user!.id);
        if (cancelled) return;
        setProfile(result);
        setDisplayName(result.displayName);
        setBio(result.bio ?? "");
        setOriginal({ displayName: result.displayName, bio: result.bio ?? "" });
        setStatus("ready");
      } catch {
        if (!cancelled) {
          showToast("通信に失敗しました。時間をおいて再度お試しください", true);
          setStatus("error");
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  // 未保存の変更がある状態での離脱に確認ダイアログを出す（docs/03_screen_design.md SC-06）
  useEffect(() => {
    const changed = displayName !== original.displayName || bio !== original.bio;
    if (!changed) return;

    function handleBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault();
      // Chrome は returnValue の設定が必須（値の中身はブラウザ側の定型文になり無視される）
      event.returnValue = "";
    }
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [displayName, bio, original]);

  const displayNameError = validateProfileDisplayName(displayName);
  const bioError = validateBio(bio);
  const bioLength = countChars(bio);
  const bioCounterClass = bioLength > 160 ? "char-counter is-over" : bioLength > 140 ? "char-counter is-warn" : "char-counter";

  const changed = displayName !== original.displayName || bio !== original.bio;
  const canSave = changed && !displayNameError && !bioError && !isSaving;

  async function handleSave() {
    if (!canSave) return;
    setIsSaving(true);
    try {
      const payload: { displayName?: string; bio?: string | null } = {};
      if (displayName !== original.displayName) {
        payload.displayName = displayName;
      }
      if (bio !== original.bio) {
        payload.bio = bio === "" ? null : bio;
      }
      const updated = await updateProfile(payload);
      if (user) {
        updateUser({ ...user, displayName: updated.displayName });
      }
      showToast("プロフィールを更新しました");
      navigate(`/users/${updated.id}`, { replace: true });
    } catch {
      showToast("通信に失敗しました。時間をおいて再度お試しください", true);
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <>
      <AppHeader />
      <main className="app-main">
        <div className="back-bar">
          <button className="btn btn-outline btn-sm" type="button" onClick={() => navigate(-1)}>
            ← 戻る
          </button>
          <span className="title">プロフィールを編集</span>
          <span style={{ flex: 1 }} />
          <button
            className="btn btn-primary btn-sm"
            type="button"
            disabled={!canSave}
            onClick={() => void handleSave()}
          >
            {isSaving ? "保存中..." : "保存"}
          </button>
        </div>

        {status === "loading" && (
          <div className="skeleton-card">
            <div className="sk sk-avatar" />
            <div style={{ flex: 1 }}>
              <div className="sk sk-line sk-w-30" />
              <div className="sk sk-line sk-w-60" />
            </div>
          </div>
        )}

        {status === "error" && (
          <div className="state-error">
            <p>プロフィールの取得に失敗しました</p>
          </div>
        )}

        {status === "ready" && profile && (
          <div style={{ padding: "20px 16px" }}>
            <div className="form-field">
              <label>
                プロフィール画像
                <span style={{ color: "var(--color-text-sub)", fontSize: "13px", fontWeight: 600 }}>
                  （Phase2）
                </span>
              </label>
              <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
                <span aria-hidden="true">
                  <span className="avatar avatar-lg">{[...profile.displayName][0] ?? "?"}</span>
                </span>
                <div>
                  <button className="btn btn-outline btn-sm" type="button" disabled>
                    画像を変更
                  </button>
                  <div className="field-hint">JPEG / PNG / WebP、5MBまで。円形にトリミングされます</div>
                </div>
              </div>
            </div>

            <div className="form-field">
              <label className="required" htmlFor="displayName">
                表示名
              </label>
              <input
                className={displayNameError ? "input is-error" : "input"}
                type="text"
                id="displayName"
                value={displayName}
                maxLength={100}
                onChange={(event) => setDisplayName(event.target.value)}
              />
              {displayNameError ? (
                <div className="field-error" role="alert">
                  <span aria-hidden="true">⚠️</span>
                  <span>{displayNameError}</span>
                </div>
              ) : (
                <div className="field-hint">1〜50文字</div>
              )}
            </div>

            <div className="form-field">
              <label htmlFor="bio">自己紹介</label>
              <textarea
                className={bioError ? "textarea is-error" : "textarea"}
                id="bio"
                rows={4}
                value={bio}
                onChange={(event) => setBio(event.target.value)}
              />
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "6px" }}>
                {bioError ? (
                  <div className="field-error" role="alert">
                    <span aria-hidden="true">⚠️</span>
                    <span>{bioError}</span>
                  </div>
                ) : (
                  <span className="field-hint" style={{ margin: 0 }}>
                    改行が使えます
                  </span>
                )}
                <span className={bioCounterClass}>{bioLength}/160</span>
              </div>
            </div>

            <div className="divider-thick" style={{ margin: "24px -16px" }} />

            <div className="form-field">
              <label htmlFor="username">ユーザー名</label>
              <input className="input" type="text" id="username" value={`@${profile.username}`} disabled />
              <div className="field-hint">ユーザー名は変更できません</div>
            </div>
          </div>
        )}
      </main>
    </>
  );
}
