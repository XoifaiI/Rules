package testing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DeepDiff {

  private final List<String> differences;

  private DeepDiff(List<String> differences) {
    this.differences = differences;
  }

  public static DeepDiff compare(Object expected, Object actual) {
    List<String> foundDifferences = new ArrayList<>();
    IdentityHashMap<Object, Object> visitedObjects = new IdentityHashMap<>();
    scanForDifferences(expected, actual, visitedObjects, "", foundDifferences);
    return new DeepDiff(foundDifferences);
  }

  public boolean hasDifferences() {
    return !differences.isEmpty();
  }

  public List<String> getDifferences() {
    return differences;
  }

  public String format() {
    return String.join("\n", differences);
  }

  private static void scanForDifferences(
      Object expected,
      Object actual,
      IdentityHashMap<Object, Object> visitedObjects,
      String currentPath,
      List<String> foundDifferences) {

    if (expected == actual) {
      return;
    }

    if (expected instanceof Condition condition) {
      Condition.Result result = condition.test(actual);
      if (!result.passed()) {
        foundDifferences.add(currentPath + (result.message() != null
            ? String.format(result.message(), "")
            : "failed condition"));
      }
      return;
    }

    if (expected == null || actual == null) {
      foundDifferences.add(String.format("%s(value: %s) != (value: %s)",
          currentPath, Readable.format(expected), Readable.format(actual)));
      return;
    }

    if (!expected.getClass().equals(actual.getClass())) {
      foundDifferences.add(String.format("%s(type: %s) != (type: %s)",
          currentPath,
          expected.getClass().getSimpleName(),
          actual.getClass().getSimpleName()));
      return;
    }

    if (expected instanceof Map<?, ?> expectedMap) {
      if (visitedObjects.containsKey(expected)) {
        return;
      }
      visitedObjects.put(expected, actual);
      scanMapDifferences(expectedMap, (Map<?, ?>) actual, visitedObjects,
          currentPath, foundDifferences);
      return;
    }

    if (expected instanceof Collection<?> expectedCollection) {
      if (visitedObjects.containsKey(expected)) {
        return;
      }
      visitedObjects.put(expected, actual);
      scanCollectionDifferences(expectedCollection, (Collection<?>) actual,
          visitedObjects, currentPath, foundDifferences);
      return;
    }

    if (expected.getClass().isArray()) {
      if (visitedObjects.containsKey(expected)) {
        return;
      }
      visitedObjects.put(expected, actual);
      scanArrayDifferences(expected, actual, visitedObjects, currentPath,
          foundDifferences);
      return;
    }

    if (!Objects.equals(expected, actual)) {
      foundDifferences.add(String.format("%s(value: %s) != (value: %s)",
          currentPath, Readable.format(expected), Readable.format(actual)));
    }
  }

  private static void scanMapDifferences(
      Map<?, ?> expectedMap,
      Map<?, ?> actualMap,
      IdentityHashMap<Object, Object> visitedObjects,
      String currentPath,
      List<String> foundDifferences) {

    for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
      Object key = entry.getKey();
      String keyPath = currentPath + "[" + Readable.format(key) + "]";

      if (!actualMap.containsKey(key)) {
        foundDifferences.add(keyPath + ": missing in actual");
        continue;
      }

      scanForDifferences(entry.getValue(), actualMap.get(key), visitedObjects,
          keyPath + ": ", foundDifferences);
    }

    for (Object key : actualMap.keySet()) {
      if (!expectedMap.containsKey(key)) {
        String keyPath = currentPath + "[" + Readable.format(key) + "]";
        foundDifferences.add(keyPath + ": unexpected in actual");
      }
    }
  }

  private static void scanCollectionDifferences(
      Collection<?> expectedCollection,
      Collection<?> actualCollection,
      IdentityHashMap<Object, Object> visitedObjects,
      String currentPath,
      List<String> foundDifferences) {

    if (expectedCollection.size() != actualCollection.size()) {
      foundDifferences.add(String.format("%ssize mismatch: %d != %d",
          currentPath, expectedCollection.size(), actualCollection.size()));
      return;
    }

    Iterator<?> expectedIterator = expectedCollection.iterator();
    Iterator<?> actualIterator = actualCollection.iterator();
    int elementIndex = 0;

    while (expectedIterator.hasNext() && actualIterator.hasNext()) {
      scanForDifferences(
          expectedIterator.next(),
          actualIterator.next(),
          visitedObjects,
          currentPath + "[" + elementIndex + "]: ",
          foundDifferences);
      elementIndex++;
    }
  }

  private static void scanArrayDifferences(
      Object expectedArray,
      Object actualArray,
      IdentityHashMap<Object, Object> visitedObjects,
      String currentPath,
      List<String> foundDifferences) {

    int expectedLength = java.lang.reflect.Array.getLength(expectedArray);
    int actualLength = java.lang.reflect.Array.getLength(actualArray);

    if (expectedLength != actualLength) {
      foundDifferences.add(String.format("%slength mismatch: %d != %d",
          currentPath, expectedLength, actualLength));
      return;
    }

    for (int index = 0; index < expectedLength; index++) {
      scanForDifferences(
          java.lang.reflect.Array.get(expectedArray, index),
          java.lang.reflect.Array.get(actualArray, index),
          visitedObjects,
          currentPath + "[" + index + "]: ",
          foundDifferences);
    }
  }
}