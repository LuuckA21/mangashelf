package me.luucka.mangashelf.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import me.luucka.mangashelf.common.NotFoundException;
import me.luucka.mangashelf.config.AppProperties;
import org.springframework.stereotype.Component;

/** Cover files on disk; names are always server-generated {@code <uuid>.jpg}. */
@Component
public class CoverStorage {

  private static final Pattern NAME =
      Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg");

  private final Path dir;

  public CoverStorage(AppProperties props) {
    this.dir = props.uploadsDir().toAbsolutePath().normalize();
  }

  public String save(byte[] jpeg) {
    var name = UUID.randomUUID() + ".jpg";
    try {
      Files.createDirectories(dir);
      Files.write(resolve(name), jpeg);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return name;
  }

  public byte[] read(String fileName) {
    try {
      return Files.readAllBytes(resolve(fileName));
    } catch (NoSuchFileException e) {
      throw new NotFoundException("Copertina non trovata");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Missing files are ignored: cleanup must never fail the request. */
  public void delete(String fileName) {
    try {
      Files.deleteIfExists(resolve(fileName));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Path resolve(String fileName) {
    var path = dir.resolve(fileName).normalize();
    if (!NAME.matcher(fileName).matches() || !path.startsWith(dir)) {
      throw new IllegalArgumentException("Invalid cover file name");
    }
    return path;
  }
}
