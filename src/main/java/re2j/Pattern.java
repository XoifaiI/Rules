package re2j;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public final class Pattern implements Serializable {
  @Serial
  private static final long serialVersionUID = 0;

  public static final int CASE_INSENSITIVE = 1;
  public static final int DOTALL = 2;
  public static final int MULTILINE = 4;
  public static final int DISABLE_UNICODE_GROUPS = 8;
  public static final int LONGEST_MATCH = 16;

  private final String pattern;
  private final int flags;
  private final transient RE2 re2;

  Pattern(String pattern, int flags, RE2 re2) {
    if (pattern == null) {
      throw new NullPointerException("pattern is null");
    }
    if (re2 == null) {
      throw new NullPointerException("re2 is null");
    }
    this.pattern = pattern;
    this.flags = flags;
    this.re2 = re2;
  }

  void reset() {
    re2.reset();
  }

  public int flags() {
    return flags;
  }

  public String pattern() {
    return pattern;
  }

  RE2 re2() {
    return re2;
  }

  public static Pattern compile(String regex) {
    return compile(regex, regex, 0);
  }

  public static Pattern compile(String regex, int flags) {
    var flaggedRegex = regex;
    if ((flags & CASE_INSENSITIVE) != 0) {
      flaggedRegex = "(?i)" + flaggedRegex;
    }
    if ((flags & DOTALL) != 0) {
      flaggedRegex = "(?s)" + flaggedRegex;
    }
    if ((flags & MULTILINE) != 0) {
      flaggedRegex = "(?m)" + flaggedRegex;
    }
    if ((flags & ~(MULTILINE | DOTALL | CASE_INSENSITIVE | DISABLE_UNICODE_GROUPS | LONGEST_MATCH)) != 0) {
      throw new IllegalArgumentException(
          "Flags should only be a combination "
              + "of MULTILINE, DOTALL, CASE_INSENSITIVE, DISABLE_UNICODE_GROUPS, LONGEST_MATCH");
    }
    return compile(flaggedRegex, regex, flags);
  }

  private static Pattern compile(String flaggedRegex, String regex, int flags) {
    int re2Flags = RE2.PERL;
    if ((flags & DISABLE_UNICODE_GROUPS) != 0) {
      re2Flags &= ~RE2.UNICODE_GROUPS;
    }
    return new Pattern(
        regex, flags, RE2.compileImpl(flaggedRegex, re2Flags, (flags & LONGEST_MATCH) != 0));
  }

  public static boolean matches(String regex, CharSequence input) {
    return compile(regex).matcher(input).matches();
  }

  public static boolean matches(String regex, byte[] input) {
    return compile(regex).matcher(input).matches();
  }

  public boolean matches(String input) {
    return this.matcher(input).matches();
  }

  public boolean matches(byte[] input) {
    return this.matcher(input).matches();
  }

  public Matcher matcher(CharSequence input) {
    return new Matcher(this, input);
  }

  public Matcher matcher(byte[] input) {
    return new Matcher(this, MatcherInput.utf8(input));
  }

  Matcher matcher(MatcherInput input) {
    return new Matcher(this, input);
  }

  public String[] split(String input) {
    return split(input, 0);
  }

  public String[] split(String input, int limit) {
    return split(new Matcher(this, input), limit);
  }

  private String[] split(Matcher matcher, int limit) {
    var result = new ArrayList<String>();
    int emptiesSkipped = 0;
    int lastEnd = 0;
    while (matcher.find()) {
      if (lastEnd == 0 && matcher.end() == 0) {
        lastEnd = matcher.end();
        continue;
      }
      if (limit > 0 && result.size() == limit - 1) {
        break;
      }
      if (lastEnd == matcher.start()) {
        if (limit == 0) {
          emptiesSkipped++;
          lastEnd = matcher.end();
          continue;
        }
      } else {
        while (emptiesSkipped > 0) {
          result.add("");
          emptiesSkipped--;
        }
      }
      result.add(matcher.substring(lastEnd, matcher.start()));
      lastEnd = matcher.end();
    }
    if (limit == 0 && lastEnd != matcher.inputLength()) {
      while (emptiesSkipped > 0) {
        result.add("");
        emptiesSkipped--;
      }
      result.add(matcher.substring(lastEnd, matcher.inputLength()));
    }
    if (limit != 0 || result.isEmpty()) {
      result.add(matcher.substring(lastEnd, matcher.inputLength()));
    }
    return result.toArray(new String[0]);
  }

  public static String quote(String string) {
    return RE2.quoteMeta(string);
  }

  @Override
  public String toString() {
    return pattern;
  }

  public int programSize() {
    return re2.numberOfInstructions();
  }

  public int groupCount() {
    return re2.numberOfCapturingGroups();
  }

  public Map<String, Integer> namedGroups() {
    return Collections.unmodifiableMap(re2.namedGroups);
  }

  @Serial
  Object readResolve() {
    return Pattern.compile(pattern, flags);
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof Pattern other)) {
      return false;
    }
    return flags == other.flags && pattern.equals(other.pattern);
  }

  @Override
  public int hashCode() {
    int result = pattern.hashCode();
    result = 31 * result + flags;
    return result;
  }
}