package io.github.xoifaii.rules;

/// What a rule answered. A failure carries the path to the value and the reason, so a rule over a
/// struct or a collection can say which field or element refused. The path is empty at the top level.
public sealed interface Verdict {

    static Verdict pass() {
        return Passed.INSTANCE;
    }

    static Verdict fail(String reason) {
        return new Failed("", reason);
    }

    boolean passed();

    Verdict at(String segment);

    record Passed() implements Verdict {

        static final Passed INSTANCE = new Passed();

        @Override
        public boolean passed() {
            return true;
        }

        @Override
        public Verdict at(String segment) {
            return this;
        }
    }

    record Failed(String path, String reason) implements Verdict {

        public Failed {
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }

        @Override
        public boolean passed() {
            return false;
        }

        @Override
        public Verdict at(String segment) {
            if (path.isEmpty()) {
                return new Failed(segment, reason);
            }
            if (path.startsWith("[")) {
                return new Failed(segment + path, reason);
            }

            return new Failed(segment + "." + path, reason);
        }

        public String message() {
            if (path.isEmpty()) {
                return reason;
            }

            return path + ": " + reason;
        }
    }
}
