package com.example.snstimeline.post.dto;

/**
 * #29 GET /timeline/new-count のレスポンス。
 *
 * <p>本エンドポイントは要件定義時の設計書（#1〜#28）には存在しない。SC-03 の新着通知バナー （モックアップ・設計書のいずれにも無い今回追加の要望）のために新設した
 * （docs/09_decision_log.md D-31）。
 */
public record NewCountResponse(long count) {}
