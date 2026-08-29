import { request } from "./client";
import type { UploadFileResponse } from "./types";

/** 許可する画像形式（docs/06_non_functional.md 3.5）。サーバー側でも同じ検証を行う */
export const ALLOWED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;

/** 上限5MB。サーバー側の app.upload.max-size-mb と合わせる */
export const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

/**
 * #25 画像アップロード（F-IM-01）。
 *
 * 送信前にクライアント側でも形式とサイズを確認する（SC-03 の MD-01 の挙動）。
 * ここで弾けば無駄な通信をせずに済むが、<b>これは利便性のためであって検証ではない</b>。
 * 実際の検証はサーバー側が行う（クライアントの検証は迂回できるため）。
 */
export async function uploadFile(file: File): Promise<UploadFileResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return request<UploadFileResponse>("/files", { method: "POST", formData });
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";

/** 配信URL（#26）。認証不要なので <img src> にそのまま渡せる */
export function fileUrl(fileId: number): string {
  return `${API_BASE_URL}/files/${fileId}`;
}

/**
 * サーバーが返す画像パス（例: `avatarUrl`, `PostImageSummary.url`）を絶対URLにする。
 *
 * バックエンドはオリジンを含まない `/api/v1/files/{id}` 形式のパスを返す
 * （LOCAL/S3の切り替えに影響されないようにするため）。フロントとバックエンドは
 * 別オリジン（:5173 / :8080）のため、そのまま `<img src>` に渡すとフロント自身への
 * リクエストになり失敗する。null はそのまま null を返す。
 */
export function resolveFileUrl(path: string | null): string | null {
  if (path === null) return null;
  return `${new URL(API_BASE_URL).origin}${path}`;
}
