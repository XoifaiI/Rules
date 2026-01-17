package re2j;

import java.nio.charset.StandardCharsets;

public sealed abstract class MatcherInput permits MatcherInput.Utf8MatcherInput, MatcherInput.Utf16MatcherInput {
  public enum Encoding {
    UTF_16,
    UTF_8
  }

  public static MatcherInput utf16(CharSequence charSequence) {
    return new Utf16MatcherInput(charSequence);
  }

  public static MatcherInput utf8(byte[] bytes) {
    return new Utf8MatcherInput(bytes);
  }

  public static MatcherInput utf8(String input) {
    return new Utf8MatcherInput(input.getBytes(StandardCharsets.UTF_8));
  }

  public abstract Encoding getEncoding();

  public abstract CharSequence asCharSequence();

  public abstract byte[] asBytes();

  public abstract int length();

  static final class Utf8MatcherInput extends MatcherInput {
    private final byte[] bytes;

    Utf8MatcherInput(byte[] bytes) {
      this.bytes = bytes;
    }

    @Override
    public Encoding getEncoding() {
      return Encoding.UTF_8;
    }

    @Override
    public CharSequence asCharSequence() {
      return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public byte[] asBytes() {
      return bytes;
    }

    @Override
    public int length() {
      return bytes.length;
    }
  }

  static final class Utf16MatcherInput extends MatcherInput {
    private final CharSequence charSequence;

    Utf16MatcherInput(CharSequence charSequence) {
      this.charSequence = charSequence;
    }

    @Override
    public Encoding getEncoding() {
      return Encoding.UTF_16;
    }

    @Override
    public CharSequence asCharSequence() {
      return charSequence;
    }

    @Override
    public byte[] asBytes() {
      return charSequence.toString().getBytes(StandardCharsets.UTF_16);
    }

    @Override
    public int length() {
      return charSequence.length();
    }
  }
}