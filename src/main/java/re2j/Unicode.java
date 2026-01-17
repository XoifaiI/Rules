package re2j;

public final class Unicode {

  public static final int MAX_RUNE = 0x10FFFF;

  static final int MAX_ASCII = 0x7f;
  static final int MAX_LATIN1 = 0xFF;

  public static final int MIN_FOLD = 0x0041;
  public static final int MAX_FOLD = 0x1E943;

  private static final int PRINTABLE_ASCII_START = 0x20;
  private static final int PRINTABLE_ASCII_END = 0x7F;
  private static final int EXTENDED_LATIN_PRINTABLE_START = 0xA1;
  private static final int SOFT_HYPHEN = 0xAD;

  private static final int ASCII_CASE_BIT = 0x20;

  private Unicode() {
  }

  static boolean isUpper(int rune) {
    return Character.isUpperCase(rune);
  }

  static boolean isPrint(int rune) {
    if (rune <= MAX_LATIN1) {
      return isPrintableLatin1(rune);
    }
    return isPrintableUnicodeCategory(Character.getType(rune));
  }

  private static boolean isPrintableLatin1(int rune) {
    boolean isBasicPrintableAscii = rune >= PRINTABLE_ASCII_START && rune < PRINTABLE_ASCII_END;
    boolean isExtendedLatinPrintable = rune >= EXTENDED_LATIN_PRINTABLE_START && rune != SOFT_HYPHEN;
    return isBasicPrintableAscii || isExtendedLatinPrintable;
  }

  private static boolean isPrintableUnicodeCategory(int unicodeCategory) {
    return switch (unicodeCategory) {
      case Character.UPPERCASE_LETTER,
          Character.LOWERCASE_LETTER,
          Character.TITLECASE_LETTER,
          Character.MODIFIER_LETTER,
          Character.OTHER_LETTER,
          Character.NON_SPACING_MARK,
          Character.ENCLOSING_MARK,
          Character.COMBINING_SPACING_MARK,
          Character.DECIMAL_DIGIT_NUMBER,
          Character.LETTER_NUMBER,
          Character.OTHER_NUMBER,
          Character.CONNECTOR_PUNCTUATION,
          Character.DASH_PUNCTUATION,
          Character.START_PUNCTUATION,
          Character.END_PUNCTUATION,
          Character.INITIAL_QUOTE_PUNCTUATION,
          Character.FINAL_QUOTE_PUNCTUATION,
          Character.OTHER_PUNCTUATION,
          Character.MATH_SYMBOL,
          Character.CURRENCY_SYMBOL,
          Character.MODIFIER_SYMBOL,
          Character.OTHER_SYMBOL ->
        true;
      default -> false;
    };
  }

  public static int simpleFold(int rune) {
    if (rune < UnicodeTables.CASE_ORBIT.length && UnicodeTables.CASE_ORBIT[rune] != 0) {
      return UnicodeTables.CASE_ORBIT[rune];
    }

    int lowerCase = Character.toLowerCase(rune);
    int upperCase = Character.toUpperCase(rune);

    boolean runeIsUpperCase = lowerCase != rune && upperCase == rune;
    if (runeIsUpperCase) {
      return lowerCase;
    }

    boolean runeIsLowerCase = upperCase != rune && lowerCase == rune;
    if (runeIsLowerCase) {
      return upperCase;
    }

    boolean runeIsTitleCaseOrSpecial = lowerCase != rune && upperCase != rune;
    if (runeIsTitleCaseOrSpecial) {
      if (Character.toUpperCase(lowerCase) == rune || Character.toLowerCase(lowerCase) == rune) {
        return lowerCase;
      }
      if (Character.toLowerCase(upperCase) == rune || Character.toUpperCase(upperCase) == rune) {
        return upperCase;
      }
    }

    return rune;
  }

  public static boolean equalsIgnoreCase(int firstRune, int secondRune) {
    boolean eitherInvalidOrEqual = firstRune < 0 || secondRune < 0 || firstRune == secondRune;
    if (eitherInvalidOrEqual) {
      return true;
    }

    if (firstRune <= MAX_ASCII && secondRune <= MAX_ASCII) {
      return asciiEqualsIgnoreCase(firstRune, secondRune);
    }

    return existsInCaseFoldCycle(firstRune, secondRune);
  }

  private static boolean asciiEqualsIgnoreCase(int firstRune, int secondRune) {
    int firstNormalized = toAsciiLowerCase(firstRune);
    int secondNormalized = toAsciiLowerCase(secondRune);
    return firstNormalized == secondNormalized;
  }

  private static int toAsciiLowerCase(int rune) {
    boolean isUpperCaseAscii = rune >= 'A' && rune <= 'Z';
    if (isUpperCaseAscii) {
      return rune | ASCII_CASE_BIT;
    }
    return rune;
  }

  private static boolean existsInCaseFoldCycle(int startRune, int targetRune) {
    for (int foldedRune = simpleFold(startRune); foldedRune != startRune; foldedRune = simpleFold(foldedRune)) {
      if (foldedRune == targetRune) {
        return true;
      }
    }
    return false;
  }
}