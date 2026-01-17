package re2j;

import java.util.Arrays;

public final class Prog {
  private static final int MAX_INSTRUCTIONS = 100000;

  Inst[] inst = new Inst[10];
  int instSize = 0;
  int start;
  int numCap = 2;

  Prog() {
  }

  Inst getInst(int programCounter) {
    return inst[programCounter];
  }

  int numInst() {
    return instSize;
  }

  void addInst(int opcode) {
    if (instSize >= MAX_INSTRUCTIONS) {
      throw new PatternSyntaxException("compiled program too large", "");
    }
    if (instSize >= inst.length) {
      inst = Arrays.copyOf(inst, Math.min(MAX_INSTRUCTIONS, inst.length * 2));
    }
    inst[instSize] = new Inst(opcode);
    instSize++;
  }

  Inst skipNop(int programCounter) {
    var instruction = inst[programCounter];
    while (instruction.op == Inst.NOP || instruction.op == Inst.CAPTURE) {
      instruction = inst[programCounter];
      programCounter = instruction.out;
    }
    return instruction;
  }

  boolean prefix(StringBuilder prefix) {
    var instruction = skipNop(start);
    if (!Inst.isRuneOp(instruction.op) || instruction.runes.length != 1) {
      return instruction.op == Inst.MATCH;
    }
    while (Inst.isRuneOp(instruction.op)
        && instruction.runes.length == 1
        && (instruction.arg & RE2.FOLD_CASE) == 0) {
      prefix.appendCodePoint(instruction.runes[0]);
      instruction = skipNop(instruction.out);
    }
    return instruction.op == Inst.MATCH;
  }

  int startCond() {
    int flag = 0;
    int programCounter = start;
    loop: for (;;) {
      var instruction = inst[programCounter];
      switch (instruction.op) {
        case Inst.EMPTY_WIDTH -> flag |= instruction.arg;
        case Inst.FAIL -> {
          return -1;
        }
        case Inst.CAPTURE, Inst.NOP -> {
        }
        default -> {
          break loop;
        }
      }
      programCounter = instruction.out;
    }
    return flag;
  }

  int next(int patchEntry) {
    var instruction = inst[patchEntry >> 1];
    if ((patchEntry & 1) == 0) {
      return instruction.out;
    }
    return instruction.arg;
  }

  void patch(int patchList, int targetAddress) {
    while (patchList != 0) {
      var instruction = inst[patchList >> 1];
      if ((patchList & 1) == 0) {
        patchList = instruction.out;
        instruction.out = targetAddress;
      } else {
        patchList = instruction.arg;
        instruction.arg = targetAddress;
      }
    }
  }

  int append(int firstList, int secondList) {
    if (firstList == 0) {
      return secondList;
    }
    if (secondList == 0) {
      return firstList;
    }
    int lastEntry = firstList;
    for (;;) {
      int nextEntry = next(lastEntry);
      if (nextEntry == 0) {
        break;
      }
      lastEntry = nextEntry;
    }
    var instruction = inst[lastEntry >> 1];
    if ((lastEntry & 1) == 0) {
      instruction.out = secondList;
    } else {
      instruction.arg = secondList;
    }
    return firstList;
  }

  @Override
  public String toString() {
    var stringBuilder = new StringBuilder();
    for (int programCounter = 0; programCounter < instSize; ++programCounter) {
      int lengthBefore = stringBuilder.length();
      stringBuilder.append(programCounter);
      if (programCounter == start) {
        stringBuilder.append('*');
      }
      stringBuilder
          .append("        ".substring(stringBuilder.length() - lengthBefore))
          .append(inst[programCounter])
          .append('\n');
    }
    return stringBuilder.toString();
  }
}