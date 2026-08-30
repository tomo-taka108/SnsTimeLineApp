package com.example.snstimeline.config;

import com.example.snstimeline.common.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger UI）の設定。
 *
 * <p>ここで定義した内容は {@code /v3/api-docs} のJSONに反映され、ローカルの {@code /swagger-ui.html} と、GitHub Pages
 * で公開している静的な仕様書（{@code docs/api/}）の 両方で同じものが表示される。
 *
 * <p>公開版の仕様書は閲覧専用である。Swagger UI の「Try it out」はリクエスト先が {@code localhost:8080}
 * になるため、実際にAPIを叩いて試すのはローカル起動時のみ。
 */
@Configuration
public class OpenApiConfig {

  /** Authorize ダイアログと各APIの鍵アイコンを紐づける識別子。実際のヘッダ名ではない。 */
  private static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI snsTimelineOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SNS Timeline API")
                .version("v1")
                .description(
                    """
                    X/Twitter風SNSアプリ（学習目的）のREST API。

                    ## 使い方（ローカル起動時）

                    1. `POST /api/v1/auth/signup` または `POST /api/v1/auth/login` を実行する
                    2. レスポンスの `accessToken` をコピーする
                    3. 画面右上の **Authorize** に貼り付ける
                    4. 以降のリクエストに `Authorization: Bearer ...` が自動で付与される

                    アクセストークンの有効期限は15分。切れたら `POST /api/v1/auth/refresh` で再発行する。

                    ## 設計の背景

                    本書は「APIが今どういう形か」を実装から自動生成したもの。
                    「なぜこの形にしたか」（カーソルページネーションを選んだ理由、
                    401と403の使い分けなど）は `docs/05_api_design.md` に記載している。
                    """))
        // 全エンドポイントに既定で Bearer 認証を要求する。認証不要な
        // signup/login/refresh/ファイル取得は、各メソッド側で @SecurityRequirements を
        // 空指定して打ち消している
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "`POST /api/v1/auth/login` で取得したアクセストークンを入力する"
                                + "（`Bearer ` の接頭辞は不要）。")));
  }

  /**
   * エラーレスポンス（4xx / 5xx）のスキーマを {@link ErrorResponse} に差し替える。
   *
   * <p><b>これが無いと、404 のレスポンス例に成功時の型（{@code PostSummary} など）が表示されてしまう。</b> springdoc
   * はメソッドの戻り値の型を全ステータスに流用するため、{@code @ApiResponse} に 説明文だけを書いても型は直らない。
   *
   * <p>各メソッドに {@code @ApiResponse(content = ...)} を書けば個別に直せるが、 29エンドポイントすべてに同じ記述が必要になる。エラー形式は
   * {@code GlobalExceptionHandler} が 全経路で統一している（docs/05_api_design.md 1.3）ので、ここで一括指定する。
   *
   * <p>あわせて、全エンドポイント共通の 401（認証が必要なもののみ）と 500 を補う。 これらは個々のメソッドに書いていないが、実際には起こりうる。
   */
  @Bean
  public OpenApiCustomizer errorResponseCustomizer() {
    return openApi -> {
      // ErrorResponse を components に登録し、$ref で参照できるようにする
      ModelConverters.getInstance()
          .readAll(ErrorResponse.class)
          .forEach(openApi.getComponents()::addSchemas);

      Content errorContent =
          new Content()
              .addMediaType(
                  org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                  new MediaType()
                      .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));

      openApi
          .getPaths()
          .values()
          .forEach(
              pathItem ->
                  pathItem
                      .readOperations()
                      .forEach(
                          operation -> {
                            var responses = operation.getResponses();

                            // 既存の 4xx/5xx はスキーマだけ差し替える（説明文は各メソッドのものを活かす）
                            responses.forEach(
                                (status, response) -> {
                                  if (isError(status)) {
                                    response.setContent(errorContent);
                                  }
                                });

                            // 認証が必要なエンドポイントには 401 を補う。
                            // security が空リスト（@SecurityRequirements）のものは認証不要なので付けない
                            boolean requiresAuth =
                                operation.getSecurity() == null
                                    || !operation.getSecurity().isEmpty();
                            if (requiresAuth && !responses.containsKey("401")) {
                              responses.addApiResponse(
                                  "401",
                                  new ApiResponse()
                                      .description("トークンが未指定・無効・期限切れ（UNAUTHENTICATED）")
                                      .content(errorContent));
                            }

                            if (!responses.containsKey("500")) {
                              responses.addApiResponse(
                                  "500",
                                  new ApiResponse()
                                      .description("サーバー内部エラー（INTERNAL_ERROR）")
                                      .content(errorContent));
                            }
                          }));
    };
  }

  private static boolean isError(String statusCode) {
    return statusCode.startsWith("4") || statusCode.startsWith("5");
  }
}
