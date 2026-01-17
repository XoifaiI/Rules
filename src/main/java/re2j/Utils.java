package re2j;

import java.util.Arrays;

public final class Utils {

  public static final int[] EMPTY_INTS = {};

  static final int EMPTY_BEGIN_LINE = 0x01;
  static final int EMPTY_END_LINE = 0x02;
  static final int EMPTY_BEGIN_TEXT = 0x04;
  static final int EMPTY_END_TEXT = 0x08;
  static final int EMPTY_WORD_BOUNDARY = 0x10;
  static final int EMPTY_NO_WORD_BOUNDARY = 0x20;
  static final int EMPTY_ALL = -1;

  private static final String REGEX_METACHARACTERS = "\\.+*?()|[]{}^$";

  private static final int SINGLE_BYTE_MAX = 0x100;
  private static final int HEX_DIGIT_OFFSET = 10;
  private static final int NOT_FOUND = -1;

  private Utils() {
  }

  static boolean isalnum(int character) {
    boolean isDigit = character >= '0' && character <= '9';
    boolean isUpperCaseLetter = character >= 'A' && character <= 'Z';
    boolean isLowerCaseLetter = character >= 'a' && character <= 'z';
    return isDigit || isUpperCaseLetter || isLowerCaseLetter;
  }

  static int unhex(int character) {
    if (character >= '0' && character <= '9') {
      return character - '0';
    }
    if (character >= 'a' && character <= 'f') {
      return character - 'a' + HEX_DIGIT_OFFSET;
    }
    if (character >= 'A' && character <= 'F') {
      return character - 'A' + HEX_DIGIT_OFFSET;
    }
    return NOT_FOUND;
  }

  static void escapeRune(StringBuilder output, int rune) {
    if (Unicode.isPrint(rune)) {
      appendPrintableRune(output, rune);
      return;
    }
    appendEscapedNonPrintableRune(output, rune);
  }

  private static void appendPrintableRune(StringBuilder output, int rune) {
    boolean isMetacharacter = REGEX_METACHARACTERS.indexOf((char) rune) >= 0;
    if (isMetacharacter) {
      output.append('\\');
    }
    output.appendCodePoint(rune);
  }

  private static void appendEscapedNonPrintableRune(StringBuilder output, int rune) {
    switch (rune) {
      case '"' -> output.append("\\\"");
      case '\\' -> output.append("\\\\");
      case '\t' -> output.append("\\t");
      case '\n' -> output.append("\\n");
      case '\r' -> output.append("\\r");
      case '\b' -> output.append("\\b");
      case '\f' -> output.append("\\f");
      default -> appendHexEscapedRune(output, rune);
    }
  }

  private static void appendHexEscapedRune(StringBuilder output, int rune) {
    String hexValue = Integer.toHexString(rune);

    if (rune < SINGLE_BYTE_MAX) {
      output.append("\\x");
      boolean needsLeadingZero = hexValue.length() == 1;
      if (needsLeadingZero) {
        output.append('0');
      }
      output.append(hexValue);
    } else {
      output.append("\\x{").append(hexValue).append('}');
    }
  }

  static int[] stringToRunes(String text) {
    return text.codePoints().toArray();
  }

  static String runeToString(int rune) {
    return Character.toString(rune);
  }

  public static int[] subarray(int[] array, int start, int end) {
    return Arrays.copyOfRange(array, start, end);
  }

  public static byte[] subarray(byte[] array, int start, int end) {
    return Arrays.copyOfRange(array, start, end);
  }

  static int indexOf(byte[] source, byte[] target, int fromIndex) {
    if (fromIndex >= source.length) {
      return target.length == 0 ? source.length : NOT_FOUND;
    }
    if (fromIndex < 0) {
      fromIndex = 0;
    }
    if (target.length == 0) {
      return fromIndex;
    }

    return findByteSequence(source, target, fromIndex);
  }

  private static int findByteSequence(byte[] source, byte[] target, int startIndex) {
    byte firstTargetByte = target[0];
    int lastPossibleMatchStart = source.length - target.length;

    for (int searchIndex = startIndex; searchIndex <= lastPossibleMatchStart; searchIndex++) {
      searchIndex = findNextOccurrenceOfByte(source, firstTargetByte, searchIndex, lastPossibleMatchStart);

      if (searchIndex > lastPossibleMatchStart) {
        break;
      }

      if (remainingBytesMatch(source, target, searchIndex)) {
        return searchIndex;
      }
    }

    return NOT_FOUND;
  }

  private static int findNextOccurrenceOfByte(
      byte[] source,
      byte targetByte,
      int startIndex,
      int maxIndex) {

    int currentIndex = startIndex;
    while (currentIndex <= maxIndex && source[currentIndex] != targetByte) {
      currentIndex++;
    }
    return currentIndex;
  }

  private static boolean remainingBytesMatch(byte[] source, byte[] target, int sourceStartIndex) {
    int sourceIndex = sourceStartIndex + 1;
    int sourceEndIndex = sourceStartIndex + target.length;

    for (int targetIndex = 1; sourceIndex < sourceEndIndex; sourceIndex++, targetIndex++) {
      if (source[sourceIndex] != target[targetIndex]) {
        return false;
      }
    }

    return true;
  }

  static boolean isWordRune(int rune) {
    boolean isUpperCaseLetter = rune >= 'A' && rune <= 'Z';
    boolean isLowerCaseLetter = rune >= 'a' && rune <= 'z';
    boolean isDigit = rune >= '0' && rune <= '9';
    boolean isUnderscore = rune == '_';
    return isUpperCaseLetter || isLowerCaseLetter || isDigit || isUnderscore;
  }

  static int emptyOpContext(int precedingRune, int followingRune) {
    int emptyWidthFlags = 0;

    emptyWidthFlags |= computeLineAndTextStartFlags(precedingRune);
    emptyWidthFlags |= computeLineAndTextEndFlags(followingRune);
    emptyWidthFlags |= computeWordBoundaryFlags(precedingRune, followingRune);

    return emptyWidthFlags;
  }

  private static int computeLineAndTextStartFlags(int precedingRune) {
    int flags = 0;

    boolean atTextStart = precedingRune < 0;
    if (atTextStart) {
      flags |= EMPTY_BEGIN_TEXT | EMPTY_BEGIN_LINE;
    }

    boolean afterNewline = precedingRune == '\n';
    if (afterNewline) {
      flags |= EMPTY_BEGIN_LINE;
    }

    return flags;
  }

  private static int computeLineAndTextEndFlags(int followingRune) {
    int flags = 0;

    boolean atTextEnd = followingRune < 0;
    if (atTextEnd) {
      flags |= EMPTY_END_TEXT | EMPTY_END_LINE;
    }

    boolean beforeNewline = followingRune == '\n';
    if (beforeNewline) {
      flags |= EMPTY_END_LINE;
    }

    return flags;
  }

  private static int computeWordBoundaryFlags(int precedingRune, int followingRune) {
    boolean precedingIsWordChar = isWordRune(precedingRune);
    boolean followingIsWordChar = isWordRune(followingRune);
    boolean atWordBoundary = precedingIsWordChar != followingIsWordChar;

    if (atWordBoundary) {
      return EMPTY_WORD_BOUNDARY;
    }
    return EMPTY_NO_WORD_BOUNDARY;
  }
}