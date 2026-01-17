package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

public class SimplifyTest {

  public static void main(String[] args) {
    entry(() -> {
      describe("Simplify", () -> {
        describe("already simple constructs", () -> {
          runTests(ALREADY_SIMPLE);
        });

        describe("POSIX character classes", () -> {
          runTests(POSIX_CLASSES);
        });

        describe("Perl character classes", () -> {
          runTests(PERL_CLASSES);
        });

        describe("POSIX repetitions", () -> {
          runTests(POSIX_REPETITIONS);
        });

        describe("operator simplification", () -> {
          runTests(OPERATOR_SIMPLIFICATION);
        });

        describe("character class simplification", () -> {
          runTests(CHAR_CLASS_SIMPLIFICATION);
        });

        describe("empty and full character classes", () -> {
          runTests(EMPTY_FULL_CLASSES);
        });

        describe("Unicode case folding", () -> {
          runTests(UNICODE_CASE_FOLDING);
        });

        describe("empty string as regex", () -> {
          runTests(EMPTY_STRING_REGEX);
        });
      });
    });
  }

  private static void runTests(String[][] tests) {
    for (String[] tc : tests) {
      String input = tc[0];
      String expected = tc[1];

      test(escape(input) + " -> " + escape(expected), () -> {
        Regexp re = Parser.parse(input, RE2.MATCH_NL | (RE2.PERL & ~RE2.ONE_LINE));
        String actual = Simplify.simplify(re).toString();
        expect(actual).toBe(expected);
      });
    }
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

  private static final String[][] ALREADY_SIMPLE = {
      { "a", "a" },
      { "ab", "ab" },
      { "a|b", "[a-b]" },
      { "ab|cd", "ab|cd" },
      { "(ab)*", "(ab)*" },
      { "(ab)+", "(ab)+" },
      { "(ab)?", "(ab)?" },
      { ".", "(?s:.)" },
      { "^", "^" },
      { "$", "$" },
      { "[ac]", "[ac]" },
      { "[^ac]", "[^ac]" },
  };

  private static final String[][] POSIX_CLASSES = {
      { "[[:alnum:]]", "[0-9A-Za-z]" },
      { "[[:alpha:]]", "[A-Za-z]" },
      { "[[:blank:]]", "[\\t ]" },
      { "[[:cntrl:]]", "[\\x00-\\x1f\\x7f]" },
      { "[[:digit:]]", "[0-9]" },
      { "[[:graph:]]", "[!-~]" },
      { "[[:lower:]]", "[a-z]" },
      { "[[:print:]]", "[ -~]" },
      { "[[:punct:]]", "[!-/:-@\\[-`\\{-~]" },
      { "[[:space:]]", "[\\t-\\r ]" },
      { "[[:upper:]]", "[A-Z]" },
      { "[[:xdigit:]]", "[0-9A-Fa-f]" },
  };

  private static final String[][] PERL_CLASSES = {
      { "\\d", "[0-9]" },
      { "\\s", "[\\t-\\n\\f-\\r ]" },
      { "\\w", "[0-9A-Z_a-z]" },
      { "\\D", "[^0-9]" },
      { "\\S", "[^\\t-\\n\\f-\\r ]" },
      { "\\W", "[^0-9A-Z_a-z]" },
      { "[\\d]", "[0-9]" },
      { "[\\s]", "[\\t-\\n\\f-\\r ]" },
      { "[\\w]", "[0-9A-Z_a-z]" },
      { "[\\D]", "[^0-9]" },
      { "[\\S]", "[^\\t-\\n\\f-\\r ]" },
      { "[\\W]", "[^0-9A-Z_a-z]" },
  };

  private static final String[][] POSIX_REPETITIONS = {
      { "a{1}", "a" },
      { "a{2}", "aa" },
      { "a{5}", "aaaaa" },
      { "a{0,1}", "a?" },
      { "(a){0,2}", "(?:(a)(a)?)?" },
      { "(a){0,4}", "(?:(a)(?:(a)(?:(a)(a)?)?)?)?" },
      { "(a){2,6}", "(a)(a)(?:(a)(?:(a)(?:(a)(a)?)?)?)?" },
      { "a{0,2}", "(?:aa?)?" },
      { "a{0,4}", "(?:a(?:a(?:aa?)?)?)?" },
      { "a{2,6}", "aa(?:a(?:a(?:aa?)?)?)?" },
      { "a{0,}", "a*" },
      { "a{1,}", "a+" },
      { "a{2,}", "aa+" },
      { "a{5,}", "aaaaa+" },
  };

  private static final String[][] OPERATOR_SIMPLIFICATION = {
      { "(?:a{1,}){1,}", "a+" },
      { "(a{1,}b{1,})", "(a+b+)" },
      { "a{1,}|b{1,}", "a+|b+" },
      { "(?:a{1,})*", "(?:a+)*" },
      { "(?:a{1,})+", "a+" },
      { "(?:a{1,})?", "(?:a+)?" },
      { "", "(?:)" },
      { "a{0}", "(?:)" },
  };

  private static final String[][] CHAR_CLASS_SIMPLIFICATION = {
      { "[ab]", "[a-b]" },
      { "[a-za-za-z]", "[a-z]" },
      { "[A-Za-zA-Za-z]", "[A-Za-z]" },
      { "[ABCDEFGH]", "[A-H]" },
      { "[AB-CD-EF-GH]", "[A-H]" },
      { "[W-ZP-XE-R]", "[E-Z]" },
      { "[a-ee-gg-m]", "[a-m]" },
      { "[a-ea-ha-m]", "[a-m]" },
      { "[a-ma-ha-e]", "[a-m]" },
      { "[a-zA-Z0-9 -~]", "[ -~]" },
  };

  private static final String[][] EMPTY_FULL_CLASSES = {
      { "[^[:cntrl:][:^cntrl:]]", "[^\\x00-\\x{10FFFF}]" },
      { "[[:cntrl:][:^cntrl:]]", "(?s:.)" },
  };

  private static final String[][] UNICODE_CASE_FOLDING = {
      { "(?i)A", "(?i:A)" },
      { "(?i)a", "(?i:A)" },
      { "(?i)[A]", "(?i:A)" },
      { "(?i)[a]", "(?i:A)" },
      { "(?i)K", "(?i:K)" },
      { "(?i)k", "(?i:K)" },
      { "(?i)\\x{212a}", "(?i:K)" },
      { "(?i)[K]", "[Kk\u212A]" },
      { "(?i)[k]", "[Kk\u212A]" },
      { "(?i)[\\x{212a}]", "[Kk\u212A]" },
      { "(?i)[a-z]", "[A-Za-z\u017F\u212A]" },
      { "(?i)[\\x00-\\x{FFFD}]", "[\\x00-\uFFFD]" },
      { "(?i)[\\x00-\\x{10FFFF}]", "(?s:.)" },
  };

  private static final String[][] EMPTY_STRING_REGEX = {
      { "(a|b|)", "([a-b]|(?:))" },
      { "(|)", "()" },
      { "a()", "a()" },
      { "(()|())", "(()|())" },
      { "(a|)", "(a|(?:))" },
      { "ab()cd()", "ab()cd()" },
      { "()", "()" },
      { "()*", "()*" },
      { "()+", "()+" },
      { "()?", "()?" },
      { "(){0}", "(?:)" },
      { "(){1}", "()" },
      { "(){1,}", "()+" },
      { "(){0,2}", "(?:()()?)?" },
      { "(?:(a){0})", "(?:)" },
  };
}