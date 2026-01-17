package re2j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

final class UnicodeTables {

  static final int[] CASE_ORBIT = buildCaseOrbit();
  static final Map<String, int[][]> CATEGORIES = buildCategories();
  static final Map<String, int[][]> FOLD_CATEGORIES = buildFoldCategories();
  static final Map<String, int[][]> SCRIPTS = buildScripts();
  static final Map<String, int[][]> FOLD_SCRIPT = buildFoldScripts();

  private static final int CASE_ORBIT_TABLE_SIZE = 0x10450;

  private static final int LATIN_CAPITAL_K = 'K';
  private static final int LATIN_SMALL_K = 'k';
  private static final int KELVIN_SIGN = 0x212A;

  private static final int LATIN_CAPITAL_S = 'S';
  private static final int LATIN_SMALL_S = 's';
  private static final int LATIN_SMALL_LONG_S = 0x17F;

  private UnicodeTables() {
  }

  private static int[] buildCaseOrbit() {
    int[] orbit = new int[CASE_ORBIT_TABLE_SIZE];

    buildBasicCaseOrbitPairs(orbit);
    buildTripleCaseFoldCycles(orbit);
    buildAsciiLetterOrbitPairs(orbit);

    return orbit;
  }

  private static void buildBasicCaseOrbitPairs(int[] orbit) {
    for (int codePoint = 0; codePoint < orbit.length; codePoint++) {
      int lowerCase = Character.toLowerCase(codePoint);
      int upperCase = Character.toUpperCase(codePoint);

      boolean codePointIsUpperCase = lowerCase != codePoint
          && lowerCase < orbit.length
          && upperCase == codePoint;

      if (codePointIsUpperCase) {
        if (orbit[codePoint] == 0) {
          orbit[codePoint] = lowerCase;
        }
        if (orbit[lowerCase] == 0) {
          orbit[lowerCase] = codePoint;
        }
        continue;
      }

      boolean codePointIsLowerCase = upperCase != codePoint
          && upperCase < orbit.length
          && lowerCase == codePoint;

      if (codePointIsLowerCase) {
        if (orbit[codePoint] == 0) {
          orbit[codePoint] = upperCase;
        }
        if (orbit[upperCase] == 0) {
          orbit[upperCase] = codePoint;
        }
      }
    }
  }

  private static void buildTripleCaseFoldCycles(int[] orbit) {
    orbit[LATIN_CAPITAL_K] = LATIN_SMALL_K;
    orbit[LATIN_SMALL_K] = KELVIN_SIGN;
    orbit[KELVIN_SIGN] = LATIN_CAPITAL_K;

    orbit[LATIN_CAPITAL_S] = LATIN_SMALL_S;
    orbit[LATIN_SMALL_S] = LATIN_SMALL_LONG_S;
    orbit[LATIN_SMALL_LONG_S] = LATIN_CAPITAL_S;
  }

  private static void buildAsciiLetterOrbitPairs(int[] orbit) {
    int asciiCaseOffset = 'a' - 'A';

    for (int upperCaseLetter = 'A'; upperCaseLetter <= 'Z'; upperCaseLetter++) {
      boolean hasTripleCaseFoldCycle = upperCaseLetter == LATIN_CAPITAL_K || upperCaseLetter == LATIN_CAPITAL_S;

      if (!hasTripleCaseFoldCycle) {
        int lowerCaseLetter = upperCaseLetter + asciiCaseOffset;
        orbit[upperCaseLetter] = lowerCaseLetter;
        orbit[lowerCaseLetter] = upperCaseLetter;
      }
    }
  }

  private static int[][] buildRangesForType(int... unicodeGeneralCategories) {
    ArrayList<int[]> ranges = new ArrayList<>();
    int rangeStart = -1;
    int previousCodePoint = -1;

    for (int codePoint = 0; codePoint <= Unicode.MAX_RUNE; codePoint++) {
      int codePointCategory = Character.getType(codePoint);
      boolean matchesCategory = containsCategory(unicodeGeneralCategories, codePointCategory);

      if (matchesCategory) {
        if (rangeStart < 0) {
          rangeStart = codePoint;
        }
        previousCodePoint = codePoint;
      } else if (rangeStart >= 0) {
        ranges.add(createRangeEntry(rangeStart, previousCodePoint));
        rangeStart = -1;
      }
    }

    if (rangeStart >= 0) {
      ranges.add(createRangeEntry(rangeStart, previousCodePoint));
    }

    return ranges.toArray(new int[0][]);
  }

  private static boolean containsCategory(int[] categories, int targetCategory) {
    for (int category : categories) {
      if (category == targetCategory) {
        return true;
      }
    }
    return false;
  }

  private static int[] createRangeEntry(int rangeStart, int rangeEnd) {
    int stride = 1;
    return new int[] { rangeStart, rangeEnd, stride };
  }

  private static int[][] buildRangesForScript(Character.UnicodeScript script) {
    ArrayList<int[]> ranges = new ArrayList<>();
    int rangeStart = -1;
    int previousCodePoint = -1;

    for (int codePoint = 0; codePoint <= Unicode.MAX_RUNE; codePoint++) {
      boolean belongsToScript = isCodePointInScript(codePoint, script);

      if (belongsToScript) {
        if (rangeStart < 0) {
          rangeStart = codePoint;
        }
        previousCodePoint = codePoint;
      } else if (rangeStart >= 0) {
        ranges.add(createRangeEntry(rangeStart, previousCodePoint));
        rangeStart = -1;
      }
    }

    if (rangeStart >= 0) {
      ranges.add(createRangeEntry(rangeStart, previousCodePoint));
    }

    return ranges.toArray(new int[0][]);
  }

  private static boolean isCodePointInScript(int codePoint, Character.UnicodeScript script) {
    try {
      return Character.UnicodeScript.of(codePoint) == script;
    } catch (IllegalArgumentException invalidCodePoint) {
      return false;
    }
  }

  private static Map<String, int[][]> buildCategories() {
    HashMap<String, int[][]> categoryMap = new HashMap<>();

    categoryMap.put("C", buildRangesForType(
        Character.CONTROL, Character.FORMAT, Character.SURROGATE,
        Character.PRIVATE_USE, Character.UNASSIGNED));
    categoryMap.put("Cc", buildRangesForType(Character.CONTROL));
    categoryMap.put("Cf", buildRangesForType(Character.FORMAT));
    categoryMap.put("Co", buildRangesForType(Character.PRIVATE_USE));
    categoryMap.put("Cs", buildRangesForType(Character.SURROGATE));

    categoryMap.put("L", buildRangesForType(
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER,
        Character.TITLECASE_LETTER, Character.MODIFIER_LETTER, Character.OTHER_LETTER));
    categoryMap.put("Ll", buildRangesForType(Character.LOWERCASE_LETTER));
    categoryMap.put("Lm", buildRangesForType(Character.MODIFIER_LETTER));
    categoryMap.put("Lo", buildRangesForType(Character.OTHER_LETTER));
    categoryMap.put("Lt", buildRangesForType(Character.TITLECASE_LETTER));
    categoryMap.put("Lu", buildRangesForType(Character.UPPERCASE_LETTER));

    categoryMap.put("M", buildRangesForType(
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK));
    categoryMap.put("Mc", buildRangesForType(Character.COMBINING_SPACING_MARK));
    categoryMap.put("Me", buildRangesForType(Character.ENCLOSING_MARK));
    categoryMap.put("Mn", buildRangesForType(Character.NON_SPACING_MARK));

    categoryMap.put("N", buildRangesForType(
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER));
    categoryMap.put("Nd", buildRangesForType(Character.DECIMAL_DIGIT_NUMBER));
    categoryMap.put("Nl", buildRangesForType(Character.LETTER_NUMBER));
    categoryMap.put("No", buildRangesForType(Character.OTHER_NUMBER));

    categoryMap.put("P", buildRangesForType(
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
        Character.START_PUNCTUATION, Character.END_PUNCTUATION,
        Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION));
    categoryMap.put("Pc", buildRangesForType(Character.CONNECTOR_PUNCTUATION));
    categoryMap.put("Pd", buildRangesForType(Character.DASH_PUNCTUATION));
    categoryMap.put("Pe", buildRangesForType(Character.END_PUNCTUATION));
    categoryMap.put("Pf", buildRangesForType(Character.FINAL_QUOTE_PUNCTUATION));
    categoryMap.put("Pi", buildRangesForType(Character.INITIAL_QUOTE_PUNCTUATION));
    categoryMap.put("Po", buildRangesForType(Character.OTHER_PUNCTUATION));
    categoryMap.put("Ps", buildRangesForType(Character.START_PUNCTUATION));

    categoryMap.put("S", buildRangesForType(
        Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL,
        Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL));
    categoryMap.put("Sc", buildRangesForType(Character.CURRENCY_SYMBOL));
    categoryMap.put("Sk", buildRangesForType(Character.MODIFIER_SYMBOL));
    categoryMap.put("Sm", buildRangesForType(Character.MATH_SYMBOL));
    categoryMap.put("So", buildRangesForType(Character.OTHER_SYMBOL));

    categoryMap.put("Z", buildRangesForType(
        Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR));
    categoryMap.put("Zl", buildRangesForType(Character.LINE_SEPARATOR));
    categoryMap.put("Zp", buildRangesForType(Character.PARAGRAPH_SEPARATOR));
    categoryMap.put("Zs", buildRangesForType(Character.SPACE_SEPARATOR));

    return categoryMap;
  }

  private static Map<String, int[][]> buildFoldCategories() {
    HashMap<String, int[][]> foldCategoryMap = new HashMap<>();
    foldCategoryMap.put("Lu", buildFoldRanges(buildRangesForType(Character.UPPERCASE_LETTER)));
    foldCategoryMap.put("Ll", buildFoldRanges(buildRangesForType(Character.LOWERCASE_LETTER)));
    foldCategoryMap.put("Lt", buildFoldRanges(buildRangesForType(Character.TITLECASE_LETTER)));
    foldCategoryMap.put("Mn", buildFoldRanges(buildRangesForType(Character.NON_SPACING_MARK)));
    return foldCategoryMap;
  }

  private static Map<String, int[][]> buildScripts() {
    HashMap<String, int[][]> scriptMap = new HashMap<>();

    for (Character.UnicodeScript script : Character.UnicodeScript.values()) {
      if (script != Character.UnicodeScript.UNKNOWN) {
        int[][] scriptRanges = buildRangesForScript(script);
        if (scriptRanges.length > 0) {
          String scriptName = convertSnakeCaseToTitleCase(script.name());
          scriptMap.put(scriptName, scriptRanges);
        }
      }
    }

    return scriptMap;
  }

  private static Map<String, int[][]> buildFoldScripts() {
    HashMap<String, int[][]> foldScriptMap = new HashMap<>();
    foldScriptMap.put("Common", buildFoldRanges(buildRangesForScript(Character.UnicodeScript.COMMON)));
    foldScriptMap.put("Greek", buildFoldRanges(buildRangesForScript(Character.UnicodeScript.GREEK)));
    foldScriptMap.put("Inherited", buildFoldRanges(buildRangesForScript(Character.UnicodeScript.INHERITED)));
    return foldScriptMap;
  }

  private static int[][] buildFoldRanges(int[][] baseRanges) {
    ArrayList<int[]> additionalFoldedRanges = new ArrayList<>();

    for (int[] range : baseRanges) {
      int rangeStart = range[0];
      int rangeEnd = range[1];

      for (int codePoint = rangeStart; codePoint <= rangeEnd; codePoint++) {
        addCodePointIfMissingFromRanges(additionalFoldedRanges, baseRanges, Character.toLowerCase(codePoint));
        addCodePointIfMissingFromRanges(additionalFoldedRanges, baseRanges, Character.toUpperCase(codePoint));
        addCodePointIfMissingFromRanges(additionalFoldedRanges, baseRanges, Character.toTitleCase(codePoint));
      }
    }

    if (additionalFoldedRanges.isEmpty()) {
      return new int[0][];
    }

    return mergeOverlappingRanges(additionalFoldedRanges);
  }

  private static void addCodePointIfMissingFromRanges(
      ArrayList<int[]> additionalRanges,
      int[][] baseRanges,
      int codePoint) {

    boolean isValidCodePoint = codePoint >= 0;
    boolean alreadyInBaseRanges = isCodePointInRanges(baseRanges, codePoint);
    boolean alreadyInAdditionalRanges = isCodePointInRangeList(additionalRanges, codePoint);

    if (isValidCodePoint && !alreadyInBaseRanges && !alreadyInAdditionalRanges) {
      additionalRanges.add(createRangeEntry(codePoint, codePoint));
    }
  }

  private static int[][] mergeOverlappingRanges(ArrayList<int[]> ranges) {
    ranges.sort((firstRange, secondRange) -> firstRange[0] - secondRange[0]);

    ArrayList<int[]> mergedRanges = new ArrayList<>();

    for (int[] currentRange : ranges) {
      if (mergedRanges.isEmpty()) {
        mergedRanges.add(currentRange);
        continue;
      }

      int[] lastMergedRange = mergedRanges.getLast();
      boolean rangesAreAdjacent = lastMergedRange[1] >= currentRange[0] - 1;

      if (rangesAreAdjacent) {
        lastMergedRange[1] = Math.max(lastMergedRange[1], currentRange[1]);
      } else {
        mergedRanges.add(currentRange);
      }
    }

    return mergedRanges.toArray(new int[0][]);
  }

  private static boolean isCodePointInRanges(int[][] ranges, int codePoint) {
    for (int[] range : ranges) {
      if (codePoint >= range[0] && codePoint <= range[1]) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCodePointInRangeList(ArrayList<int[]> ranges, int codePoint) {
    for (int[] range : ranges) {
      if (codePoint >= range[0] && codePoint <= range[1]) {
        return true;
      }
    }
    return false;
  }

  private static String convertSnakeCaseToTitleCase(String snakeCaseName) {
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;

    for (int i = 0; i < snakeCaseName.length(); i++) {
      char character = snakeCaseName.charAt(i);

      if (character == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(character));
        capitalizeNext = false;
      } else {
        result.append(Character.toLowerCase(character));
      }
    }

    return result.toString();
  }
}