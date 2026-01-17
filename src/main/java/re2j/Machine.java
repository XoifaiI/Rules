package re2j;

import java.util.Arrays;

final class Machine {
  private static final int MAX_THREAD_POOL_SIZE = 1024;

  private final RE2 re2;
  private final Prog program;
  private final ThreadQueue primaryQueue;
  private final ThreadQueue secondaryQueue;
  private Thread[] threadPool = new Thread[10];
  private int threadPoolSize;
  private boolean hasMatched;
  private int[] matchCaptures;
  private int numCaptures;

  Machine next;

  Machine(RE2 re2) {
    this.program = re2.prog;
    this.re2 = re2;
    this.primaryQueue = new ThreadQueue(program.numInst());
    this.secondaryQueue = new ThreadQueue(program.numInst());
    this.matchCaptures = new int[program.numCap < 2 ? 2 : program.numCap];
  }

  Machine(Machine original) {
    this.re2 = original.re2;
    this.program = original.program;
    this.primaryQueue = original.primaryQueue;
    this.secondaryQueue = original.secondaryQueue;
    this.threadPool = original.threadPool;
    this.threadPoolSize = original.threadPoolSize;
    this.hasMatched = original.hasMatched;
    this.matchCaptures = original.matchCaptures;
    this.numCaptures = original.numCaptures;
  }

  void init(int captureCount) {
    this.numCaptures = captureCount;
    if (captureCount > matchCaptures.length) {
      initializeNewCaptures(captureCount);
    } else {
      resetCaptures(captureCount);
    }
  }

  private void resetCaptures(int captureCount) {
    for (int index = 0; index < threadPoolSize; index++) {
      Arrays.fill(threadPool[index].captures, 0, captureCount, 0);
    }
  }

  private void initializeNewCaptures(int captureCount) {
    for (int index = 0; index < threadPoolSize; index++) {
      threadPool[index].captures = new int[captureCount];
    }
    this.matchCaptures = new int[captureCount];
  }

  int[] submatches() {
    if (numCaptures == 0) {
      return Utils.EMPTY_INTS;
    }
    return Arrays.copyOf(matchCaptures, numCaptures);
  }

  private Thread allocateThread(Inst instruction) {
    Thread thread;
    if (threadPoolSize > 0) {
      threadPoolSize--;
      thread = threadPool[threadPoolSize];
    } else {
      thread = new Thread(matchCaptures.length);
    }
    thread.instruction = instruction;
    return thread;
  }

  private void freeQueueThreads(ThreadQueue queue) {
    freeQueueThreads(queue, 0);
  }

  private void freeQueueThreads(ThreadQueue queue, int startIndex) {
    int threadCount = queue.size - startIndex;
    int availablePoolSlots = MAX_THREAD_POOL_SIZE - threadPoolSize;
    int threadsToAdd = Math.min(threadCount, availablePoolSlots);
    if (threadsToAdd > 0 && threadPool.length < threadPoolSize + threadsToAdd) {
      threadPool = Arrays.copyOf(
          threadPool,
          Math.min(MAX_THREAD_POOL_SIZE, Math.max(threadPool.length * 2, threadPoolSize + threadsToAdd)));
    }
    int addedCount = 0;
    for (int index = startIndex; index < queue.size && addedCount < threadsToAdd; ++index) {
      var thread = queue.denseThreads[index];
      if (thread != null) {
        threadPool[threadPoolSize++] = thread;
        addedCount++;
      }
    }
    queue.clear();
  }

  private void freeThread(Thread thread) {
    if (threadPoolSize >= MAX_THREAD_POOL_SIZE) {
      return;
    }
    if (threadPool.length <= threadPoolSize) {
      threadPool = Arrays.copyOf(threadPool, Math.min(MAX_THREAD_POOL_SIZE, threadPool.length * 2));
    }
    threadPool[threadPoolSize++] = thread;
  }

  boolean match(MachineInput input, int position, int anchorMode) {
    int startCondition = re2.cond;
    if (startCondition == Utils.EMPTY_ALL) {
      return false;
    }
    if ((anchorMode == RE2.ANCHOR_START || anchorMode == RE2.ANCHOR_BOTH) && position != 0) {
      return false;
    }
    hasMatched = false;
    Arrays.fill(matchCaptures, 0, program.numCap, -1);
    var currentQueue = primaryQueue;
    var nextQueue = secondaryQueue;
    int stepResult = input.step(position);
    int currentRune = stepResult >> 3;
    int currentWidth = stepResult & 7;
    int lookaheadRune = -1;
    int lookaheadWidth = 0;
    if (stepResult != MachineInput.EOF) {
      stepResult = input.step(position + currentWidth);
      lookaheadRune = stepResult >> 3;
      lookaheadWidth = stepResult & 7;
    }
    int contextFlags;
    if (position == 0) {
      contextFlags = Utils.emptyOpContext(-1, currentRune);
    } else {
      contextFlags = input.context(position);
    }
    for (;;) {
      if (currentQueue.isEmpty()) {
        if ((startCondition & Utils.EMPTY_BEGIN_TEXT) != 0 && position != 0) {
          break;
        }
        if (hasMatched) {
          break;
        }
        if (!re2.prefix.isEmpty() && lookaheadRune != re2.prefixRune && input.canCheckPrefix()) {
          int advanceDistance = input.index(re2, position);
          if (advanceDistance < 0) {
            break;
          }
          position += advanceDistance;
          stepResult = input.step(position);
          currentRune = stepResult >> 3;
          currentWidth = stepResult & 7;
          stepResult = input.step(position + currentWidth);
          lookaheadRune = stepResult >> 3;
          lookaheadWidth = stepResult & 7;
        }
      }
      if (!hasMatched && (position == 0 || anchorMode == RE2.UNANCHORED)) {
        if (numCaptures > 0) {
          matchCaptures[0] = position;
        }
        addThread(currentQueue, program.start, position, matchCaptures, contextFlags, null);
      }
      int nextPosition = position + currentWidth;
      contextFlags = input.context(nextPosition);
      processStep(
          currentQueue,
          nextQueue,
          position,
          nextPosition,
          currentRune,
          contextFlags,
          anchorMode,
          position == input.endPos());
      if (currentWidth == 0) {
        break;
      }
      if (numCaptures == 0 && hasMatched) {
        break;
      }
      position += currentWidth;
      currentRune = lookaheadRune;
      currentWidth = lookaheadWidth;
      if (currentRune != -1) {
        stepResult = input.step(position + currentWidth);
        lookaheadRune = stepResult >> 3;
        lookaheadWidth = stepResult & 7;
      }
      var swapQueue = currentQueue;
      currentQueue = nextQueue;
      nextQueue = swapQueue;
    }
    freeQueueThreads(nextQueue);
    return hasMatched;
  }

  private void processStep(
      ThreadQueue currentQueue,
      ThreadQueue nextQueue,
      int position,
      int nextPosition,
      int codePoint,
      int nextCondition,
      int anchorMode,
      boolean atEndOfInput) {
    boolean preferLongestMatch = re2.longest;
    for (int threadIndex = 0; threadIndex < currentQueue.size; ++threadIndex) {
      var thread = currentQueue.denseThreads[threadIndex];
      if (thread == null) {
        continue;
      }
      if (preferLongestMatch && hasMatched && numCaptures > 0 && matchCaptures[0] < thread.captures[0]) {
        freeThread(thread);
        continue;
      }
      var instruction = thread.instruction;
      boolean shouldAddToNextQueue = false;
      switch (instruction.op) {
        case Inst.MATCH -> {
          if (anchorMode == RE2.ANCHOR_BOTH && !atEndOfInput) {
            break;
          }
          if (numCaptures > 0 && (!preferLongestMatch || !hasMatched || matchCaptures[1] < position)) {
            thread.captures[1] = position;
            System.arraycopy(thread.captures, 0, matchCaptures, 0, numCaptures);
          }
          if (!preferLongestMatch) {
            freeQueueThreads(currentQueue, threadIndex + 1);
          }
          hasMatched = true;
        }
        case Inst.RUNE -> shouldAddToNextQueue = instruction.matchRune(codePoint);
        case Inst.RUNE1 -> shouldAddToNextQueue = codePoint == instruction.runes[0];
        case Inst.RUNE_ANY -> shouldAddToNextQueue = true;
        case Inst.RUNE_ANY_NOT_NL -> shouldAddToNextQueue = codePoint != '\n';
        default -> throw new IllegalStateException("bad inst");
      }
      if (shouldAddToNextQueue) {
        thread = addThread(
            nextQueue,
            instruction.out,
            nextPosition,
            thread.captures,
            nextCondition,
            thread);
      }
      if (thread != null) {
        freeThread(thread);
        currentQueue.denseThreads[threadIndex] = null;
      }
    }
    currentQueue.clear();
  }

  private Thread addThread(
      ThreadQueue queue,
      int programCounter,
      int position,
      int[] captures,
      int condition,
      Thread thread) {
    if (programCounter == 0) {
      return thread;
    }
    if (queue.contains(programCounter)) {
      return thread;
    }
    int denseIndex = queue.add(programCounter);
    var instruction = program.inst[programCounter];
    switch (instruction.op) {
      case Inst.FAIL -> {
      }
      case Inst.ALT, Inst.ALT_MATCH -> {
        thread = addThread(queue, instruction.out, position, captures, condition, thread);
        thread = addThread(queue, instruction.arg, position, captures, condition, thread);
      }
      case Inst.EMPTY_WIDTH -> {
        if ((instruction.arg & ~condition) == 0) {
          thread = addThread(queue, instruction.out, position, captures, condition, thread);
        }
      }
      case Inst.NOP -> thread = addThread(queue, instruction.out, position, captures, condition, thread);
      case Inst.CAPTURE -> {
        if (instruction.arg < numCaptures) {
          int originalPosition = captures[instruction.arg];
          captures[instruction.arg] = position;
          addThread(queue, instruction.out, position, captures, condition, null);
          captures[instruction.arg] = originalPosition;
        } else {
          thread = addThread(queue, instruction.out, position, captures, condition, thread);
        }
      }
      case Inst.MATCH, Inst.RUNE, Inst.RUNE1, Inst.RUNE_ANY, Inst.RUNE_ANY_NOT_NL -> {
        if (thread == null) {
          thread = allocateThread(instruction);
        } else {
          thread.instruction = instruction;
        }
        if (numCaptures > 0 && thread.captures != captures) {
          System.arraycopy(captures, 0, thread.captures, 0, numCaptures);
        }
        queue.denseThreads[denseIndex] = thread;
        thread = null;
      }
      default -> throw new IllegalStateException("unhandled");
    }
    return thread;
  }

  private static final class Thread {
    int[] captures;
    Inst instruction;

    Thread(int captureCount) {
      this.captures = new int[captureCount];
    }
  }

  private static final class ThreadQueue {
    final Thread[] denseThreads;
    final int[] denseProgramCounters;
    final int[] sparseIndex;
    int size;

    ThreadQueue(int capacity) {
      this.sparseIndex = new int[capacity];
      this.denseProgramCounters = new int[capacity];
      this.denseThreads = new Thread[capacity];
    }

    boolean contains(int programCounter) {
      int index = sparseIndex[programCounter];
      return index < size && denseProgramCounters[index] == programCounter;
    }

    boolean isEmpty() {
      return size == 0;
    }

    int add(int programCounter) {
      int index = size++;
      sparseIndex[programCounter] = index;
      denseThreads[index] = null;
      denseProgramCounters[index] = programCounter;
      return index;
    }

    void clear() {
      size = 0;
    }

    @Override
    public String toString() {
      var stringBuilder = new StringBuilder();
      stringBuilder.append('{');
      for (int index = 0; index < size; ++index) {
        if (index != 0) {
          stringBuilder.append(", ");
        }
        stringBuilder.append(denseProgramCounters[index]);
      }
      stringBuilder.append('}');
      return stringBuilder.toString();
    }
  }
}