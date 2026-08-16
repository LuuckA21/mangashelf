package me.luucka.mangashelf;

import me.luucka.mangashelf.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
// Serialising Page directly exposes Spring Data's internal shape, whose
// stability across versions is not guaranteed. VIA_DTO produces a documented
// wrapper instead, with "content" and a "page" object.
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class MangaShelfApplication {

    public static void main(String[] args) {
        SpringApplication.run(MangaShelfApplication.class, args);
    }
}
