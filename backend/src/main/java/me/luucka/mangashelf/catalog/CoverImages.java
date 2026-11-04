package me.luucka.mangashelf.catalog;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;

/**
 * Re-encodes uploaded JPEG/PNG covers as an upright, bounded JPEG; EXIF and GPS data are dropped.
 */
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
        int orientation = reader.getFormatName().equalsIgnoreCase("jpeg") ? orientation(input) : 1;
        var src = orient(decode(reader), orientation);
        boolean swap = orientation >= 5;
        int w = swap ? reader.getHeight(0) : reader.getWidth(0);
        int h = swap ? reader.getWidth(0) : reader.getHeight(0);
        return encode(src, w, h);
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

  /** EXIF Orientation (1-8) from the JPEG APP1 segment; 1 when absent or malformed. */
  static int orientation(byte[] jpeg) {
    try {
      var b = ByteBuffer.wrap(jpeg);
      if (b.getShort(0) != (short) 0xffd8) {
        return 1;
      }
      int pos = 2;
      while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xff) == 0xff) {
        int marker = jpeg[pos + 1] & 0xff;
        if (marker == 0xda || marker == 0xd9) {
          break; // start of scan: no more metadata
        }
        int len = b.getShort(pos + 2) & 0xffff;
        int tiff = pos + 10; // marker(2) + length(2) + "Exif\0\0"(6)
        if (marker == 0xe1 && len >= 16 && b.getInt(pos + 4) == 0x45786966) {
          b.order(b.getShort(tiff) == 0x4d4d ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
          int ifd = tiff + b.getInt(tiff + 4);
          int count = b.getShort(ifd) & 0xffff;
          for (int i = 0; i < count; i++) {
            int e = ifd + 2 + 12 * i;
            if ((b.getShort(e) & 0xffff) == 0x0112) {
              int o = b.getShort(e + 8) & 0xffff;
              return o >= 1 && o <= 8 ? o : 1;
            }
          }
          return 1;
        }
        pos += 2 + len;
      }
    } catch (RuntimeException e) {
      // Malformed EXIF (out-of-range offsets): treat as upright.
    }
    return 1;
  }

  private static BufferedImage orient(BufferedImage src, int orientation) {
    if (orientation == 1) {
      return src;
    }
    int w = src.getWidth();
    int h = src.getHeight();
    var at =
        switch (orientation) {
          case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
          case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
          case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
          case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
          case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
          case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
          default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
    boolean swap = orientation >= 5;
    var out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
    var g = out.createGraphics();
    try {
      g.drawImage(src, at, null);
    } finally {
      g.dispose();
    }
    return out;
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
