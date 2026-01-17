package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class RE2ReplaceAllFunctionTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("RE2", () -> {
        describe("replaceAllFunc", () -> {
          for (String[] tc : REPLACE_FUNC_TESTS) {
            String pattern = tc[0];
            String input = tc[1];
            String expected = tc[2];

            test(pattern + " on " + input, () -> {
              RE2 re = RE2.compile(pattern);
              String actual = re.replaceAllFunc(input, REPLACE_XSY, input.length());
              expect(actual).toBe(expected);
            });
          }
        });
      });
    });
  }

  private static final RE2.ReplaceFunc REPLACE_XSY = s -> "x" + s + "y";

  private static final String[][] REPLACE_FUNC_TESTS = {
      { "[a-c]", "defabcdef", "defxayxbyxcydef" },
      { "[a-c]+", "defabcdef", "defxabcydef" },
      { "[a-c]*", "defabcdef", "xydxyexyfxabcydxyexyfxy" },
  };
}