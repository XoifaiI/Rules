package re2j;

import java.util.ArrayList;

public final class Simplify {

  private static final int MAX_SIMPLIFY_DEPTH = 1000;

  private Simplify() {
  }

  public static Regexp simplify(Regexp regexp) {
    return simplifyInternal(regexp, 0);
  }

  private static Regexp simplifyInternal(Regexp regexp, int depth) {
    if (regexp == null) {
      return null;
    }
    if (depth > MAX_SIMPLIFY_DEPTH) {
      return regexp;
    }
    return switch (regexp.op) {
      case CAPTURE, CONCAT, ALTERNATE -> simplifyCompoundExpression(regexp, depth);
      case STAR, PLUS, QUEST -> simplifyBasicQuantifier(regexp, depth);
      case REPEAT -> simplifyBoundedRepeat(regexp, depth);
      default -> regexp;
    };
  }

  private static Regexp simplifyCompoundExpression(Regexp regexp, int depth) {
    Regexp result = regexp;
    for (int i = 0; i < regexp.subs.length; ++i) {
      Regexp originalSub = regexp.subs[i];
      Regexp simplifiedSub = simplifyInternal(originalSub, depth + 1);

      boolean subWasSimplified = simplifiedSub != originalSub;
      boolean needToCopyRegexp = result == regexp && subWasSimplified;

      if (needToCopyRegexp) {
        result = new Regexp(regexp);
        result.runes = null;
        result.subs = Parser.subarray(regexp.subs, 0, regexp.subs.length);
      }
      if (result != regexp) {
        result.subs[i] = simplifiedSub;
      }
    }
    return result;
  }

  private static Regexp simplifyBasicQuantifier(Regexp regexp, int depth) {
    Regexp simplifiedSub = simplifyInternal(regexp.subs[0], depth + 1);
    return simplifyQuantifier(regexp.op, regexp.flags, simplifiedSub, regexp);
  }

  private static Regexp simplifyBoundedRepeat(Regexp regexp, int depth) {
    int minRepeats = regexp.min;
    int maxRepeats = regexp.max;

    boolean isZeroRepeat = minRepeats == 0 && maxRepeats == 0;
    if (isZeroRepeat) {
      return new Regexp(Regexp.Op.EMPTY_MATCH);
    }

    Regexp simplifiedSub = simplifyInternal(regexp.subs[0], depth + 1);

    boolean hasUnboundedMax = maxRepeats == -1;
    if (hasUnboundedMax) {
      return simplifyUnboundedRepeat(regexp, simplifiedSub, minRepeats);
    }

    boolean isExactlyOneRepeat = minRepeats == 1 && maxRepeats == 1;
    if (isExactlyOneRepeat) {
      return simplifiedSub;
    }

    return simplifyBoundedRepeatRange(regexp, simplifiedSub, minRepeats, maxRepeats);
  }

  private static Regexp simplifyUnboundedRepeat(Regexp regexp, Regexp simplifiedSub, int minRepeats) {
    if (minRepeats == 0) {
      return simplifyQuantifier(Regexp.Op.STAR, regexp.flags, simplifiedSub, null);
    }
    if (minRepeats == 1) {
      return simplifyQuantifier(Regexp.Op.PLUS, regexp.flags, simplifiedSub, null);
    }

    ArrayList<Regexp> concatenatedParts = new ArrayList<>();
    for (int i = 0; i < minRepeats - 1; i++) {
      concatenatedParts.add(simplifiedSub);
    }
    Regexp oneOrMoreSuffix = simplifyQuantifier(Regexp.Op.PLUS, regexp.flags, simplifiedSub, null);
    concatenatedParts.add(oneOrMoreSuffix);

    Regexp concatenation = new Regexp(Regexp.Op.CONCAT);
    concatenation.subs = concatenatedParts.toArray(new Regexp[0]);
    return concatenation;
  }

  private static Regexp simplifyBoundedRepeatRange(
      Regexp regexp,
      Regexp simplifiedSub,
      int minRepeats,
      int maxRepeats) {

    ArrayList<Regexp> requiredParts = null;
    if (minRepeats > 0) {
      requiredParts = new ArrayList<>();
      for (int i = 0; i < minRepeats; i++) {
        requiredParts.add(simplifiedSub);
      }
    }

    boolean hasOptionalRepeats = maxRepeats > minRepeats;
    if (hasOptionalRepeats) {
      Regexp optionalChain = buildOptionalChain(regexp, simplifiedSub, minRepeats, maxRepeats);

      if (requiredParts == null) {
        return optionalChain;
      }
      requiredParts.add(optionalChain);
    }

    if (requiredParts != null) {
      Regexp concatenation = new Regexp(Regexp.Op.CONCAT);
      concatenation.subs = requiredParts.toArray(new Regexp[0]);
      return concatenation;
    }

    return new Regexp(Regexp.Op.NO_MATCH);
  }

  private static Regexp buildOptionalChain(
      Regexp regexp,
      Regexp simplifiedSub,
      int minRepeats,
      int maxRepeats) {

    Regexp optionalChain = simplifyQuantifier(Regexp.Op.QUEST, regexp.flags, simplifiedSub, null);

    for (int i = minRepeats + 1; i < maxRepeats; i++) {
      Regexp subFollowedByOptional = new Regexp(Regexp.Op.CONCAT);
      subFollowedByOptional.subs = new Regexp[] { simplifiedSub, optionalChain };
      optionalChain = simplifyQuantifier(Regexp.Op.QUEST, regexp.flags, subFollowedByOptional, null);
    }

    return optionalChain;
  }

  private static Regexp simplifyQuantifier(
      Regexp.Op quantifierOp,
      int flags,
      Regexp subExpression,
      Regexp originalRegexp) {

    boolean subIsEmptyMatch = subExpression.op == Regexp.Op.EMPTY_MATCH;
    if (subIsEmptyMatch) {
      return subExpression;
    }

    boolean subHasSameQuantifier = quantifierOp == subExpression.op;
    boolean greedinesMatches = (flags & RE2.NON_GREEDY) == (subExpression.flags & RE2.NON_GREEDY);
    if (subHasSameQuantifier && greedinesMatches) {
      return subExpression;
    }

    boolean canReuseOriginal = originalRegexp != null
        && originalRegexp.op == quantifierOp
        && (originalRegexp.flags & RE2.NON_GREEDY) == (flags & RE2.NON_GREEDY)
        && subExpression == originalRegexp.subs[0];
    if (canReuseOriginal) {
      return originalRegexp;
    }

    Regexp quantified = new Regexp(quantifierOp);
    quantified.flags = flags;
    quantified.subs = new Regexp[] { subExpression };
    return quantified;
  }
}