package re2j;

import static testing.Testing.describe;
import static testing.Testing.entry;
import static testing.Testing.expect;
import static testing.Testing.test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

public class ParserTest {

  private static final Map<Regexp.Op, String> OP_NAMES = new EnumMap<>(Regexp.Op.class);

  static {
    OP_NAMES.put(Regexp.Op.NO_MATCH, "no");
    OP_NAMES.put(Regexp.Op.EMPTY_MATCH, "emp");
    OP_NAMES.put(Regexp.Op.LITERAL, "lit");
    OP_NAMES.put(Regexp.Op.CHAR_CLASS, "cc");
    OP_NAMES.put(Regexp.Op.ANY_CHAR_NOT_NL, "dnl");
    OP_NAMES.put(Regexp.Op.ANY_CHAR, "dot");
    OP_NAMES.put(Regexp.Op.BEGIN_LINE, "bol");
    OP_NAMES.put(Regexp.Op.END_LINE, "eol");
    OP_NAMES.put(Regexp.Op.BEGIN_TEXT, "bot");
    OP_NAMES.put(Regexp.Op.END_TEXT, "eot");
    OP_NAMES.put(Regexp.Op.WORD_BOUNDARY, "wb");
    OP_NAMES.put(Regexp.Op.NO_WORD_BOUNDARY, "nwb");
    OP_NAMES.put(Regexp.Op.CAPTURE, "cap");
    OP_NAMES.put(Regexp.Op.STAR, "star");
    OP_NAMES.put(Regexp.Op.PLUS, "plus");
    OP_NAMES.put(Regexp.Op.QUEST, "que");
    OP_NAMES.put(Regexp.Op.REPEAT, "rep");
    OP_NAMES.put(Regexp.Op.CONCAT, "cat");
    OP_NAMES.put(Regexp.Op.ALTERNATE, "alt");
  }

  private static final int TEST_FLAGS = RE2.MATCH_NL | RE2.PERL_X | RE2.UNICODE_GROUPS;

  public static void main(String[] args) {
    entry(() -> {
      describe("Parser", () -> {
        describe("parse simple", () -> {
          for (String[] tc : PARSE_TESTS) {
            test("parses " + escape(tc[0]), () -> {
              Regexp re = Parser.parse(tc[0], TEST_FLAGS);
              String d = dump(re);
              expect(d).toBe(tc[1]);
            });
          }
        });

        describe("parse fold case", () -> {
          for (String[] tc : FOLDCASE_TESTS) {
            test("parses " + escape(tc[0]) + " with FOLD_CASE", () -> {
              Regexp re = Parser.parse(tc[0], RE2.FOLD_CASE);
              String d = dump(re);
              expect(d).toBe(tc[1]);
            });
          }
        });

        describe("parse literal", () -> {
          for (String[] tc : LITERAL_TESTS) {
            test("parses " + escape(tc[0]) + " with LITERAL", () -> {
              Regexp re = Parser.parse(tc[0], RE2.LITERAL);
              String d = dump(re);
              expect(d).toBe(tc[1]);
            });
          }
        });

        describe("parse match NL", () -> {
          for (String[] tc : MATCHNL_TESTS) {
            test("parses " + escape(tc[0]) + " with MATCH_NL", () -> {
              Regexp re = Parser.parse(tc[0], RE2.MATCH_NL);
              String d = dump(re);
              expect(d).toBe(tc[1]);
            });
          }
        });

        describe("parse no match NL", () -> {
          for (String[] tc : NOMATCHNL_TESTS) {
            test("parses " + escape(tc[0]) + " without MATCH_NL", () -> {
              Regexp re = Parser.parse(tc[0], 0);
              String d = dump(re);
              expect(d).toBe(tc[1]);
            });
          }
        });

        describe("invalid regexps", () -> {
          for (String regexp : INVALID_REGEXPS) {
            test("rejects " + escape(regexp) + " in PERL mode", () -> {
              expect(() -> Parser.parse(regexp, RE2.PERL)).toThrow();
            });

            test("rejects " + escape(regexp) + " in POSIX mode", () -> {
              expect(() -> Parser.parse(regexp, RE2.POSIX)).toThrow();
            });
          }
        });

        describe("PERL only regexps", () -> {
          for (String regexp : ONLY_PERL) {
            test("accepts " + escape(regexp) + " in PERL mode", () -> {
              Parser.parse(regexp, RE2.PERL);
            });

            test("rejects " + escape(regexp) + " in POSIX mode", () -> {
              expect(() -> Parser.parse(regexp, RE2.POSIX)).toThrow();
            });
          }
        });

        describe("POSIX only regexps", () -> {
          for (String regexp : ONLY_POSIX) {
            test("rejects " + escape(regexp) + " in PERL mode", () -> {
              expect(() -> Parser.parse(regexp, RE2.PERL)).toThrow();
            });

            test("accepts " + escape(regexp) + " in POSIX mode", () -> {
              Parser.parse(regexp, RE2.POSIX);
            });
          }
        });

        describe("appendRange collapse", () -> {
          test("collapses adjacent ranges", () -> {
            CharClass cc = new CharClass();
            for (int i = 'A'; i <= 'Z'; i++) {
              cc.appendRange(i, i);
              cc.appendRange(i + 'a' - 'A', i + 'a' - 'A');
            }
            expect(runesToString(cc.toArray())).toBe("AZaz");
          });
        });

        describe("toString equivalent parse", () -> {
          for (String[] tt : PARSE_TESTS) {
            test("toString of " + escape(tt[0]) + " parses equivalently", () -> {
              Regexp re = Parser.parse(tt[0], TEST_FLAGS);
              String d = dump(re);
              expect(d).toBe(tt[1]);

              String s = re.toString();
              if (!s.equals(tt[0])) {
                Regexp nre = Parser.parse(s, TEST_FLAGS);
                String nd = dump(nre);
                expect(nd).toBe(d);
                expect(nre.toString()).toBe(s);
              }
            });
          }
        });
      });
    });
  }

  private static String escape(String s) {
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

  private static String dump(Regexp re) {
    StringBuilder b = new StringBuilder();
    dumpRegexp(b, re);
    return b.toString();
  }

  private static void dumpRegexp(StringBuilder b, Regexp re) {
    String name = OP_NAMES.get(re.op);
    if (name == null) {
      b.append("op").append(re.op);
    } else {
      switch (re.op) {
        case STAR:
        case PLUS:
        case QUEST:
        case REPEAT:
          if ((re.flags & RE2.NON_GREEDY) != 0) {
            b.append('n');
          }
          b.append(name);
          break;
        case LITERAL:
          if (re.runes.length > 1) {
            b.append("str");
          } else {
            b.append("lit");
          }
          if ((re.flags & RE2.FOLD_CASE) != 0) {
            for (int r : re.runes) {
              if (Unicode.simpleFold(r) != r) {
                b.append("fold");
                break;
              }
            }
          }
          break;
        default:
          b.append(name);
          break;
      }
    }
    b.append('{');
    switch (re.op) {
      default:
        break;
      case END_TEXT:
        if ((re.flags & RE2.WAS_DOLLAR) == 0) {
          b.append("\\z");
        }
        break;
      case LITERAL:
        for (int r : re.runes) {
          b.appendCodePoint(r);
        }
        break;
      case CONCAT:
      case ALTERNATE:
        for (Regexp sub : re.subs) {
          dumpRegexp(b, sub);
        }
        break;
      case STAR:
      case PLUS:
      case QUEST:
        dumpRegexp(b, re.subs[0]);
        break;
      case REPEAT:
        b.append(re.min).append(',').append(re.max).append(' ');
        dumpRegexp(b, re.subs[0]);
        break;
      case CAPTURE:
        if (re.name != null && !re.name.isEmpty()) {
          b.append(re.name);
          b.append(':');
        }
        dumpRegexp(b, re.subs[0]);
        break;
      case CHAR_CLASS: {
        String sep = "";
        for (int i = 0; i < re.runes.length; i += 2) {
          b.append(sep);
          sep = " ";
          int lo = re.runes[i];
          int hi = re.runes[i + 1];
          if (lo == hi) {
            b.append(String.format("%#x", lo));
          } else {
            b.append(String.format("%#x-%#x", lo, hi));
          }
        }
        break;
      }
    }
    b.append('}');
  }

  private interface RunePredicate {
    boolean applies(int rune);
  }

  private static final RunePredicate IS_UPPER = r -> Character.getType(r) == Character.UPPERCASE_LETTER;

  private static final String CASE_INSENSITIVE_LU = "cc{0x41-0x5a 0x61-0x7a 0xc0-0xd6 0xd8-0xf6 0xf8-0x130 0x132-0x137 "
      +
      "0x139-0x148 0x14a-0x17e 0x180-0x18c 0x18e-0x1a9 0x1ac-0x1b9 0x1bc-0x1bd " +
      "0x1bf 0x1c4-0x1ef 0x1f1-0x220 0x222-0x233 0x23a-0x254 0x256-0x257 0x259 " +
      "0x25b-0x25c 0x260-0x261 0x263-0x266 0x268-0x26c 0x26f 0x271-0x272 0x275 " +
      "0x27d 0x280 0x282-0x283 0x287-0x28c 0x292 0x29d-0x29e 0x370-0x373 " +
      "0x376-0x377 0x37b-0x37d 0x37f 0x386 0x388-0x38a 0x38c 0x38e-0x38f " +
      "0x391-0x3a1 0x3a3-0x3af 0x3b1-0x3c1 0x3c3-0x3cf 0x3d2-0x3d4 0x3d7-0x3ef " +
      "0x3f2-0x3f4 0x3f7-0x3fb 0x3fd-0x481 0x48a-0x52f 0x531-0x556 0x561-0x586 " +
      "0x10a0-0x10c5 0x10c7 0x10cd 0x10d0-0x10fa 0x10fd-0x10ff 0x13a0-0x13f5 " +
      "0x13f8-0x13fd 0x1c89-0x1c8a 0x1c90-0x1cba 0x1cbd-0x1cbf 0x1d79 0x1d7d " +
      "0x1d8e 0x1e00-0x1e95 0x1e9e 0x1ea0-0x1f15 0x1f18-0x1f1d 0x1f20-0x1f45 " +
      "0x1f48-0x1f4d 0x1f51 0x1f53 0x1f55 0x1f57 0x1f59 0x1f5b 0x1f5d " +
      "0x1f5f-0x1f7d 0x1fb0-0x1fb1 0x1fb8-0x1fbb 0x1fc8-0x1fcb 0x1fd0-0x1fd1 " +
      "0x1fd8-0x1fdb 0x1fe0-0x1fe1 0x1fe5 0x1fe8-0x1fec 0x1ff8-0x1ffb 0x2102 " +
      "0x2107 0x210b-0x210d 0x2110-0x2112 0x2115 0x2119-0x211d 0x2124 0x2126 " +
      "0x2128 0x212a-0x212d 0x2130-0x2133 0x213e-0x213f 0x2145 0x214e " +
      "0x2183-0x2184 0x2c00-0x2c70 0x2c72-0x2c73 0x2c75-0x2c76 0x2c7e-0x2ce3 " +
      "0x2ceb-0x2cee 0x2cf2-0x2cf3 0x2d00-0x2d25 0x2d27 0x2d2d 0xa640-0xa66d " +
      "0xa680-0xa69b 0xa722-0xa72f 0xa732-0xa76f 0xa779-0xa787 0xa78b-0xa78d " +
      "0xa790-0xa794 0xa796-0xa7ae 0xa7b0-0xa7cd 0xa7d0-0xa7d1 0xa7d6-0xa7dc " +
      "0xa7f5-0xa7f6 0xab53 0xab70-0xabbf 0xff21-0xff3a 0xff41-0xff5a " +
      "0x10400-0x1044f 0x104b0-0x104d3 0x104d8-0x104fb 0x10570-0x1057a " +
      "0x1057c-0x1058a 0x1058c-0x10592 0x10594-0x10595 0x10597-0x105a1 " +
      "0x105a3-0x105b1 0x105b3-0x105b9 0x105bb-0x105bc 0x10c80-0x10cb2 " +
      "0x10cc0-0x10cf2 0x10d50-0x10d65 0x10d70-0x10d85 0x118a0-0x118df " +
      "0x16e40-0x16e7f 0x1d400-0x1d419 0x1d434-0x1d44d 0x1d468-0x1d481 " +
      "0x1d49c 0x1d49e-0x1d49f 0x1d4a2 0x1d4a5-0x1d4a6 0x1d4a9-0x1d4ac " +
      "0x1d4ae-0x1d4b5 0x1d4d0-0x1d4e9 0x1d504-0x1d505 0x1d507-0x1d50a " +
      "0x1d50d-0x1d514 0x1d516-0x1d51c 0x1d538-0x1d539 0x1d53b-0x1d53e " +
      "0x1d540-0x1d544 0x1d546 0x1d54a-0x1d550 0x1d56c-0x1d585 0x1d5a0-0x1d5b9 " +
      "0x1d5d4-0x1d5ed 0x1d608-0x1d621 0x1d63c-0x1d655 0x1d670-0x1d689 " +
      "0x1d6a8-0x1d6c0 0x1d6e2-0x1d6fa 0x1d71c-0x1d734 0x1d756-0x1d76e " +
      "0x1d790-0x1d7a8 0x1d7ca 0x1e900-0x1e943}";

  private static String mkCharClass(RunePredicate f) {
    Regexp re = new Regexp(Regexp.Op.CHAR_CLASS);
    ArrayList<Integer> runes = new ArrayList<>();
    int lo = -1;
    for (int i = 0; i <= Unicode.MAX_RUNE; i++) {
      if (f.applies(i)) {
        if (lo < 0) {
          lo = i;
        }
      } else {
        if (lo >= 0) {
          runes.add(lo);
          runes.add(i - 1);
          lo = -1;
        }
      }
    }
    if (lo >= 0) {
      runes.add(lo);
      runes.add(Unicode.MAX_RUNE);
    }
    re.runes = new int[runes.size()];
    int j = 0;
    for (Integer i : runes) {
      re.runes[j++] = i;
    }
    return dump(re);
  }

  private static String runesToString(int[] runes) {
    StringBuilder out = new StringBuilder();
    for (int rune : runes) {
      out.appendCodePoint(rune);
    }
    return out.toString();
  }

  private static final String MODERN_Z_CATEGORY = "cc{0x20 0xa0 0x1680 0x2000-0x200a 0x2028-0x2029 0x202f 0x205f 0x3000}";

  private static final String[][] PARSE_TESTS = {
      { "a", "lit{a}" },
      { "a.", "cat{lit{a}dot{}}" },
      { "a.b", "cat{lit{a}dot{}lit{b}}" },
      { "ab", "str{ab}" },
      { "a.b.c", "cat{lit{a}dot{}lit{b}dot{}lit{c}}" },
      { "abc", "str{abc}" },
      { "a|^", "alt{lit{a}bol{}}" },
      { "a|b", "cc{0x61-0x62}" },
      { "(a)", "cap{lit{a}}" },
      { "(a)|b", "alt{cap{lit{a}}lit{b}}" },
      { "a*", "star{lit{a}}" },
      { "a+", "plus{lit{a}}" },
      { "a?", "que{lit{a}}" },
      { "a{2}", "rep{2,2 lit{a}}" },
      { "a{2,3}", "rep{2,3 lit{a}}" },
      { "a{2,}", "rep{2,-1 lit{a}}" },
      { "a*?", "nstar{lit{a}}" },
      { "a+?", "nplus{lit{a}}" },
      { "a??", "nque{lit{a}}" },
      { "a{2}?", "nrep{2,2 lit{a}}" },
      { "a{2,3}?", "nrep{2,3 lit{a}}" },
      { "a{2,}?", "nrep{2,-1 lit{a}}" },
      { "", "emp{}" },
      { "|", "emp{}" },
      { "|x|", "alt{emp{}lit{x}emp{}}" },
      { ".", "dot{}" },
      { "^", "bol{}" },
      { "$", "eol{}" },
      { "\\|", "lit{|}" },
      { "\\(", "lit{(}" },
      { "\\)", "lit{)}" },
      { "\\*", "lit{*}" },
      { "\\+", "lit{+}" },
      { "\\?", "lit{?}" },
      { "{", "lit{{}" },
      { "}", "lit{}}" },
      { "\\.", "lit{.}" },
      { "\\^", "lit{^}" },
      { "\\$", "lit{$}" },
      { "\\\\", "lit{\\}" },
      { "[ace]", "cc{0x61 0x63 0x65}" },
      { "[abc]", "cc{0x61-0x63}" },
      { "[a-z]", "cc{0x61-0x7a}" },
      { "[a]", "lit{a}" },
      { "\\-", "lit{-}" },
      { "-", "lit{-}" },
      { "\\_", "lit{_}" },
      { "abc", "str{abc}" },
      { "abc|def", "alt{str{abc}str{def}}" },
      { "abc|def|ghi", "alt{str{abc}str{def}str{ghi}}" },
      { "[[:lower:]]", "cc{0x61-0x7a}" },
      { "[a-z]", "cc{0x61-0x7a}" },
      { "[^[:lower:]]", "cc{0x0-0x60 0x7b-0x10ffff}" },
      { "[[:^lower:]]", "cc{0x0-0x60 0x7b-0x10ffff}" },
      { "(?i)[[:lower:]]", "cc{0x41-0x5a 0x61-0x7a 0x17f 0x212a}" },
      { "(?i)[a-z]", "cc{0x41-0x5a 0x61-0x7a 0x17f 0x212a}" },
      { "(?i)[^[:lower:]]", "cc{0x0-0x40 0x5b-0x60 0x7b-0x17e 0x180-0x2129 0x212b-0x10ffff}" },
      { "(?i)[[:^lower:]]", "cc{0x0-0x40 0x5b-0x60 0x7b-0x17e 0x180-0x2129 0x212b-0x10ffff}" },
      { "\\d", "cc{0x30-0x39}" },
      { "\\D", "cc{0x0-0x2f 0x3a-0x10ffff}" },
      { "\\s", "cc{0x9-0xa 0xc-0xd 0x20}" },
      { "\\S", "cc{0x0-0x8 0xb 0xe-0x1f 0x21-0x10ffff}" },
      { "\\w", "cc{0x30-0x39 0x41-0x5a 0x5f 0x61-0x7a}" },
      { "\\W", "cc{0x0-0x2f 0x3a-0x40 0x5b-0x5e 0x60 0x7b-0x10ffff}" },
      { "(?i)\\w", "cc{0x30-0x39 0x41-0x5a 0x5f 0x61-0x7a 0x17f 0x212a}" },
      { "(?i)\\W", "cc{0x0-0x2f 0x3a-0x40 0x5b-0x5e 0x60 0x7b-0x17e 0x180-0x2129 0x212b-0x10ffff}" },
      { "[^\\\\]", "cc{0x0-0x5b 0x5d-0x10ffff}" },
      { "\\p{Braille}", "cc{0x2800-0x28ff}" },
      { "\\P{Braille}", "cc{0x0-0x27ff 0x2900-0x10ffff}" },
      { "\\p{^Braille}", "cc{0x0-0x27ff 0x2900-0x10ffff}" },
      { "\\P{^Braille}", "cc{0x2800-0x28ff}" },
      { "\\pZ", MODERN_Z_CATEGORY },
      { "[\\p{Braille}]", "cc{0x2800-0x28ff}" },
      { "[\\P{Braille}]", "cc{0x0-0x27ff 0x2900-0x10ffff}" },
      { "[\\p{^Braille}]", "cc{0x0-0x27ff 0x2900-0x10ffff}" },
      { "[\\P{^Braille}]", "cc{0x2800-0x28ff}" },
      { "[\\pZ]", MODERN_Z_CATEGORY },
      { "\\p{Lu}", mkCharClass(IS_UPPER) },
      { "[\\p{Lu}]", mkCharClass(IS_UPPER) },
      { "(?i)[\\p{Lu}]", CASE_INSENSITIVE_LU },
      { "\\p{Any}", "dot{}" },
      { "\\p{^Any}", "cc{}" },
      { "[\\012-\\234]\\141", "cat{cc{0xa-0x9c}lit{a}}" },
      { "[\\x{41}-\\x7a]\\x61", "cat{cc{0x41-0x7a}lit{a}}" },
      { "a{,2}", "str{a{,2}}" },
      { "\\.\\^\\$\\\\", "str{.^$\\}" },
      { "[a-zABC]", "cc{0x41-0x43 0x61-0x7a}" },
      { "[^a]", "cc{0x0-0x60 0x62-0x10ffff}" },
      { "[α-ε☺]", "cc{0x3b1-0x3b5 0x263a}" },
      { "a*{", "cat{star{lit{a}}lit{{}}" },
      { "(?:ab)*", "star{str{ab}}" },
      { "(ab)*", "star{cap{str{ab}}}" },
      { "ab|cd", "alt{str{ab}str{cd}}" },
      { "a(b|c)d", "cat{lit{a}cap{cc{0x62-0x63}}lit{d}}" },
      { "(?:a)", "lit{a}" },
      { "(?:ab)(?:cd)", "str{abcd}" },
      { "(?:a+b+)(?:c+d+)", "cat{plus{lit{a}}plus{lit{b}}plus{lit{c}}plus{lit{d}}}" },
      { "(?:a+|b+)|(?:c+|d+)", "alt{plus{lit{a}}plus{lit{b}}plus{lit{c}}plus{lit{d}}}" },
      { "(?:a|b)|(?:c|d)", "cc{0x61-0x64}" },
      { "a|.", "dot{}" },
      { ".|a", "dot{}" },
      { "(?:[abc]|A|Z|hello|world)", "alt{cc{0x41 0x5a 0x61-0x63}str{hello}str{world}}" },
      { "(?:[abc]|A|Z)", "cc{0x41 0x5a 0x61-0x63}" },
      { "\\Q+|*?{[\\E", "str{+|*?{[}" },
      { "\\Q+\\E+", "plus{lit{+}}" },
      { "\\Qab\\E+", "cat{lit{a}plus{lit{b}}}" },
      { "\\Q\\\\E", "lit{\\}" },
      { "\\Q\\\\\\E", "str{\\\\}" },
      { "(?m)^", "bol{}" },
      { "(?m)$", "eol{}" },
      { "(?-m)^", "bot{}" },
      { "(?-m)$", "eot{}" },
      { "(?m)\\A", "bot{}" },
      { "(?m)\\z", "eot{\\z}" },
      { "(?-m)\\A", "bot{}" },
      { "(?-m)\\z", "eot{\\z}" },
      { "(?P<name>a)", "cap{name:lit{a}}" },
      { "(?<name>a)", "cap{name:lit{a}}" },
      { "[Aa]", "litfold{A}" },
      { "[\\x{100}\\x{101}]", "litfold{Ā}" },
      { "[Δδ]", "litfold{Δ}" },
      { "abcde", "str{abcde}" },
      { "[Aa][Bb]cd", "cat{strfold{AB}str{cd}}" },
      {
          "abc|abd|aef|bcx|bcy",
          "alt{cat{lit{a}alt{cat{lit{b}cc{0x63-0x64}}str{ef}}}cat{str{bc}cc{0x78-0x79}}}"
      },
      {
          "ax+y|ax+z|ay+w",
          "cat{lit{a}alt{cat{plus{lit{x}}lit{y}}cat{plus{lit{x}}lit{z}}cat{plus{lit{y}}lit{w}}}}"
      },
      { "(?:.)", "dot{}" },
      { "(?:x|(?:xa))", "cat{lit{x}alt{emp{}lit{a}}}" },
      { "(?:.|(?:.a))", "cat{dot{}alt{emp{}lit{a}}}" },
      { "(?:A(?:A|a))", "cat{lit{A}litfold{A}}" },
      { "(?:A|a)", "litfold{A}" },
      { "A|(?:A|a)", "litfold{A}" },
      { "(?s).", "dot{}" },
      { "(?-s).", "dnl{}" },
      { "(?:(?:^).)", "cat{bol{}dot{}}" },
      { "(?-s)(?:(?:^).)", "cat{bol{}dnl{}}" },
      { "[\\x00-\\x{10FFFF}]", "dot{}" },
      { "[^\\x00-\\x{10FFFF}]", "cc{}" },
      { "(?:[a][a-])", "cat{lit{a}cc{0x2d 0x61}}" },
      { "abc|abd", "cat{str{ab}cc{0x63-0x64}}" },
      { "a(?:b)c|abd", "cat{str{ab}cc{0x63-0x64}}" },
      {
          "abc|abd|aef|bcx|bcy",
          "alt{cat{lit{a}alt{cat{lit{b}cc{0x63-0x64}}str{ef}}}cat{str{bc}cc{0x78-0x79}}}"
      },
      { "abc|x|abd", "alt{str{abc}lit{x}str{abd}}" },
      { "(?i)abc|ABD", "cat{strfold{AB}cc{0x43-0x44 0x63-0x64}}" },
      { "[ab]c|[ab]d", "cat{cc{0x61-0x62}cc{0x63-0x64}}" },
      { ".c|.d", "cat{dot{}cc{0x63-0x64}}" },
      { "x{2}|x{2}[0-9]", "cat{rep{2,2 lit{x}}alt{emp{}cc{0x30-0x39}}}" },
      { "x{2}y|x{2}[0-9]y", "cat{rep{2,2 lit{x}}alt{lit{y}cat{cc{0x30-0x39}lit{y}}}}" },
      { "a.*?c|a.*?b", "cat{lit{a}alt{cat{nstar{dot{}}lit{c}}cat{nstar{dot{}}lit{b}}}}" },
  };

  private static final String[][] FOLDCASE_TESTS = {
      { "AbCdE", "strfold{ABCDE}" },
      { "[Aa]", "litfold{A}" },
      { "a", "litfold{A}" },
      { "A[F-g]", "cat{litfold{A}cc{0x41-0x7a 0x17f 0x212a}}" },
      { "[[:upper:]]", "cc{0x41-0x5a 0x61-0x7a 0x17f 0x212a}" },
      { "[[:lower:]]", "cc{0x41-0x5a 0x61-0x7a 0x17f 0x212a}" },
  };

  private static final String[][] LITERAL_TESTS = {
      { "(|)^$.[*+?]{5,10},\\", "str{(|)^$.[*+?]{5,10},\\}" },
  };

  private static final String[][] MATCHNL_TESTS = {
      { ".", "dot{}" },
      { "\n", "lit{\n}" },
      { "[^a]", "cc{0x0-0x60 0x62-0x10ffff}" },
      { "[a\\n]", "cc{0xa 0x61}" },
  };

  private static final String[][] NOMATCHNL_TESTS = {
      { ".", "dnl{}" },
      { "\n", "lit{\n}" },
      { "[^a]", "cc{0x0-0x9 0xb-0x60 0x62-0x10ffff}" },
      { "[a\\n]", "cc{0xa 0x61}" },
  };

  private static final String[] INVALID_REGEXPS = {
      "(",
      ")",
      "(a",
      "(a|b|",
      "(a|b",
      "[a-z",
      "([a-z)",
      "x{1001}",
      "x{9876543210}",
      "x{2,1}",
      "x{1,9876543210}",
      "(?P<name>a",
      "(?P<name>",
      "(?P<name",
      "(?P<x y>a)",
      "(?P<>a)",
      "(?<name>a",
      "(?<name>",
      "(?<name",
      "(?<x y>a)",
      "(?<>a)",
      "[a-Z]",
      "(?i)[a-Z]",
      "a{100000}",
      "a{100000,}",
      "x{1001",
      "x{9876543210",
      "x{9876543210,",
      "x{2,1",
      "x{1,9876543210",
      "(?P<foo>bar)(?P<foo>baz)",
      "(?P<foo>bar)(?<foo>baz)",
      "(?<foo>bar)(?P<foo>baz)",
      "(?<foo>bar)(?<foo>baz)",
      "\\x",
      "\\xv",
  };

  private static final String[] ONLY_PERL = {
      "[a-b-c]",
      "\\Qabc\\E",
      "\\Q*+?{[\\E",
      "\\Q\\\\E",
      "\\Q\\\\\\E",
      "\\Q\\\\\\\\E",
      "\\Q\\\\\\\\\\E",
      "(?:a)",
      "(?P<name>a)",
      "(?<name>a)",
  };

  private static final String[] ONLY_POSIX = {
      "a++",
      "a**",
      "a?*",
      "a+*",
      "a{1}*",
      ".{1}{2}.{3}",
  };
}