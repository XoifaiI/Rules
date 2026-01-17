package re2j;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Matcher {
  private static final int MAX_REPLACEMENT_EXPANSIONS = 10000;

  private final Pattern pattern;
  private final int[] groupBounds;
  private final Map<String, Integer> namedGroups;
  private final int groupCount;
  private final int numberOfInstructions;

  private MatcherInput matcherInput;
  private int inputLength;
  private int appendPosition;
  private boolean hasMatch;
  private boolean hasGroups;
  private int anchorFlag;

  private Matcher(Pattern pattern) {
    if (pattern == null) {
      throw new NullPointerException("pattern is null");
    }
    this.pattern = pattern;
    var re2 = pattern.re2();
    groupCount = re2.numberOfCapturingGroups();
    groupBounds = new int[2 + 2 * groupCount];
    namedGroups = re2.namedGroups;
    numberOfInstructions = re2.numberOfInstructions();
  }

  public Matcher(Pattern pattern, CharSequence input) {
    this(pattern);
    reset(input);
  }

  public Matcher(Pattern pattern, MatcherInput input) {
    this(pattern);
    reset(input);
  }

  public Pattern pattern() {
    return pattern;
  }

  public Matcher reset() {
    inputLength = matcherInput.length();
    appendPosition = 0;
    hasMatch = false;
    hasGroups = false;
    return this;
  }

  public Matcher reset(CharSequence input) {
    return reset(MatcherInput.utf16(input));
  }

  public Matcher reset(byte[] bytes) {
    return reset(MatcherInput.utf8(bytes));
  }

  private Matcher reset(MatcherInput input) {
    if (input == null) {
      throw new NullPointerException("input is null");
    }
    matcherInput = input;
    reset();
    return this;
  }

  public int start() {
    return start(0);
  }

  public int end() {
    return end(0);
  }

  public int start(int group) {
    loadGroup(group);
    return groupBounds[2 * group];
  }

  public int start(String group) {
    var groupIndex = namedGroups.get(group);
    if (groupIndex == null) {
      throw new IllegalArgumentException("group '" + group + "' not found");
    }
    return start(groupIndex);
  }

  public int end(int group) {
    loadGroup(group);
    return groupBounds[2 * group + 1];
  }

  public int end(String group) {
    var groupIndex = namedGroups.get(group);
    if (groupIndex == null) {
      throw new IllegalArgumentException("group '" + group + "' not found");
    }
    return end(groupIndex);
  }

  public int programSize() {
    return numberOfInstructions;
  }

  public String group() {
    return group(0);
  }

  public String group(int group) {
    int start = start(group);
    int end = end(group);
    if (start < 0 && end < 0) {
      return null;
    }
    return substring(start, end);
  }

  public String group(String group) {
    var groupIndex = namedGroups.get(group);
    if (groupIndex == null) {
      throw new IllegalArgumentException("group '" + group + "' not found");
    }
    return group(groupIndex);
  }

  public int groupCount() {
    return groupCount;
  }

  private void loadGroup(int group) {
    if (group < 0 || group > groupCount) {
      throw new IndexOutOfBoundsException("Group index out of bounds: " + group);
    }
    if (!hasMatch) {
      throw new IllegalStateException("perhaps no match attempted");
    }
    if (group == 0 || hasGroups) {
      return;
    }
    int searchEnd = groupBounds[1] + 1;
    if (searchEnd > inputLength) {
      searchEnd = inputLength;
    }
    boolean ok = pattern.re2().match(matcherInput, groupBounds[0], searchEnd, anchorFlag, groupBounds, 1 + groupCount);
    if (!ok) {
      throw new IllegalStateException("inconsistency in matching group data");
    }
    hasGroups = true;
  }

  public boolean matches() {
    return genMatch(0, RE2.ANCHOR_BOTH);
  }

  public boolean lookingAt() {
    return genMatch(0, RE2.ANCHOR_START);
  }

  public boolean find() {
    int start = 0;
    if (hasMatch) {
      start = groupBounds[1];
      if (groupBounds[0] == groupBounds[1]) {
        start++;
      }
    }
    return genMatch(start, RE2.UNANCHORED);
  }

  public boolean find(int start) {
    if (start < 0 || start > inputLength) {
      throw new IndexOutOfBoundsException("start index out of bounds: " + start);
    }
    reset();
    return genMatch(start, 0);
  }

  private boolean genMatch(int startByte, int anchor) {
    boolean ok = pattern.re2().match(matcherInput, startByte, inputLength, anchor, groupBounds, 1);
    if (!ok) {
      return false;
    }
    hasMatch = true;
    hasGroups = false;
    anchorFlag = anchor;
    return true;
  }

  String substring(int start, int end) {
    if (matcherInput.getEncoding() == MatcherInput.Encoding.UTF_8) {
      return new String(matcherInput.asBytes(), start, end - start, StandardCharsets.UTF_8);
    }
    return matcherInput.asCharSequence().subSequence(start, end).toString();
  }

  int inputLength() {
    return inputLength;
  }

  public static String quoteReplacement(String source) {
    if (source.indexOf('\\') < 0 && source.indexOf('$') < 0) {
      return source;
    }
    var stringBuilder = new StringBuilder();
    for (int charIndex = 0; charIndex < source.length(); ++charIndex) {
      char currentChar = source.charAt(charIndex);
      if (currentChar == '\\' || currentChar == '$') {
        stringBuilder.append('\\');
      }
      stringBuilder.append(currentChar);
    }
    return stringBuilder.toString();
  }

  public Matcher appendReplacement(StringBuffer stringBuffer, String replacement) {
    var result = new StringBuilder();
    appendReplacement(result, replacement);
    stringBuffer.append(result);
    return this;
  }

  public Matcher appendReplacement(StringBuilder stringBuilder, String replacement) {
    int matchStart = start();
    int matchEnd = end();
    if (appendPosition < matchStart) {
      stringBuilder.append(substring(appendPosition, matchStart));
    }
    appendPosition = matchEnd;
    appendReplacementInternal(stringBuilder, replacement);
    return this;
  }

  private void appendReplacementInternal(StringBuilder stringBuilder, String replacement) {
    int expansionCount = 0;
    int lastAppendedIndex = 0;
    int currentIndex = 0;
    int replacementLength = replacement.length();
    for (; currentIndex < replacementLength - 1; currentIndex++) {
      if (replacement.charAt(currentIndex) == '\\') {
        if (lastAppendedIndex < currentIndex) {
          stringBuilder.append(replacement, lastAppendedIndex, currentIndex);
        }
        currentIndex++;
        lastAppendedIndex = currentIndex;
        continue;
      }
      if (replacement.charAt(currentIndex) == '$') {
        if (++expansionCount > MAX_REPLACEMENT_EXPANSIONS) {
          throw new IllegalArgumentException("too many group references in replacement");
        }
        int nextChar = replacement.charAt(currentIndex + 1);
        if (nextChar >= '0' && nextChar <= '9') {
          int groupNumber = nextChar - '0';
          if (lastAppendedIndex < currentIndex) {
            stringBuilder.append(replacement, lastAppendedIndex, currentIndex);
          }
          for (currentIndex += 2; currentIndex < replacementLength; currentIndex++) {
            nextChar = replacement.charAt(currentIndex);
            if (nextChar < '0' || nextChar > '9' || groupNumber * 10 + nextChar - '0' > groupCount) {
              break;
            }
            groupNumber = groupNumber * 10 + nextChar - '0';
          }
          if (groupNumber > groupCount) {
            throw new IndexOutOfBoundsException("n > number of groups: " + groupNumber);
          }
          String groupValue = group(groupNumber);
          if (groupValue != null) {
            stringBuilder.append(groupValue);
          }
          lastAppendedIndex = currentIndex;
          currentIndex--;
          continue;
        } else if (nextChar == '{') {
          if (lastAppendedIndex < currentIndex) {
            stringBuilder.append(replacement, lastAppendedIndex, currentIndex);
          }
          currentIndex++;
          int closingBraceIndex = currentIndex + 1;
          int maxSearchIndex = Math.min(replacement.length(), closingBraceIndex + 256);
          while (closingBraceIndex < maxSearchIndex
              && replacement.charAt(closingBraceIndex) != '}'
              && replacement.charAt(closingBraceIndex) != ' ') {
            closingBraceIndex++;
          }
          if (closingBraceIndex >= maxSearchIndex || replacement.charAt(closingBraceIndex) != '}') {
            throw new IllegalArgumentException("named capture group is missing trailing '}'");
          }
          String groupName = replacement.substring(currentIndex + 1, closingBraceIndex);
          stringBuilder.append(group(groupName));
          lastAppendedIndex = closingBraceIndex + 1;
          currentIndex = closingBraceIndex;
        }
      }
    }
    if (lastAppendedIndex < replacementLength) {
      stringBuilder.append(replacement, lastAppendedIndex, replacementLength);
    }
  }

  public StringBuffer appendTail(StringBuffer stringBuffer) {
    stringBuffer.append(substring(appendPosition, inputLength));
    return stringBuffer;
  }

  public StringBuilder appendTail(StringBuilder stringBuilder) {
    stringBuilder.append(substring(appendPosition, inputLength));
    return stringBuilder;
  }

  public String replaceAll(String replacement) {
    return replace(replacement, true);
  }

  public String replaceFirst(String replacement) {
    return replace(replacement, false);
  }

  private String replace(String replacement, boolean replaceAll) {
    reset();
    var stringBuffer = new StringBuffer();
    while (find()) {
      appendReplacement(stringBuffer, replacement);
      if (!replaceAll) {
        break;
      }
    }
    appendTail(stringBuffer);
    return stringBuffer.toString();
  }
}