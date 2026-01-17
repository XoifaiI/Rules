package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class RE2ReplaceTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("RE2", () -> {
        describe("replaceAll", () -> {
          for (Object[] tc : REPLACE_TESTS) {
            String pattern = (String) tc[0];
            String replacement = (String) tc[1];
            String source = (String) tc[2];
            String expected = (String) tc[3];
            boolean replaceFirst = (Boolean) tc[4];

            if (!replaceFirst) {
              String testName = String.format(
                  "/%s/ on \"%s\" -> \"%s\"", pattern, escape(source), escape(expected));

              test(testName, () -> {
                RE2 re = RE2.compile(pattern);
                String actual = re.replaceAll(source, replacement);
                expect(actual).toBe(expected);
              });
            }
          }
        });

        describe("replaceFirst", () -> {
          for (Object[] tc : REPLACE_TESTS) {
            String pattern = (String) tc[0];
            String replacement = (String) tc[1];
            String source = (String) tc[2];
            String expected = (String) tc[3];
            boolean replaceFirst = (Boolean) tc[4];

            if (replaceFirst) {
              String testName = String.format(
                  "/%s/ on \"%s\" -> \"%s\"", pattern, escape(source), escape(expected));

              test(testName, () -> {
                RE2 re = RE2.compile(pattern);
                String actual = re.replaceFirst(source, replacement);
                expect(actual).toBe(expected);
              });
            }
          }
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
        sb.append(String.format("\\u%04x", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static final Object[][] REPLACE_TESTS = {
      { "", "", "", "", false },
      { "", "x", "", "x", false },
      { "", "", "abc", "abc", false },
      { "", "x", "abc", "xaxbxcx", false },

      { "b", "", "", "", false },
      { "b", "x", "", "", false },
      { "b", "", "abc", "ac", false },
      { "b", "x", "abc", "axc", false },
      { "y", "", "", "", false },
      { "y", "x", "", "", false },
      { "y", "", "abc", "abc", false },
      { "y", "x", "abc", "abc", false },

      { "[a-c]*", "x", "\u65e5", "x\u65e5x", false },
      { "[^\u65e5]", "x", "abc\u65e5def", "xxx\u65e5xxx", false },

      { "^[a-c]*", "x", "abcdabc", "xdabc", false },
      { "[a-c]*$", "x", "abcdabc", "abcdx", false },
      { "^[a-c]*$", "x", "abcdabc", "abcdabc", false },
      { "^[a-c]*", "x", "abc", "x", false },
      { "[a-c]*$", "x", "abc", "x", false },
      { "^[a-c]*$", "x", "abc", "x", false },
      { "^[a-c]*", "x", "dabce", "xdabce", false },
      { "[a-c]*$", "x", "dabce", "dabcex", false },
      { "^[a-c]*$", "x", "dabce", "dabce", false },
      { "^[a-c]*", "x", "", "x", false },
      { "[a-c]*$", "x", "", "x", false },
      { "^[a-c]*$", "x", "", "x", false },
      { "^[a-c]+", "x", "abcdabc", "xdabc", false },
      { "[a-c]+$", "x", "abcdabc", "abcdx", false },
      { "^[a-c]+$", "x", "abcdabc", "abcdabc", false },
      { "^[a-c]+", "x", "abc", "x", false },
      { "[a-c]+$", "x", "abc", "x", false },
      { "^[a-c]+$", "x", "abc", "x", false },
      { "^[a-c]+", "x", "dabce", "dabce", false },
      { "[a-c]+$", "x", "dabce", "dabce", false },
      { "^[a-c]+$", "x", "dabce", "dabce", false },
      { "^[a-c]+", "x", "", "", false },
      { "[a-c]+$", "x", "", "", false },
      { "^[a-c]+$", "x", "", "", false },

      { "abc", "def", "abcdefg", "defdefg", false },
      { "bc", "BC", "abcbcdcdedef", "aBCBCdcdedef", false },
      { "abc", "", "abcdabc", "d", false },
      { "x", "xXx", "xxxXxxx", "xXxxXxxXxXxXxxXxxXx", false },
      { "abc", "d", "", "", false },
      { "abc", "d", "abc", "d", false },
      { ".+", "x", "abc", "x", false },
      { "[a-c]*", "x", "def", "xdxexfx", false },
      { "[a-c]+", "x", "abcbcdcdedef", "xdxdedef", false },
      { "[a-c]*", "x", "abcbcdcdedef", "xdxdxexdxexfx", false },

      { "", "", "", "", true },
      { "", "x", "", "x", true },
      { "", "", "abc", "abc", true },
      { "", "x", "abc", "xabc", true },

      { "b", "", "", "", true },
      { "b", "x", "", "", true },
      { "b", "", "abc", "ac", true },
      { "b", "x", "abc", "axc", true },
      { "y", "", "", "", true },
      { "y", "x", "", "", true },
      { "y", "", "abc", "abc", true },
      { "y", "x", "abc", "abc", true },

      { "[a-c]*", "x", "\u65e5", "x\u65e5", true },
      { "[^\u65e5]", "x", "abc\u65e5def", "xbc\u65e5def", true },

      { "^[a-c]*", "x", "abcdabc", "xdabc", true },
      { "[a-c]*$", "x", "abcdabc", "abcdx", true },
      { "^[a-c]*$", "x", "abcdabc", "abcdabc", true },
      { "^[a-c]*", "x", "abc", "x", true },
      { "[a-c]*$", "x", "abc", "x", true },
      { "^[a-c]*$", "x", "abc", "x", true },
      { "^[a-c]*", "x", "dabce", "xdabce", true },
      { "[a-c]*$", "x", "dabce", "dabcex", true },
      { "^[a-c]*$", "x", "dabce", "dabce", true },
      { "^[a-c]*", "x", "", "x", true },
      { "[a-c]*$", "x", "", "x", true },
      { "^[a-c]*$", "x", "", "x", true },
      { "^[a-c]+", "x", "abcdabc", "xdabc", true },
      { "[a-c]+$", "x", "abcdabc", "abcdx", true },
      { "^[a-c]+$", "x", "abcdabc", "abcdabc", true },
      { "^[a-c]+", "x", "abc", "x", true },
      { "[a-c]+$", "x", "abc", "x", true },
      { "^[a-c]+$", "x", "abc", "x", true },
      { "^[a-c]+", "x", "dabce", "dabce", true },
      { "[a-c]+$", "x", "dabce", "dabce", true },
      { "^[a-c]+$", "x", "dabce", "dabce", true },
      { "^[a-c]+", "x", "", "", true },
      { "[a-c]+$", "x", "", "", true },
      { "^[a-c]+$", "x", "", "", true },

      { "abc", "def", "abcdefg", "defdefg", true },
      { "bc", "BC", "abcbcdcdedef", "aBCbcdcdedef", true },
      { "abc", "", "abcdabc", "dabc", true },
      { "x", "xXx", "xxxXxxx", "xXxxxXxxx", true },
      { "abc", "d", "", "", true },
      { "abc", "d", "abc", "d", true },
      { ".+", "x", "abc", "x", true },
      { "[a-c]*", "x", "def", "xdef", true },
      { "[a-c]+", "x", "abcbcdcdedef", "xdcdedef", true },
      { "[a-c]*", "x", "abcbcdcdedef", "xdcdedef", true },
  };
}