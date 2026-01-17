package re2j;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public final class Regexp {

  public enum Op {
    NO_MATCH,
    EMPTY_MATCH,
    LITERAL,
    CHAR_CLASS,
    ANY_CHAR_NOT_NL,
    ANY_CHAR,
    BEGIN_LINE,
    END_LINE,
    BEGIN_TEXT,
    END_TEXT,
    WORD_BOUNDARY,
    NO_WORD_BOUNDARY,
    CAPTURE,
    STAR,
    PLUS,
    QUEST,
    REPEAT,
    CONCAT,
    ALTERNATE,
    LEFT_PAREN,
    VERTICAL_BAR;

    boolean isPseudo() {
      return ordinal() >= LEFT_PAREN.ordinal();
    }
  }

  public static final Regexp[] EMPTY_SUBS = {};

  Op op;
  int flags;
  Regexp[] subs;
  int[] runes;
  int min;
  int max;
  int cap;
  String name;
  Map<String, Integer> namedGroups = Collections.emptyMap();

  Regexp(Op op) {
    this.op = op;
  }

  Regexp(Regexp original) {
    this.op = original.op;
    this.flags = original.flags;
    this.subs = original.subs;
    this.runes = original.runes;
    this.min = original.min;
    this.max = original.max;
    this.cap = original.cap;
    this.name = original.name;
    this.namedGroups = original.namedGroups;
  }

  void reinit() {
    this.flags = 0;
    subs = EMPTY_SUBS;
    runes = null;
    cap = min = max = 0;
    name = null;
    this.namedGroups = Collections.emptyMap();
  }

  @Override
  public String toString() {
    StringBuilder output = new StringBuilder();
    appendTo(output);
    return output.toString();
  }

  private static void escapeHyphenIfNeeded(StringBuilder output, int rune) {
    if (rune == '-') {
      output.append('\\');
    }
  }

  private void appendTo(StringBuilder output) {
    switch (op) {
      case NO_MATCH -> output.append("[^\\x00-\\x{10FFFF}]");
      case EMPTY_MATCH -> output.append("(?:)");
      case STAR, PLUS, QUEST, REPEAT -> appendQuantifiedExpression(output);
      case CONCAT -> appendConcatenation(output);
      case ALTERNATE -> appendAlternation(output);
      case LITERAL -> appendLiteral(output);
      case ANY_CHAR_NOT_NL -> output.append("(?-s:.)");
      case ANY_CHAR -> output.append("(?s:.)");
      case CAPTURE -> appendCaptureGroup(output);
      case BEGIN_TEXT -> output.append("\\A");
      case END_TEXT -> appendEndText(output);
      case BEGIN_LINE -> output.append('^');
      case END_LINE -> output.append('$');
      case WORD_BOUNDARY -> output.append("\\b");
      case NO_WORD_BOUNDARY -> output.append("\\B");
      case CHAR_CLASS -> appendCharacterClass(output);
      default -> output.append(op);
    }
  }

  private void appendQuantifiedExpression(StringBuilder output) {
    Regexp subExpression = subs[0];
    boolean needsGrouping = subExpression.op.ordinal() > Op.CAPTURE.ordinal()
        || (subExpression.op == Op.LITERAL && subExpression.runes.length > 1);

    if (needsGrouping) {
      output.append("(?:");
      subExpression.appendTo(output);
      output.append(')');
    } else {
      subExpression.appendTo(output);
    }

    appendQuantifier(output);

    if ((flags & RE2.NON_GREEDY) != 0) {
      output.append('?');
    }
  }

  private void appendQuantifier(StringBuilder output) {
    switch (op) {
      case STAR -> output.append('*');
      case PLUS -> output.append('+');
      case QUEST -> output.append('?');
      case REPEAT -> {
        output.append('{').append(min);
        if (min != max) {
          output.append(',');
          if (max >= 0) {
            output.append(max);
          }
        }
        output.append('}');
      }
      default -> {
      }
    }
  }

  private void appendConcatenation(StringBuilder output) {
    for (Regexp subExpression : subs) {
      if (subExpression.op == Op.ALTERNATE) {
        output.append("(?:");
        subExpression.appendTo(output);
        output.append(')');
      } else {
        subExpression.appendTo(output);
      }
    }
  }

  private void appendAlternation(StringBuilder output) {
    String separator = "";
    for (Regexp subExpression : subs) {
      output.append(separator);
      separator = "|";
      subExpression.appendTo(output);
    }
  }

  private void appendLiteral(StringBuilder output) {
    boolean isCaseInsensitive = (flags & RE2.FOLD_CASE) != 0;

    if (isCaseInsensitive) {
      output.append("(?i:");
    }
    for (int rune : runes) {
      Utils.escapeRune(output, rune);
    }
    if (isCaseInsensitive) {
      output.append(')');
    }
  }

  private void appendCaptureGroup(StringBuilder output) {
    boolean hasName = name != null && !name.isEmpty();

    if (hasName) {
      output.append("(?P<");
      output.append(name);
      output.append(">");
    } else {
      output.append('(');
    }

    if (subs[0].op != Op.EMPTY_MATCH) {
      subs[0].appendTo(output);
    }
    output.append(')');
  }

  private void appendEndText(StringBuilder output) {
    boolean wasDollarAnchor = (flags & RE2.WAS_DOLLAR) != 0;

    if (wasDollarAnchor) {
      output.append("(?-m:$)");
    } else {
      output.append("\\z");
    }
  }

  private void appendCharacterClass(StringBuilder output) {
    boolean hasInvalidLength = runes.length % 2 != 0;
    if (hasInvalidLength) {
      output.append("[invalid char class]");
      return;
    }

    output.append('[');

    if (runes.length == 0) {
      output.append("^\\x00-\\x{10FFFF}");
    } else if (isNegatedCharacterClass()) {
      appendNegatedCharacterClassRanges(output);
    } else {
      appendCharacterClassRanges(output);
    }

    output.append(']');
  }

  private boolean isNegatedCharacterClass() {
    return runes[0] == 0 && runes[runes.length - 1] == Unicode.MAX_RUNE;
  }

  private void appendNegatedCharacterClassRanges(StringBuilder output) {
    output.append('^');
    for (int i = 1; i < runes.length - 1; i += 2) {
      int rangeLow = runes[i] + 1;
      int rangeHigh = runes[i + 1] - 1;
      appendCharacterRange(output, rangeLow, rangeHigh);
    }
  }

  private void appendCharacterClassRanges(StringBuilder output) {
    for (int i = 0; i < runes.length; i += 2) {
      int rangeLow = runes[i];
      int rangeHigh = runes[i + 1];
      appendCharacterRange(output, rangeLow, rangeHigh);
    }
  }

  private void appendCharacterRange(StringBuilder output, int rangeLow, int rangeHigh) {
    escapeHyphenIfNeeded(output, rangeLow);
    Utils.escapeRune(output, rangeLow);
    if (rangeLow != rangeHigh) {
      output.append('-');
      escapeHyphenIfNeeded(output, rangeHigh);
      Utils.escapeRune(output, rangeHigh);
    }
  }

  int maxCap() {
    int maxCapture = 0;
    if (op == Op.CAPTURE) {
      maxCapture = cap;
    }
    if (subs != null) {
      for (Regexp subExpression : subs) {
        int subMaxCapture = subExpression.maxCap();
        if (maxCapture < subMaxCapture) {
          maxCapture = subMaxCapture;
        }
      }
    }
    return maxCapture;
  }

  @Override
  public int hashCode() {
    int hashcode = op.hashCode();
    switch (op) {
      case END_TEXT -> hashcode += 31 * (flags & RE2.WAS_DOLLAR);
      case LITERAL, CHAR_CLASS -> hashcode += 31 * Arrays.hashCode(runes);
      case ALTERNATE, CONCAT -> hashcode += 31 * Arrays.deepHashCode(subs);
      case STAR, PLUS, QUEST ->
        hashcode += 31 * (flags & RE2.NON_GREEDY) + 31 * subs[0].hashCode();
      case REPEAT -> hashcode += 31 * min + 31 * max + 31 * subs[0].hashCode();
      case CAPTURE ->
        hashcode += 31 * cap + 31 * (name != null ? name.hashCode() : 0) + 31 * subs[0].hashCode();
      default -> {
      }
    }
    return hashcode;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Regexp otherRegexp)) {
      return false;
    }
    Regexp thisRegexp = this;
    if (thisRegexp.op != otherRegexp.op) {
      return false;
    }
    return switch (thisRegexp.op) {
      case END_TEXT ->
        (thisRegexp.flags & RE2.WAS_DOLLAR) == (otherRegexp.flags & RE2.WAS_DOLLAR);
      case LITERAL, CHAR_CLASS -> Arrays.equals(thisRegexp.runes, otherRegexp.runes);
      case ALTERNATE, CONCAT -> areSubExpressionsEqual(thisRegexp, otherRegexp);
      case STAR, PLUS, QUEST ->
        (thisRegexp.flags & RE2.NON_GREEDY) == (otherRegexp.flags & RE2.NON_GREEDY)
            && thisRegexp.subs[0].equals(otherRegexp.subs[0]);
      case REPEAT ->
        (thisRegexp.flags & RE2.NON_GREEDY) == (otherRegexp.flags & RE2.NON_GREEDY)
            && thisRegexp.min == otherRegexp.min
            && thisRegexp.max == otherRegexp.max
            && thisRegexp.subs[0].equals(otherRegexp.subs[0]);
      case CAPTURE ->
        thisRegexp.cap == otherRegexp.cap
            && (thisRegexp.name == null
                ? otherRegexp.name == null
                : thisRegexp.name.equals(otherRegexp.name))
            && thisRegexp.subs[0].equals(otherRegexp.subs[0]);
      default -> true;
    };
  }

  private static boolean areSubExpressionsEqual(Regexp first, Regexp second) {
    if (first.subs.length != second.subs.length) {
      return false;
    }
    for (int i = 0; i < first.subs.length; ++i) {
      if (!first.subs[i].equals(second.subs[i])) {
        return false;
      }
    }
    return true;
  }
}