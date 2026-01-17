package re2j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class Parser {

  private static final String ERR_INTERNAL_ERROR = "regexp/syntax: internal error";
  private static final String ERR_INVALID_CHAR_RANGE = "invalid character class range";
  private static final String ERR_INVALID_ESCAPE = "invalid escape sequence";
  private static final String ERR_INVALID_NAMED_CAPTURE = "invalid named capture";
  private static final String ERR_INVALID_PERL_OP = "invalid or unsupported Perl syntax";
  private static final String ERR_INVALID_REPEAT_OP = "invalid nested repetition operator";
  private static final String ERR_INVALID_REPEAT_SIZE = "invalid repeat count";
  private static final String ERR_MISSING_BRACKET = "missing closing ]";
  private static final String ERR_MISSING_PAREN = "missing closing )";
  private static final String ERR_MISSING_REPEAT_ARGUMENT = "missing argument to repetition operator";
  private static final String ERR_TRAILING_BACKSLASH = "trailing backslash at end of expression";
  private static final String ERR_DUPLICATE_NAMED_CAPTURE = "duplicate capture group name";
  private static final String ERR_NESTING_DEPTH_EXCEEDED = "nesting depth exceeded";
  private static final String ERR_TOO_MANY_ALTERNATIONS = "too many alternations";

  private static final int MAX_NESTING_DEPTH = 1000;
  private static final int MAX_ALTERNATIONS = 10000;
  private static final int MAX_CAPTURE_NAME_LENGTH = 64;

  private static final int[][] ANY_TABLE = { { 0, Unicode.MAX_RUNE, 1 } };

  private final String wholeRegexp;
  private int flags;
  private final Stack stack = new Stack();
  private Regexp free;
  private int numCap = 0;
  private int nestingDepth = 0;
  private int alternationCount = 0;
  private final Map<String, Integer> namedGroups = new HashMap<>();

  private Parser(String wholeRegexp, int flags) {
    this.wholeRegexp = wholeRegexp;
    this.flags = flags;
  }

  public static Regexp parse(String pattern, int flags) throws PatternSyntaxException {
    return new Parser(pattern, flags).parseInternal();
  }

  public static Regexp literalRegexp(String string, int flags) {
    var regexp = new Regexp(Regexp.Op.LITERAL);
    regexp.flags = flags;
    regexp.runes = Utils.stringToRunes(string);
    return regexp;
  }

  private Regexp parseInternal() throws PatternSyntaxException {
    if ((flags & RE2.LITERAL) != 0) {
      return literalRegexp(wholeRegexp, flags);
    }
    var iterator = new StringIterator(wholeRegexp);
    int lastRepeatPos = -1;
    while (iterator.more()) {
      int repeatPos = -1;
      bigswitch: switch (iterator.peek()) {
        case '(' -> {
          if (++nestingDepth > MAX_NESTING_DEPTH) {
            throw new PatternSyntaxException(ERR_NESTING_DEPTH_EXCEEDED, wholeRegexp);
          }
          if ((flags & RE2.PERL_X) != 0 && iterator.lookingAt("(?")) {
            parsePerlFlags(iterator);
            break bigswitch;
          }
          op(Regexp.Op.LEFT_PAREN).cap = ++numCap;
          iterator.skip(1);
        }
        case '|' -> {
          if (++alternationCount > MAX_ALTERNATIONS) {
            throw new PatternSyntaxException(ERR_TOO_MANY_ALTERNATIONS, wholeRegexp);
          }
          parseVerticalBar();
          iterator.skip(1);
        }
        case ')' -> {
          nestingDepth--;
          parseRightParen();
          iterator.skip(1);
        }
        case '^' -> {
          iterator.skip(1);
          if ((flags & RE2.ONE_LINE) != 0) {
            op(Regexp.Op.BEGIN_TEXT);
          } else {
            op(Regexp.Op.BEGIN_LINE);
          }
        }
        case '$' -> {
          iterator.skip(1);
          if ((flags & RE2.ONE_LINE) != 0) {
            op(Regexp.Op.END_TEXT).flags |= RE2.WAS_DOLLAR;
          } else {
            op(Regexp.Op.END_LINE);
          }
        }
        case '.' -> {
          iterator.skip(1);
          if ((flags & RE2.DOT_NL) != 0) {
            op(Regexp.Op.ANY_CHAR);
          } else {
            op(Regexp.Op.ANY_CHAR_NOT_NL);
          }
        }
        case '[' -> parseClass(iterator);
        case '*', '+', '?' -> {
          repeatPos = iterator.pos();
          int beforePos = iterator.pos();
          switch (iterator.pop()) {
            case '*' -> repeat(Regexp.Op.STAR, 0, -1, beforePos, iterator, lastRepeatPos);
            case '+' -> repeat(Regexp.Op.PLUS, 1, -1, beforePos, iterator, lastRepeatPos);
            case '?' -> repeat(Regexp.Op.QUEST, 0, 1, beforePos, iterator, lastRepeatPos);
            default -> throw new IllegalStateException();
          }
        }
        case '{' -> {
          repeatPos = iterator.pos();
          int beforePos = iterator.pos();
          int minMax = parseRepeat(iterator);
          if (minMax < 0) {
            iterator.rewindTo(beforePos);
            literal(iterator.pop());
            break bigswitch;
          }
          int min = minMax >> 16;
          int max = (short) (minMax & 0xffff);
          repeat(Regexp.Op.REPEAT, min, max, beforePos, iterator, lastRepeatPos);
        }
        case '\\' -> {
          int beforePos = iterator.pos();
          iterator.skip(1);
          if ((flags & RE2.PERL_X) != 0 && iterator.more()) {
            int codePoint = iterator.pop();
            switch (codePoint) {
              case 'A' -> {
                op(Regexp.Op.BEGIN_TEXT);
                break bigswitch;
              }
              case 'b' -> {
                op(Regexp.Op.WORD_BOUNDARY);
                break bigswitch;
              }
              case 'B' -> {
                op(Regexp.Op.NO_WORD_BOUNDARY);
                break bigswitch;
              }
              case 'C' -> throw new PatternSyntaxException(ERR_INVALID_ESCAPE, "\\C");
              case 'Q' -> {
                String literalText = iterator.rest();
                int endIndex = literalText.indexOf("\\E");
                if (endIndex >= 0) {
                  literalText = literalText.substring(0, endIndex);
                }
                iterator.skipString(literalText);
                iterator.skipString("\\E");
                for (int charIndex = 0; charIndex < literalText.length();) {
                  int literalCodePoint = literalText.codePointAt(charIndex);
                  literal(literalCodePoint);
                  charIndex += Character.charCount(literalCodePoint);
                }
                break bigswitch;
              }
              case 'z' -> {
                op(Regexp.Op.END_TEXT);
                break bigswitch;
              }
              default -> iterator.rewindTo(beforePos);
            }
          }
          var regexp = newRegexp(Regexp.Op.CHAR_CLASS);
          regexp.flags = flags;
          if (iterator.lookingAt("\\p") || iterator.lookingAt("\\P")) {
            var charClass = new CharClass();
            if (parseUnicodeClass(iterator, charClass)) {
              regexp.runes = charClass.toArray();
              push(regexp);
              break bigswitch;
            }
          }
          var charClass = new CharClass();
          if (parsePerlClassEscape(iterator, charClass)) {
            regexp.runes = charClass.toArray();
            push(regexp);
            break bigswitch;
          }
          iterator.rewindTo(beforePos);
          reuse(regexp);
          literal(parseEscape(iterator));
        }
        default -> literal(iterator.pop());
      }
      lastRepeatPos = repeatPos;
    }
    concat();
    if (swapVerticalBar()) {
      pop();
    }
    alternate();
    int stackSize = stack.size();
    if (stackSize != 1) {
      throw new PatternSyntaxException(ERR_MISSING_PAREN, wholeRegexp);
    }
    var result = stack.get(0);
    result.namedGroups = namedGroups;
    return result;
  }

  private static final String ERR_INVALID_REPEAT_SYNTAX = "invalid repeat syntax";

  private static int parseRepeat(StringIterator iterator) throws PatternSyntaxException {
    int start = iterator.pos();
    if (!iterator.more() || !iterator.lookingAt('{')) {
      return -1;
    }
    iterator.skip(1);

    if (!iterator.more() || iterator.peek() < '0' || iterator.peek() > '9') {
      iterator.rewindTo(start);
      return -1;
    }

    int min = parseInt(iterator);

    if (!iterator.more()) {
      throw new PatternSyntaxException(ERR_INVALID_REPEAT_SYNTAX, iterator.from(start));
    }

    int max;
    if (iterator.lookingAt('}')) {
      max = min;
    } else if (iterator.lookingAt(',')) {
      iterator.skip(1);
      if (!iterator.more()) {
        throw new PatternSyntaxException(ERR_INVALID_REPEAT_SYNTAX, iterator.from(start));
      }
      if (iterator.lookingAt('}')) {
        max = -1;
      } else {
        max = parseInt(iterator);
        if (max == -1) {
          throw new PatternSyntaxException(ERR_INVALID_REPEAT_SYNTAX, iterator.from(start));
        }
      }
    } else {
      throw new PatternSyntaxException(ERR_INVALID_REPEAT_SYNTAX, iterator.from(start));
    }

    if (!iterator.more() || !iterator.lookingAt('}')) {
      throw new PatternSyntaxException(ERR_INVALID_REPEAT_SYNTAX, iterator.from(start));
    }
    iterator.skip(1);

    if (min < 0 || min > 1000 || max == -2 || max > 1000 || (max >= 0 && min > max)) {
      throw new PatternSyntaxException(ERR_INVALID_REPEAT_SIZE, iterator.from(start));
    }
    return (min << 16) | (max & 0xffff);
  }

  private void parsePerlFlags(StringIterator iterator) throws PatternSyntaxException {
    int startPos = iterator.pos();

    String rest = iterator.rest();
    if (rest.startsWith("(?P<") || rest.startsWith("(?<")) {
      int nameBegin = rest.charAt(2) == 'P' ? 4 : 3;
      int nameEnd = rest.indexOf('>', nameBegin);
      if (nameEnd < 0) {
        throw new PatternSyntaxException(ERR_INVALID_NAMED_CAPTURE, rest);
      }
      String name = rest.substring(nameBegin, nameEnd);
      if (name.length() > MAX_CAPTURE_NAME_LENGTH) {
        throw new PatternSyntaxException(
            "capture group name exceeds maximum length of " + MAX_CAPTURE_NAME_LENGTH, name);
      }

      iterator.skipString(name);
      iterator.skip(nameBegin + 1);
      if (!isValidCaptureName(name)) {
        throw new PatternSyntaxException(ERR_INVALID_NAMED_CAPTURE, rest.substring(0, nameEnd + 1));
      }
      var regexp = op(Regexp.Op.LEFT_PAREN);
      regexp.cap = ++numCap;
      if (namedGroups.put(name, numCap) != null) {
        throw new PatternSyntaxException(ERR_DUPLICATE_NAMED_CAPTURE, name);
      }
      regexp.name = name;
      return;
    }

    iterator.skip(2);
    int localFlags = this.flags;
    int sign = +1;
    boolean sawFlag = false;
    loop: while (iterator.more()) {
      int codePoint = iterator.pop();
      switch (codePoint) {
        default:
          break loop;
        case 'i':
          localFlags |= RE2.FOLD_CASE;
          sawFlag = true;
          break;
        case 'm':
          localFlags &= ~RE2.ONE_LINE;
          sawFlag = true;
          break;
        case 's':
          localFlags |= RE2.DOT_NL;
          sawFlag = true;
          break;
        case 'U':
          localFlags |= RE2.NON_GREEDY;
          sawFlag = true;
          break;
        case '-':
          if (sign < 0) {
            break loop;
          }
          sign = -1;
          localFlags = ~localFlags;
          sawFlag = false;
          break;
        case ':':
        case ')':
          if (sign < 0) {
            if (!sawFlag) {
              break loop;
            }
            localFlags = ~localFlags;
          }
          if (codePoint == ':') {
            op(Regexp.Op.LEFT_PAREN);
          }
          this.flags = localFlags;
          return;
      }
    }
    throw new PatternSyntaxException(ERR_INVALID_PERL_OP, iterator.from(startPos));
  }

  private static boolean isValidCaptureName(String name) {
    if (name.isEmpty()) {
      return false;
    }

    char firstChar = name.charAt(0);
    if (firstChar != '_'
        && !(firstChar >= 'A' && firstChar <= 'Z')
        && !(firstChar >= 'a' && firstChar <= 'z')) {
      return false;
    }

    for (int charIndex = 1; charIndex < name.length(); ++charIndex) {
      char currentChar = name.charAt(charIndex);
      if (currentChar != '_' && !Utils.isalnum(currentChar)) {
        return false;
      }
    }
    return true;
  }

  private Regexp newRegexp(Regexp.Op op) {
    var regexp = free;
    if (regexp != null && regexp.subs != null && regexp.subs.length > 0) {
      free = regexp.subs[0];
      regexp.reinit();
      regexp.op = op;
    } else {
      regexp = new Regexp(op);
    }
    return regexp;
  }

  private void reuse(Regexp regexp) {
    if (regexp.subs != null && regexp.subs.length > 0) {
      regexp.subs[0] = free;
    }
    free = regexp;
  }

  private Regexp pop() {
    return stack.remove(stack.size() - 1);
  }

  private Regexp[] popToPseudo() {
    int stackSize = stack.size();
    int startIndex = stackSize;
    while (startIndex > 0 && !stack.get(startIndex - 1).op.isPseudo()) {
      startIndex--;
    }
    var result = stack.subList(startIndex, stackSize).toArray(new Regexp[0]);
    stack.removeRange(startIndex, stackSize);
    return result;
  }

  private Regexp push(Regexp regexp) {
    if (regexp.op == Regexp.Op.CHAR_CLASS
        && regexp.runes.length == 2
        && regexp.runes[0] == regexp.runes[1]) {
      if (maybeConcat(regexp.runes[0], flags & ~RE2.FOLD_CASE)) {
        return null;
      }
      regexp.op = Regexp.Op.LITERAL;
      regexp.runes = new int[] { regexp.runes[0] };
      regexp.flags = flags & ~RE2.FOLD_CASE;
    } else if ((regexp.op == Regexp.Op.CHAR_CLASS
        && regexp.runes.length == 4
        && regexp.runes[0] == regexp.runes[1]
        && regexp.runes[2] == regexp.runes[3]
        && Unicode.simpleFold(regexp.runes[0]) == regexp.runes[2]
        && Unicode.simpleFold(regexp.runes[2]) == regexp.runes[0])
        || (regexp.op == Regexp.Op.CHAR_CLASS
            && regexp.runes.length == 2
            && regexp.runes[0] + 1 == regexp.runes[1]
            && Unicode.simpleFold(regexp.runes[0]) == regexp.runes[1]
            && Unicode.simpleFold(regexp.runes[1]) == regexp.runes[0])) {
      if (maybeConcat(regexp.runes[0], flags | RE2.FOLD_CASE)) {
        return null;
      }
      regexp.op = Regexp.Op.LITERAL;
      regexp.runes = new int[] { regexp.runes[0] };
      regexp.flags = flags | RE2.FOLD_CASE;
    } else {
      maybeConcat(-1, 0);
    }
    stack.add(regexp);
    return regexp;
  }

  private boolean maybeConcat(int rune, int flags) {
    int stackSize = stack.size();
    if (stackSize < 2) {
      return false;
    }
    var topRegexp = stack.get(stackSize - 1);
    var secondRegexp = stack.get(stackSize - 2);
    if (topRegexp.op != Regexp.Op.LITERAL
        || secondRegexp.op != Regexp.Op.LITERAL
        || (topRegexp.flags & RE2.FOLD_CASE) != (secondRegexp.flags & RE2.FOLD_CASE)) {
      return false;
    }
    secondRegexp.runes = concatRunes(secondRegexp.runes, topRegexp.runes);
    if (rune >= 0) {
      topRegexp.runes = new int[] { rune };
      topRegexp.flags = flags;
      return true;
    }
    pop();
    reuse(topRegexp);
    return false;
  }

  private Regexp newLiteral(int rune, int flags) {
    var regexp = newRegexp(Regexp.Op.LITERAL);
    regexp.flags = flags;
    if ((flags & RE2.FOLD_CASE) != 0) {
      rune = minFoldRune(rune);
    }
    regexp.runes = new int[] { rune };
    return regexp;
  }

  private static int minFoldRune(int rune) {
    if (rune < Unicode.MIN_FOLD || rune > Unicode.MAX_FOLD) {
      return rune;
    }
    int minRune = rune;
    int originalRune = rune;
    for (rune = Unicode.simpleFold(rune); rune != originalRune; rune = Unicode.simpleFold(rune)) {
      if (minRune > rune) {
        minRune = rune;
      }
    }
    return minRune;
  }

  private void literal(int rune) {
    push(newLiteral(rune, flags));
  }

  private Regexp op(Regexp.Op op) {
    var regexp = newRegexp(op);
    regexp.flags = flags;
    return push(regexp);
  }

  private void repeat(
      Regexp.Op op, int min, int max, int beforePos, StringIterator iterator, int lastRepeatPos)
      throws PatternSyntaxException {
    int localFlags = this.flags;
    if ((localFlags & RE2.PERL_X) != 0) {
      if (iterator.more() && iterator.lookingAt('?')) {
        iterator.skip(1);
        localFlags ^= RE2.NON_GREEDY;
      }
      if (lastRepeatPos != -1) {
        throw new PatternSyntaxException(ERR_INVALID_REPEAT_OP, iterator.from(lastRepeatPos));
      }
    }
    int stackSize = stack.size();
    if (stackSize == 0) {
      throw new PatternSyntaxException(ERR_MISSING_REPEAT_ARGUMENT, iterator.from(beforePos));
    }
    var subRegexp = stack.get(stackSize - 1);
    if (subRegexp.op.isPseudo()) {
      throw new PatternSyntaxException(ERR_MISSING_REPEAT_ARGUMENT, iterator.from(beforePos));
    }
    var regexp = newRegexp(op);
    regexp.min = min;
    regexp.max = max;
    regexp.flags = localFlags;
    regexp.subs = new Regexp[] { subRegexp };
    stack.set(stackSize - 1, regexp);
  }

  private void parseVerticalBar() {
    concat();
    if (!swapVerticalBar()) {
      op(Regexp.Op.VERTICAL_BAR);
    }
  }

  private void parseRightParen() throws PatternSyntaxException {
    concat();
    if (swapVerticalBar()) {
      pop();
    }
    alternate();

    int stackSize = stack.size();
    if (stackSize < 2) {
      throw new PatternSyntaxException(ERR_INTERNAL_ERROR, "stack underflow");
    }
    var innerRegexp = pop();
    var openParenRegexp = pop();
    if (openParenRegexp.op != Regexp.Op.LEFT_PAREN) {
      throw new PatternSyntaxException(ERR_MISSING_PAREN, wholeRegexp);
    }

    this.flags = openParenRegexp.flags;
    if (openParenRegexp.cap == 0) {
      push(innerRegexp);
    } else {
      openParenRegexp.op = Regexp.Op.CAPTURE;
      openParenRegexp.subs = new Regexp[] { innerRegexp };
      push(openParenRegexp);
    }
  }

  private Regexp concat() {
    maybeConcat(-1, 0);
    var subs = popToPseudo();
    if (subs.length == 0) {
      return push(newRegexp(Regexp.Op.EMPTY_MATCH));
    }
    return push(collapse(subs, Regexp.Op.CONCAT));
  }

  private Regexp alternate() {
    var subs = popToPseudo();
    if (subs.length > 0) {
      cleanAlt(subs[subs.length - 1]);
    }
    if (subs.length == 0) {
      return push(newRegexp(Regexp.Op.NO_MATCH));
    }
    return push(collapse(subs, Regexp.Op.ALTERNATE));
  }

  private void cleanAlt(Regexp regexp) {
    if (regexp.op == Regexp.Op.CHAR_CLASS) {
      regexp.runes = new CharClass(regexp.runes).cleanClass().toArray();
      if (regexp.runes.length == 2
          && regexp.runes[0] == 0
          && regexp.runes[1] == Unicode.MAX_RUNE) {
        regexp.runes = null;
        regexp.op = Regexp.Op.ANY_CHAR;
      } else if (regexp.runes.length == 4
          && regexp.runes[0] == 0
          && regexp.runes[1] == '\n' - 1
          && regexp.runes[2] == '\n' + 1
          && regexp.runes[3] == Unicode.MAX_RUNE) {
        regexp.runes = null;
        regexp.op = Regexp.Op.ANY_CHAR_NOT_NL;
      }
    }
  }

  private Regexp collapse(Regexp[] subs, Regexp.Op op) {
    if (subs.length == 1) {
      return subs[0];
    }
    int totalLength = 0;
    for (var sub : subs) {
      totalLength += (sub.op == op) ? sub.subs.length : 1;
    }
    var newSubs = new Regexp[totalLength];
    int destIndex = 0;
    for (var sub : subs) {
      if (sub.op == op) {
        System.arraycopy(sub.subs, 0, newSubs, destIndex, sub.subs.length);
        destIndex += sub.subs.length;
        reuse(sub);
      } else {
        newSubs[destIndex++] = sub;
      }
    }
    var regexp = newRegexp(op);
    regexp.subs = newSubs;
    if (op == Regexp.Op.ALTERNATE) {
      regexp.subs = factor(regexp.subs, regexp.flags);
      if (regexp.subs.length == 1) {
        var old = regexp;
        regexp = regexp.subs[0];
        reuse(old);
      }
    }
    return regexp;
  }

  private Regexp[] factor(Regexp[] array, int flags) {
    if (array.length < 2) {
      return array;
    }

    int arrayStart = 0;
    int arrayLength = array.length;
    int outputLength = 0;
    int[] prefixRunes = null;
    int prefixLength = 0;
    int prefixFlags = 0;
    int rangeStart = 0;
    for (int index = 0; index <= arrayLength; index++) {
      int[] currentRunes = null;
      int currentRunesLength = 0;
      int currentFlags = 0;
      if (index < arrayLength) {
        var regexp = array[arrayStart + index];
        if (regexp.op == Regexp.Op.CONCAT && regexp.subs.length > 0) {
          regexp = regexp.subs[0];
        }
        if (regexp.op == Regexp.Op.LITERAL) {
          currentRunes = regexp.runes;
          currentRunesLength = regexp.runes.length;
          currentFlags = regexp.flags & RE2.FOLD_CASE;
        }
        if (currentFlags == prefixFlags) {
          int sameCount = 0;
          while (sameCount < prefixLength
              && sameCount < currentRunesLength
              && prefixRunes[sameCount] == currentRunes[sameCount]) {
            sameCount++;
          }
          if (sameCount > 0) {
            prefixLength = sameCount;
            continue;
          }
        }
      }

      if (index == rangeStart) {
      } else if (index == rangeStart + 1) {
        array[outputLength++] = array[arrayStart + rangeStart];
      } else {
        var prefix = newRegexp(Regexp.Op.LITERAL);
        prefix.flags = prefixFlags;
        prefix.runes = Utils.subarray(prefixRunes, 0, prefixLength);
        for (int subIndex = rangeStart; subIndex < index; subIndex++) {
          array[arrayStart + subIndex] = removeLeadingString(array[arrayStart + subIndex], prefixLength);
        }
        var suffix = collapse(subarray(array, arrayStart + rangeStart, arrayStart + index), Regexp.Op.ALTERNATE);
        var regexp = newRegexp(Regexp.Op.CONCAT);
        regexp.subs = new Regexp[] { prefix, suffix };
        array[outputLength++] = regexp;
      }

      rangeStart = index;
      prefixRunes = currentRunes;
      prefixLength = currentRunesLength;
      prefixFlags = currentFlags;
    }

    arrayLength = outputLength;
    arrayStart = 0;

    rangeStart = 0;
    outputLength = 0;
    Regexp firstRegexp = null;
    for (int index = 0; index <= arrayLength; index++) {
      Regexp currentFirst = null;
      if (index < arrayLength) {
        currentFirst = leadingRegexp(array[arrayStart + index]);
        if (firstRegexp != null
            && firstRegexp.equals(currentFirst)
            && (isCharClass(firstRegexp)
                || (firstRegexp.op == Regexp.Op.REPEAT
                    && firstRegexp.min == firstRegexp.max
                    && isCharClass(firstRegexp.subs[0])))) {
          continue;
        }
      }

      if (index == rangeStart) {
      } else if (index == rangeStart + 1) {
        array[outputLength++] = array[arrayStart + rangeStart];
      } else {
        var prefix = firstRegexp;
        for (int subIndex = rangeStart; subIndex < index; subIndex++) {
          boolean shouldReuse = subIndex != rangeStart;
          array[arrayStart + subIndex] = removeLeadingRegexp(array[arrayStart + subIndex], shouldReuse);
        }
        var suffix = collapse(subarray(array, arrayStart + rangeStart, arrayStart + index), Regexp.Op.ALTERNATE);
        var regexp = newRegexp(Regexp.Op.CONCAT);
        regexp.subs = new Regexp[] { prefix, suffix };
        array[outputLength++] = regexp;
      }

      rangeStart = index;
      firstRegexp = currentFirst;
    }

    arrayLength = outputLength;
    arrayStart = 0;

    rangeStart = 0;
    outputLength = 0;
    for (int index = 0; index <= arrayLength; index++) {
      if (index < arrayLength && isCharClass(array[arrayStart + index])) {
        continue;
      }

      if (index == rangeStart) {
      } else if (index == rangeStart + 1) {
        array[outputLength++] = array[arrayStart + rangeStart];
      } else {
        int maxIndex = rangeStart;
        for (int subIndex = rangeStart + 1; subIndex < index; subIndex++) {
          Regexp subMax = array[arrayStart + maxIndex];
          Regexp subCurrent = array[arrayStart + subIndex];
          if (subMax.op.ordinal() < subCurrent.op.ordinal()
              || (subMax.op == subCurrent.op
                  && (subMax.runes != null ? subMax.runes.length
                      : 0) < (subCurrent.runes != null ? subCurrent.runes.length : 0))) {
            maxIndex = subIndex;
          }
        }

        Regexp temp = array[arrayStart + rangeStart];
        array[arrayStart + rangeStart] = array[arrayStart + maxIndex];
        array[arrayStart + maxIndex] = temp;

        for (int subIndex = rangeStart + 1; subIndex < index; subIndex++) {
          mergeCharClass(array[arrayStart + rangeStart], array[arrayStart + subIndex]);
          reuse(array[arrayStart + subIndex]);
        }
        cleanAlt(array[arrayStart + rangeStart]);
        array[outputLength++] = array[arrayStart + rangeStart];
      }

      if (index < arrayLength) {
        array[outputLength++] = array[arrayStart + index];
      }
      rangeStart = index + 1;
    }

    arrayLength = outputLength;
    arrayStart = 0;

    rangeStart = 0;
    outputLength = 0;
    for (int index = 0; index < arrayLength; ++index) {
      if (index + 1 < arrayLength
          && array[arrayStart + index].op == Regexp.Op.EMPTY_MATCH
          && array[arrayStart + index + 1].op == Regexp.Op.EMPTY_MATCH) {
        continue;
      }
      array[outputLength++] = array[arrayStart + index];
    }

    arrayLength = outputLength;
    arrayStart = 0;

    return subarray(array, arrayStart, arrayLength);
  }

  private Regexp removeLeadingString(Regexp regexp, int numChars) {
    if (regexp.op == Regexp.Op.CONCAT && regexp.subs.length > 0) {
      var sub = regexp.subs[0];
      sub.runes = Utils.subarray(sub.runes, numChars, sub.runes.length);
      if (sub.runes.length == 0) {
        reuse(sub);
        switch (regexp.subs.length) {
          case 0, 1:
            regexp.op = Regexp.Op.EMPTY_MATCH;
            regexp.subs = null;
            break;
          case 2:
            var old = regexp;
            regexp = regexp.subs[1];
            reuse(old);
            break;
          default:
            regexp.subs = subarray(regexp.subs, 1, regexp.subs.length);
            break;
        }
      }
      return regexp;
    }
    if (regexp.op == Regexp.Op.LITERAL) {
      regexp.runes = Utils.subarray(regexp.runes, numChars, regexp.runes.length);
      if (regexp.runes.length == 0) {
        regexp.op = Regexp.Op.EMPTY_MATCH;
      }
    }
    return regexp;
  }

  private static Regexp leadingRegexp(Regexp regexp) {
    if (regexp.op == Regexp.Op.EMPTY_MATCH) {
      return null;
    }
    if (regexp.op == Regexp.Op.CONCAT && regexp.subs.length > 0) {
      var sub = regexp.subs[0];
      if (sub.op == Regexp.Op.EMPTY_MATCH) {
        return null;
      }
      return sub;
    }
    return regexp;
  }

  private Regexp removeLeadingRegexp(Regexp regexp, boolean shouldReuse) {
    if (regexp.op == Regexp.Op.CONCAT && regexp.subs.length > 0) {
      if (shouldReuse) {
        reuse(regexp.subs[0]);
      }
      regexp.subs = subarray(regexp.subs, 1, regexp.subs.length);
      switch (regexp.subs.length) {
        case 0:
          regexp.op = Regexp.Op.EMPTY_MATCH;
          regexp.subs = null;
          break;
        case 1:
          var old = regexp;
          regexp = regexp.subs[0];
          reuse(old);
          break;
        default:
          break;
      }
      return regexp;
    }
    if (shouldReuse) {
      reuse(regexp);
    }
    return newRegexp(Regexp.Op.EMPTY_MATCH);
  }

  private static boolean isCharClass(Regexp regexp) {
    return (regexp.op == Regexp.Op.LITERAL && regexp.runes.length == 1)
        || regexp.op == Regexp.Op.CHAR_CLASS
        || regexp.op == Regexp.Op.ANY_CHAR_NOT_NL
        || regexp.op == Regexp.Op.ANY_CHAR;
  }

  private static boolean matchRune(Regexp regexp, int rune) {
    return switch (regexp.op) {
      case LITERAL -> regexp.runes.length == 1 && regexp.runes[0] == rune;
      case CHAR_CLASS -> {
        for (int index = 0; index < regexp.runes.length; index += 2) {
          if (regexp.runes[index] <= rune && rune <= regexp.runes[index + 1]) {
            yield true;
          }
        }
        yield false;
      }
      case ANY_CHAR_NOT_NL -> rune != '\n';
      case ANY_CHAR -> true;
      default -> false;
    };
  }

  private static void mergeCharClass(Regexp destination, Regexp source) {
    switch (destination.op) {
      case ANY_CHAR -> {
      }
      case ANY_CHAR_NOT_NL -> {
        if (matchRune(source, '\n')) {
          destination.op = Regexp.Op.ANY_CHAR;
        }
      }
      case CHAR_CLASS -> {
        if (source.op == Regexp.Op.LITERAL) {
          destination.runes = new CharClass(destination.runes).appendLiteral(source.runes[0], source.flags).toArray();
        } else {
          destination.runes = new CharClass(destination.runes).appendClass(source.runes).toArray();
        }
      }
      case LITERAL -> {
        if (source.runes[0] == destination.runes[0] && source.flags == destination.flags) {
          break;
        }
        destination.op = Regexp.Op.CHAR_CLASS;
        destination.runes = new CharClass()
            .appendLiteral(destination.runes[0], destination.flags)
            .appendLiteral(source.runes[0], source.flags)
            .toArray();
      }
      default -> {
      }
    }
  }

  private boolean swapVerticalBar() {
    int stackSize = stack.size();
    if (stackSize >= 3
        && stack.get(stackSize - 2).op == Regexp.Op.VERTICAL_BAR
        && isCharClass(stack.get(stackSize - 1))
        && isCharClass(stack.get(stackSize - 3))) {
      var topRegexp = stack.get(stackSize - 1);
      var thirdRegexp = stack.get(stackSize - 3);
      if (topRegexp.op.ordinal() > thirdRegexp.op.ordinal()) {
        var temp = thirdRegexp;
        thirdRegexp = topRegexp;
        topRegexp = temp;
        stack.set(stackSize - 3, thirdRegexp);
      }
      mergeCharClass(thirdRegexp, topRegexp);
      reuse(topRegexp);
      pop();
      return true;
    }
    if (stackSize >= 2) {
      var topRegexp = stack.get(stackSize - 1);
      var secondRegexp = stack.get(stackSize - 2);
      if (secondRegexp.op == Regexp.Op.VERTICAL_BAR) {
        if (stackSize >= 3) {
          cleanAlt(stack.get(stackSize - 3));
        }
        stack.set(stackSize - 2, topRegexp);
        stack.set(stackSize - 1, secondRegexp);
        return true;
      }
    }
    return false;
  }

  private static int parseInt(StringIterator iterator) {
    int start = iterator.pos();
    while (iterator.more() && iterator.peek() >= '0' && iterator.peek() <= '9') {
      iterator.skip(1);
    }
    String numString = iterator.from(start);
    if (numString.isEmpty() || (numString.length() > 1 && numString.charAt(0) == '0')) {
      return -1;
    }
    if (numString.length() > 8) {
      return -2;
    }
    return Integer.parseInt(numString);
  }

  private static int parseEscape(StringIterator iterator) throws PatternSyntaxException {
    int startPos = iterator.pos();
    iterator.skip(1);
    if (!iterator.more()) {
      throw new PatternSyntaxException(ERR_TRAILING_BACKSLASH, "");
    }
    int codePoint = iterator.pop();
    if (!Utils.isalnum(codePoint)) {
      return codePoint;
    }
    switch (codePoint) {
      case '0', '1', '2', '3', '4', '5', '6', '7' -> {
        int octalValue = codePoint - '0';
        for (int digitIndex = 1; digitIndex < 3; digitIndex++) {
          if (!iterator.more() || iterator.peek() < '0' || iterator.peek() > '7') {
            break;
          }
          octalValue = octalValue * 8 + iterator.pop() - '0';
        }
        return octalValue;
      }
      case 'x' -> {
        if (!iterator.more()) {
          throw new PatternSyntaxException(ERR_INVALID_ESCAPE, iterator.from(startPos));
        }
        codePoint = iterator.pop();
        if (codePoint == '{') {
          int hexDigitCount = 0;
          int hexValue = 0;
          while (iterator.more()) {
            codePoint = iterator.pop();
            if (codePoint == '}') {
              if (hexDigitCount == 0) {
                throw new PatternSyntaxException(ERR_INVALID_ESCAPE, iterator.from(startPos));
              }
              return hexValue;
            }
            int digitValue = Utils.unhex(codePoint);
            if (digitValue < 0) {
              throw new PatternSyntaxException(ERR_INVALID_ESCAPE, iterator.from(startPos));
            }
            hexValue = hexValue * 16 + digitValue;
            if (hexValue > Unicode.MAX_RUNE) {
              throw new PatternSyntaxException(ERR_INVALID_ESCAPE, iterator.from(startPos));
            }
            hexDigitCount++;
          }
          throw new PatternSyntaxException(ERR_INVALID_ESCAPE, iterator.from(startPos));
        }
        int highNibble = Utils.unhex(codePoint);
        if (!iterator.more()) {
          throw new PatternSyntaxException(ERR_INVALID_ESCAPE, iterator.from(startPos));
        }
        codePoint = iterator.pop();
        int lowNibble = Utils.unhex(codePoint);
        if (highNibble < 0 || lowNibble < 0) {
          throw new PatternSyntaxException(ERR_INVALID_ESCAPE, iterator.from(startPos));
        }
        return highNibble * 16 + lowNibble;
      }
      case 'a' -> {
        return 7;
      }
      case 'f' -> {
        return '\f';
      }
      case 'n' -> {
        return '\n';
      }
      case 'r' -> {
        return '\r';
      }
      case 't' -> {
        return '\t';
      }
      case 'v' -> {
        return 11;
      }
    }
    throw new PatternSyntaxException(ERR_INVALID_ESCAPE, iterator.from(startPos));
  }

  private boolean parsePerlClassEscape(StringIterator iterator, CharClass charClass) {
    int beforePos = iterator.pos();
    if (!iterator.more() || iterator.pop() != '\\' || !iterator.more()) {
      iterator.rewindTo(beforePos);
      return false;
    }
    int codePoint = iterator.pop();
    var group = CharGroup.PERL_GROUPS.get("\\" + (char) codePoint);
    if (group == null) {
      iterator.rewindTo(beforePos);
      return false;
    }
    charClass.appendGroup(group, (flags & RE2.FOLD_CASE) != 0);
    return true;
  }

  private boolean parseNamedClass(StringIterator iterator, CharClass charClass)
      throws PatternSyntaxException {
    String rest = iterator.rest();
    int closingIndex = rest.indexOf(":]");
    if (closingIndex < 0) {
      return false;
    }
    String name = rest.substring(0, closingIndex + 2);
    iterator.skipString(name);
    var group = CharGroup.POSIX_GROUPS.get(name);
    if (group == null) {
      throw new PatternSyntaxException(ERR_INVALID_CHAR_RANGE, name);
    }
    charClass.appendGroup(group, (flags & RE2.FOLD_CASE) != 0);
    return true;
  }

  private static Pair<int[][], int[][]> unicodeTable(String name) {
    if (name.equals("Any")) {
      return new Pair<>(ANY_TABLE, ANY_TABLE);
    }
    int[][] table = UnicodeTables.CATEGORIES.get(name);
    if (table != null) {
      return new Pair<>(table, UnicodeTables.FOLD_CATEGORIES.get(name));
    }
    table = UnicodeTables.SCRIPTS.get(name);
    if (table != null) {
      return new Pair<>(table, UnicodeTables.FOLD_SCRIPT.get(name));
    }
    return null;
  }

  private boolean parseUnicodeClass(StringIterator iterator, CharClass charClass)
      throws PatternSyntaxException {
    int startPos = iterator.pos();
    if ((flags & RE2.UNICODE_GROUPS) == 0
        || (!iterator.lookingAt("\\p") && !iterator.lookingAt("\\P"))) {
      return false;
    }
    iterator.skip(1);
    int sign = +1;
    int codePoint = iterator.pop();
    if (codePoint == 'P') {
      sign = -1;
    }
    if (!iterator.more()) {
      iterator.rewindTo(startPos);
      throw new PatternSyntaxException(ERR_INVALID_CHAR_RANGE, iterator.rest());
    }
    codePoint = iterator.pop();
    String name;
    if (codePoint != '{') {
      name = Utils.runeToString(codePoint);
    } else {
      String rest = iterator.rest();
      int closingIndex = rest.indexOf('}');
      if (closingIndex < 0) {
        iterator.rewindTo(startPos);
        throw new PatternSyntaxException(ERR_INVALID_CHAR_RANGE, iterator.rest());
      }
      name = rest.substring(0, closingIndex);
      iterator.skipString(name);
      iterator.skip(1);
    }
    if (!name.isEmpty() && name.charAt(0) == '^') {
      sign = -sign;
      name = name.substring(1);
    }
    var pair = unicodeTable(name);
    if (pair == null) {
      throw new PatternSyntaxException(ERR_INVALID_CHAR_RANGE, iterator.from(startPos));
    }
    int[][] table = pair.first;
    int[][] foldTable = pair.second;
    if ((flags & RE2.FOLD_CASE) == 0 || foldTable == null) {
      charClass.appendTableWithSign(table, sign);
    } else {
      int[] combinedRanges = new CharClass().appendTable(table).appendTable(foldTable).cleanClass().toArray();
      charClass.appendClassWithSign(combinedRanges, sign);
    }
    return true;
  }

  private void parseClass(StringIterator iterator) throws PatternSyntaxException {
    int startPos = iterator.pos();
    iterator.skip(1);
    var regexp = newRegexp(Regexp.Op.CHAR_CLASS);
    regexp.flags = flags;
    var charClass = new CharClass();
    int sign = +1;
    if (iterator.more() && iterator.lookingAt('^')) {
      sign = -1;
      iterator.skip(1);
      if ((flags & RE2.CLASS_NL) == 0) {
        charClass.appendRange('\n', '\n');
      }
    }
    boolean isFirstChar = true;
    while (!iterator.more() || iterator.peek() != ']' || isFirstChar) {
      if (!iterator.more()) {
        throw new PatternSyntaxException(ERR_MISSING_BRACKET, iterator.from(startPos));
      }
      if (iterator.more()
          && iterator.lookingAt('-')
          && (flags & RE2.PERL_X) == 0
          && !isFirstChar) {
        String rest = iterator.rest();
        if (rest.equals("-") || !rest.startsWith("-]")) {
          iterator.rewindTo(startPos);
          throw new PatternSyntaxException(ERR_INVALID_CHAR_RANGE, iterator.rest());
        }
      }
      isFirstChar = false;
      int beforePos = iterator.pos();
      if (iterator.lookingAt("[:")) {
        if (parseNamedClass(iterator, charClass)) {
          continue;
        }
        iterator.rewindTo(beforePos);
      }
      if (parseUnicodeClass(iterator, charClass)) {
        continue;
      }
      if (parsePerlClassEscape(iterator, charClass)) {
        continue;
      }
      iterator.rewindTo(beforePos);
      int rangeLow = parseClassChar(iterator, startPos);
      int rangeHigh = rangeLow;
      if (iterator.more() && iterator.lookingAt('-')) {
        iterator.skip(1);
        if (iterator.more() && iterator.lookingAt(']')) {
          iterator.skip(-1);
        } else {
          rangeHigh = parseClassChar(iterator, startPos);
          if (rangeHigh < rangeLow) {
            throw new PatternSyntaxException(ERR_INVALID_CHAR_RANGE, iterator.from(beforePos));
          }
        }
      }
      if ((flags & RE2.FOLD_CASE) == 0) {
        charClass.appendRange(rangeLow, rangeHigh);
      } else {
        charClass.appendFoldedRange(rangeLow, rangeHigh);
      }
    }
    iterator.skip(1);
    charClass.cleanClass();
    if (sign < 0) {
      charClass.negateClass();
    }
    regexp.runes = charClass.toArray();
    push(regexp);
  }

  private int parseClassChar(StringIterator iterator, int startPos) throws PatternSyntaxException {
    if (!iterator.more()) {
      throw new PatternSyntaxException(ERR_MISSING_BRACKET, iterator.from(startPos));
    }
    if (iterator.lookingAt('\\')) {
      return parseEscape(iterator);
    }
    return iterator.pop();
  }

  static Regexp[] subarray(Regexp[] array, int start, int end) {
    var result = new Regexp[end - start];
    System.arraycopy(array, start, result, 0, end - start);
    return result;
  }

  private static int[] concatRunes(int[] first, int[] second) {
    var combined = new int[first.length + second.length];
    System.arraycopy(first, 0, combined, 0, first.length);
    System.arraycopy(second, 0, combined, first.length, second.length);
    return combined;
  }

  private record Pair<F, S>(F first, S second) {
  }

  private static final class Stack extends ArrayList<Regexp> {
    @Override
    public void removeRange(int fromIndex, int toIndex) {
      super.removeRange(fromIndex, toIndex);
    }
  }

  static final class StringIterator {
    private final String str;
    private int pos;

    StringIterator(String str) {
      this.str = str;
      this.pos = 0;
    }

    int pos() {
      return pos;
    }

    boolean more() {
      return pos < str.length();
    }

    int peek() {
      return str.codePointAt(pos);
    }

    int pop() {
      int codePoint = str.codePointAt(pos);
      pos += Character.charCount(codePoint);
      return codePoint;
    }

    void skip(int count) {
      pos += count;
    }

    void skipString(String string) {
      pos += string.length();
    }

    void rewindTo(int position) {
      pos = position;
    }

    String from(int start) {
      return str.substring(start, pos);
    }

    String rest() {
      return str.substring(pos);
    }

    boolean lookingAt(char character) {
      return more() && str.charAt(pos) == character;
    }

    boolean lookingAt(String prefix) {
      return str.startsWith(prefix, pos);
    }
  }
}