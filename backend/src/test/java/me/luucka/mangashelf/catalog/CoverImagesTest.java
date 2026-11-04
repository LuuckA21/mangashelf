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
        .isInstanceOf(InvalidImageException.class)
        .hasMessage("Immagine non valida");
    var gif = encode(10, 10, BufferedImage.TYPE_INT_RGB, "gif");
    assertThatThrownBy(() -> CoverImages.toJpeg(gif))
        .isInstanceOf(InvalidImageException.class)
        .hasMessage("Sono ammessi solo JPEG e PNG");
  }

  private static byte[] pngDeclaring(int w, int h) throws Exception {
    var png = encode(1, 1, BufferedImage.TYPE_INT_RGB, "png");
    // IHDR data starts at byte 16: width, height; its CRC covers type + data (bytes 12..28).
    var buf = ByteBuffer.wrap(png);
    buf.putInt(16, w);
    buf.putInt(20, h);
    var crc = new CRC32();
    crc.update(png, 12, 17);
    buf.putInt(29, (int) crc.getValue());
    return png;
  }

  @Test
  void rejectsDecompressionBomb() throws Exception {
    var png = pngDeclaring(20000, 20000);
    assertThatThrownBy(() -> CoverImages.toJpeg(png))
        .isInstanceOf(InvalidImageException.class)
        .hasMessage("Immagine troppo grande");
  }

  @Test
  void rejectsOverlongSide() throws Exception {
    var png = pngDeclaring(50000, 2000);
    assertThatThrownBy(() -> CoverImages.toJpeg(png))
        .isInstanceOf(InvalidImageException.class)
        .hasMessage("Immagine troppo grande");
  }

  @Test
  void legalSkewedImageIsShrunk() throws Exception {
    var img = decode(CoverImages.toJpeg(encode(4000, 200, BufferedImage.TYPE_INT_RGB, "png")));
    assertThat(img.getWidth()).isEqualTo(480);
    assertThat(img.getHeight()).isEqualTo(24);
  }

  /** 200x100 JPEG, left half red and right half blue, with an APP1 holding this orientation. */
  private static byte[] jpegWithOrientation(int orientation) throws Exception {
    var img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < 200; x++) {
      for (int y = 0; y < 100; y++) {
        img.setRGB(x, y, x < 100 ? 0xff0000 : 0x0000ff);
      }
    }
    var plain = new ByteArrayOutputStream();
    ImageIO.write(img, "jpg", plain);
    var jpeg = plain.toByteArray();
    var tiff =
        ByteBuffer.allocate(26)
            .put("MM".getBytes(StandardCharsets.ISO_8859_1))
            .putShort((short) 42)
            .putInt(8)
            .putShort((short) 1)
            .putShort((short) 0x0112)
            .putShort((short) 3)
            .putInt(1)
            .putShort((short) orientation)
            .putShort((short) 0)
            .array();
    var exif = ByteBuffer.allocate(6 + tiff.length);
    exif.put("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1)).put(tiff);
    var out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2);
    out.write(0xff);
    out.write(0xe1);
    out.write((exif.capacity() + 2) >> 8);
    out.write((exif.capacity() + 2) & 0xff);
    out.write(exif.array());
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  @Test
  void orientation6RotatesLandscapePixelsToPortrait() throws Exception {
    var img = decode(CoverImages.toJpeg(jpegWithOrientation(6)));
    assertThat(img.getWidth()).isEqualTo(100);
    assertThat(img.getHeight()).isEqualTo(200);
    // Rotating 90 degrees clockwise puts the red left half on top.
    assertThat((img.getRGB(50, 20) >> 16) & 0xff).isGreaterThan(200);
    assertThat(img.getRGB(50, 180) & 0xff).isGreaterThan(200);
  }

  @Test
  void orientation3FlipsUpsideDown() throws Exception {
    var img = decode(CoverImages.toJpeg(jpegWithOrientation(3)));
    assertThat(img.getWidth()).isEqualTo(200);
    assertThat(img.getRGB(20, 50) & 0xff).isGreaterThan(200);
  }

  @Test
  void orientation1AndMissingExifAreUnchanged() throws Exception {
    var img = decode(CoverImages.toJpeg(jpegWithOrientation(1)));
    assertThat(img.getWidth()).isEqualTo(200);
    assertThat((img.getRGB(20, 50) >> 16) & 0xff).isGreaterThan(200);
    var plain = decode(CoverImages.toJpeg(encode(200, 100, BufferedImage.TYPE_INT_RGB, "jpg")));
    assertThat(plain.getWidth()).isEqualTo(200);
  }

  @Test
  void malformedExifIsIgnored() throws Exception {
    var jpeg = jpegWithOrientation(6);
    ByteBuffer.wrap(jpeg).putInt(16, 0x7fffff00); // SOI + APP1 header + "Exif" + TIFF header
    var img = decode(CoverImages.toJpeg(jpeg));
    assertThat(img.getWidth()).isEqualTo(200);
  }
}
