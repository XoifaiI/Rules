package re2j;

public final class Compiler {
  private static final int[] ANY_RUNE_EXCEPT_NEWLINE = { 0, '\n' - 1, '\n' + 1, Unicode.MAX_RUNE };
  private static final int[] ANY_RUNE = { 0, Unicode.MAX_RUNE };

  private final Prog program = new Prog();

  private Compiler() {
    createInstruction(Inst.FAIL);
  }

  public static Prog compileRegexp(Regexp regexp) {
    var compiler = new Compiler();
    var fragment = compiler.compile(regexp);
    compiler.program.patch(fragment.patchList, compiler.createInstruction(Inst.MATCH).instructionIndex);
    compiler.program.start = fragment.instructionIndex;
    return compiler.program;
  }

  private Fragment createInstruction(int opcode) {
    program.addInst(opcode);
    return new Fragment(program.numInst() - 1, 0, true);
  }

  private Fragment createNoOperation() {
    var fragment = createInstruction(Inst.NOP);
    return new Fragment(fragment.instructionIndex, fragment.instructionIndex << 1, fragment.matchesEmptyString);
  }

  private Fragment createFailure() {
    return new Fragment(0, 0, false);
  }

  private Fragment createCapture(int captureArgument) {
    var fragment = createInstruction(Inst.CAPTURE);
    program.getInst(fragment.instructionIndex).arg = captureArgument;
    if (program.numCap < captureArgument + 1) {
      program.numCap = captureArgument + 1;
    }
    return new Fragment(fragment.instructionIndex, fragment.instructionIndex << 1, fragment.matchesEmptyString);
  }

  private Fragment concatenate(Fragment first, Fragment second) {
    if (first.instructionIndex == 0 || second.instructionIndex == 0) {
      return createFailure();
    }
    program.patch(first.patchList, second.instructionIndex);
    return new Fragment(first.instructionIndex, second.patchList,
        first.matchesEmptyString && second.matchesEmptyString);
  }

  private Fragment alternate(Fragment first, Fragment second) {
    if (first.instructionIndex == 0) {
      return second;
    }
    if (second.instructionIndex == 0) {
      return first;
    }
    var fragment = createInstruction(Inst.ALT);
    var instruction = program.getInst(fragment.instructionIndex);
    instruction.out = first.instructionIndex;
    instruction.arg = second.instructionIndex;
    return new Fragment(
        fragment.instructionIndex,
        program.append(first.patchList, second.patchList),
        first.matchesEmptyString || second.matchesEmptyString);
  }

  private Fragment createLoop(Fragment body, boolean nonGreedy) {
    var fragment = createInstruction(Inst.ALT);
    var instruction = program.getInst(fragment.instructionIndex);
    int patchList;
    if (nonGreedy) {
      instruction.arg = body.instructionIndex;
      patchList = fragment.instructionIndex << 1;
    } else {
      instruction.out = body.instructionIndex;
      patchList = fragment.instructionIndex << 1 | 1;
    }
    program.patch(body.patchList, fragment.instructionIndex);
    return new Fragment(fragment.instructionIndex, patchList, fragment.matchesEmptyString);
  }

  private Fragment createOptional(Fragment body, boolean nonGreedy) {
    var fragment = createInstruction(Inst.ALT);
    var instruction = program.getInst(fragment.instructionIndex);
    int patchList;
    if (nonGreedy) {
      instruction.arg = body.instructionIndex;
      patchList = fragment.instructionIndex << 1;
    } else {
      instruction.out = body.instructionIndex;
      patchList = fragment.instructionIndex << 1 | 1;
    }
    return new Fragment(
        fragment.instructionIndex,
        program.append(patchList, body.patchList),
        fragment.matchesEmptyString);
  }

  private Fragment createZeroOrMore(Fragment body, boolean nonGreedy) {
    if (body.matchesEmptyString) {
      return createOptional(createOneOrMore(body, nonGreedy), nonGreedy);
    }
    return createLoop(body, nonGreedy);
  }

  private Fragment createOneOrMore(Fragment body, boolean nonGreedy) {
    return new Fragment(body.instructionIndex, createLoop(body, nonGreedy).patchList, body.matchesEmptyString);
  }

  private Fragment createEmptyWidthAssertion(int assertionType) {
    var fragment = createInstruction(Inst.EMPTY_WIDTH);
    program.getInst(fragment.instructionIndex).arg = assertionType;
    return new Fragment(fragment.instructionIndex, fragment.instructionIndex << 1, fragment.matchesEmptyString);
  }

  private Fragment createRuneMatcher(int rune, int flags) {
    return createRuneMatcher(new int[] { rune }, flags);
  }

  private Fragment createRuneMatcher(int[] runes, int flags) {
    var fragment = createInstruction(Inst.RUNE);
    var instruction = program.getInst(fragment.instructionIndex);
    instruction.runes = runes;
    flags &= RE2.FOLD_CASE;
    if (runes.length != 1 || Unicode.simpleFold(runes[0]) == runes[0]) {
      flags &= ~RE2.FOLD_CASE;
    }
    instruction.arg = flags;
    if (((flags & RE2.FOLD_CASE) == 0 && runes.length == 1)
        || (runes.length == 2 && runes[0] == runes[1])) {
      instruction.op = Inst.RUNE1;
    } else if (runes.length == 2 && runes[0] == 0 && runes[1] == Unicode.MAX_RUNE) {
      instruction.op = Inst.RUNE_ANY;
    } else if (runes.length == 4
        && runes[0] == 0
        && runes[1] == '\n' - 1
        && runes[2] == '\n' + 1
        && runes[3] == Unicode.MAX_RUNE) {
      instruction.op = Inst.RUNE_ANY_NOT_NL;
    }
    return new Fragment(fragment.instructionIndex, fragment.instructionIndex << 1, false);
  }

  private Fragment compile(Regexp regexp) {
    return switch (regexp.op) {
      case NO_MATCH -> createFailure();
      case EMPTY_MATCH -> createNoOperation();
      case LITERAL -> {
        if (regexp.runes.length == 0) {
          yield createNoOperation();
        }
        Fragment result = null;
        for (int rune : regexp.runes) {
          var runeFragment = createRuneMatcher(rune, regexp.flags);
          result = (result == null) ? runeFragment : concatenate(result, runeFragment);
        }
        yield result;
      }
      case CHAR_CLASS -> createRuneMatcher(regexp.runes, regexp.flags);
      case ANY_CHAR_NOT_NL -> createRuneMatcher(ANY_RUNE_EXCEPT_NEWLINE, 0);
      case ANY_CHAR -> createRuneMatcher(ANY_RUNE, 0);
      case BEGIN_LINE -> createEmptyWidthAssertion(Utils.EMPTY_BEGIN_LINE);
      case END_LINE -> createEmptyWidthAssertion(Utils.EMPTY_END_LINE);
      case BEGIN_TEXT -> createEmptyWidthAssertion(Utils.EMPTY_BEGIN_TEXT);
      case END_TEXT -> createEmptyWidthAssertion(Utils.EMPTY_END_TEXT);
      case WORD_BOUNDARY -> createEmptyWidthAssertion(Utils.EMPTY_WORD_BOUNDARY);
      case NO_WORD_BOUNDARY -> createEmptyWidthAssertion(Utils.EMPTY_NO_WORD_BOUNDARY);
      case CAPTURE -> {
        var openingCapture = createCapture(regexp.cap << 1);
        var subExpression = compile(regexp.subs[0]);
        var closingCapture = createCapture(regexp.cap << 1 | 1);
        yield concatenate(concatenate(openingCapture, subExpression), closingCapture);
      }
      case STAR -> createZeroOrMore(compile(regexp.subs[0]), (regexp.flags & RE2.NON_GREEDY) != 0);
      case PLUS -> createOneOrMore(compile(regexp.subs[0]), (regexp.flags & RE2.NON_GREEDY) != 0);
      case QUEST -> createOptional(compile(regexp.subs[0]), (regexp.flags & RE2.NON_GREEDY) != 0);
      case CONCAT -> {
        if (regexp.subs.length == 0) {
          yield createNoOperation();
        }
        Fragment result = null;
        for (Regexp subExpression : regexp.subs) {
          var subFragment = compile(subExpression);
          result = (result == null) ? subFragment : concatenate(result, subFragment);
        }
        yield result;
      }
      case ALTERNATE -> {
        if (regexp.subs.length == 0) {
          yield createNoOperation();
        }
        Fragment result = null;
        for (Regexp subExpression : regexp.subs) {
          var subFragment = compile(subExpression);
          result = (result == null) ? subFragment : alternate(result, subFragment);
        }
        yield result;
      }
      default -> throw new IllegalStateException("regexp: unhandled case in compile");
    };
  }

  private record Fragment(int instructionIndex, int patchList, boolean matchesEmptyString) {
  }
}