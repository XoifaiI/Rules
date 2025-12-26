# rules

a hardened java validation library.

---

### compile

```
javac -cp "lib/*" -d bin src/main/java/rules/*.java
```

### test

```
java -cp "bin;lib/*" tests.SimpleTest
```

---

### usage

```java
Rule<String> name = Rules.notBlank();
Rule<Number> age = Rules.positive();

ValidationResult r = name.safeValidate("alice");
if (r.isValid()) {
    // good
}
```

---

### defaults

- max depth: 32  
- max collection: 10,000  
- cycle detection: on  

---

### requires

- java 17+  
- RE2j (in lib/)

---