package me.luucka.mangashelf.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/** Boot's own pageable properties are skipped once @EnableSpringDataWebSupport is present. */
@Configuration
class PagingConfig {

  @Bean
  PageableHandlerMethodArgumentResolverCustomizer maxPageSize(
      @Value("${spring.data.web.pageable.max-page-size:100}") int max) {
    return resolver -> resolver.setMaxPageSize(max);
  }
}
