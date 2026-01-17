package re2j;

sealed abstract class MachineInput
    permits MachineInput.UTF8Input, MachineInput.UTF16Input {

  static final int EOF = (-1 << 3);
  static final int INVALID = (0xFFFD << 3) | 1;

  static MachineInput fromUTF8(byte[] bytes) {
    return new UTF8Input(bytes, 0, bytes.length);
  }

  static MachineInput fromUTF8(byte[] bytes, int start, int end) {
    return new UTF8Input(bytes, start, end);
  }

  static MachineInput fromUTF16(CharSequence charSequence) {
    return new UTF16Input(charSequence, 0, charSequence.length());
  }

  static MachineInput fromUTF16(CharSequence charSequence, int start, int end) {
    return new UTF16Input(charSequence, start, end);
  }

  abstract int step(int position);

  abstract boolean canCheckPrefix();

  abstract int index(RE2 re2, int position);

  abstract int context(int position);

  abstract int endPos();

  static final class UTF8Input extends MachineInput {
    private final byte[] bytes;
    private final int start;
    private final int end;

    UTF8Input(byte[] bytes, int start, int end) {
      if (end > bytes.length) {
        throw new ArrayIndexOutOfBoundsException(
            "end is greater than length: " + end + " > " + bytes.length);
      }
      this.bytes = bytes;
      this.start = start;
      this.end = end;
    }

    private static boolean isContinuationByte(byte byteValue) {
      return (byteValue & 0xC0) == 0x80;
    }

    @Override
    int step(int position) {
      position += start;
      if (position >= end) {
        return EOF;
      }
      int leadByte = bytes[position++] & 0xff;
      if ((leadByte & 0x80) == 0) {
        return leadByte << 3 | 1;
      } else if ((leadByte & 0xE0) == 0xC0) {
        if (position >= end) {
          return EOF;
        }
        if (!isContinuationByte(bytes[position])) {
          return INVALID;
        }
        int continuation1 = bytes[position] & 0x3F;
        int codePoint = (leadByte & 0x1F) << 6 | continuation1;
        if (codePoint < 0x80) {
          return INVALID;
        }
        return codePoint << 3 | 2;
      } else if ((leadByte & 0xF0) == 0xE0) {
        if (position + 1 >= end) {
          return EOF;
        }
        if (!isContinuationByte(bytes[position]) || !isContinuationByte(bytes[position + 1])) {
          return INVALID;
        }
        int continuation1 = bytes[position] & 0x3F;
        int continuation2 = bytes[position + 1] & 0x3F;
        int codePoint = (leadByte & 0x0F) << 12 | continuation1 << 6 | continuation2;
        if (codePoint < 0x800) {
          return INVALID;
        }
        if (codePoint >= 0xD800 && codePoint <= 0xDFFF) {
          return INVALID;
        }
        return codePoint << 3 | 3;
      } else if ((leadByte & 0xF8) == 0xF0) {
        if (position + 2 >= end) {
          return EOF;
        }
        if (!isContinuationByte(bytes[position])
            || !isContinuationByte(bytes[position + 1])
            || !isContinuationByte(bytes[position + 2])) {
          return INVALID;
        }
        int continuation1 = bytes[position] & 0x3F;
        int continuation2 = bytes[position + 1] & 0x3F;
        int continuation3 = bytes[position + 2] & 0x3F;
        int codePoint = (leadByte & 0x07) << 18 | continuation1 << 12 | continuation2 << 6 | continuation3;
        if (codePoint < 0x10000 || codePoint > 0x10FFFF) {
          return INVALID;
        }
        return codePoint << 3 | 4;
      } else {
        return INVALID;
      }
    }

    @Override
    boolean canCheckPrefix() {
      return true;
    }

    @Override
    int index(RE2 re2, int position) {
      position += start;
      int foundIndex = Utils.indexOf(bytes, re2.prefixUTF8, position);
      return foundIndex < 0 ? foundIndex : foundIndex - position;
    }

    @Override
    int context(int position) {
      position += this.start;
      int precedingRune = -1;
      if (position > this.start && position <= this.end) {
        int searchPosition = position - 1;
        precedingRune = bytes[searchPosition--] & 0xff;
        if (precedingRune >= 0x80) {
          int searchLimit = position - 4;
          if (searchLimit < this.start) {
            searchLimit = this.start;
          }
          while (searchPosition >= searchLimit && (bytes[searchPosition] & 0xC0) == 0x80) {
            searchPosition--;
          }
          if (searchPosition < this.start) {
            searchPosition = this.start;
          }
          int stepResult = step(searchPosition - this.start);
          precedingRune = (stepResult == INVALID || stepResult == EOF) ? 0xFFFD : stepResult >> 3;
        }
      }
      int followingRune;
      if (position < this.end) {
        int stepResult = step(position - this.start);
        followingRune = (stepResult == INVALID || stepResult == EOF) ? 0xFFFD : stepResult >> 3;
      } else {
        followingRune = -1;
      }
      return Utils.emptyOpContext(precedingRune, followingRune);
    }

    @Override
    int endPos() {
      return end;
    }
  }

  static final class UTF16Input extends MachineInput {
    private final CharSequence charSequence;
    private final int start;
    private final int end;

    UTF16Input(CharSequence charSequence, int start, int end) {
      this.charSequence = charSequence;
      this.start = start;
      this.end = end;
    }

    @Override
    int step(int position) {
      position += start;
      if (position < end) {
        int codePoint = Character.codePointAt(charSequence, position);
        return codePoint << 3 | Character.charCount(codePoint);
      }
      return EOF;
    }

    @Override
    boolean canCheckPrefix() {
      return true;
    }

    @Override
    int index(RE2 re2, int position) {
      position += start;
      int foundIndex = indexOf(charSequence, re2.prefix, position);
      return foundIndex < 0 ? foundIndex : foundIndex - position;
    }

    @Override
    int context(int position) {
      position += start;
      int precedingRune = position > 0 && position <= charSequence.length()
          ? Character.codePointBefore(charSequence, position)
          : -1;
      int followingRune = position < charSequence.length() ? Character.codePointAt(charSequence, position) : -1;
      return Utils.emptyOpContext(precedingRune, followingRune);
    }

    @Override
    int endPos() {
      return end;
    }

    private int indexOf(CharSequence haystack, String needle, int fromIndex) {
      if (haystack instanceof String string) {
        return string.indexOf(needle, fromIndex);
      }
      if (haystack instanceof StringBuilder stringBuilder) {
        return stringBuilder.indexOf(needle, fromIndex);
      }
      return indexOfFallback(haystack, needle, fromIndex);
    }

    private int indexOfFallback(CharSequence haystack, String needle, int fromIndex) {
      if (fromIndex >= haystack.length()) {
        return needle.isEmpty() ? 0 : -1;
      }
      if (fromIndex < 0) {
        fromIndex = 0;
      }
      if (needle.isEmpty()) {
        return fromIndex;
      }
      char firstChar = needle.charAt(0);
      int maxSearchIndex = haystack.length() - needle.length();
      for (int searchIndex = fromIndex; searchIndex <= maxSearchIndex; searchIndex++) {
        if (haystack.charAt(searchIndex) != firstChar) {
          while (++searchIndex <= maxSearchIndex && haystack.charAt(searchIndex) != firstChar) {
          }
        }
        if (searchIndex <= maxSearchIndex) {
          int haystackIndex = searchIndex + 1;
          int matchEndIndex = haystackIndex + needle.length() - 1;
          for (int needleIndex = 1; haystackIndex < matchEndIndex
              && haystack.charAt(haystackIndex) == needle.charAt(needleIndex); haystackIndex++, needleIndex++) {
          }
          if (haystackIndex == matchEndIndex) {
            return searchIndex;
          }
        }
      }
      return -1;
    }
  }
}