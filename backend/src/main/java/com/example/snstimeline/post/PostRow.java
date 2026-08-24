package com.example.snstimeline.post;

import java.time.OffsetDateTime;

/**
 * タイムラインSQLの1行（docs/09_decision_log.md D-32）。
 *
 * <p><b>ドメインモデル {@link Post} とは別物。</b> {@code posts} と {@code users} を JOIN
 * した結果を平坦に受けるための型で、投稿者の情報を含む。
 *
 * <p>MyBatis の {@code <resultMap>} / {@code <association>} でネストさせない理由:
 *
 * <ol>
 *   <li>{@code UserSummary.avatarUrl} はDBのカラムではなく {@code avatarFileId} から
 *       組み立てる導出値である。XMLで直結すると生成箇所がXMLとJavaに分裂する
 *   <li>本プロジェクトには {@code <resultMap>} がまだ1つも無い。導入すると 「どのSQLはどちら」という分岐が生まれる
 *   <li>record を {@code <constructor>} で組むと、フィールドを増やしたときに コンパイルエラーにならず黙って壊れる
 * </ol>
 *
 * <p><b>カラムの別名とフィールド名は1対1で対応させること。</b> MyBatis はカラム名の誤りを起動時に検知せず、値が黙って null になる（D-25）。
 */
public record PostRow(
    Long id,
    Long userId,
    String body,
    int likeCount,
    int commentCount,
    OffsetDateTime createdAt,
    OffsetDateTime editedAt,
    Long authorId,
    String authorUsername,
    String authorDisplayName,
    Long authorAvatarFileId) {}
