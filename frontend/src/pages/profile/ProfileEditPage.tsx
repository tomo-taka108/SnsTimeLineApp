import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { useNavigate } from "react-router-dom";
import { ALLOWED_IMAGE_TYPES, MAX_IMAGE_SIZE_BYTES, uploadFile } from "../../api/files";
import { fetchProfile, updateProfile } from "../../api/users";
import type { UserProfile } from "../../api/types";
import { AppHeader } from "../../components/AppHeader";
import { Avatar } from "../../components/Avatar";
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
 */
export function ProfileEditPage() {
  const navigate = useNavigate();
  const { user, updateUser } = useAuth();
  const { showToast } = useToast();

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [displayName, setDisplayName] = useState("");
  const [bio, setBio] = useState("");
  // undefined = 未変更（保存時にキー自体を送らない）。null = 削除。number = 新しい画像
  const [avatarFileId, setAvatarFileId] = useState<number | null | undefined>(undefined);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | null>(null);
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
  const [original, setOriginal] = useState({ displayName: "", bio: "", avatarUrl: null as string | null });
  const [isSaving, setIsSaving] = useState(false);
  const avatarInputRef = useRef<HTMLInputElement>(null);

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
        setAvatarFileId(undefined);
        setAvatarPreviewUrl(result.avatarUrl);
        setOriginal({ displayName: result.displayName, bio: result.bio ?? "", avatarUrl: result.avatarUrl });
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

  const avatarChanged = avatarFileId !== undefined;

  // 未保存の変更がある状態での離脱に確認ダイアログを出す（docs/03_screen_design.md SC-06）
  useEffect(() => {
    const changed = displayName !== original.displayName || bio !== original.bio || avatarChanged;
    if (!changed) return;

    function handleBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault();
      // Chrome は returnValue の設定が必須（値の中身はブラウザ側の定型文になり無視される）
      event.returnValue = "";
    }
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [displayName, bio, avatarChanged, original]);

  const displayNameError = validateProfileDisplayName(displayName);
  const bioError = validateBio(bio);
  const bioLength = countChars(bio);
  const bioCounterClass = bioLength > 160 ? "char-counter is-over" : bioLength > 140 ? "char-counter is-warn" : "char-counter";

  const changed = displayName !== original.displayName || bio !== original.bio || avatarChanged;
  const canSave = changed && !displayNameError && !bioError && !isSaving && !isUploadingAvatar;

  async function handleAvatarSelect(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    // クライアント側の検証。実際の検証はサーバー側が行う
    if (!ALLOWED_IMAGE_TYPES.includes(file.type as (typeof ALLOWED_IMAGE_TYPES)[number])) {
      showToast("対応していないファイル形式です（JPEG / PNG / WebP のみ）", true);
      return;
    }
    if (file.size > MAX_IMAGE_SIZE_BYTES) {
      showToast("ファイルサイズが大きすぎます（5MBまで）", true);
      return;
    }

    setIsUploadingAvatar(true);
    try {
      const uploaded = await uploadFile(file);
      setAvatarFileId(uploaded.fileId);
      setAvatarPreviewUrl(uploaded.url);
    } catch {
      showToast("画像のアップロードに失敗しました", true);
    } finally {
      setIsUploadingAvatar(false);
    }
  }

  function handleAvatarRemove() {
    setAvatarFileId(null);
    setAvatarPreviewUrl(null);
  }

  async function handleSave() {
    if (!canSave) return;
    setIsSaving(true);
    try {
      const payload: { displayName?: string; bio?: string | null; avatarFileId?: number | null } = {};
      if (displayName !== original.displayName) {
        payload.displayName = displayName;
      }
      if (bio !== original.bio) {
        payload.bio = bio === "" ? null : bio;
      }
      if (avatarChanged) {
        payload.avatarFileId = avatarFileId;
      }
      const updated = await updateProfile(payload);
      if (user) {
        updateUser({ ...user, displayName: updated.displayName, avatarUrl: updated.avatarUrl });
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
              <label>プロフィール画像</label>
              <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
                <Avatar
                  user={{ id: profile.id, username: profile.username, displayName, avatarUrl: avatarPreviewUrl }}
                  size="lg"
                />
                <div>
                  <input
                    ref={avatarInputRef}
                    type="file"
                    accept={ALLOWED_IMAGE_TYPES.join(",")}
                    onChange={(event) => void handleAvatarSelect(event)}
                    hidden
                  />
                  <button
                    className="btn btn-outline btn-sm"
                    type="button"
                    disabled={isUploadingAvatar || isSaving}
                    onClick={() => avatarInputRef.current?.click()}
                  >
                    {isUploadingAvatar ? "アップロード中..." : "画像を変更"}
                  </button>
                  {avatarPreviewUrl && (
                    <button
                      className="btn btn-outline btn-sm"
                      type="button"
                      disabled={isUploadingAvatar || isSaving}
                      onClick={handleAvatarRemove}
                      style={{ marginLeft: "8px" }}
                    >
                      削除
                    </button>
                  )}
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
