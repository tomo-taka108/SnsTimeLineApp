package com.example.snstimeline;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.snstimeline")
public class SnsTimelineApplication {

  public static void main(String[] args) {
    SpringApplication.run(SnsTimelineApplication.class, args);
  }
}
