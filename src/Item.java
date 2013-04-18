public class Item {
  public final String name;
  public final String description;
  public static final Item NULL = new Item(null, null);

  public Item(String name, String description) {
    this.name = name;
    this.description = description;
  }
}
