package me.luucka.mangashelf;

import me.luucka.mangashelf.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
// Serializzare Page direttamente espone la struttura interna di Spring Data,
// che non ne garantisce la stabilita' fra versioni: VIA_DTO produce invece
// un involucro documentato, con "content" e un oggetto "page".
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class MangaShelfApplication {

    public static void main(String[] args) {
        SpringApplication.run(MangaShelfApplication.class, args);
    }
}
