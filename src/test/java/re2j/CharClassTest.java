package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

import java.util.Arrays;

public class CharClassTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("CharClass", () -> {
        describe("cleanClass", () -> {
          test("handles empty class", () -> {
            assertClass(cc().cleanClass());
          });

          test("deduplicates identical ranges", () -> {
            assertClass(cc(10, 20, 10, 20, 10, 20).cleanClass(), 10, 20);
          });

          test("keeps single range unchanged", () -> {
            assertClass(cc(10, 20).cleanClass(), 10, 20);
          });

          test("merges adjacent ranges", () -> {
            assertClass(cc(10, 20, 20, 30).cleanClass(), 10, 30);
          });

          test("merges overlapping ranges", () -> {
            assertClass(cc(10, 20, 30, 40, 20, 30).cleanClass(), 10, 40);
          });

          test("merges contained ranges", () -> {
            assertClass(cc(0, 50, 20, 30).cleanClass(), 0, 50);
          });

          test("sorts and keeps disjoint ranges", () -> {
            assertClass(
                cc(10, 11, 13, 14, 16, 17, 19, 20, 22, 23).cleanClass(),
                10, 11, 13, 14, 16, 17, 19, 20, 22, 23);
          });

          test("sorts unsorted disjoint ranges", () -> {
            assertClass(
                cc(13, 14, 10, 11, 22, 23, 19, 20, 16, 17).cleanClass(),
                10, 11, 13, 14, 16, 17, 19, 20, 22, 23);
          });

          test("merges all into single range when covered", () -> {
            assertClass(cc(13, 14, 10, 11, 22, 23, 19, 20, 16, 17, 5, 25).cleanClass(), 5, 25);
          });

          test("merges overlapping middle ranges", () -> {
            assertClass(cc(13, 14, 10, 11, 22, 23, 19, 20, 16, 17, 12, 21).cleanClass(), 10, 23);
          });

          test("handles full unicode range", () -> {
            assertClass(cc(0, Unicode.MAX_RUNE).cleanClass(), 0, Unicode.MAX_RUNE);
          });

          test("handles range starting at zero", () -> {
            assertClass(cc(0, 50).cleanClass(), 0, 50);
          });

          test("handles range ending at max rune", () -> {
            assertClass(cc(50, Unicode.MAX_RUNE).cleanClass(), 50, Unicode.MAX_RUNE);
          });
        });

        describe("appendLiteral", () -> {
          test("appends single character", () -> {
            assertClass(cc().appendLiteral('a', 0), 'a', 'a');
          });

          test("extends existing range at start", () -> {
            assertClass(cc('a', 'f').appendLiteral('a', 0), 'a', 'f');
          });

          test("extends existing range before start", () -> {
            assertClass(cc('b', 'f').appendLiteral('a', 0), 'a', 'f');
          });

          test("extends existing range at end", () -> {
            assertClass(cc('a', 'f').appendLiteral('g', 0), 'a', 'g');
          });

          test("adds separate range for non-adjacent char", () -> {
            assertClass(cc('a', 'f').appendLiteral('A', 0), 'a', 'f', 'A', 'A');
          });

          test("fold case adds both cases for new char", () -> {
            assertClass(cc().appendLiteral('A', RE2.FOLD_CASE), 'A', 'A', 'a', 'a');
          });

          test("fold case extends and adds case variant", () -> {
            assertClass(cc('a', 'f').appendLiteral('a', RE2.FOLD_CASE), 'a', 'f', 'A', 'A');
          });

          test("fold case extends range before and adds case", () -> {
            assertClass(cc('b', 'f').appendLiteral('a', RE2.FOLD_CASE), 'a', 'f', 'A', 'A');
          });

          test("fold case extends range after and adds case", () -> {
            assertClass(cc('a', 'f').appendLiteral('g', RE2.FOLD_CASE), 'a', 'g', 'G', 'G');
          });

          test("fold case with existing uppercase", () -> {
            assertClass(cc('a', 'f').appendLiteral('A', RE2.FOLD_CASE), 'a', 'f', 'A', 'A');
          });

          test("space is below fold range without fold", () -> {
            assertClass(cc('a', 'f').appendLiteral(' ', 0), 'a', 'f', ' ', ' ');
          });

          test("space is below fold range with fold", () -> {
            assertClass(cc('a', 'f').appendLiteral(' ', RE2.FOLD_CASE), 'a', 'f', ' ', ' ');
          });
        });

        describe("appendFoldedRange", () -> {
          test("full range needs no folding", () -> {
            assertClass(cc().appendFoldedRange(10, 0x10ff0), 10, 0x10ff0);
          });

          test("range outside fold possibilities unchanged", () -> {
            assertClass(cc().appendFoldedRange(' ', '&'), ' ', '&');
          });

          test("folds suffix above min fold", () -> {
            assertClass(cc().appendFoldedRange(' ', 'C'), ' ', 'C', 'a', 'c');
          });

          test("folds deseret characters", () -> {
            assertClass(
                cc().appendFoldedRange(0x10400, 0x104f0),
                0x10400, 0x10426,
                0x10426, 0x104D6,
                0x104D7, 0x104FB);
          });
        });

        describe("appendClass", () -> {
          test("appends simple range", () -> {
            assertClass(cc().appendClass(i('a', 'z')), 'a', 'z');
          });

          test("merges overlapping ranges", () -> {
            assertClass(cc('a', 'f').appendClass(i('c', 't')), 'a', 't');
          });

          test("merges in reverse order", () -> {
            assertClass(cc('c', 't').appendClass(i('a', 'f')), 'a', 't');
          });

          test("appends negated class", () -> {
            assertClass(
                cc('d', 'e').appendNegatedClass(i('b', 'f')),
                'd', 'e', 0, 'a', 'g', Unicode.MAX_RUNE);
          });
        });

        describe("appendFoldedClass", () -> {
          test("folds lowercase alphabet", () -> {
            char longS = 0x17F;
            char kelvin = 0x212A;
            assertClass(
                cc().appendFoldedClass(i('a', 'z')),
                s("akAK" + kelvin + kelvin + "lsLS" + longS + longS + "tzTZ"));
          });

          test("folds partial range", () -> {
            char longS = 0x17F;
            char kelvin = 0x212A;
            assertClass(
                cc('a', 'f').appendFoldedClass(i('c', 't')),
                s("akCK" + kelvin + kelvin + "lsLS" + longS + longS + "ttTT"));
          });

          test("folds non-overlapping range", () -> {
            assertClass(
                cc('c', 't').appendFoldedClass(i('a', 'f')),
                'c', 't', 'a', 'f', 'A', 'F');
          });
        });

        describe("negateClass", () -> {
          test("negates empty class to full range", () -> {
            assertClass(cc().negateClass(), '\0', Unicode.MAX_RUNE);
          });

          test("negates single range", () -> {
            assertClass(cc('A', 'Z').negateClass(), '\0', '@', '[', Unicode.MAX_RUNE);
          });

          test("negates multiple ranges", () -> {
            assertClass(
                cc('A', 'Z', 'a', 'z').negateClass(),
                '\0', '@', '[', '`', '{', Unicode.MAX_RUNE);
          });
        });

        describe("appendTable", () -> {
          test("appends table with stride 1 and stride 4", () -> {
            assertClass(
                cc().appendTable(new int[][] { i('a', 'z', 1), i('A', 'M', 4) }),
                'a', 'z', 'A', 'A', 'E', 'E', 'I', 'I', 'M', 'M');
          });

          test("appends table with stride 2 even chars", () -> {
            assertClass(
                cc().appendTable(new int[][] { i(0x100, 0x12e, 2) }),
                s("\u0100\u0100\u0102\u0102\u0104\u0104\u0106\u0106" +
                    "\u0108\u0108\u010a\u010a\u010c\u010c\u010e\u010e" +
                    "\u0110\u0110\u0112\u0112\u0114\u0114\u0116\u0116" +
                    "\u0118\u0118\u011a\u011a\u011c\u011c\u011e\u011e" +
                    "\u0120\u0120\u0122\u0122\u0124\u0124\u0126\u0126" +
                    "\u0128\u0128\u012a\u012a\u012c\u012c\u012e\u012e"));
          });

          test("appends table with stride 2 odd chars", () -> {
            assertClass(
                cc().appendTable(new int[][] { i(0x101, 0x12f, 2) }),
                s("\u0101\u0101\u0103\u0103\u0105\u0105\u0107\u0107" +
                    "\u0109\u0109\u010b\u010b\u010d\u010d\u010f\u010f" +
                    "\u0111\u0111\u0113\u0113\u0115\u0115\u0117\u0117" +
                    "\u0119\u0119\u011b\u011b\u011d\u011d\u011f\u011f" +
                    "\u0121\u0121\u0123\u0123\u0125\u0125\u0127\u0127" +
                    "\u0129\u0129\u012b\u012b\u012d\u012d\u012f\u012f"));
          });

          test("appends negated table", () -> {
            assertClass(
                cc().appendNegatedTable(new int[][] { i('b', 'f', 1) }),
                0, 'a', 'g', Unicode.MAX_RUNE);
          });
        });

        describe("appendGroup", () -> {
          test("appends digit group", () -> {
            assertClass(cc().appendGroup(CharGroup.PERL_GROUPS.get("\\d"), false), '0', '9');
          });

          test("appends negated digit group", () -> {
            assertClass(
                cc().appendGroup(CharGroup.PERL_GROUPS.get("\\D"), false),
                0, '/', ':', Unicode.MAX_RUNE);
          });
        });

        describe("toString", () -> {
          test("formats class correctly", () -> {
            expect(cc(10, 10, 12, 20).toString()).toBe("[0xa 0xc-0x14]");
          });
        });
      });
    });
  }

  private static CharClass cc(int... x) {
    return new CharClass(x);
  }

  private static int[] i(int... x) {
    return x;
  }

  private static int[] s(String str) {
    return Utils.stringToRunes(str);
  }

  private static void assertClass(CharClass cc, int... expected) {
    int[] actual = cc.toArray();
    if (!Arrays.equals(actual, expected)) {
      throw new AssertionError(
          "Incorrect CharClass value:\n"
              + "Expected: " + Arrays.toString(expected) + "\n"
              + "Actual:   " + Arrays.toString(actual));
    }
  }
}