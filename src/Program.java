import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.pool.KeyedObjectPoolFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPoolFactory;
import org.apache.commons.pool.impl.GenericObjectPool;

public class Program {
  public static int maxKeywordSize;

  private static DataSource getPoolingDataSource(String driverClass, String url, String user, String password) throws ClassNotFoundException {
    Class.forName(driverClass);
    ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(url, user, password);
    GenericObjectPool connectionPool = new GenericObjectPool();
    KeyedObjectPoolFactory stmtPool = new GenericKeyedObjectPoolFactory(null);
    new PoolableConnectionFactory(connectionFactory, connectionPool, stmtPool, null, false, true);
    return new PoolingDataSource(connectionPool);
  }

  public static void main(String[] args) throws Exception, InterruptedException, ClassNotFoundException, SQLException {
    OptionParser parser = new OptionParser();
    parser.acceptsAll(Arrays.asList("help", "h", "?"), "Display the help.").forHelp();
    OptionSpec<String> queryOption = parser.accepts("query", "Query the database for the given keyword.")
      .withRequiredArg().ofType(String.class);
    OptionSpec<Integer> batchSizeOption = parser.accepts("batchSize", "The size of the insert batch.")
      .withRequiredArg().ofType(Integer.class).defaultsTo(100);
    OptionSpec<Integer> dbThreadCountOption = parser.accepts("dbThreadCount", "The count of threads doing insert into the DB.")
      .withRequiredArg().ofType(Integer.class).defaultsTo(1);
    OptionSpec<String> fileOption = parser.acceptsAll(Arrays.asList("f", "file"),
      "A file to be read. Must be a gzipped file of json records separated with one or more whitespaces.")
      .withRequiredArg().ofType(String.class);

    OptionSet options = parser.parse(args);
    String filePath = options.valueOf(fileOption);
    String query = options.valueOf(queryOption);
    if (options.has("?") || args.length == 0 || (filePath == null && query == null)) {
      parser.printHelpOn(System.out);
      return;
    }

    final DataSource ds = getPoolingDataSource("org.postgresql.Driver", "jdbc:postgresql:exdb", "postgres", "123");

    if (query != null) {
      doQuery(ds, query);
      return;
    }

    maxKeywordSize = getMaxKeywordSize(ds);

    final AtomicInteger dbThreadCount = new AtomicInteger(options.valueOf(dbThreadCountOption));
    int batchSize = options.valueOf(batchSizeOption);

    final BlockingQueue<List<Item>> bus = new LinkedBlockingDeque<List<Item>>();
    final AtomicInteger accepted = new AtomicInteger();
    final AtomicInteger parsed = new AtomicInteger();
    final AtomicInteger inserted = new AtomicInteger();

    ThreadPoolExecutor exec = (ThreadPoolExecutor)Executors.newFixedThreadPool(dbThreadCount.get() + 1);

    // Progress
    exec.execute(new Runnable() {
      @Override
      public void run() {
        try {
          while (dbThreadCount.get() > 0) {
            System.out.print(String.format("Parsed = %d, accepted = %d, inserted = %d\r", parsed.get(), accepted.get(), inserted.get()));
            Thread.sleep(1000);
          }
        } catch (InterruptedException e) {
        }
        System.out.println(String.format("Parsed = %d, accepted = %d, inserted = %d", parsed.get(), accepted.get(), inserted.get()));
      }
    });

    // Insertion
    for (int i = dbThreadCount.get(); i >= 0; --i) {
      exec.execute(new Runnable() {
        @Override
        public void run() {
          Connection c = null;
          PreparedStatement stmt = null, stmt2 = null;
          try {
            List<Item> items;
            c = ds.getConnection();
            c.setAutoCommit(false);
            stmt = c.prepareStatement("INSERT INTO items (name, description) VALUES(?, ?)");
            stmt2 = c.prepareStatement("INSERT INTO keywords (name, keyword, count) VALUES(?, ?, ?)");
            while (!(items = bus.take()).isEmpty()) {
              for (Item item : items) {
                stmt.setString(1, item.name);
                stmt.setString(2, item.description);
                stmt.addBatch();
                stmt2.setString(1, item.name);
                for (Map.Entry<String, int[]> kc : item.keywords.entrySet()) {
                  stmt2.setString(2, kc.getKey());
                  stmt2.setInt(3, kc.getValue()[0]);
                  stmt2.addBatch();
                }
              }
              stmt.executeBatch();
              stmt2.executeBatch();
              c.commit();
              inserted.addAndGet(items.size());
            }
          } catch (SQLException e) {
            printStackTrace(e);
          } catch (Exception e) {
            e.printStackTrace();
          } finally {
            close(c);
            close(stmt);
            close(stmt2);
          }
          dbThreadCount.decrementAndGet();
        }
      });
    }

    // Parsing
    GZIPInputStream gzipIn = new GZIPInputStream(new BufferedInputStream(new FileInputStream(filePath)));
    JsonFactory jf = new JsonFactory();
    JsonParser jParser = jf.createJsonParser(gzipIn);
    List<Item> batch = new ArrayList<Item>(batchSize);
    do {
      String name = null, description = null;
      while (jParser.nextToken() != JsonToken.END_OBJECT) {
        String key = jParser.getCurrentName();
        if (key == null) {
          continue;
        }
        if ("name".equals(key)) {
          jParser.nextToken();
          name = jParser.getText();
        } else if ("description".equals(key)) {
          jParser.nextToken();
          description = jParser.getText();
        } else {
          JsonToken nextToken = jParser.nextToken();
          if (nextToken == JsonToken.START_OBJECT || nextToken == JsonToken.START_ARRAY) {
            jParser.skipChildren();
          }
        }
      }
      parsed.incrementAndGet();
      if (name != null) {
        batch.add(new Item(name, description));
        if (batch.size() == batchSize) {
          bus.put(batch);
          accepted.addAndGet(batch.size());
          batch = new ArrayList<Item>(batchSize);
        }
      }
      // No need to loop if there are no consumers
    } while (jParser.nextToken() == JsonToken.START_OBJECT && dbThreadCount.get() > 0);
    if (!batch.isEmpty() && dbThreadCount.get() > 0) {
      bus.put(batch);
      accepted.addAndGet(batch.size());
    }
    jParser.close();
    gzipIn.close();

    for (int i = dbThreadCount.get(); i >= 0; --i) {
      bus.put(new ArrayList<Item>());
    }

    exec.shutdown();
    exec.awaitTermination(1, TimeUnit.HOURS);
  }

  private static void close(PreparedStatement stmt) {
    if (stmt != null) {
      try {
        stmt.close();
      } catch (Exception e) {
      }
    }
  }

  private static void close(Connection c) {
    if (c != null) {
      try {
        c.close();
      } catch (Exception e) {
      }
    }
  }

  private static int getMaxKeywordSize(DataSource ds) throws Exception {
    Connection c = null;
    ResultSet rs = null;
    try {
      c = ds.getConnection();
      DatabaseMetaData meta = c.getMetaData();
      rs = meta.getColumns(null, null, "keywords", "keyword");
      while (rs.next()) {
        if ("keyword".equals(rs.getString("COLUMN_NAME"))) {
          return rs.getInt("COLUMN_SIZE");
        }
      }
      throw new Exception("The keyword column was not found!");
    } finally {
      close(c);
      close(rs);
    }
  }

  private static void doQuery(DataSource ds, String query) throws SQLException {
    Connection c = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try {
      c = ds.getConnection();
      stmt = c.prepareStatement("SELECT name, count from keywords where keyword = ?");
      stmt.setString(1, query);
      rs = stmt.executeQuery();
      while (rs.next()) {
        System.out.println(String.format("%s : %d\n", rs.getString(1), rs.getInt(2)));
      }
    } finally {
      close(c);
      close(stmt);
      close(rs);
    }
  }

  private static void close(ResultSet rs) {
    if (rs != null) {
      try {
        rs.close();
      } catch (Exception e) {
      }
    }
  }

  private static void printStackTrace(SQLException e) {
    if (e.getNextException() != null) {
      printStackTrace(e.getNextException());
    }
    e.printStackTrace();
  }
}
