package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class StrconvTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("Strconv", () -> {
        describe("unquote", () -> {
          describe("valid strings", () -> {
            for (String[] tc : UNQUOTE_TESTS) {
              String input = tc[0];
              String expected = tc[1];

              if (expected != null) {
                test("unquotes " + escape(input), () -> {
                  String actual = Strconv.unquote(input);
                  expect(actual).toBe(expected);
                });
              }
            }
          });

          describe("invalid strings", () -> {
            for (String[] tc : UNQUOTE_TESTS) {
              String input = tc[0];
              String expected = tc[1];

              if (expected == null) {
                test("rejects " + escape(input), () -> {
                  expect(() -> Strconv.unquote(input)).toThrow();
                });
              }
            }
          });
        });
      });
    });
  }

  private static String escape(String s) {
    if (s.isEmpty()) {
      return "(empty)";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 32 || c > 126) {
        sb.append(String.format("\\x%02x", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static String rune(int r) {
    return new StringBuilder().appendCodePoint(r).toString();
  }

  private static final String[][] UNQUOTE_TESTS = {
      { "\"\"", "" },
      { "\"a\"", "a" },
      { "\"abc\"", "abc" },
      { "\"☺\"", "☺" },
      { "\"hello world\"", "hello world" },
      { "\"\\xFF\"", "\u00FF" },
      { "\"\\377\"", "\377" },
      { "\"\\u1234\"", "\u1234" },
      { "\"\\U00010111\"", rune(0x10111) },
      { "\"\\U0001011111\"", rune(0x10111) + "11" },
      { "\"\\a\\b\\f\\n\\r\\t\\v\\\\\\\"\"", "\007\b\f\n\r\t\013\\\"" },
      { "\"'\"", "'" },
      { "'a'", "a" },
      { "'☹'", "☹" },
      { "'\\a'", "\u0007" },
      { "'\\x10'", "\u0010" },
      { "'\\377'", "\377" },
      { "'\\u1234'", "\u1234" },
      { "'\\U00010111'", rune(0x10111) },
      { "'\\t'", "\t" },
      { "' '", " " },
      { "'\\''", "'" },
      { "'\"'", "\"" },
      { "``", "" },
      { "`a`", "a" },
      { "`abc`", "abc" },
      { "`☺`", "☺" },
      { "`hello world`", "hello world" },
      { "`\\xFF`", "\\xFF" },
      { "`\\377`", "\\377" },
      { "`\\`", "\\" },
      { "`\n`", "\n" },
      { "`\t`", "\t" },
      { "` `", " " },
      { "", null },
      { "\"", null },
      { "\"a", null },
      { "\"'", null },
      { "b\"", null },
      { "\"\\\"", null },
      { "'\\'", null },
      { "'ab'", null },
      { "\"\\x1!\"", null },
      { "\"\\U12345678\"", null },
      { "\"\\z\"", null },
      { "`", null },
      { "`xxx", null },
      { "`\"", null },
      { "\"\\'\"", null },
      { "'\\\"'", null },
      { "\"\n\"", null },
      { "\"\\n\n\"", null },
      { "'\n'", null },
  };

  static final class Strconv {

    private static int unquoteChar(String s, int[] i, char quote) {
      int c = s.codePointAt(i[0]);
      i[0] = s.offsetByCodePoints(i[0], 1);

      if (c == quote && (quote == '\'' || quote == '"')) {
        throw new IllegalArgumentException("unescaped quotation mark in literal");
      }
      if (c != '\\') {
        return c;
      }

      c = s.codePointAt(i[0]);
      i[0] = s.offsetByCodePoints(i[0], 1);

      switch (c) {
        case 'a':
          return 0x07;
        case 'b':
          return '\b';
        case 'f':
          return '\f';
        case 'n':
          return '\n';
        case 'r':
          return '\r';
        case 't':
          return '\t';
        case 'v':
          return 0x0B;
        case 'x':
        case 'u':
        case 'U': {
          int n = 0;
          switch (c) {
            case 'x':
              n = 2;
              break;
            case 'u':
              n = 4;
              break;
            case 'U':
              n = 8;
              break;
          }
          int v = 0;
          for (int j = 0; j < n; j++) {
            int d = s.codePointAt(i[0]);
            i[0] = s.offsetByCodePoints(i[0], 1);

            int x = Utils.unhex(d);
            if (x == -1) {
              throw new IllegalArgumentException("not a hex char: " + d);
            }
            v = (v << 4) | x;
          }
          if (c == 'x') {
            return v;
          }
          if (v > Unicode.MAX_RUNE) {
            throw new IllegalArgumentException("Unicode code point out of range");
          }
          return v;
        }
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7': {
          int v = c - '0';
          for (int j = 0; j < 2; j++) {
            int d = s.codePointAt(i[0]);
            i[0] = s.offsetByCodePoints(i[0], 1);

            int x = d - '0';
            if (x < 0 || x > 7) {
              throw new IllegalArgumentException("illegal octal digit");
            }
            v = (v << 3) | x;
          }
          if (v > 255) {
            throw new IllegalArgumentException("octal value out of range");
          }
          return v;
        }
        case '\\':
          return '\\';
        case '\'':
        case '"':
          if (c != quote) {
            throw new IllegalArgumentException("unnecessary backslash escape");
          }
          return c;
        default:
          throw new IllegalArgumentException("unexpected character");
      }
    }

    static String unquote(String s) {
      int n = s.length();
      if (n < 2) {
        throw new IllegalArgumentException("too short");
      }
      char quote = s.charAt(0);
      if (quote != s.charAt(n - 1)) {
        throw new IllegalArgumentException("quotes don't match");
      }
      s = s.substring(1, n - 1);
      if (quote == '`') {
        if (s.indexOf('`') >= 0) {
          throw new IllegalArgumentException("backquoted string contains '`'");
        }
        return s;
      }
      if (quote != '"' && quote != '\'') {
        throw new IllegalArgumentException("invalid quotation mark");
      }
      if (s.indexOf('\n') >= 0) {
        throw new IllegalArgumentException("multiline string literal");
      }
      if (s.indexOf('\\') < 0 && s.indexOf(quote) < 0) {
        if (quote == '"' || s.codePointCount(0, s.length()) == 1) {
          return s;
        }
      }

      int[] i = { 0 };
      StringBuilder buf = new StringBuilder();
      int len = s.length();
      while (i[0] < len) {
        buf.appendCodePoint(unquoteChar(s, i, quote));
        if (quote == '\'' && i[0] != len) {
          throw new IllegalArgumentException("single-quotation must be one char");
        }
      }

      return buf.toString();
    }

    private Strconv() {
    }
  }
}