package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class UnicodeTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("Unicode", () -> {
        describe("fold constants", () -> {
          test("MIN_FOLD is correct", () -> {
            int firstFold = -1;
            for (int i = 0; i <= Unicode.MAX_RUNE; i++) {
              if (Unicode.simpleFold(i) != i) {
                firstFold = i;
                break;
              }
            }
            expect(Unicode.MIN_FOLD).toBe(firstFold);
          });

          test("MAX_FOLD is correct", () -> {
            int lastFold = -1;
            for (int i = 0; i <= Unicode.MAX_RUNE; i++) {
              if (Unicode.simpleFold(i) != i) {
                lastFold = i;
              }
            }
            expect(Unicode.MAX_FOLD).toBe(lastFold);
          });
        });

        describe("equalsIgnoreCase", () -> {
          describe("lowercase letters", () -> {
            for (int r = 'a'; r <= 'z'; r++) {
              int lower = r;
              int upper = r - ('a' - 'A');

              test(((char) lower) + " equals " + ((char) lower), () -> {
                expect(Unicode.equalsIgnoreCase(lower, lower)).toBe(true);
              });

              test(((char) upper) + " equals " + ((char) upper), () -> {
                expect(Unicode.equalsIgnoreCase(upper, upper)).toBe(true);
              });

              test(((char) lower) + " equals " + ((char) upper), () -> {
                expect(Unicode.equalsIgnoreCase(lower, upper)).toBe(true);
              });

              test(((char) upper) + " equals " + ((char) lower), () -> {
                expect(Unicode.equalsIgnoreCase(upper, lower)).toBe(true);
              });
            }
          });

          describe("special cases that should match", () -> {
            test("{ equals {", () -> {
              expect(Unicode.equalsIgnoreCase('{', '{')).toBe(true);
            });

            test("é equals É", () -> {
              expect(Unicode.equalsIgnoreCase('é', 'É')).toBe(true);
            });

            test("Ú equals ú", () -> {
              expect(Unicode.equalsIgnoreCase('Ú', 'ú')).toBe(true);
            });

            test("Kelvin sign equals K", () -> {
              expect(Unicode.equalsIgnoreCase('\u212A', 'K')).toBe(true);
            });

            test("Kelvin sign equals k", () -> {
              expect(Unicode.equalsIgnoreCase('\u212A', 'k')).toBe(true);
            });
          });

          describe("cases that should not match", () -> {
            test("Kelvin sign not equals a", () -> {
              expect(Unicode.equalsIgnoreCase('\u212A', 'a')).toBe(false);
            });

            test("ü not equals ű", () -> {
              expect(Unicode.equalsIgnoreCase('ü', 'ű')).toBe(false);
            });

            test("b not equals k", () -> {
              expect(Unicode.equalsIgnoreCase('b', 'k')).toBe(false);
            });

            test("C not equals x", () -> {
              expect(Unicode.equalsIgnoreCase('C', 'x')).toBe(false);
            });

            test("/ not equals _", () -> {
              expect(Unicode.equalsIgnoreCase('/', '_')).toBe(false);
            });

            test("d not equals )", () -> {
              expect(Unicode.equalsIgnoreCase('d', ')')).toBe(false);
            });

            test("@ not equals `", () -> {
              expect(Unicode.equalsIgnoreCase('@', '`')).toBe(false);
            });
          });
        });
      });
    });
  }
}