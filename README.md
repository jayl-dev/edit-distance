# Beyond StringUtils.getLevenshteinDistance  

## Introduction
The Apache *commons‑lang* library includes a standard implementation of the Levenshtein (edit) distance algorithm. While the algorithm is well‑known and widely used, its default behavior treats all edits equally. In real‑world fuzzy matching, some edits should cost more or less depending on context.

For example, `"newyork"` and `"newyonk"` both have edit distance 1 from `"new york"`, but intuitively `"newyork"` is closer. This motivates a version of edit distance that supports **custom weighting**.

This article explores enhancements to the commons‑lang implementation, including:

- variable edit costs  
- trace‑back reconstruction  
- threshold‑based early exit  
- normalization  
- performance improvements  

---

## Background: Edit Distance
Levenshtein distance measures the minimum number of insertions, deletions, and substitutions required to transform one string into another.

Common improvements include:

- reducing memory usage  
- storing edit operations  
- normalizing results  
- diagonal‑stripe optimization for thresholded search  
- custom per‑operation or per‑character costs  
- lazy evaluation  
- parallelization  

---

## Brew Edit Distance
The CPAN module **Text::Brew** extends edit distance by storing a *TraceBack* object at each matrix cell:

```
{ cost, previous_edit_move }
```

This allows reconstruction of the exact edit sequence.

The commons‑lang implementation uses only two rows of the matrix for memory efficiency. A hybrid approach combines the two‑row optimization with Brew‑style trace‑back.

---

## Custom Cost Functions
To support weighted edits, the algorithm introduces:

- an `Edit` structure describing the operation  
- a `CostFunction` interface that returns a cost for each edit  

Example: ignoring spaces entirely:

```java
CostFunction ignoreSpace = new CostFunction() {
  @Override
  public float getEditCost(Edit edit, int index) {
    return edit.move == Move.MATCH || edit.character == ' ' ? 0 : 1.0f;
  }
};
```

This enables domain‑specific tuning such as:

- cheaper edits for whitespace  
- cheaper edits for punctuation  
- higher penalties for vowel removal  
- lower penalties for end‑of‑string edits  

---

## Threshold Optimization

When computing weighted edit distance, you may want to stop early if the cost already exceeds a maximum acceptable threshold. This avoids unnecessary computation when the strings are clearly too different.

The algorithm checks, after each row, whether **any** cell in the current row is still below the threshold. If not, it terminates immediately.

### Code Example (with threshold logic)

```java
private float threshold = Float.MAX_VALUE; // change this if you want a threshold

for (i = 1; i <= m; i++) {
    from_i = fromString.charAt(i - 1);

    // DELETE from_i
    edit = new Edit(Move.DELETE, from_i);
    d[0] = new TraceBack(
        p[0].cost + costFunction.getEditCost(edit, i),
        edit,
        null
    );

    for (j = 1; j <= n; j++) {
        to_j = toString.charAt(j - 1);

        // SUBSTITUTE or MATCH
        edit = new Edit(to_j == from_i ? Move.MATCH : Move.SUBSTITUTE, from_i, to_j);
        TraceBack sub = new TraceBack(
            costFunction.getEditCost(edit, i),
            edit,
            p[j - 1]
        );

        // DELETE
        edit = new Edit(Move.DELETE, from_i);
        TraceBack del = new TraceBack(
            costFunction.getEditCost(edit, i),
            edit,
            p[j]
        );

        // INSERT
        edit = new Edit(Move.INSERT, to_j);
        TraceBack insert = new TraceBack(
            costFunction.getEditCost(edit, j),
            edit,
            d[j - 1]
        );

        d[j] = best(sub, insert, del);
    }

    // Check if any path is still within threshold
    boolean stillInRange = false;
    for (int x = 0; x <= n; x++) {
        if (d[x].cost < threshold) {
            stillInRange = true;
            break;
        }
    }

    if (!stillInRange) {
        throw new RuntimeException("Threshold reached");
    }

    // Swap rows
    _d = p;
    p = d;
    d = _d;
}
```


---

## Normalization
Normalized distance maps results to the range `[0,1]`:

```
distance / max(len(a), len(b))
```

or

```
distance / average length
```

This makes scores comparable across different string lengths.

---

## Performance Notes
A benchmark comparing:

- commons‑lang Levenshtein  
- Brew‑style weighted edit distance  

showed the weighted version was slower due to object creation. Reusing `TraceBack` objects and flattening the structure significantly improved performance.

---

# Mixing Look‑Alike and Sound‑Alike in String Similarity Metrics  

## Introduction
Edit distance captures **visual similarity**, but humans also rely heavily on **phonetic similarity**. Two names may look different but sound nearly identical, or vice‑versa.

This article explores combining:

- **edit distance** (visual similarity)  
- **Metaphone** (phonetic similarity)  

to create a more human‑like similarity metric.

---

## Visual Similarity
Edit distance works well for spelling variations:

- De Marcro  
- Di Marco  
- D’Marco  
- De Merco  
- Dy Marrcro  
- DiMecrro  

These are visually close to “DiMarco”.

But edit distance alone fails when two words sound alike but differ in spelling.

---

## Phonetic Similarity
Phonetic algorithms like **Metaphone** encode words based on pronunciation. This collapses many spelling variations into the same phonetic form.

Examples of sound‑alike pairs:

- Ellan ↔ Allan  
- Iga ↔ Eega  
- Zandy ↔ Sandy  
- Phonda ↔ Fonda  
- Phat Joe Burger ↔ Fat Joe Burger  
- Kandy Kane ↔ Candy Cane  

Metaphone uses a series of rewrite rules (e.g., “ph” → “F”, “sch” → “SK”, “dg” → “J” before i/e/y).

---

## Metaphone Rule Set (Look‑Alike + Sound‑Alike Processing)

To incorporate phonetic similarity, you can apply a sequence of rewrite rules that transform a string into a simplified phonetic form. These rules collapse visually different but phonetically similar patterns (e.g., *ph → F*, *sch → SK*, *dg → J*).

```ruby
RULES = [
  # Regexp, replacement
  [ /([bcdfhjklmnpqrstvwxyz])\1+/, '\1' ],  # Remove doubled consonants except g. [PHP] remove c from regexp.
  [ /^ae/, 'E' ],
  [ /^[gkp]n/, 'N' ],
  [ /^wr/, 'R' ],
  [ /^x/, 'S' ],
  [ /^wh/, 'W' ],
  [ /mb$/, 'M' ],  # [PHP] remove $ from regexp.
  [ /(?!^)sch/, 'SK' ],
  [ /th/, '0' ],
  [ /t?ch|sh/, 'X' ],
  [ /c(?=ia)/, 'X' ],
  [ /[st](?=i[ao])/, 'X' ],
  [ /s?c(?=[iey])/, 'S' ],
  [ /[cq]/, 'K' ],
  [ /dg(?=[iey])/, 'J' ],
  [ /d/, 'T' ],
  [ /g(?=h[^aeiou])/, '' ],
  [ /gn(ed)?/, 'N' ],
  [ /([^g]|^)g(?=[iey])/, '\1J' ],
  [ /g+/, 'K' ],
  [ /ph/, 'F' ],
  [ /([aeiou])h(?=\b|[^aeiou])/, '\1' ],
  [ /[wy](?![aeiou])/, '' ],
  [ /z/, 'S' ],
  [ /v/, 'F' ],
  [ /(?!^)[aeiou]+/, '' ],
]
```

## Combining Both Dimensions
A practical combined similarity check:

1. Encode the first few characters of each string using Metaphone.  
2. If the phonetic prefixes differ, reject immediately.  
3. Compute normalized edit distance.  
4. Accept only if the distance is below a threshold.

This hybrid approach captures:

- visual similarity  
- phonetic similarity  
- the importance of the first character(s)  
- exceptions where different letters sound the same  

---

## Why This Works
Humans rely heavily on:

- the first letter  
- the first sound  
- overall pronunciation  
- general shape of the word  

By mixing phonetic and visual signals, the algorithm avoids:

- false negatives (Phonda vs Fonda)  
- false positives (DiMarco vs TiMarco)  

It produces results that align more closely with human intuition.

---
# References

- https://en.wikipedia.org/wiki/Levenshtein_distance  
- http://www.ling.ohio-state.edu/~cbrew/2002/winter/684.02/string-distance.html  
- http://search.cpan.org/src/KCIVEY/Text-Brew-0.02/lib/Text/Brew.pm  
- http://svn.apache.org/viewvc/commons/proper/lang/trunk/src/java/org/apache/commons/lang/StringUtils.java?view=markup  

---