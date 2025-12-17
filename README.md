# Beyond StringUtils.getLevensteinDistance

## Levenstein Distance (Edit Distance)

Apache **commons‑lang** includes an implementation of the Levenshtein Distance algorithm (aka Edit Distance). That implementation is based on an article by **Michael Gilleland**. The basic algorithm is well understood; this article focuses on improving it.

A motivating example:  
Using `StringUtils.getLevensteinDistance`, both `"newyork"` and `"newyonk"` have distance 1 from `"new york"`. But semantically, `"newyork"` is closer. We want a distance **< 1** for `"newyork"` by applying different weights to certain edits (spaces, punctuation, etc.).

To do this, we modify the commons‑lang implementation to allow **custom weighting**.

---

# Possible Improvements (from Wikipedia)

- Use less space: **O(m)** instead of **O(mn)**  
- Store insert/delete/substitute counts or positions  
- Normalize distance to **[0,1]**  
- If only interested in distances ≤ *k*, compute only a diagonal stripe of width `2k+1`  
- Assign different penalty costs per operation or per character  
- Move initialization of `d[i,0]` inside main loop  
- Initialize first row with 0 for fuzzy search  
- Parallelization  
- Lazy evaluation to get **O(m(1+d))** time

---

# Introducing Brew Edit Distance

A CPAN module, **Text::Brew**, implements a variation of edit distance based on an article by **Chris Brew**. Instead of storing only distances, it stores a **TraceBack** structure at each cell:

```
{ cost, previous_edit_move }
```

This allows reconstructing the edit path.

Example:  
Comparing `"abc"` → `"abcd"` yields:

```
[match, match, match, insert]
distance = 1
```

### Hybrid Approach

Text::Brew uses a full matrix; commons‑lang uses only two rows.  
We combine both approaches.

Pseudo‑code:

```text
# d1 and d2 are arrays of length n
for i below m
  d1(0) = (delCost, DEL, nil)
  for j below n
    if string1(i) == string2(j)
      subst = 0
    else
      subst = substCost
    endif
    d1(j+1) = best(
      (subst, MATCH, d2(j)),
      (insCost, INS, d1(j)),
      (delCost, DEL, d2(j+1))
    )
  endfor
  swap(d1, d2)
endfor
```

---

# Cost Function

Text::Brew allows different costs for insert/delete/substitute, but not per‑character or per‑position. We want:

- Lower cost for editing spaces  
- Lower cost for punctuation  
- Lower cost for end‑of‑string edits  
- Higher cost for vowel deletions, etc.

We introduce:

```java
enum Move { DELETE, INSERT, SUBSTITUTE, MATCH }

class Edit {
  public static final Edit INITIAL = new Edit(null, '0');
  Move move;
  char character;
  char subCharacter; // for SUBSTITUTE
}

abstract class CostFunction {
  public abstract float getEditCost(Edit edit, int index);
}
```

Example: ignore spaces:

```java
CostFunction ignoreSpace =
  new CostFunction() {
    @Override
    public float getEditCost(Edit edit, int index) {
      return edit.move == Move.MATCH || edit.character == ' ' ? 0 : 1.0f;
    }
  };
```

Use it in the algorithm:

```java
edit = new Edit(Move.DELETE, from_i);
d[0] = new TraceBack(p[0].cost + costFunction.getEditCost(edit, i), edit, null);
```

TraceBack:

```java
class TraceBack {
  float cost;
  Edit edit;
  TraceBack prevTraceBack;
}
```

---

# Threshold Optimization

If you only care whether distance ≤ *k*, you can stop early.

After each row:

```java
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
```

This avoids scanning the full matrix.

---

# Normalized Distance

Normalize to `[0,1]`:

```
normalized = edit_distance / avg(len(a), len(b))
```

or:

```
normalized = edit_distance / max(len(a), len(b))
```

---

# Performance Considerations

A benchmark comparing:

- `StringUtils.getLevenshteinDistance`
- `brewEditDistance`

On a Q6600 @ 2.40GHz:

```
getLevenshteinDistance = 0.98 seconds
brewEditDistance       = 9.639 seconds
```

Profiling showed most time spent constructing `TraceBack` and `Edit` objects.

### Optimization

Reuse objects:

```java
private TraceBack d[][] = new TraceBack[100][100];
```

Flatten TraceBack:

```java
static class TraceBack {
  float cost = 0;
  Move move = null;
  char character = '0';
  char subCharacter = '0';
  TraceBack prevTraceBack = null;
}
```

After optimization:

```
getLevenshteinDistance = 0.993 seconds
brewEditDistance       = 4.416 seconds
```

---

# References

- https://en.wikipedia.org/wiki/Levenshtein_distance  
- http://www.ling.ohio-state.edu/~cbrew/2002/winter/684.02/string-distance.html  
- http://search.cpan.org/src/KCIVEY/Text-Brew-0.02/lib/Text/Brew.pm  
- http://svn.apache.org/viewvc/commons/proper/lang/trunk/src/java/org/apache/commons/lang/StringUtils.java?view=markup  

---
