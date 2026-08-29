package com.example.snstimeline.file;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code stored_files} テーブルへのアクセス（docs/09_decision_log.md D-25）。 */
@Mapper
public interface FileMapper {

  /** #25 アップロード。採番されたIDを返す（record は書き戻せないため RETURNING で受け取る）。 */
  Long insert(StoredFile file);

  /** #26 配信、および投稿添付時の所有者チェックに使う。 */
  Optional<StoredFile> findById(@Param("fileId") Long fileId);
}
