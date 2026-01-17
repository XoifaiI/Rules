package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class RE2NumSubexpsTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("RE2", () -> {
        describe("numberOfCapturingGroups", () -> {
          for (Object[] tc : NUM_SUBEXP_CASES) {
            String input = (String) tc[0];
            int expected = (Integer) tc[1];
            String displayName = input.isEmpty() ? "(empty)" : input;

            test(displayName + " has " + expected + " groups", () -> {
              int actual = RE2.compile(input).numberOfCapturingGroups();
              expect(actual).toBe(expected);
            });
          }
        });
      });
    });
  }

  private static final Object[][] NUM_SUBEXP_CASES = {
      { "", 0 },
      { ".*", 0 },
      { "abba", 0 },
      { "ab(b)a", 1 },
      { "ab(.*)a", 1 },
      { "(.*)ab(.*)a", 2 },
      { "(.*)(ab)(.*)a", 3 },
      { "(.*)((a)b)(.*)a", 4 },
      { "(.*)(\\(ab)(.*)a", 3 },
      { "(.*)(\\(a\\)b)(.*)a", 3 },
  };
}