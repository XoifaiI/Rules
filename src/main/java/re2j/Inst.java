package re2j;

final class Inst {
  static final int ALT = 1;
  static final int ALT_MATCH = 2;
  static final int CAPTURE = 3;
  static final int EMPTY_WIDTH = 4;
  static final int FAIL = 5;
  static final int MATCH = 6;
  static final int NOP = 7;
  static final int RUNE = 8;
  static final int RUNE1 = 9;
  static final int RUNE_ANY = 10;
  static final int RUNE_ANY_NOT_NL = 11;

  private static final int LINEAR_SEARCH_THRESHOLD = 8;

  int op;
  int out;
  int arg;
  int[] runes;

  Inst(int opcode) {
    this.op = opcode;
  }

  static boolean isRuneOp(int opcode) {
    return opcode >= RUNE && opcode <= RUNE_ANY_NOT_NL;
  }

  boolean matchRune(int codePoint) {
    if (runes.length == 1) {
      int expectedCodePoint = runes[0];
      if ((arg & RE2.FOLD_CASE) != 0) {
        return Unicode.equalsIgnoreCase(expectedCodePoint, codePoint);
      }
      return codePoint == expectedCodePoint;
    }
    for (int index = 0; index < runes.length && index <= LINEAR_SEARCH_THRESHOLD; index += 2) {
      if (codePoint < runes[index]) {
        return false;
      }
      if (codePoint <= runes[index + 1]) {
        return true;
      }
    }
    int lowIndex = 0;
    int highIndex = runes.length / 2;
    while (lowIndex < highIndex) {
      int midIndex = lowIndex + (highIndex - lowIndex) / 2;
      int rangeLow = runes[2 * midIndex];
      if (rangeLow <= codePoint) {
        if (codePoint <= runes[2 * midIndex + 1]) {
          return true;
        }
        lowIndex = midIndex + 1;
      } else {
        highIndex = midIndex;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return switch (op) {
      case ALT -> "alt -> " + out + ", " + arg;
      case ALT_MATCH -> "altmatch -> " + out + ", " + arg;
      case CAPTURE -> "cap " + arg + " -> " + out;
      case EMPTY_WIDTH -> "empty " + arg + " -> " + out;
      case MATCH -> "match";
      case FAIL -> "fail";
      case NOP -> "nop -> " + out;
      case RUNE -> {
        if (runes == null) {
          yield "rune <null>";
        }
        yield "rune " + escapeRunesToString(runes)
            + (((arg & RE2.FOLD_CASE) != 0) ? "/i" : "")
            + " -> " + out;
      }
      case RUNE1 -> "rune1 " + escapeRunesToString(runes) + " -> " + out;
      case RUNE_ANY -> "any -> " + out;
      case RUNE_ANY_NOT_NL -> "anynotnl -> " + out;
      default -> throw new IllegalStateException("unhandled case in Inst.toString");
    };
  }

  private static String escapeRunesToString(int[] runes) {
    var stringBuilder = new StringBuilder();
    stringBuilder.append('"');
    for (int rune : runes) {
      Utils.escapeRune(stringBuilder, rune);
    }
    stringBuilder.append('"');
    return stringBuilder.toString();
  }
}