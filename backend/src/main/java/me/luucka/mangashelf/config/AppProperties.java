package me.luucka.mangashelf.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Path uploadsDir, InitialAdmin initialAdmin) {

  public record InitialAdmin(String username, String password) {}
}
