import java.util.Comparator;

public class CaseInsesitiveStringComparator implements Comparator<String> {
  public static final Comparator<String> instance = new CaseInsesitiveStringComparator();

  @Override
  public int compare(String o1, String o2) {
    return o1.compareToIgnoreCase(o2);
  }
}
