package me.luucka.mangashelf.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CoverImagesTest {

  private static byte[] encode(int w, int h, int type, String format) throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(w, h, type), format, out);
    return out.toByteArray();
  }

  private static BufferedImage decode(byte[] jpeg) throws Exception {
    return ImageIO.read(new ByteArrayInputStream(jpeg));
  }

  @Test
  void shrinksLargeImageKeepingRatio() throws Exception {
    var img = decode(CoverImages.toJpeg(encode(1200, 1800, BufferedImage.TYPE_INT_RGB, "png")));
    assertThat(img.getWidth()).isEqualTo(480);
    assertThat(img.getHeight()).isEqualTo(720);
  }

  @Test
  void doesNotUpscale() throws Exception {
    var img = decode(CoverImages.toJpeg(encode(100, 150, BufferedImage.TYPE_INT_RGB, "png")));
    assertThat(img.getWidth()).isEqualTo(100);
    assertThat(img.getHeight()).isEqualTo(150);
  }

  @Test
  void transparentPngBecomesPaper() throws Exception {
    var img = decode(CoverImages.toJpeg(encode(50, 50, BufferedImage.TYPE_INT_ARGB, "png")));
    int rgb = img.getRGB(0, 0);
    assertThat((rgb >> 16) & 0xff).isBetween(0xf1 - 3, 0xf1 + 3);
    assertThat((rgb >> 8) & 0xff).isBetween(0xef - 3, 0xef + 3);
    assertThat(rgb & 0xff).isBetween(0xe8 - 3, 0xe8 + 3);
  }

  @Test
  void stripsExif() throws Exception {
    var jpeg = encode(20, 20, BufferedImage.TYPE_INT_RGB, "jpg");
    var exif = "Exif\0\0GPS-SECRET".getBytes(StandardCharsets.ISO_8859_1);
    var out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2); // SOI
    out.write(0xff);
    out.write(0xe1); // APP1
    out.write((exif.length + 2) >> 8);
    out.write((exif.length + 2) & 0xff);
    out.write(exif);
    out.write(jpeg, 2, jpeg.length - 2);
    var input = out.toByteArray();
    assertThat(new String(input, StandardCharsets.ISO_8859_1)).contains("Exif\0\0");

    var result = CoverImages.toJpeg(input);

    assertThat(new String(result, StandardCharsets.ISO_8859_1)).doesNotContain("Exif\0\0");
  }

  @Test
  void rejectsNonImage() throws Exception {
    assertThatThrownBy(() -> CoverImages.toJpeg("hello".getBytes()))
        .isInstanceOf(InvalidImageException.class);
    var gif = encode(10, 10, BufferedImage.TYPE_INT_RGB, "gif");
    assertThatThrownBy(() -> CoverImages.toJpeg(gif)).isInstanceOf(InvalidImageException.class);
  }

  @Test
  void rejectsDecompressionBomb() throws Exception {
    var png = encode(1, 1, BufferedImage.TYPE_INT_RGB, "png");
    // IHDR data starts at byte 16: width, height; its CRC covers type + data (bytes 12..28).
    var buf = ByteBuffer.wrap(png);
    buf.putInt(16, 20000);
    buf.putInt(20, 20000);
    var crc = new CRC32();
    crc.update(png, 12, 17);
    buf.putInt(29, (int) crc.getValue());

    assertThatThrownBy(() -> CoverImages.toJpeg(png)).isInstanceOf(InvalidImageException.class);
  }
}
