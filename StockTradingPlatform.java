import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/*
 * StockTradingPlatform.java
 * A simple simulated stock trading platform (CLI) demonstrating OOP design.
 * Features:
 *  - Market with multiple stocks and live-ish price updates (simulated)
 *  - Users with portfolios (buy/sell operations)
 *  - Transaction history and portfolio performance tracking over time
 *  - Persistence using simple CSV files for portfolios and transactions
 *  - Optional features: limit orders, portfolio value history, scheduled market updates
 *
 * How to run:
 *  1. javac StockTradingPlatform.java
 *  2. java StockTradingPlatform
 *
 * Files created in current directory:
 *  - portfolios.csv        (saves users' holdings)
 *  - transactions.csv      (saves transaction history)
 *  - price_history_{symbol}.csv (saved if user requests history export)
 *
 * Note: This is a simulation intended for learning and demo only.
 */

// Basic POJO representing a Stock
class Stock {
    private final String symbol;
    private final String name;
    private double price;
    private final Random rnd = new Random();

    // Keep a small history in-memory
    private final Deque<PricePoint> history = new ArrayDeque<>();

    public Stock(String symbol, String name, double initialPrice) {
        this.symbol = symbol.toUpperCase();
        this.name = name;
        this.price = initialPrice;
        addHistory(initialPrice);
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public synchronized double getPrice() { return price; }

    // Simulate a small random price change
    public synchronized void tick() {
        // percent change between -2% and +2%
        double pct = (rnd.nextDouble() * 4.0) - 2.0;
        double change = price * pct / 100.0;
        price = Math.max(0.01, price + change);
        addHistory(price);
    }

    private void addHistory(double p) {
        history.addLast(new PricePoint(LocalDateTime.now(), p));
        if (history.size() > 1000) history.removeFirst();
    }

    public synchronized List<PricePoint> getHistory() {
        return new ArrayList<>(history);
    }
}

class PricePoint {
    public final LocalDateTime time;
    public final double price;
    public PricePoint(LocalDateTime t, double p) { time = t; price = p; }
}

// Transaction record
class Transaction {
    public final String userId;
    public final String symbol;
    public final int quantity; // + for buy, - for sell
    public final double price;
    public final LocalDateTime timestamp;

    public Transaction(String userId, String symbol, int qty, double price) {
        this.userId = userId;
        this.symbol = symbol.toUpperCase();
        this.quantity = qty;
        this.price = price;
        this.timestamp = LocalDateTime.now();
    }

    public String toCSV() {
        return String.join(",",
            userId, symbol, Integer.toString(quantity), Double.toString(price), timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
}

// Simple holding
class Holding {
    public String symbol;
    public int quantity;
    public double avgPrice; // average buy price

    public Holding(String s, int q, double p) { symbol = s; quantity = q; avgPrice = p; }
}

// Portfolio stores holdings and value history
class Portfolio {
    public final String userId;
    private final Map<String, Holding> holdings = new HashMap<>();
    private final List<PortfolioSnapshot> snapshots = new ArrayList<>();

    public Portfolio(String userId) { this.userId = userId; }

    public synchronized void buy(String symbol, int qty, double price) {
        Holding h = holdings.get(symbol);
        if (h == null) holdings.put(symbol, new Holding(symbol, qty, price));
        else {
            double newAvg = ((h.avgPrice * h.quantity) + (price * qty)) / (h.quantity + qty);
            h.quantity += qty;
            h.avgPrice = newAvg;
        }
    }

    public synchronized boolean sell(String symbol, int qty) {
        Holding h = holdings.get(symbol);
        if (h == null || h.quantity < qty) return false;
        h.quantity -= qty;
        if (h.quantity == 0) holdings.remove(symbol);
        return true;
    }

    public synchronized Map<String, Holding> getHoldings() {
        return new HashMap<>(holdings);
    }

    public synchronized void takeSnapshot(double totalValue) {
        snapshots.add(new PortfolioSnapshot(LocalDateTime.now(), totalValue));
        // keep last 1000
        if (snapshots.size() > 1000) snapshots.remove(0);
    }

    public synchronized List<PortfolioSnapshot> getSnapshots() { return new ArrayList<>(snapshots); }
}

class PortfolioSnapshot {
    public final LocalDateTime time;
    public final double value;
    public PortfolioSnapshot(LocalDateTime t, double v) { time = t; value = v; }
}

// User model
class User {
    private final String id;
    private double cash; // available cash
    private final Portfolio portfolio;

    public User(String id, double startingCash) {
        this.id = id;
        this.cash = startingCash;
        this.portfolio = new Portfolio(id);
    }

    public String getId() { return id; }
    public synchronized double getCash() { return cash; }

    public synchronized boolean canAfford(double amount) { return cash + 1e-9 >= amount; }

    public synchronized void debit(double amt) { cash -= amt; }
    public synchronized void credit(double amt) { cash += amt; }

    public Portfolio getPortfolio() { return portfolio; }
}

// The market manages stocks and scheduled updates
class Market {
    private final Map<String, Stock> stocks = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public Market() {}

    public void addStock(Stock s) { stocks.put(s.getSymbol(), s); }
    public Stock getStock(String symbol) { return stocks.get(symbol.toUpperCase()); }
    public Collection<Stock> listStocks() { return stocks.values(); }

    // start automatic periodic ticks (price updates)
    public void startAutoUpdate(long intervalMillis) {
        scheduler.scheduleAtFixedRate(() -> {
            for (Stock s : stocks.values()) s.tick();
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public void stop() { scheduler.shutdownNow(); }
}

// Persistence helper (CSV-based simple persistence)
class Persistence {
    private static final String PORTFOLIO_FILE = "portfolios.csv"; // userId,symbol,quantity,avgPrice
    private static final String TRANSACTION_FILE = "transactions.csv"; // userId,symbol,qty,price,timestamp

    public static synchronized void saveTransaction(Transaction t) {
        try (FileWriter fw = new FileWriter(TRANSACTION_FILE, true)) {
            fw.write(t.toCSV() + System.lineSeparator());
        } catch (IOException e) { System.err.println("Failed to save transaction: " + e.getMessage()); }
    }

    public static synchronized void savePortfolios(Map<String, User> users) {
        try (FileWriter fw = new FileWriter(PORTFOLIO_FILE)) {
            for (User u : users.values()) {
                for (Holding h : u.getPortfolio().getHoldings().values()) {
                    fw.write(String.join(",",
                        u.getId(), h.symbol, Integer.toString(h.quantity), Double.toString(h.avgPrice)) + System.lineSeparator());
                }
            }
        } catch (IOException e) { System.err.println("Failed to save portfolios: " + e.getMessage()); }
    }

    public static synchronized void loadPortfolios(Map<String, User> users) {
        Path p = Paths.get(PORTFOLIO_FILE);
        if (!Files.exists(p)) return;
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                String uid = parts[0];
                String sym = parts[1];
                int qty = Integer.parseInt(parts[2]);
                double avg = Double.parseDouble(parts[3]);
                users.computeIfAbsent(uid, k -> new User(k, 10000.0)).getPortfolio().buy(sym, qty, avg);
            }
        } catch (IOException e) { System.err.println("Failed to load portfolios: " + e.getMessage()); }
    }
}

// Main trading platform
public class StockTradingPlatform {
    private final Market market = new Market();
    private final Map<String, User> users = new HashMap<>();
    private final Scanner sc = new Scanner(System.in);
    private final ScheduledExecutorService snapshotScheduler = Executors.newSingleThreadScheduledExecutor();

    public StockTradingPlatform() {
        seedMarket();
        // load saved portfolios if present (creates users with default cash if missing)
        Persistence.loadPortfolios(users);
        // schedule periodic portfolio snapshots (every 30s)
        snapshotScheduler.scheduleAtFixedRate(this::takeAllSnapshots, 30, 30, TimeUnit.SECONDS);
    }

    private void seedMarket() {
        market.addStock(new Stock("AAPL", "Apple Inc.", 170.0));
        market.addStock(new Stock("GOOGL", "Alphabet Inc.", 135.0));
        market.addStock(new Stock("MSFT", "Microsoft Corp.", 310.0));
        market.addStock(new Stock("TSLA", "Tesla Inc.", 250.0));
        market.addStock(new Stock("INFY", "Infosys Ltd.", 22.0));
        market.startAutoUpdate(3000); // every 3 seconds
    }

    private void takeAllSnapshots() {
        for (User u : users.values()) {
            double value = computePortfolioValue(u);
            u.getPortfolio().takeSnapshot(value + u.getCash());
        }
    }

    private double computePortfolioValue(User u) {
        double total = 0.0;
        for (Holding h : u.getPortfolio().getHoldings().values()) {
            Stock s = market.getStock(h.symbol);
            if (s != null) total += h.quantity * s.getPrice();
        }
        return total;
    }

    public void run() {
        System.out.println("Welcome to the simulated Stock Trading Platform!\n");
        boolean exit = false;
        while (!exit) {
            try {
                System.out.println("1) Register / Login  2) Market  3) My Account  4) Save State  5) Exit");
                System.out.print("Choose: ");
                String choice = sc.nextLine().trim();
                switch (choice) {
                    case "1": userMenu(); break;
                    case "2": marketMenu(); break;
                    case "3": accountMenu(); break;
                    case "4": saveState(); break;
                    case "5": exit = true; break;
                    default: System.out.println("Unknown option");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
        shutdown();
    }

    private void userMenu() {
        System.out.print("Enter your user id (email or name): ");
        String uid = sc.nextLine().trim();
        users.computeIfAbsent(uid, k -> new User(k, 10000.0));
        System.out.println("Logged in as: " + uid);
    }

    private void marketMenu() {
        System.out.println("Market - Live Prices:");
        for (Stock s : market.listStocks()) {
            System.out.printf("%s - %s : %.2f\n", s.getSymbol(), s.getName(), s.getPrice());
        }
        System.out.println("Commands: [buy], [sell], [history], [detail], [back]");
        while (true) {
            System.out.print("market> ");
            String line = sc.nextLine().trim();
            if (line.equalsIgnoreCase("back")) break;
            if (line.startsWith("buy")) handleBuy(line);
            else if (line.startsWith("sell")) handleSell(line);
            else if (line.startsWith("history")) handleHistory(line);
            else if (line.startsWith("detail")) handleDetail(line);
            else System.out.println("Unknown command");
        }
    }

    private void handleBuy(String line) {
        // buy <userId> <symbol> <qty>
        String[] parts = line.split("\\s+");
        if (parts.length < 4) { System.out.println("Usage: buy <userId> <symbol> <qty>"); return; }
        String uid = parts[1];
        String sym = parts[2];
        int qty = Integer.parseInt(parts[3]);
        User u = users.get(uid);
        if (u == null) { System.out.println("User not found. Please register/login first."); return; }
        Stock s = market.getStock(sym);
        if (s == null) { System.out.println("Stock not found: " + sym); return; }
        double price = s.getPrice();
        double cost = price * qty;
        if (!u.canAfford(cost)) { System.out.println("Insufficient cash. Needed: " + cost); return; }
        // execute
        u.debit(cost);
        u.getPortfolio().buy(s.getSymbol(), qty, price);
        Transaction t = new Transaction(u.getId(), s.getSymbol(), qty, price);
        Persistence.saveTransaction(t);
        System.out.printf("Bought %d of %s at %.2f each (total %.2f)\n", qty, s.getSymbol(), price, cost);
    }

    private void handleSell(String line) {
        // sell <userId> <symbol> <qty>
        String[] parts = line.split("\\s+");
        if (parts.length < 4) { System.out.println("Usage: sell <userId> <symbol> <qty>"); return; }
        String uid = parts[1];
        String sym = parts[2];
        int qty = Integer.parseInt(parts[3]);
        User u = users.get(uid);
        if (u == null) { System.out.println("User not found."); return; }
        Stock s = market.getStock(sym);
        if (s == null) { System.out.println("Stock not found: " + sym); return; }
        boolean ok = u.getPortfolio().sell(s.getSymbol(), qty);
        if (!ok) { System.out.println("Not enough holdings to sell."); return; }
        double proceeds = s.getPrice() * qty;
        u.credit(proceeds);
        Transaction t = new Transaction(u.getId(), s.getSymbol(), -qty, s.getPrice());
        Persistence.saveTransaction(t);
        System.out.printf("Sold %d of %s at %.2f each (total %.2f)\n", qty, s.getSymbol(), s.getPrice(), proceeds);
    }

    private void handleHistory(String line) {
        // history <symbol>
        String[] parts = line.split("\\s+");
        if (parts.length < 2) { System.out.println("Usage: history <symbol>"); return; }
        String sym = parts[1];
        Stock s = market.getStock(sym);
        if (s == null) { System.out.println("Stock not found: " + sym); return; }
        List<PricePoint> hist = s.getHistory();
        System.out.println("Timestamp,Price");
        for (PricePoint p : hist) {
            System.out.printf("%s,%.4f\n", p.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), p.price);
        }
        // optional: save to CSV
        System.out.print("Save history to CSV file? (y/n): ");
        String ans = sc.nextLine().trim();
        if (ans.equalsIgnoreCase("y")) {
            String fname = "price_history_" + s.getSymbol() + ".csv";
            try (FileWriter fw = new FileWriter(fname)) {
                fw.write("timestamp,price\n");
                for (PricePoint p : hist) fw.write(p.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "," + p.price + System.lineSeparator());
                System.out.println("Saved to " + fname);
            } catch (IOException e) { System.err.println("Failed to save: " + e.getMessage()); }
        }
    }

    private void handleDetail(String line) {
        // detail <symbol>
        String[] parts = line.split("\\s+");
        if (parts.length < 2) { System.out.println("Usage: detail <symbol>"); return; }
        String sym = parts[1];
        Stock s = market.getStock(sym);
        if (s == null) { System.out.println("Stock not found: " + sym); return; }
        System.out.printf("%s - %s\nPrice: %.2f\n", s.getSymbol(), s.getName(), s.getPrice());
        System.out.println("Recent history (last 10):");
        List<PricePoint> hist = s.getHistory();
        int start = Math.max(0, hist.size() - 10);
        for (int i = start; i < hist.size(); ++i) {
            PricePoint p = hist.get(i);
            System.out.printf("%s -> %.4f\n", p.time.format(DateTimeFormatter.ISO_LOCAL_TIME), p.price);
        }
    }

    private void accountMenu() {
        System.out.print("Enter your user id: ");
        String uid = sc.nextLine().trim();
        User u = users.get(uid);
        if (u == null) { System.out.println("User not found."); return; }
        System.out.println("Commands: [balance] [holdings] [history] [value] [export] [back]");
        while (true) {
            System.out.print("account> ");
            String line = sc.nextLine().trim();
            if (line.equalsIgnoreCase("back")) break;
            if (line.equalsIgnoreCase("balance")) System.out.printf("Cash: %.2f\n", u.getCash());
            else if (line.equalsIgnoreCase("holdings")) showHoldings(u);
            else if (line.equalsIgnoreCase("history")) showTransactionsForUser(u);
            else if (line.equalsIgnoreCase("value")) System.out.printf("Total Value (cash + holdings): %.2f\n", u.getCash() + computePortfolioValue(u));
            else if (line.equalsIgnoreCase("export")) exportPortfolioSnapshots(u);
            else System.out.println("Unknown command");
        }
    }

    private void showHoldings(User u) {
        Map<String, Holding> h = u.getPortfolio().getHoldings();
        if (h.isEmpty()) { System.out.println("No holdings."); return; }
        System.out.println("Symbol | Qty | AvgPrice | MarketPrice | MarketValue");
        for (Holding hh : h.values()) {
            Stock s = market.getStock(hh.symbol);
            double mp = s != null ? s.getPrice() : 0.0;
            System.out.printf("%6s | %3d | %8.2f | %10.2f | %10.2f\n",
                hh.symbol, hh.quantity, hh.avgPrice, mp, hh.quantity * mp);
        }
    }

    private void showTransactionsForUser(User u) {
        Path p = Paths.get("transactions.csv");
        if (!Files.exists(p)) { System.out.println("No transactions recorded."); return; }
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            System.out.println("time,user,symbol,qty,price");
            while ((line = br.readLine()) != null) {
                if (line.startsWith(u.getId() + ",") ) System.out.println(line);
            }
        } catch (IOException e) { System.err.println("Failed to read transactions: " + e.getMessage()); }
    }

    private void exportPortfolioSnapshots(User u) {
        List<PortfolioSnapshot> snaps = u.getPortfolio().getSnapshots();
        if (snaps.isEmpty()) { System.out.println("No snapshots yet. Wait for scheduled snapshots."); return; }
        String fname = "portfolio_snapshots_" + u.getId().replaceAll("[^a-zA-Z0-9]", "_") + ".csv";
        try (FileWriter fw = new FileWriter(fname)) {
            fw.write("time,value\n");
            for (PortfolioSnapshot s : snaps) fw.write(s.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "," + s.value + System.lineSeparator());
            System.out.println("Saved snapshots to " + fname);
        } catch (IOException e) { System.err.println("Failed to save snapshots: " + e.getMessage()); }
    }

    private void saveState() {
        Persistence.savePortfolios(users);
        System.out.println("Saved portfolios to file.");
    }

    private void shutdown() {
        market.stop();
        snapshotScheduler.shutdownNow();
        saveState();
        System.out.println("Goodbye!");
    }

    public static void main(String[] args) {
        StockTradingPlatform app = new StockTradingPlatform();
        app.run();
    }
}

