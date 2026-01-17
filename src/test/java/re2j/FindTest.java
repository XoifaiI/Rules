package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class FindTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("RE2 Find Methods", () -> {
        for (TestCase tc : TEST_CASES) {
          String testName = String.format("pat=%s text=%s", tc.pat, tc.text);

          describe(testName, () -> {
            test("findUTF8", () -> {
              testFindUTF8(tc);
            });

            test("find", () -> {
              testFind(tc);
            });

            test("findUTF8Index", () -> {
              testFindUTF8Index(tc);
            });

            test("findIndex", () -> {
              testFindIndex(tc);
            });

            test("findAllUTF8", () -> {
              testFindAllUTF8(tc);
            });

            test("findAll", () -> {
              testFindAll(tc);
            });

            test("findAllUTF8Index", () -> {
              testFindAllUTF8Index(tc);
            });

            test("findAllIndex", () -> {
              testFindAllIndex(tc);
            });

            test("findUTF8Submatch", () -> {
              testFindUTF8Submatch(tc);
            });

            test("findSubmatch", () -> {
              testFindSubmatch(tc);
            });

            test("findUTF8SubmatchIndex", () -> {
              testFindUTF8SubmatchIndex(tc);
            });

            test("findSubmatchIndex", () -> {
              testFindSubmatchIndex(tc);
            });

            test("findAllUTF8Submatch", () -> {
              testFindAllUTF8Submatch(tc);
            });

            test("findAllSubmatch", () -> {
              testFindAllSubmatch(tc);
            });

            test("findAllUTF8SubmatchIndex", () -> {
              testFindAllUTF8SubmatchIndex(tc);
            });

            test("findAllSubmatchIndex", () -> {
              testFindAllSubmatchIndex(tc);
            });
          });
        }
      });
    });
  }

  public static class TestCase {
    public final String pat;
    public final String text;
    public final byte[] textUTF8;
    public final int[][] matches;

    TestCase(String pat, String text, int n, int... x) {
      this.pat = pat;
      this.text = text;
      this.textUTF8 = text.getBytes(StandardCharsets.UTF_8);
      this.matches = new int[n][];
      if (n > 0) {
        int runLength = x.length / n;
        for (int j = 0, i = 0; i < n; i++) {
          matches[i] = new int[runLength];
          System.arraycopy(x, j, matches[i], 0, runLength);
          j += runLength;
        }
      }
    }

    byte[] submatchBytes(int i, int j) {
      return Utils.subarray(textUTF8, matches[i][2 * j], matches[i][2 * j + 1]);
    }

    String submatchString(int i, int j) {
      return new String(submatchBytes(i, j), StandardCharsets.UTF_8);
    }
  }

  public static final TestCase[] TEST_CASES = {
      new TestCase("", "", 1, 0, 0),
      new TestCase("^abcdefg", "abcdefg", 1, 0, 7),
      new TestCase("a+", "baaab", 1, 1, 4),
      new TestCase("abcd..", "abcdef", 1, 0, 6),
      new TestCase("a", "a", 1, 0, 1),
      new TestCase("x", "y", 0),
      new TestCase("b", "abc", 1, 1, 2),
      new TestCase(".", "a", 1, 0, 1),
      new TestCase(".*", "abcdef", 1, 0, 6),
      new TestCase("^", "abcde", 1, 0, 0),
      new TestCase("$", "abcde", 1, 5, 5),
      new TestCase("^abcd$", "abcd", 1, 0, 4),
      new TestCase("^bcd'", "abcdef", 0),
      new TestCase("^abcd$", "abcde", 0),
      new TestCase("a+", "baaab", 1, 1, 4),
      new TestCase("a*", "baaab", 3, 0, 0, 1, 4, 5, 5),
      new TestCase("[a-z]+", "abcd", 1, 0, 4),
      new TestCase("[^a-z]+", "ab1234cd", 1, 2, 6),
      new TestCase("[a\\-\\]z]+", "az]-bcz", 2, 0, 4, 6, 7),
      new TestCase("[^\\n]+", "abcd\n", 1, 0, 4),
      new TestCase("[日本語]+", "日本語日本語", 1, 0, 18),
      new TestCase("日本語+", "日本語", 1, 0, 9),
      new TestCase("日本語+", "日本語語語語", 1, 0, 18),
      new TestCase("()", "", 1, 0, 0, 0, 0),
      new TestCase("(a)", "a", 1, 0, 1, 0, 1),
      new TestCase("(.)(.)", "日a", 1, 0, 4, 0, 3, 3, 4),
      new TestCase("(.*)", "", 1, 0, 0, 0, 0),
      new TestCase("(.*)", "abcd", 1, 0, 4, 0, 4),
      new TestCase("(..)(..)", "abcd", 1, 0, 4, 0, 2, 2, 4),
      new TestCase("(([^xyz]*)(d))", "abcd", 1, 0, 4, 0, 4, 0, 3, 3, 4),
      new TestCase("((a|b|c)*(d))", "abcd", 1, 0, 4, 0, 4, 2, 3, 3, 4),
      new TestCase("(((a|b|c)*)(d))", "abcd", 1, 0, 4, 0, 4, 0, 3, 2, 3, 3, 4),
      new TestCase("\\a\\f\\n\\r\\t\\v", "\007\f\n\r\t\013", 1, 0, 6),
      new TestCase("[\\a\\f\\n\\r\\t\\v]+", "\007\f\n\r\t\013", 1, 0, 6),
      new TestCase("a*(|(b))c*", "aacc", 1, 0, 4, 2, 2, -1, -1),
      new TestCase("(.*).*", "ab", 1, 0, 2, 0, 2),
      new TestCase("[.]", ".", 1, 0, 1),
      new TestCase("/$", "/abc/", 1, 4, 5),
      new TestCase("/$", "/abc", 0),
      new TestCase(".", "abc", 3, 0, 1, 1, 2, 2, 3),
      new TestCase("(.)", "abc", 3, 0, 1, 0, 1, 1, 2, 1, 2, 2, 3, 2, 3),
      new TestCase(".(.)", "abcd", 2, 0, 2, 1, 2, 2, 4, 3, 4),
      new TestCase("ab*", "abbaab", 3, 0, 3, 3, 4, 4, 6),
      new TestCase("a(b*)", "abbaab", 3, 0, 3, 1, 3, 3, 4, 4, 4, 4, 6, 5, 6),
      new TestCase("ab$", "cab", 1, 1, 3),
      new TestCase("axxb$", "axxcb", 0),
      new TestCase("data", "daXY data", 1, 5, 9),
      new TestCase("da(.)a$", "daXY data", 1, 5, 9, 7, 8),
      new TestCase("zx+", "zzx", 1, 1, 3),
      new TestCase("ab$", "abcab", 1, 3, 5),
      new TestCase("(aa)*$", "a", 1, 1, 1, -1, -1),
      new TestCase("(?:.|(?:.a))", "", 0),
      new TestCase("(?:A(?:A|a))", "Aa", 1, 0, 2),
      new TestCase("(?:A|(?:A|a))", "a", 1, 0, 1),
      new TestCase("(a){0}", "", 1, 0, 0, -1, -1),
      new TestCase("(?-s)(?:(?:^).)", "\n", 0),
      new TestCase("(?s)(?:(?:^).)", "\n", 1, 0, 1),
      new TestCase("(?:(?:^).)", "\n", 0),
      new TestCase("\\b", "x", 2, 0, 0, 1, 1),
      new TestCase("\\b", "xx", 2, 0, 0, 2, 2),
      new TestCase("\\b", "x y", 4, 0, 0, 1, 1, 2, 2, 3, 3),
      new TestCase("\\b", "xx yy", 4, 0, 0, 2, 2, 3, 3, 5, 5),
      new TestCase("\\B", "x", 0),
      new TestCase("\\B", "xx", 1, 1, 1),
      new TestCase("\\B", "x y", 0),
      new TestCase("\\B", "xx yy", 2, 1, 1, 4, 4),
      new TestCase("[^\\S\\s]", "abcd", 0),
      new TestCase("[^\\S[:space:]]", "abcd", 0),
      new TestCase("[^\\D\\d]", "abcd", 0),
      new TestCase("[^\\D[:digit:]]", "abcd", 0),
      new TestCase("(?i)\\W", "x", 0),
      new TestCase("(?i)\\W", "k", 0),
      new TestCase("(?i)\\W", "s", 0),
      new TestCase(
          "\\!\\\"\\#\\$\\%\\&\\'\\(\\)\\*\\+\\,\\-\\.\\/\\:\\;\\<\\=\\>\\?\\@\\[\\\\\\]\\^\\_\\{\\|\\}\\~",
          "!\"#$%&'()*+,-./:;<=>?@[\\]^_{|}~",
          1, 0, 31),
      new TestCase(
          "[\\!\\\"\\#\\$\\%\\&\\'\\(\\)\\*\\+\\,\\-\\.\\/\\:\\;\\<\\=\\>\\?\\@\\[\\\\\\]\\^\\_\\{\\|\\}\\~]+",
          "!\"#$%&'()*+,-./:;<=>?@[\\]^_{|}~",
          1, 0, 31),
      new TestCase("\\`", "`", 1, 0, 1),
      new TestCase("[\\`]+", "`", 1, 0, 1),
      new TestCase(
          ".",
          "qwertyuiopasdfghjklzxcvbnm1234567890",
          36,
          0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10,
          10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15, 16, 16, 17, 17, 18, 18, 19, 19, 20,
          20, 21, 21, 22, 22, 23, 23, 24, 24, 25, 25, 26, 26, 27, 27, 28, 28, 29, 29, 30,
          30, 31, 31, 32, 32, 33, 33, 34, 34, 35, 35, 36),
      new TestCase("(|a)*", "aa", 3, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2),
  };

  private static void testFindUTF8(TestCase tc) {
    RE2 re = RE2.compile(tc.pat);
    if (!re.toString().equals(tc.pat)) {
      throw new AssertionError(String.format(
          "RE2.toString() = \"%s\"; should be \"%s\"", re.toString(), tc.pat));
    }
    byte[] result = re.findUTF8(tc.textUTF8);
    if (tc.matches.length == 0 && len(result) == 0) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "findUTF8: expected no match; got one: %s", tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "findUTF8: expected match; got none: %s", tc.pat));
    }
    byte[] expect = tc.submatchBytes(0, 0);
    if (!Arrays.equals(expect, result)) {
      throw new AssertionError(String.format(
          "findUTF8: expected %s; got %s: %s",
          new String(expect, StandardCharsets.UTF_8),
          new String(result, StandardCharsets.UTF_8),
          tc.pat));
    }
  }

  private static void testFind(TestCase tc) {
    String result = RE2.compile(tc.pat).find(tc.text);
    if (tc.matches.length == 0 && result.isEmpty()) {
      return;
    }
    if (tc.matches.length == 0 && !result.isEmpty()) {
      throw new AssertionError(String.format(
          "find: expected no match; got one: %s", tc.pat));
    }
    if (tc.matches.length > 0 && result.isEmpty()) {
      int[] match = tc.matches[0];
      if (match[0] != match[1]) {
        throw new AssertionError(String.format(
            "find: expected match; got none: %s", tc.pat));
      }
      return;
    }
    String expect = tc.submatchString(0, 0);
    if (!expect.equals(result)) {
      throw new AssertionError(String.format(
          "find: expected %s got %s: %s", expect, result, tc.pat));
    }
  }

  private static void testFindIndexCommon(
      String testName, TestCase tc, int[] result, boolean resultIndicesAreUTF8) {
    if (tc.matches.length == 0 && len(result) == 0) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "%s: expected no match; got one: %s", testName, tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "%s: expected match; got none: %s", testName, tc.pat));
    }
    if (!resultIndicesAreUTF8) {
      result = utf16IndicesToUtf8(result, tc.text);
    }
    int[] expect = tc.matches[0];
    if (expect[0] != result[0] || expect[1] != result[1]) {
      throw new AssertionError(String.format(
          "%s: expected %s got %s: %s",
          testName, Arrays.toString(expect), Arrays.toString(result), tc.pat));
    }
  }

  private static void testFindUTF8Index(TestCase tc) {
    testFindIndexCommon(
        "testFindUTF8Index", tc, RE2.compile(tc.pat).findUTF8Index(tc.textUTF8), true);
  }

  private static void testFindIndex(TestCase tc) {
    testFindIndexCommon(
        "testFindIndex", tc, RE2.compile(tc.pat).findIndex(tc.text), false);
  }

  private static void testFindAllUTF8(TestCase tc) {
    List<byte[]> result = RE2.compile(tc.pat).findAllUTF8(tc.textUTF8, -1);
    if (tc.matches.length == 0 && result == null) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "findAllUTF8: expected no match; got one: %s", tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "findAllUTF8: expected match; got none: %s", tc.pat));
    }
    if (tc.matches.length != result.size()) {
      throw new AssertionError(String.format(
          "findAllUTF8: expected %d matches; got %d: %s",
          tc.matches.length, result.size(), tc.pat));
    }
    for (int i = 0; i < tc.matches.length; i++) {
      byte[] expect = tc.submatchBytes(i, 0);
      if (!Arrays.equals(expect, result.get(i))) {
        throw new AssertionError(String.format(
            "findAllUTF8: match %d: expected %s; got %s: %s",
            i, new String(expect, StandardCharsets.UTF_8),
            new String(result.get(i), StandardCharsets.UTF_8), tc.pat));
      }
    }
  }

  private static void testFindAll(TestCase tc) {
    List<String> result = RE2.compile(tc.pat).findAll(tc.text, -1);
    if (tc.matches.length == 0 && result == null) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "findAll: expected no match; got one: %s", tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "findAll: expected match; got none: %s", tc.pat));
    }
    if (tc.matches.length != result.size()) {
      throw new AssertionError(String.format(
          "findAll: expected %d matches; got %d: %s",
          tc.matches.length, result.size(), tc.pat));
    }
    for (int i = 0; i < tc.matches.length; i++) {
      String expect = tc.submatchString(i, 0);
      if (!expect.equals(result.get(i))) {
        throw new AssertionError(String.format(
            "findAll: expected %s; got %s: %s", expect, result.get(i), tc.pat));
      }
    }
  }

  private static void testFindAllIndexCommon(
      String testName, TestCase tc, List<int[]> result, boolean resultIndicesAreUTF8) {
    if (tc.matches.length == 0 && result == null) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "%s: expected no match; got one: %s", testName, tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "%s: expected match; got none: %s", testName, tc.pat));
    }
    if (tc.matches.length != result.size()) {
      throw new AssertionError(String.format(
          "%s: expected %d matches; got %d: %s",
          testName, tc.matches.length, result.size(), tc.pat));
    }
    for (int k = 0; k < tc.matches.length; k++) {
      int[] e = tc.matches[k];
      int[] res = result.get(k);
      if (!resultIndicesAreUTF8) {
        res = utf16IndicesToUtf8(res, tc.text);
      }
      if (e[0] != res[0] || e[1] != res[1]) {
        throw new AssertionError(String.format(
            "%s: match %d: expected %s; got %s: %s",
            testName, k, Arrays.toString(e), Arrays.toString(res), tc.pat));
      }
    }
  }

  private static void testFindAllUTF8Index(TestCase tc) {
    testFindAllIndexCommon(
        "testFindAllUTF8Index", tc,
        RE2.compile(tc.pat).findAllUTF8Index(tc.textUTF8, -1), true);
  }

  private static void testFindAllIndex(TestCase tc) {
    testFindAllIndexCommon(
        "testFindAllIndex", tc,
        RE2.compile(tc.pat).findAllIndex(tc.text, -1), false);
  }

  private static void testSubmatchBytes(String testName, TestCase tc, int n, byte[][] result) {
    int[] submatches = tc.matches[n];
    if (submatches.length != len(result) * 2) {
      throw new AssertionError(String.format(
          "%s %d: expected %d submatches; got %d: %s",
          testName, n, submatches.length / 2, len(result), tc.pat));
    }
    for (int k = 0; k < len(result); k++) {
      if (submatches[k * 2] == -1) {
        if (result[k] != null) {
          throw new AssertionError(String.format(
              "%s %d: expected null got %s: %s",
              testName, n, Arrays.toString(result), tc.pat));
        }
        continue;
      }
      byte[] expect = tc.submatchBytes(n, k);
      if (!Arrays.equals(expect, result[k])) {
        throw new AssertionError(String.format(
            "%s %d: expected %s; got %s: %s",
            testName, n, new String(expect, StandardCharsets.UTF_8),
            new String(result[k], StandardCharsets.UTF_8), tc.pat));
      }
    }
  }

  private static void testFindUTF8Submatch(TestCase tc) {
    byte[][] result = RE2.compile(tc.pat).findUTF8Submatch(tc.textUTF8);
    if (tc.matches.length == 0 && result == null) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "expected no match; got one: %s", tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "expected match; got none: %s", tc.pat));
    }
    testSubmatchBytes("testFindUTF8Submatch", tc, 0, result);
  }

  private static void testSubmatch(String testName, TestCase tc, int n, String[] result) {
    int[] submatches = tc.matches[n];
    if (submatches.length != len(result) * 2) {
      throw new AssertionError(String.format(
          "%s %d: expected %d submatches; got %d: %s",
          testName, n, submatches.length / 2, len(result), tc.pat));
    }
    for (int k = 0; k < submatches.length; k += 2) {
      if (submatches[k] == -1) {
        if (result[k / 2] != null && !result[k / 2].isEmpty()) {
          throw new AssertionError(String.format(
              "%s %d: expected null got %s: %s",
              testName, n, Arrays.toString(result), tc.pat));
        }
        continue;
      }
      String expect = tc.submatchString(n, k / 2);
      if (!expect.equals(result[k / 2])) {
        throw new AssertionError(String.format(
            "%s %d: expected %s got %s: %s",
            testName, n, expect, Arrays.toString(result), tc.pat));
      }
    }
  }

  private static void testFindSubmatch(TestCase tc) {
    String[] result = RE2.compile(tc.pat).findSubmatch(tc.text);
    if (tc.matches.length == 0 && result == null) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "expected no match; got one: %s", tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "expected match; got none: %s", tc.pat));
    }
    testSubmatch("testFindSubmatch", tc, 0, result);
  }

  private static void testSubmatchIndices(
      String testName, TestCase tc, int n, int[] result, boolean resultIndicesAreUTF8) {
    int[] expect = tc.matches[n];
    if (expect.length != len(result)) {
      throw new AssertionError(String.format(
          "%s %d: expected %d matches; got %d: %s",
          testName, n, expect.length / 2, len(result) / 2, tc.pat));
    }
    if (!resultIndicesAreUTF8) {
      result = utf16IndicesToUtf8(result, tc.text);
    }
    for (int k = 0; k < expect.length; ++k) {
      if (expect[k] != result[k]) {
        throw new AssertionError(String.format(
            "%s %d: submatch error: expected %s got %s: %s",
            testName, n, Arrays.toString(expect), Arrays.toString(result), tc.pat));
      }
    }
  }

  private static void testFindSubmatchIndexCommon(
      String testName, TestCase tc, int[] result, boolean resultIndicesAreUTF8) {
    if (tc.matches.length == 0 && result == null) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "%s: expected no match; got one: %s", testName, tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "%s: expected match; got none: %s", testName, tc.pat));
    }
    testSubmatchIndices(testName, tc, 0, result, resultIndicesAreUTF8);
  }

  private static void testFindUTF8SubmatchIndex(TestCase tc) {
    testFindSubmatchIndexCommon(
        "testFindUTF8SubmatchIndex", tc,
        RE2.compile(tc.pat).findUTF8SubmatchIndex(tc.textUTF8), true);
  }

  private static void testFindSubmatchIndex(TestCase tc) {
    testFindSubmatchIndexCommon(
        "testFindSubmatchIndex", tc,
        RE2.compile(tc.pat).findSubmatchIndex(tc.text), false);
  }

  private static void testFindAllUTF8Submatch(TestCase tc) {
    List<byte[][]> result = RE2.compile(tc.pat).findAllUTF8Submatch(tc.textUTF8, -1);
    if (tc.matches.length == 0 && result == null) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "expected no match; got one: %s", tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "expected match; got none: %s", tc.pat));
    }
    if (tc.matches.length != result.size()) {
      throw new AssertionError(String.format(
          "expected %d matches; got %d: %s", tc.matches.length, result.size(), tc.pat));
    }
    for (int k = 0; k < tc.matches.length; ++k) {
      testSubmatchBytes("testFindAllUTF8Submatch", tc, k, result.get(k));
    }
  }

  private static void testFindAllSubmatch(TestCase tc) {
    List<String[]> result = RE2.compile(tc.pat).findAllSubmatch(tc.text, -1);
    if (tc.matches.length == 0 && result == null) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "expected no match; got one: %s", tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "expected match; got none: %s", tc.pat));
    }
    if (tc.matches.length != result.size()) {
      throw new AssertionError(String.format(
          "expected %d matches; got %d: %s", tc.matches.length, result.size(), tc.pat));
    }
    for (int k = 0; k < tc.matches.length; ++k) {
      testSubmatch("testFindAllSubmatch", tc, k, result.get(k));
    }
  }

  private static void testFindAllSubmatchIndexCommon(
      String testName, TestCase tc, List<int[]> result, boolean resultIndicesAreUTF8) {
    if (tc.matches.length == 0 && result == null) {
      return;
    }
    if (tc.matches.length == 0 && result != null) {
      throw new AssertionError(String.format(
          "%s: expected no match; got one: %s", testName, tc.pat));
    }
    if (tc.matches.length > 0 && result == null) {
      throw new AssertionError(String.format(
          "%s: expected match; got none: %s", testName, tc.pat));
    }
    if (tc.matches.length != result.size()) {
      throw new AssertionError(String.format(
          "%s: expected %d matches; got %d: %s",
          testName, tc.matches.length, result.size(), tc.pat));
    }
    for (int k = 0; k < tc.matches.length; ++k) {
      testSubmatchIndices(testName, tc, k, result.get(k), resultIndicesAreUTF8);
    }
  }

  private static void testFindAllUTF8SubmatchIndex(TestCase tc) {
    testFindAllSubmatchIndexCommon(
        "testFindAllUTF8SubmatchIndex", tc,
        RE2.compile(tc.pat).findAllUTF8SubmatchIndex(tc.textUTF8, -1), true);
  }

  private static void testFindAllSubmatchIndex(TestCase tc) {
    testFindAllSubmatchIndexCommon(
        "testFindAllSubmatchIndex", tc,
        RE2.compile(tc.pat).findAllSubmatchIndex(tc.text, -1), false);
  }

  private static int len(Object array) {
    if (array == null) {
      return 0;
    }
    if (array instanceof byte[]) {
      return ((byte[]) array).length;
    }
    if (array instanceof int[]) {
      return ((int[]) array).length;
    }
    if (array instanceof Object[]) {
      return ((Object[]) array).length;
    }
    return 0;
  }

  private static int[] utf16IndicesToUtf8(int[] idx16, String text) {
    int[] idx8 = new int[idx16.length];
    for (int i = 0; i < idx16.length; ++i) {
      if (idx16[i] == -1) {
        idx8[i] = -1;
      } else {
        idx8[i] = text.substring(0, idx16[i]).getBytes(StandardCharsets.UTF_8).length;
      }
    }
    return idx8;
  }
}