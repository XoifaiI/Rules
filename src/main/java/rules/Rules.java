package rules;

import java.util.Collection;
import java.util.Map;

public final class Rules {

  private Rules() {
  }

  public static Rule<Object> notNull() {
    return ObjectRules.notNull();
  }

  public static Rule<Object> isNull() {
    return ObjectRules.isNull();
  }

  public static <T> Rule<Object> instanceOf(Class<T> type) {
    return ObjectRules.instanceOf(type);
  }

  public static <T> Rule<T> optional(Rule<T> rule) {
    return ObjectRules.optional(rule);
  }

  @SafeVarargs
  public static <T> Rule<T> oneOf(T... values) {
    return ObjectRules.oneOf(values);
  }

  public static Rule<Number> finite() {
    return NumberRules.finite();
  }

  public static Rule<Number> integer() {
    return NumberRules.integer();
  }

  public static Rule<Number> positive() {
    return NumberRules.positive();
  }

  public static Rule<Number> negative() {
    return NumberRules.negative();
  }

  public static Rule<Number> nonNegative() {
    return NumberRules.nonNegative();
  }

  public static Rule<Number> between(double min, double max) {
    return NumberRules.between(min, max);
  }

  public static Rule<Number> min(double min) {
    return NumberRules.min(min);
  }

  public static Rule<Number> max(double max) {
    return NumberRules.max(max);
  }

  public static Rule<String> notEmpty() {
    return StringRules.notEmpty();
  }

  public static Rule<String> notBlank() {
    return StringRules.notBlank();
  }

  public static Rule<String> length(int exactLength) {
    return StringRules.length(exactLength);
  }

  public static Rule<String> lengthBetween(int min, int max) {
    return StringRules.lengthBetween(min, max);
  }

  public static Rule<String> minLength(int min) {
    return StringRules.minLength(min);
  }

  public static Rule<String> maxLength(int max) {
    return StringRules.maxLength(max);
  }

  public static Rule<String> matches(String regex) {
    return StringRules.matches(regex);
  }

  public static Rule<String> uuidV4() {
    return StringRules.uuidV4();
  }

  public static <T> Rule<Collection<T>> collectionNotEmpty() {
    return CollectionRules.notEmpty();
  }

  public static <T> Rule<Collection<T>> collectionSize(int size) {
    return CollectionRules.size(size);
  }

  public static <T> Rule<Collection<T>> allMatch(Rule<T> rule) {
    return CollectionRules.allMatch(rule);
  }

  public static <T> Rule<Collection<T>> anyMatch(Rule<T> rule) {
    return CollectionRules.anyMatch(rule);
  }

  public static <K, V> Rule<Map<K, V>> mapNotEmpty() {
    return CollectionRules.mapNotEmpty();
  }

  public static <K, V> Rule<Map<K, V>> allKeys(Rule<K> rule) {
    return CollectionRules.allKeys(rule);
  }

  public static <K, V> Rule<Map<K, V>> allValues(Rule<V> rule) {
    return CollectionRules.allValues(rule);
  }

  @SafeVarargs
  public static <T> Rule<T> all(Rule<? super T>... rules) {
    return CompositeRules.all(rules);
  }

  @SafeVarargs
  public static <T> Rule<T> any(Rule<? super T>... rules) {
    return CompositeRules.any(rules);
  }

  @SafeVarargs
  public static <T> Rule<T> none(Rule<? super T>... rules) {
    return CompositeRules.none(rules);
  }

  public static <T> Rule<T> when(Rule<T> condition, Rule<T> thenRule) {
    return CompositeRules.when(condition, thenRule);
  }

  public static <K, V> StructRule.Builder<K, V> struct() {
    return StructRule.builder();
  }
}
