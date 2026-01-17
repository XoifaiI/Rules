package re2j;

import java.util.Arrays;

public final class CharClass {
  private static final int CASE_FOLDING_BATCH_SIZE_THRESHOLD = 256;

  private int[] codePointRanges;
  private int rangesLength;

  public CharClass() {
    this.codePointRanges = Utils.EMPTY_INTS;
    this.rangesLength = 0;
  }

  public CharClass(int[] codePointRanges) {
    this.codePointRanges = codePointRanges;
    this.rangesLength = codePointRanges.length;
  }

  private void ensureCapacity(int requiredLength) {
    if (codePointRanges.length < requiredLength) {
      if (requiredLength < rangesLength * 2) {
        requiredLength = rangesLength * 2;
      }
      codePointRanges = Arrays.copyOf(codePointRanges, requiredLength);
    }
  }

  public int[] toArray() {
    if (rangesLength == codePointRanges.length) {
      return codePointRanges;
    }
    return Arrays.copyOf(codePointRanges, rangesLength);
  }

  public CharClass cleanClass() {
    if (rangesLength < 4) {
      return this;
    }
    sortCodePointRangePairs(codePointRanges, 0, rangesLength - 2);
    int writeIndex = 2;
    for (int readIndex = 2; readIndex < rangesLength; readIndex += 2) {
      int currentRangeLow = codePointRanges[readIndex];
      int currentRangeHigh = codePointRanges[readIndex + 1];
      if (currentRangeLow <= codePointRanges[writeIndex - 1] + 1) {
        if (currentRangeHigh > codePointRanges[writeIndex - 1]) {
          codePointRanges[writeIndex - 1] = currentRangeHigh;
        }
        continue;
      }
      codePointRanges[writeIndex] = currentRangeLow;
      codePointRanges[writeIndex + 1] = currentRangeHigh;
      writeIndex += 2;
    }
    rangesLength = writeIndex;
    return this;
  }

  public CharClass appendLiteral(int codePoint, int flags) {
    return ((flags & RE2.FOLD_CASE) != 0)
        ? appendFoldedRange(codePoint, codePoint)
        : appendRange(codePoint, codePoint);
  }

  public CharClass appendRange(int rangeLow, int rangeHigh) {
    if (rangesLength > 0) {
      for (int lookbackOffset = 2; lookbackOffset <= 4; lookbackOffset += 2) {
        if (rangesLength >= lookbackOffset) {
          int existingRangeLow = codePointRanges[rangesLength - lookbackOffset];
          int existingRangeHigh = codePointRanges[rangesLength - lookbackOffset + 1];
          if (rangeLow <= existingRangeHigh + 1 && existingRangeLow <= rangeHigh + 1) {
            if (rangeLow < existingRangeLow) {
              codePointRanges[rangesLength - lookbackOffset] = rangeLow;
            }
            if (rangeHigh > existingRangeHigh) {
              codePointRanges[rangesLength - lookbackOffset + 1] = rangeHigh;
            }
            return this;
          }
        }
      }
    }
    ensureCapacity(rangesLength + 2);
    codePointRanges[rangesLength++] = rangeLow;
    codePointRanges[rangesLength++] = rangeHigh;
    return this;
  }

  public CharClass appendFoldedRange(int rangeLow, int rangeHigh) {
    if (rangeLow <= Unicode.MIN_FOLD && rangeHigh >= Unicode.MAX_FOLD) {
      return appendRange(rangeLow, rangeHigh);
    }
    if (rangeHigh < Unicode.MIN_FOLD || rangeLow > Unicode.MAX_FOLD) {
      return appendRange(rangeLow, rangeHigh);
    }
    int foldableRangeSize = Unicode.MAX_FOLD - Unicode.MIN_FOLD;
    if (rangeHigh - rangeLow > foldableRangeSize / 2) {
      return appendRange(rangeLow, rangeHigh);
    }
    if (rangeLow < Unicode.MIN_FOLD) {
      appendRange(rangeLow, Unicode.MIN_FOLD - 1);
      rangeLow = Unicode.MIN_FOLD;
    }
    if (rangeHigh > Unicode.MAX_FOLD) {
      appendRange(Unicode.MAX_FOLD + 1, rangeHigh);
      rangeHigh = Unicode.MAX_FOLD;
    }
    int rangeSize = rangeHigh - rangeLow + 1;
    if (rangeSize > CASE_FOLDING_BATCH_SIZE_THRESHOLD) {
      appendFoldedRangeInBatches(rangeLow, rangeHigh);
    } else {
      appendFoldedRangeDirectly(rangeLow, rangeHigh);
    }
    return this;
  }

  private void appendFoldedRangeDirectly(int rangeLow, int rangeHigh) {
    for (int codePoint = rangeLow; codePoint <= rangeHigh; codePoint++) {
      appendRange(codePoint, codePoint);
      for (int foldedCodePoint = Unicode.simpleFold(codePoint); foldedCodePoint != codePoint; foldedCodePoint = Unicode
          .simpleFold(foldedCodePoint)) {
        appendRange(foldedCodePoint, foldedCodePoint);
      }
    }
  }

  private void appendFoldedRangeInBatches(int rangeLow, int rangeHigh) {
    int rangeSize = rangeHigh - rangeLow + 1;
    int maxExpectedRanges = rangeSize * 4;
    int[] temporaryRanges = new int[maxExpectedRanges * 2];
    int temporaryLength = 0;
    for (int codePoint = rangeLow; codePoint <= rangeHigh; codePoint++) {
      temporaryRanges[temporaryLength++] = codePoint;
      temporaryRanges[temporaryLength++] = codePoint;
      for (int foldedCodePoint = Unicode.simpleFold(codePoint); foldedCodePoint != codePoint; foldedCodePoint = Unicode
          .simpleFold(foldedCodePoint)) {
        temporaryRanges[temporaryLength++] = foldedCodePoint;
        temporaryRanges[temporaryLength++] = foldedCodePoint;
      }
    }
    if (temporaryLength > 2) {
      sortCodePointRangePairs(temporaryRanges, 0, temporaryLength - 2);
    }
    int mergedWriteIndex = 0;
    for (int readIndex = 0; readIndex < temporaryLength; readIndex += 2) {
      int currentRangeLow = temporaryRanges[readIndex];
      int currentRangeHigh = temporaryRanges[readIndex + 1];
      if (mergedWriteIndex > 0 && currentRangeLow <= temporaryRanges[mergedWriteIndex - 1] + 1) {
        if (currentRangeHigh > temporaryRanges[mergedWriteIndex - 1]) {
          temporaryRanges[mergedWriteIndex - 1] = currentRangeHigh;
        }
      } else {
        temporaryRanges[mergedWriteIndex++] = currentRangeLow;
        temporaryRanges[mergedWriteIndex++] = currentRangeHigh;
      }
    }
    for (int index = 0; index < mergedWriteIndex; index += 2) {
      appendRange(temporaryRanges[index], temporaryRanges[index + 1]);
    }
  }

  public CharClass appendClass(int[] sourceRanges) {
    for (int index = 0; index < sourceRanges.length; index += 2) {
      appendRange(sourceRanges[index], sourceRanges[index + 1]);
    }
    return this;
  }

  public CharClass appendFoldedClass(int[] sourceRanges) {
    for (int index = 0; index < sourceRanges.length; index += 2) {
      appendFoldedRange(sourceRanges[index], sourceRanges[index + 1]);
    }
    return this;
  }

  public CharClass appendNegatedClass(int[] sourceRanges) {
    int nextUnmatchedLow = 0;
    for (int index = 0; index < sourceRanges.length; index += 2) {
      int excludedRangeLow = sourceRanges[index];
      int excludedRangeHigh = sourceRanges[index + 1];
      if (nextUnmatchedLow <= excludedRangeLow - 1) {
        appendRange(nextUnmatchedLow, excludedRangeLow - 1);
      }
      nextUnmatchedLow = excludedRangeHigh + 1;
    }
    if (nextUnmatchedLow <= Unicode.MAX_RUNE) {
      appendRange(nextUnmatchedLow, Unicode.MAX_RUNE);
    }
    return this;
  }

  public CharClass appendTable(int[][] unicodeRangeTable) {
    for (int[] rangeTriple : unicodeRangeTable) {
      int tableLow = rangeTriple[0];
      int tableHigh = rangeTriple[1];
      int tableStride = rangeTriple[2];
      if (tableStride == 1) {
        appendRange(tableLow, tableHigh);
        continue;
      }
      for (int codePoint = tableLow; codePoint <= tableHigh; codePoint += tableStride) {
        appendRange(codePoint, codePoint);
      }
    }
    return this;
  }

  public CharClass appendNegatedTable(int[][] unicodeRangeTable) {
    int nextUnmatchedLow = 0;
    for (int[] rangeTriple : unicodeRangeTable) {
      int tableLow = rangeTriple[0];
      int tableHigh = rangeTriple[1];
      int tableStride = rangeTriple[2];
      if (tableStride == 1) {
        if (nextUnmatchedLow <= tableLow - 1) {
          appendRange(nextUnmatchedLow, tableLow - 1);
        }
        nextUnmatchedLow = tableHigh + 1;
        continue;
      }
      for (int codePoint = tableLow; codePoint <= tableHigh; codePoint += tableStride) {
        if (nextUnmatchedLow <= codePoint - 1) {
          appendRange(nextUnmatchedLow, codePoint - 1);
        }
        nextUnmatchedLow = codePoint + 1;
      }
    }
    if (nextUnmatchedLow <= Unicode.MAX_RUNE) {
      appendRange(nextUnmatchedLow, Unicode.MAX_RUNE);
    }
    return this;
  }

  CharClass appendTableWithSign(int[][] unicodeRangeTable, int sign) {
    return sign < 0 ? appendNegatedTable(unicodeRangeTable) : appendTable(unicodeRangeTable);
  }

  public CharClass negateClass() {
    int nextUnmatchedLow = 0;
    int writeIndex = 0;
    for (int readIndex = 0; readIndex < rangesLength; readIndex += 2) {
      int excludedRangeLow = codePointRanges[readIndex];
      int excludedRangeHigh = codePointRanges[readIndex + 1];
      if (nextUnmatchedLow <= excludedRangeLow - 1) {
        codePointRanges[writeIndex] = nextUnmatchedLow;
        codePointRanges[writeIndex + 1] = excludedRangeLow - 1;
        writeIndex += 2;
      }
      nextUnmatchedLow = excludedRangeHigh + 1;
    }
    rangesLength = writeIndex;
    if (nextUnmatchedLow <= Unicode.MAX_RUNE) {
      ensureCapacity(rangesLength + 2);
      codePointRanges[rangesLength++] = nextUnmatchedLow;
      codePointRanges[rangesLength++] = Unicode.MAX_RUNE;
    }
    return this;
  }

  CharClass appendClassWithSign(int[] sourceRanges, int sign) {
    return sign < 0 ? appendNegatedClass(sourceRanges) : appendClass(sourceRanges);
  }

  public CharClass appendGroup(CharGroup charGroup, boolean foldCase) {
    int[] characterClass = charGroup.cls();
    if (foldCase) {
      characterClass = new CharClass().appendFoldedClass(characterClass).cleanClass().toArray();
    }
    return appendClassWithSign(characterClass, charGroup.sign());
  }

  private static int compareRangePairs(int[] array, int index, int pivotLow, int pivotHigh) {
    int lowComparison = array[index] - pivotLow;
    return lowComparison != 0 ? lowComparison : pivotHigh - array[index + 1];
  }

  private static void sortCodePointRangePairs(int[] array, int leftBound, int rightBound) {
    int pivotIndex = ((leftBound + rightBound) / 2) & ~1;
    int pivotLow = array[pivotIndex];
    int pivotHigh = array[pivotIndex + 1];
    int leftPointer = leftBound;
    int rightPointer = rightBound;
    while (leftPointer <= rightPointer) {
      while (leftPointer < rightBound
          && compareRangePairs(array, leftPointer, pivotLow, pivotHigh) < 0) {
        leftPointer += 2;
      }
      while (rightPointer > leftBound
          && compareRangePairs(array, rightPointer, pivotLow, pivotHigh) > 0) {
        rightPointer -= 2;
      }
      if (leftPointer <= rightPointer) {
        if (leftPointer != rightPointer) {
          int temporaryValue = array[leftPointer];
          array[leftPointer] = array[rightPointer];
          array[rightPointer] = temporaryValue;
          temporaryValue = array[leftPointer + 1];
          array[leftPointer + 1] = array[rightPointer + 1];
          array[rightPointer + 1] = temporaryValue;
        }
        leftPointer += 2;
        rightPointer -= 2;
      }
    }
    if (leftBound < rightPointer) {
      sortCodePointRangePairs(array, leftBound, rightPointer);
    }
    if (leftPointer < rightBound) {
      sortCodePointRangePairs(array, leftPointer, rightBound);
    }
  }

  static String charClassToString(int[] ranges, int length) {
    var stringBuilder = new StringBuilder();
    stringBuilder.append('[');
    for (int index = 0; index < length; index += 2) {
      if (index > 0) {
        stringBuilder.append(' ');
      }
      int rangeLow = ranges[index];
      int rangeHigh = ranges[index + 1];
      if (rangeLow == rangeHigh) {
        stringBuilder.append("0x").append(Integer.toHexString(rangeLow));
      } else {
        stringBuilder.append("0x").append(Integer.toHexString(rangeLow));
        stringBuilder.append("-0x").append(Integer.toHexString(rangeHigh));
      }
    }
    stringBuilder.append(']');
    return stringBuilder.toString();
  }

  @Override
  public String toString() {
    return charClassToString(codePointRanges, rangesLength);
  }
}