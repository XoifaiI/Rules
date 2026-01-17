package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class ProgTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("Prog", () -> {
        describe("compile", () -> {
          for (String[] tc : COMPILE_TESTS) {
            String input = tc[0];
            String expected = tc[1];

            test("compiles " + escape(input), () -> {
              Regexp re = Parser.parse(input, RE2.PERL);
              Prog p = Compiler.compileRegexp(re);
              String s = p.toString();
              expect(s).toBe(expected);
            });
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

  private static final String[][] COMPILE_TESTS = {
      { "a", "0       fail\n" + "1*      rune1 \"a\" -> 2\n" + "2       match\n" },
      {
          "[A-M][n-z]",
          "0       fail\n"
              + "1*      rune \"AM\" -> 2\n"
              + "2       rune \"nz\" -> 3\n"
              + "3       match\n"
      },
      { "", "0       fail\n" + "1*      nop -> 2\n" + "2       match\n" },
      {
          "a?",
          "0       fail\n"
              + "1       rune1 \"a\" -> 3\n"
              + "2*      alt -> 1, 3\n"
              + "3       match\n"
      },
      {
          "a??",
          "0       fail\n"
              + "1       rune1 \"a\" -> 3\n"
              + "2*      alt -> 3, 1\n"
              + "3       match\n"
      },
      {
          "a+",
          "0       fail\n"
              + "1*      rune1 \"a\" -> 2\n"
              + "2       alt -> 1, 3\n"
              + "3       match\n"
      },
      {
          "a+?",
          "0       fail\n"
              + "1*      rune1 \"a\" -> 2\n"
              + "2       alt -> 3, 1\n"
              + "3       match\n"
      },
      {
          "a*",
          "0       fail\n"
              + "1       rune1 \"a\" -> 2\n"
              + "2*      alt -> 1, 3\n"
              + "3       match\n"
      },
      {
          "a*?",
          "0       fail\n"
              + "1       rune1 \"a\" -> 2\n"
              + "2*      alt -> 3, 1\n"
              + "3       match\n"
      },
      {
          "a+b+",
          "0       fail\n"
              + "1*      rune1 \"a\" -> 2\n"
              + "2       alt -> 1, 3\n"
              + "3       rune1 \"b\" -> 4\n"
              + "4       alt -> 3, 5\n"
              + "5       match\n"
      },
      {
          "(a+)(b+)",
          "0       fail\n"
              + "1*      cap 2 -> 2\n"
              + "2       rune1 \"a\" -> 3\n"
              + "3       alt -> 2, 4\n"
              + "4       cap 3 -> 5\n"
              + "5       cap 4 -> 6\n"
              + "6       rune1 \"b\" -> 7\n"
              + "7       alt -> 6, 8\n"
              + "8       cap 5 -> 9\n"
              + "9       match\n"
      },
      {
          "a+|b+",
          "0       fail\n"
              + "1       rune1 \"a\" -> 2\n"
              + "2       alt -> 1, 6\n"
              + "3       rune1 \"b\" -> 4\n"
              + "4       alt -> 3, 6\n"
              + "5*      alt -> 1, 3\n"
              + "6       match\n"
      },
      {
          "A[Aa]",
          "0       fail\n"
              + "1*      rune1 \"A\" -> 2\n"
              + "2       rune \"A\"/i -> 3\n"
              + "3       match\n"
      },
      {
          "(?:(?:^).)",
          "0       fail\n"
              + "1*      empty 4 -> 2\n"
              + "2       anynotnl -> 3\n"
              + "3       match\n"
      },
      {
          "(?:|a)+",
          "0       fail\n"
              + "1       nop -> 4\n"
              + "2       rune1 \"a\" -> 4\n"
              + "3*      alt -> 1, 2\n"
              + "4       alt -> 3, 5\n"
              + "5       match\n"
      },
      {
          "(?:|a)*",
          "0       fail\n"
              + "1       nop -> 4\n"
              + "2       rune1 \"a\" -> 4\n"
              + "3       alt -> 1, 2\n"
              + "4       alt -> 3, 6\n"
              + "5*      alt -> 3, 6\n"
              + "6       match\n"
      },
  };
}