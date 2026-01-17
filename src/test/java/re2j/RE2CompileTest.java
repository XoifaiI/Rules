package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class RE2CompileTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("RE2", () -> {
        describe("compile", () -> {
          for (String[] tc : TEST_DATA) {
            String input = tc[0];
            String expectedError = tc[1];

            if (expectedError == null) {
              test("compiles " + escape(input), () -> {
                RE2 re = RE2.compile(input);
                expect(re).toBeDefined();
              });
            } else {
              test("rejects " + escape(input), () -> {
                expect(() -> RE2.compile(input))
                    .toThrow("error parsing regexp: " + expectedError);
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
        sb.append(String.format("\\x%02x", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static final String[][] TEST_DATA = {
      { "", null },
      { ".", null },
      { "^.$", null },
      { "a", null },
      { "a*", null },
      { "a+", null },
      { "a?", null },
      { "a|b", null },
      { "a*|b*", null },
      { "(a*|b)(c*|d)", null },
      { "[a-z]", null },
      { "[a-abc-c\\-\\]\\[]", null },
      { "[a-z]+", null },
      { "[abc]", null },
      { "[^1234]", null },
      { "[^\n]", null },
      { "..|.#|..", null },
      { "\\!\\\\", null },
      { "abc]", null },
      { "a??", null },
      { "*", "missing argument to repetition operator: `*`" },
      { "+", "missing argument to repetition operator: `+`" },
      { "?", "missing argument to repetition operator: `?`" },
      { "(abc", "missing closing ): `(abc`" },
      { "abc)", "regexp/syntax: internal error: `stack underflow`" },
      { "x[a-z", "missing closing ]: `[a-z`" },
      { "[z-a]", "invalid character class range: `z-a`" },
      { "abc\\", "trailing backslash at end of expression" },
      { "a**", "invalid nested repetition operator: `**`" },
      { "a*+", "invalid nested repetition operator: `*+`" },
      { "\\x", "invalid escape sequence: `\\x`" },
      { "\\p", "invalid character class range: `\\p`" },
      { "\\p{", "invalid character class range: `\\p{`" }
  };
}