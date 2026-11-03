package me.luucka.mangashelf.catalog;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;

/** Re-encodes uploaded JPEG/PNG covers as a bounded JPEG, which drops EXIF and GPS data. */
final class CoverImages {

  private static final int MAX_W = 480;
  private static final int MAX_H = 720;
  private static final long MAX_PIXELS = 100_000_000L;
  private static final int MAX_SIDE = 20_000;
  private static final Color PAPER = new Color(0xf1efe8);

  private CoverImages() {}

  static byte[] toJpeg(byte[] input) {
    try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      var readers = in == null ? null : ImageIO.getImageReaders(in);
      if (readers == null || !readers.hasNext()) {
        throw new InvalidImageException("Immagine non valida");
      }
      var reader = readers.next();
      try {
        if (!Set.of("jpeg", "png").contains(reader.getFormatName().toLowerCase())) {
          throw new InvalidImageException("Sono ammessi solo JPEG e PNG");
        }
        reader.setInput(in, true, true);
        return encode(decode(reader), reader.getWidth(0), reader.getHeight(0));
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof InvalidImageException invalid) {
        throw invalid;
      }
      throw new InvalidImageException("Immagine non valida");
    }
  }

  /**
   * Size caps are checked before decoding; subsampling by the larger ratio keeps the raster small.
   */
  private static BufferedImage decode(ImageReader reader) throws IOException {
    int w = reader.getWidth(0);
    int h = reader.getHeight(0);
    if (w > MAX_SIDE || h > MAX_SIDE || (long) w * h > MAX_PIXELS) {
      throw new InvalidImageException("Immagine troppo grande");
    }
    int s = Math.max(1, (int) Math.floor(Math.max(w / (2.0 * MAX_W), h / (2.0 * MAX_H))));
    var param = reader.getDefaultReadParam();
    param.setSourceSubsampling(s, s, 0, 0);
    return reader.read(0, param);
  }

  private static byte[] encode(BufferedImage src, int w, int h) throws IOException {
    double scale = Math.min(1.0, Math.min((double) MAX_W / w, (double) MAX_H / h));
    int tw = Math.max(1, (int) Math.round(w * scale));
    int th = Math.max(1, (int) Math.round(h * scale));
    var out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
    var g = out.createGraphics();
    try {
      g.setColor(PAPER);
      g.fillRect(0, 0, tw, th);
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.drawImage(src, 0, 0, tw, th, null);
    } finally {
      g.dispose();
    }
    var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (var bytes = new ByteArrayOutputStream();
        var ios = ImageIO.createImageOutputStream(bytes)) {
      var param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.85f);
      writer.setOutput(ios);
      writer.write(null, new IIOImage(out, null, null), param);
      ios.flush();
      return bytes.toByteArray();
    } finally {
      writer.dispose();
    }
  }
}
