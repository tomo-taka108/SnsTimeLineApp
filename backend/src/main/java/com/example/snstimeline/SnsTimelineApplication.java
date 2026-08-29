package com.example.snstimeline;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@code annotationClass} で対象を @Mapper 付きの型に限定する。 これが無いと、スキャン範囲内の <b>すべてのインターフェース</b>
 * がMyBatisのマッパーとして 登録され、DIしたい普通のインターフェース（{@code FileStorageService} など）に対して
 * 実装クラスと重複するBeanが生まれて起動に失敗する。
 */
@SpringBootApplication
@MapperScan(basePackages = "com.example.snstimeline", annotationClass = Mapper.class)
public class SnsTimelineApplication {

  public static void main(String[] args) {
    SpringApplication.run(SnsTimelineApplication.class, args);
  }
}
