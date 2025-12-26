package rules;

import static testing.Testing.*;

public class SimpleTest {

    public static void main(String[] args) {
        entry(() -> {
            describe("Testing Framework", () -> {
                test("expect true toBe true", () -> {
                    expect(true).toBe(true);
                });

                test("expect false toBe false", () -> {
                    expect(false).toBe(false);
                });

                test("expect string equality", () -> {
                    expect("hello").toBe("hello");
                });

                test("expect number comparison", () -> {
                    expect(5).toBeGreaterThan(3);
                    expect(3).toBeLessThan(5);
                });
            });

            describe("StringRules", () -> {
                test("notEmpty rejects empty string", () -> {
                    Rule<String> rule = StringRules.notEmpty();
                    expect(rule.validate("").isInvalid()).toBe(true);
                });

                test("notEmpty accepts non-empty string", () -> {
                    Rule<String> rule = StringRules.notEmpty();
                    expect(rule.validate("hello").isValid()).toBe(true);
                });

                test("length validates exact length", () -> {
                    Rule<String> rule = StringRules.length(5);
                    expect(rule.validate("hello").isValid()).toBe(true);
                    expect(rule.validate("hi").isInvalid()).toBe(true);
                });

                test("matches uses regex", () -> {
                    Rule<String> rule = StringRules.matches("^[a-z]+$");
                    expect(rule.validate("hello").isValid()).toBe(true);
                    expect(rule.validate("Hello").isInvalid()).toBe(true);
                });
            });

            describe("NumberRules", () -> {
                test("positive validates positive numbers", () -> {
                    Rule<Number> rule = NumberRules.positive();
                    expect(rule.validate(5).isValid()).toBe(true);
                    expect(rule.validate(-5).isInvalid()).toBe(true);
                    expect(rule.validate(0).isInvalid()).toBe(true);
                });

                test("nonNegative validates zero and positive", () -> {
                    Rule<Number> rule = NumberRules.nonNegative();
                    expect(rule.validate(0).isValid()).toBe(true);
                    expect(rule.validate(5).isValid()).toBe(true);
                    expect(rule.validate(-1).isInvalid()).toBe(true);
                });
            });

            describe("CompositeRules", () -> {
                test("all() combines rules", () -> {
                    Rule<String> rule = CompositeRules.all(
                            StringRules.notEmpty(),
                            StringRules.minLength(3),
                            StringRules.maxLength(10));
                    expect(rule.validate("hello").isValid()).toBe(true);
                    expect(rule.validate("").isInvalid()).toBe(true);
                    expect(rule.validate("hi").isInvalid()).toBe(true);
                });

                test("any() succeeds if one passes", () -> {
                    Rule<String> rule = CompositeRules.any(
                            StringRules.startsWith("hello"),
                            StringRules.endsWith("world"));
                    expect(rule.validate("hello there").isValid()).toBe(true);
                    expect(rule.validate("my world").isValid()).toBe(true);
                    expect(rule.validate("nothing matches").isInvalid()).toBe(true);
                });
            });
        });
    }
}
