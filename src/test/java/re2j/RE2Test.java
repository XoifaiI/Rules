package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

import java.util.Arrays;
import java.util.List;

public class RE2Test {

  public static void main(String[] args) {
    entry(() -> {
      describe("RE2", () -> {
        describe("fullMatch", () -> {
          test("matches anchored pattern with String", () -> {
            boolean result = new RE2("ab+c").match("abbbbbc", 0, 7, RE2.ANCHOR_BOTH, null, 0);
            expect(result).toBe(true);
          });

          test("rejects non-matching anchored pattern with String", () -> {
            boolean result = new RE2("ab+c").match("xabbbbbc", 0, 8, RE2.ANCHOR_BOTH, null, 0);
            expect(result).toBe(false);
          });

          test("matches anchored pattern with UTF8 MatcherInput", () -> {
            boolean result = new RE2("ab+c")
                .match(MatcherInput.utf8("abbbbbc"), 0, 7, RE2.ANCHOR_BOTH, null, 0);
            expect(result).toBe(true);
          });

          test("rejects non-matching anchored pattern with UTF8 MatcherInput", () -> {
            boolean result = new RE2("ab+c")
                .match(MatcherInput.utf8("xabbbbbc"), 0, 8, RE2.ANCHOR_BOTH, null, 0);
            expect(result).toBe(false);
          });
        });

        describe("findEnd", () -> {
          String s = "yyyabcxxxdefzzz";
          List<MatcherInput> inputs = Arrays.asList(
              MatcherInput.utf8(s),
              MatcherInput.utf16(s));

          for (MatcherInput input : inputs) {
            String inputType = input == inputs.get(0) ? "UTF8" : "UTF16";

            test("matches full range with " + inputType, () -> {
              RE2 r = new RE2("abc.*def");
              expect(r.match(input, 0, 15, RE2.UNANCHORED, null, 0)).toBe(true);
            });

            test("matches truncated end with " + inputType, () -> {
              RE2 r = new RE2("abc.*def");
              expect(r.match(input, 0, 12, RE2.UNANCHORED, null, 0)).toBe(true);
            });

            test("matches offset start with " + inputType, () -> {
              RE2 r = new RE2("abc.*def");
              expect(r.match(input, 3, 15, RE2.UNANCHORED, null, 0)).toBe(true);
            });

            test("matches offset start and truncated end with " + inputType, () -> {
              RE2 r = new RE2("abc.*def");
              expect(r.match(input, 3, 12, RE2.UNANCHORED, null, 0)).toBe(true);
            });

            test("rejects when start past pattern with " + inputType, () -> {
              RE2 r = new RE2("abc.*def");
              expect(r.match(input, 4, 12, RE2.UNANCHORED, null, 0)).toBe(false);
            });

            test("rejects when end before pattern with " + inputType, () -> {
              RE2 r = new RE2("abc.*def");
              expect(r.match(input, 3, 11, RE2.UNANCHORED, null, 0)).toBe(false);
            });
          }
        });
      });
    });
  }
}