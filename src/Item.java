import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Item {
  public final String name;
  public final String description;
  public final Map<String, int[]> keywords = new HashMap<String, int[]>();

  private static final String delim = "\\s+|\\)|\\(|\\||\\?|\\!|\\\"|\\.|;|,|\\s+[':&-]|[':&-]\\s+";
  private static final List<String> excludedKeywords = Arrays.asList(
    "a", "the", "of", "per", "to", "this", "an", "with", "for", "and", "in", "any", "not", "only", "does", "do", "but",
    "you", "can", "or", "on", "is", "from", "your", "may", "so");

  static {
    Collections.sort(excludedKeywords);
  }

  public Item(String name, String description) {
    this.name = name;
    this.description = description;
    for (String keyword : description.split(delim)) {
      if (!keyword.isEmpty() && Collections.binarySearch(excludedKeywords, keyword, CaseInsesitiveStringComparator.instance) < 0) {
        int[] found = keywords.get(keyword);
        if (found == null) {
          found = new int[1];
          keywords.put(keyword.toLowerCase(), found);
        }
        ++found[0];
      }
    }
  }
}
