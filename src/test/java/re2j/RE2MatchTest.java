package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class RE2MatchTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("RE2", () -> {
        describe("match", () -> {
          for (FindTest.TestCase tc : FindTest.TEST_CASES) {
            String testName = String.format("pat=%s text=%s", tc.pat, tc.text);
            boolean shouldMatch = tc.matches.length > 0;

            test("match " + testName, () -> {
              RE2 re = RE2.compile(tc.pat);
              boolean result = re.match(tc.text);
              expect(result).toBe(shouldMatch);
            });

            test("matchUTF8 " + testName, () -> {
              RE2 re = RE2.compile(tc.pat);
              boolean result = re.matchUTF8(tc.textUTF8);
              expect(result).toBe(shouldMatch);
            });
          }
        });

        describe("match static", () -> {
          for (FindTest.TestCase tc : FindTest.TEST_CASES) {
            String testName = String.format("pat=%s text=%s", tc.pat, tc.text);
            boolean shouldMatch = tc.matches.length > 0;

            test("RE2.match " + testName, () -> {
              boolean result = RE2.match(tc.pat, tc.text);
              expect(result).toBe(shouldMatch);
            });
          }
        });
      });
    });
  }
}