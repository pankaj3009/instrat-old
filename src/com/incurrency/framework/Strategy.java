/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

/**
 *
 * @author pankaj
 */
public class Strategy implements NotificationListener {

    //--common parameters required for all strategies
    MainAlgorithm m;
    public HashMap<Integer, Integer> internalOpenOrders = new HashMap(); //holds mapping of symbol id to latest initialization internal order
    private HashMap<Integer, BeanPosition> position = new HashMap<>();
    private double tickSize;
    private double pointValue = 1;
    //private int internalOrderID = 1;
    private int numberOfContracts = 0;
    private double exposure;
    private Date endDate;
    private Date startDate;
    private transient AtomicBoolean longOnly = new AtomicBoolean(Boolean.TRUE);
    private transient AtomicBoolean shortOnly = new AtomicBoolean(Boolean.TRUE);
    private Boolean aggression = true;
    private double clawProfitTarget = 0;
    private double dayProfitTarget = 0;
    private double dayStopLoss = 0;
    private double maxSlippageEntry = 0;
    private double maxSlippageExit = 0;
    private int maxOrderDuration = 3;
    private int dynamicOrderDuration = 1;
    private int maxOpenPositions = 1;
    private String futBrokerageFile;
    private ArrayList<BrokerageRate> brokerageRate = new ArrayList<>();
    private String tradeFile;
    private String orderFile;
    public String timeZone;
    private double startingCapital;
    private ProfitLossManager plmanager;
    private List<Integer> strategySymbols = new ArrayList();
    private static final Logger logger = Logger.getLogger(Strategy.class.getName());
    ExecutionManager oms;
    private String strategy;
    private ArrayList<String> accounts;
    private Properties prop;
    private String parameterFile;
    private String tradedType;
    public static String newline = System.getProperty("line.separator");
    public String allAccounts;
    private static HashMap<String, String> combosAdded = new HashMap<>();
    private Integer stratCount;
    //Locks
    private final Object lockOMS = new Object();
    private final Object lockPLManager = new Object();
    private final Object lockPL = new Object();
    private boolean validation = true;
    public final String delimiter = "_";
    private boolean strategyLog;
    private boolean stopOrders = false;
    public Database<String, String> db;
    private Boolean useRedis;

    public Strategy(MainAlgorithm m, String headerStrategy, String type, Properties prop, String parameterFileName, ArrayList<String> accounts, Integer stratCount) {
        try {
            if(accounts.isEmpty()){
                //pop a message that no accounts were found. Then exit
                JOptionPane.showMessageDialog(null, "No valid broker accounts found for strategy: "+headerStrategy+". Check the connection file and parameter file to ensure the accounts are consistent");
                System.exit(0);
            }
            this.m = m;
            this.accounts = accounts;
            this.prop = prop;
            this.parameterFile = parameterFileName;
            this.tradedType = type;
            this.stratCount = stratCount;
            for (String account : getAccounts()) {
                allAccounts = allAccounts == null ? account : allAccounts + ":" + account;
            }
            for (BeanSymbol s : Parameters.symbol) {
                if (Pattern.compile(Pattern.quote(headerStrategy), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                    strategySymbols.add(s.getSerialno() - 1);
                    position.put(s.getSerialno() - 1, new BeanPosition(s.getSerialno() - 1, getStrategy()));
                }
            }
            String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-|_");
            if (stratCount == null) {
                this.strategy = tempStrategyArray[tempStrategyArray.length - 1];
            } else {
                this.strategy = tempStrategyArray[tempStrategyArray.length - 1] + stratCount;
            }
            useRedis = prop.getProperty("redisurl") != null ? true : false;
            loadParameters(headerStrategy, type, prop);
            //for (BeanConnection c : Parameters.connection) {
            boolean stratVal = true;
            for (String account : accounts) {
                String ownerEmail = "support@incurrency.com";
                if (MainAlgorithm.isUseForTrading()) {
                    for (BeanConnection c : Parameters.connection) {
                        if (c.getAccountName().equals(account)) {
                            ownerEmail = c.getOwnerEmail();
                        }
                    }
                }                
                if (useRedis) {
                    String redisURL = prop.getProperty("redisurl").toString().trim();
                    db = new RedisConnect(redisURL.split(":")[0], Utilities.getInt(redisURL.split(":")[1], 6379),Utilities.getInt(redisURL.split(":")[2], 1));
                } else {
                    String filename = "logs" + File.separator + getOrderFile();
                    db=new <String,String>DataStore();
                    if (new File(filename).exists()) {
                        InputStream initialStream = new FileInputStream(new File(filename));
                        JsonReader jr = new JsonReader(initialStream);
                        db = (Database) jr.readObject();
                        jr.close();
                    }
                }
                if(Algorithm.db==null){//using extended hashmap for executions. Initialize hashmap
                    String filename = "logs" + File.separator + getTradeFile();
                    Algorithm.db=new <String,String>DataStore();
                    if (new File(filename).exists()) {
                        InputStream initialStream = new FileInputStream(new File(filename));
                        JsonReader jr = new JsonReader(initialStream);
                        db = (Database) jr.readObject();
                        jr.close();
                    }        
                }
                stratVal = Validator.reconcile("", db, Algorithm.db, account, ownerEmail,this.getStrategy());
                if (!stratVal) {
                    logger.log(Level.INFO, "100,IntegrityCheckFailed,{0}", new Object[]{getStrategy() + delimiter + account});
                }
                validation = validation && stratVal;
            }
            //}
            if (validation) {
                //Initialize open notional orders and positions
                for (String key : db.getKeys("opentrades")) {
                    String parentsymbolname = Trade.getParentSymbol(db, key);
                    int id = Utilities.getIDFromDisplayName(Parameters.symbol, parentsymbolname);
                    int tempPosition = 0;
                    double tempPositionPrice = 0D;
                    if (id >= 0) {
                        if (Trade.getAccountName(db, key).equals("Order") && key.contains("_"+strategy)) {
                            BeanPosition p = position.get(id) == null ? new BeanPosition(id, getStrategy()) : position.get(id);
                            tempPosition = p.getPosition();
                            tempPositionPrice = p.getPrice();
                            int entrySize = Trade.getEntrySize(db, key);
                            double entryPrice = Trade.getEntryPrice(db, key);
                            switch (Trade.getEntrySide(db, key)) {
                                case BUY:
                                    tempPositionPrice = entrySize + tempPosition != 0 ? (tempPosition * tempPositionPrice + entrySize * entryPrice) / (entrySize + tempPosition) : 0D;
                                    tempPosition = tempPosition + entrySize;
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    p.setStrategy(strategy);
                                    position.put(id, p);
                                    break;
                                case SHORT:
                                    tempPositionPrice = entrySize + tempPosition != 0 ? (tempPosition * tempPositionPrice - entrySize * entryPrice) / (-entrySize + tempPosition) : 0D;
                                    tempPosition = tempPosition - entrySize;
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    p.setStrategy(strategy);    
                                    position.put(id, p);
                                    break;
                                default:
                                    break;
                            }
                            int exitSize = Trade.getExitSize(db, key);
                            double exitPrice = Trade.getExitPrice(db, key);
                            switch (Trade.getExitSide(db, key)) {
                                case COVER:
                                    tempPositionPrice = exitSize + tempPosition != 0 ? (tempPosition * tempPositionPrice + exitSize * exitPrice) / (exitSize + tempPosition) : 0D;
                                    tempPosition = tempPosition + exitSize;
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    p.setStrategy(strategy);
                                    position.put(id, p);
                                    break;
                                case SELL:
                                    tempPositionPrice = -exitSize + tempPosition != 0 ? (tempPosition * tempPositionPrice - exitSize * exitPrice) / (-exitSize + tempPosition) : 0D;
                                    tempPosition = tempPosition - exitSize;
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    p.setStrategy(strategy);
                                    position.put(id, p);
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                    if (id >= 0) {//update internal orders if id exists
                        this.internalOpenOrders.put(id, position.get(id).getPosition());
                    }
                }
                int maxorderid = 0;
                for (String key : db.getKeys("closedtrades")) {
                    String intkey = key.split("_")[1].split(":")[1];
                    maxorderid = Math.max(Utilities.getInt(intkey, 0), maxorderid);
                    maxorderid = Math.max(maxorderid, Trade.getExitOrderIDInternal(db, key));
                }
                for (String key : db.getKeys("opentrades")) {
                    String intkey = key.split("_")[1].split(":")[1];
                    maxorderid = Math.max(Utilities.getInt(intkey, 0), maxorderid);
                    maxorderid = Math.max(maxorderid, Trade.getExitOrderIDInternal(db, key));
                }
                Algorithm.orderidint = new AtomicInteger(Math.max(Algorithm.orderidint.get(), maxorderid));
                logger.log(Level.INFO, "100, OpeningInternalOrderID,{0}", new Object[]{getStrategy() + delimiter + Algorithm.orderidint.get()});

                //print positions on initialization
                for (int id : getStrategySymbols()) {
                    if (position.get(id).getPosition() != 0) {
                        logger.log(Level.INFO, "401,InitialOrderPosition,{0}", new Object[]{"Order" + delimiter + this.getStrategy() + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + position.get(id).getPosition() + delimiter + position.get(id).getPrice()});
                    }
                }


                if (MainAlgorithm.isUseForTrading()) {
                    Thread t = new Thread(oms = new ExecutionManager(this, getAggression(), this.getTickSize(), getEndDate(), this.strategy, getPointValue(), getMaxOpenPositions(), getTimeZone(), accounts, getTradeFile()));
                    t.setName(strategy + ":" + "OMS");
                    t.start();
                    plmanager = new ProfitLossManager(this, this.getStrategySymbols(), getPointValue(), getClawProfitTarget(), getDayProfitTarget(), getDayStopLoss(), accounts);
                }
                if (MainAlgorithm.isUseForTrading()) {
                    Timer closeProcessing = new Timer("Timer: " + this.strategy + " CloseProcessing");
                    closeProcessing.schedule(runPrintOrders, com.incurrency.framework.DateUtil.addSeconds(endDate, (this.maxOrderDuration + 1) * 60));
                }
            }

        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    public void displayStrategyValues() {
    }

    private void loadParameters(String strategy, String type, Properties p) {
        setTimeZone(p.getProperty("TradeTimeZone") == null ? "Asia/Kolkata" : p.getProperty("TradeTimeZone"));
        // String currDateStr = DateUtil.getFormattedDate("yyyyMMddHHmmss", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
        //Date currDate=DateUtil.parseDate("yyyyMMddHHmmss", currDateStr, TimeZone.getDefault().toString());
        stopOrders = Boolean.valueOf(p.getProperty("StopOrders", "false"));
        Date currDate = TradingUtil.getAlgoDate();
        DateFormat df = new SimpleDateFormat("yyyyMMdd");
        df.setTimeZone(TimeZone.getTimeZone(timeZone));
        String currDateStr = df.format(currDate);
        String startDateStr = currDateStr + " " + p.getProperty("StartTime");
        String endDateStr = currDateStr + " " + p.getProperty("EndTime");
        setStartDate(DateUtil.parseDate("yyyyMMdd HH:mm:ss", startDateStr, timeZone));
        setEndDate(DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr, timeZone));
        if (TradingUtil.getAlgoDate().compareTo(getEndDate()) > 0) {
            //increase enddate by one calendar day
            setEndDate(DateUtil.addDays(getEndDate(), 1));
        }
        if (TradingUtil.getAlgoDate().compareTo(getStartDate()) > 0) {
            //increase enddate by one calendar day
            setStartDate(DateUtil.addDays(getStartDate(), 1));
        }

        if (getStartDate().compareTo(getEndDate()) > 0) {
            //reduce startdate by one calendar day
            setStartDate(DateUtil.addDays(getStartDate(), -1));
        }
        setMaxSlippageEntry(p.getProperty("MaxSlippageEntry") == null ? 0.005 : Double.parseDouble(p.getProperty("MaxSlippageEntry")) / 100); // divide by 100 as input was a percentage
        setMaxSlippageExit(p.getProperty("MaxSlippageExit") == null ? 0.005 : Double.parseDouble(p.getProperty("MaxSlippageExit")) / 100); // divide by 100 as input was a percentage
        setMaxOrderDuration(p.getProperty("MaxOrderDuration") == null ? 3 : Integer.parseInt(p.getProperty("MaxOrderDuration")));
        setDynamicOrderDuration(p.getProperty("DynamicOrderDuration") == null ? 1 : Integer.parseInt(p.getProperty("DynamicOrderDuration")));
        if (MainAlgorithm.isUseForTrading()) {
            m.setCloseDate(DateUtil.addSeconds(getEndDate(), (this.getMaxOrderDuration() + 5) * 60)); //2 minutes after the enddate+max order duaration
        }
        setStrategyLog(Boolean.parseBoolean(p.getProperty("StrategyLog", "true").toString().trim()));
        setTickSize(Double.parseDouble(p.getProperty("TickSize")));
        setNumberOfContracts(p.getProperty("NumberOfContracts") == null ? 0 : Integer.parseInt(p.getProperty("NumberOfContracts")));
        setExposure(p.getProperty("Exposure") == null ? 0D : Double.parseDouble(p.getProperty("Exposure")));
        if (getExposure() == 0D && getNumberOfContracts() == 0) {
            setNumberOfContracts(1); //set minimum trade size if both exposure and contracts are not mentioned
        }
        setPointValue(p.getProperty("PointValue") == null ? 1 : Double.parseDouble(p.getProperty("PointValue")));
        setClawProfitTarget(p.getProperty("ClawProfitTarget") != null ? Double.parseDouble(p.getProperty("ClawProfitTarget")) : 0D);
        setDayProfitTarget(p.getProperty("DayProfitTarget") != null ? Double.parseDouble(p.getProperty("DayProfitTarget")) : 0D);
        setDayStopLoss(p.getProperty("DayStopLoss") != null ? Double.parseDouble(p.getProperty("DayStopLoss")) : 0D);
        setMaxOpenPositions(p.getProperty("MaximumOpenPositions") == null ? 1 : Integer.parseInt(p.getProperty("MaximumOpenPositions")));
        setFutBrokerageFile(p.getProperty("BrokerageFile") == null ? "" : p.getProperty("BrokerageFile"));
       if(!useRedis){
         setTradeFile(p.getProperty("TradeFile"));
         if (stratCount == null) {
            setOrderFile(p.getProperty("OrderFile"));
        } else {
            setOrderFile(p.getProperty("OrderFile").split("\\.")[0] + stratCount + "." + p.getProperty("OrderFile").split("\\.")[1]);

        }
        }
        setStartingCapital(p.getProperty("StartingCapital") == null ? 0D : Double.parseDouble(p.getProperty("StartingCapital")));

        longOnly = p.getProperty("Long") == null ? new AtomicBoolean(Boolean.TRUE) : new AtomicBoolean(Boolean.parseBoolean(p.getProperty("Long")));
        shortOnly = p.getProperty("Short") == null ? new AtomicBoolean(Boolean.TRUE) : new AtomicBoolean(Boolean.parseBoolean(p.getProperty("Short")));

        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Accounts" + delimiter + allAccounts});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "StartDate" + delimiter + getStartDate()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "EndDate" + delimiter + getEndDate()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "PrintTime" + delimiter + com.incurrency.framework.DateUtil.addSeconds(getEndDate(), (this.getMaxOrderDuration() + 1) * 60)});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Shutdown" + delimiter + DateUtil.addSeconds(getEndDate(), (this.getMaxOrderDuration() + 2) * 60)});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "TickSize" + delimiter + getTickSize()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ContractSize" + delimiter + getNumberOfContracts()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Exposure" + delimiter + getExposure()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ClawProfit" + delimiter + getClawProfitTarget()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "DayProfit" + delimiter + getDayProfitTarget()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "DayStopLoss" + delimiter + getDayStopLoss()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "PointValue" + delimiter + getPointValue()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "EntrySlippage" + delimiter + getMaxSlippageEntry()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ExitSlippage" + delimiter + getMaxSlippageExit()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "OrderDuration" + delimiter + getMaxOrderDuration()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "DynamicDuration" + delimiter + getDynamicOrderDuration()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "MaxOpenPositions" + delimiter + getMaxOpenPositions()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "BrokerageFile" + delimiter + getFutBrokerageFile()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "TradeFile" + delimiter + getTradeFile()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "OrderFile" + delimiter + getOrderFile()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "TimeZone" + delimiter + getTimeZone()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "StartingCapital" + delimiter + getStartingCapital()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "LongAllowed" + delimiter + getLongOnly()});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ShortAllowed" + delimiter + getShortOnly()});

        if (getFutBrokerageFile().compareTo("") != 0) {
            Properties pBrokerage = TradingUtil.loadParameters(getFutBrokerageFile());
            String brokerage1 = pBrokerage.getProperty("Brokerage");
            String addOn1 = pBrokerage.getProperty("AddOn1");
            String addOn2 = pBrokerage.getProperty("AddOn2");
            String addOn3 = pBrokerage.getProperty("AddOn3");
            String addOn4 = pBrokerage.getProperty("AddOn4");

            if (brokerage1 != null) {
                getBrokerageRate().add(TradingUtil.parseBrokerageString(brokerage1, type));
            }
            if (addOn1 != null) {
                getBrokerageRate().add(TradingUtil.parseBrokerageString(addOn1, type));
            }
            if (addOn2 != null) {
                getBrokerageRate().add(TradingUtil.parseBrokerageString(addOn2, type));
            }
            if (addOn3 != null) {
                getBrokerageRate().add(TradingUtil.parseBrokerageString(addOn3, type));
            }
            if (addOn4 != null) {
                getBrokerageRate().add(TradingUtil.parseBrokerageString(addOn4, type));
            }

        }
    }
    
    TimerTask runPrintOrders = new TimerTask() {
        @Override
        public void run() {
            //System.out.println("In Printorders");
            //logger.log(Level.INFO, "{0},{1},Reporting,Print Orders and Trades Called", new Object[]{allAccounts, getStrategy()});
            printOrders("", Strategy.this);
        }
    };

    public synchronized void printOrders(String prefix, Strategy s) {
        double[] profitGrid = new double[5];
        DecimalFormat df = new DecimalFormat("#.##");
        Map args = new HashMap<>();
        args.put(JsonWriter.PRETTY_PRINT, Boolean.TRUE);
        try {
            File dir = new File("logs");
            File file;
            File equityFile;
            String equityFileName;
            if (stratCount == null) {
                file = new File(dir, "body.txt");
                equityFileName = "Equity.csv";
                equityFile = new File(dir, equityFileName);
            } else {
                file = new File(dir, "body" + stratCount + ".txt");
                equityFileName = "Equity" + stratCount + ".csv";
                equityFile = new File(dir, equityFileName);

            }

            if (prefix.equals("")) {
                if (file.exists()) {
                    file.delete();
                }
                if (equityFile.exists()) {
                    equityFile.delete();
                }
            }
            logger.log(Level.INFO, "312,Debugging_StartedPrintOrders,{0}", new Object[]{s.getStrategy()});
            //String orderFileFullName = "logs" + File.separator + prefix + s.getOrderFile();
            String orderFileFullName = s.getOrderFile();
            if (prefix.equals("")) {
                profitGrid = TradingUtil.applyBrokerage(db, s.getBrokerageRate(), s.getPointValue(), s.getOrderFile(), s.getTimeZone(), s.getStartingCapital(), "Order", equityFileName,s.getStrategy());
                TradingUtil.writeToFile(file.getName(), "-----------------Orders:" + s.strategy + " --------------------------------------------------");
                TradingUtil.writeToFile(file.getName(), "Net P&L today: " + df.format(profitGrid[2]));
                TradingUtil.writeToFile(file.getName(), "YTD P&L: " + df.format(profitGrid[4]));
                TradingUtil.writeToFile(file.getName(), "Max Drawdown (%): " + df.format(profitGrid[5]));
                TradingUtil.writeToFile(file.getName(), "Max Drawdown (days): " + df.format(profitGrid[6]));
                TradingUtil.writeToFile(file.getName(), "Sharpe Ratio: " + df.format(profitGrid[8]));
                TradingUtil.writeToFile(file.getName(), "# days in history: " + df.format(profitGrid[9]));
            }
            if (!useRedis) {
                File f = new File("logs" + File.separator + prefix + orderFileFullName);
                if (f.exists() && !f.isDirectory()) { //delete old file
                    f.delete();
                }
                String out = JsonWriter.objectToJson(db, args);
                Utilities.writeToFile("logs", prefix + orderFileFullName, out);
            }
            //Now write trade file 
//            String tradeFileFullName = "logs" + File.separator + prefix + s.getTradeFile();
            String tradeFileFullName = s.getTradeFile();
            if (prefix.equals("")) {
                for (BeanConnection c : Parameters.connection) {
                    if (s.accounts.contains(c.getAccountName())) {
                        profitGrid = TradingUtil.applyBrokerage(s.oms.getDb(), s.getBrokerageRate(), s.getPointValue(), s.getTradeFile(), s.getTimeZone(), s.getStartingCapital(), c.getAccountName(), equityFileName,s.getStrategy());
                        TradingUtil.writeToFile(file.getName(), "-----------------Trades: " + s.strategy + " , Account: " + c.getAccountName() + "----------------------");
                        TradingUtil.writeToFile(file.getName(), "Gross P&L today: " + df.format(profitGrid[0]));
                        TradingUtil.writeToFile(file.getName(), "Brokerage today: " + df.format(profitGrid[1]));
                        TradingUtil.writeToFile(file.getName(), "Net P&L today: " + df.format(profitGrid[2]));
                        TradingUtil.writeToFile(file.getName(), "MTD P&L: " + df.format(profitGrid[3]));
                        TradingUtil.writeToFile(file.getName(), "YTD P&L: " + df.format(profitGrid[4]));
                        TradingUtil.writeToFile(file.getName(), "Max Drawdown (%): " + df.format(profitGrid[5]));
                        TradingUtil.writeToFile(file.getName(), "Max Drawdown (days): " + df.format(profitGrid[6]));
                        TradingUtil.writeToFile(file.getName(), "Avg Drawdown (days): " + df.format(profitGrid[7]));
                        TradingUtil.writeToFile(file.getName(), "Sharpe Ratio: " + df.format(profitGrid[8]));
                        TradingUtil.writeToFile(file.getName(), "# days in history: " + df.format(profitGrid[9]));
                        String message =
                                "Strategy Name:" + s.strategy + Strategy.newline
                                + "Net P&L today: " + df.format(profitGrid[2]) + Strategy.newline
                                + "YTD P&L: " + df.format(profitGrid[4]) + Strategy.newline
                                + "Max Drawdown (%): " + df.format(profitGrid[5]) + Strategy.newline
                                + "Max Drawdown (days): " + df.format(profitGrid[6]) + Strategy.newline
                                + "Sharpe Ratio: " + df.format(profitGrid[8]) + Strategy.newline
                                + "# days in history: " + df.format(profitGrid[9]);

                        String openPositions = Validator.openPositions(c.getAccountName(), s);

                        if (openPositions.equals("")) {
                            message = message + newline + "No open trade positions";
                        } else {
                            message = message + newline + openPositions;
                        }                      
                        message=message +"\n"+"\n";
                        message=message + "PNL Summary"+"\n";
                        
                        message=message+Validator.pnlSummary(s.getOms().getDb(), c.getAccountName(), s);
                        logger.log(Level.INFO, "312,Debugging_SendingEmail,{0}", new Object[]{s.getStrategy()});

                        Thread t = new Thread(new Mail(c.getOwnerEmail(), message, "EOD Reporting - " + s.getStrategy()));
                        t.start();
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException ex) {
                            logger.log(Level.INFO, "101", ex);
                        }
                    }
                }
            }
            if (!useRedis) {
                File f = new File("logs" + File.separator + prefix + tradeFileFullName);
                if (f.exists() && !f.isDirectory()) { //delete old file
                    f.delete();
                }

                String out = JsonWriter.objectToJson(oms.getDb(), args);
                Utilities.writeToFile("logs", prefix + tradeFileFullName, out);
            }
                for (BeanConnection c : Parameters.connection) {
                    if (s.accounts.contains(c.getAccountName())) {
                        Validator.reconcile(prefix, db, s.getOms().getDb(), c.getAccountName(), c.getOwnerEmail(),this.getStrategy());
                    }
                    //Validator.reconcile(prefix, s.getTradeFile(), s.getOrderFile(), account,c.getAccountName());
                }
                if(Algorithm.useForSimulation){
                    System.exit(0);
                }
            
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    public int getFirstInternalOpenOrder(int id, EnumOrderSide side, String accountName) {
        String symbol = Parameters.symbol.get(id).getDisplayname();
        EnumOrderSide entrySide = side == EnumOrderSide.SELL ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
        for (String key : db.getKeys("opentrades")) {
            if (Trade.getParentSymbol(db, key).equals(symbol) && Trade.getEntrySide(db, key).equals(entrySide) && Trade.getEntrySize(db, key) > Trade.getExitSize(db, key)) {
                return Trade.getEntryOrderIDInternal(db, key);
            }
        }
        return -1;
    }

    //used by CSV orders
/*    
     public synchronized void entry(int id, EnumOrderSide side, EnumOrderType orderType, double limitPrice, double triggerPrice, boolean scalein, EnumOrderReason reason, String orderGroup, int size, int duration, int dynamicDuration, double slippage, EnumOrderStage stage, String effectiveTime) {
     if (side == EnumOrderSide.BUY) {
     BeanPosition pd = getPosition().get(id);
     double expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
     int symbolPosition = pd.getPosition() + size;
     double positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * size + pd.getPrice() * pd.getPosition()) / (symbolPosition));
     pd.setPosition(symbolPosition);
     pd.setPositionInitDate(TradingUtil.getAlgoDate());
     pd.setPrice(positionPrice);
     getPosition().put(id, pd);
     } else {
     BeanPosition pd = getPosition().get(id);
     double expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
     int symbolPosition = pd.getPosition() - size;
     double positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * size + pd.getPrice() * pd.getPosition()) / (symbolPosition));
     pd.setPosition(symbolPosition);
     pd.setPositionInitDate(TradingUtil.getAlgoDate());
     pd.setPrice(positionPrice);
     getPosition().put(id, pd);
     }
     int internalorderid = getInternalOrderID();
     this.internalOpenOrders.put(id, internalorderid);
     getTrades().put(new OrderLink(internalorderid, 0, "Order"), new Trade(id, id, EnumOrderReason.REGULARENTRY, side, Parameters.symbol.get(id).getLastPrice(), size, internalorderid, 0, getTimeZone(), "Order"));
     logger.log(Level.INFO, "310,EntryOrder,{0},", new Object[]{getStrategy() + delimiter + internalorderid + delimiter + position.get(id).getPosition() + delimiter + position.get(id).getPrice()});
     if (MainAlgorithm.isUseForTrading()) {
     oms.tes.fireOrderEvent(internalorderid, internalorderid, Parameters.symbol.get(id), side, reason, orderType, size, limitPrice, triggerPrice, getStrategy(), duration, stage, dynamicDuration, slippage, scalein, orderGroup);
     }
     }
     */
    //used by pairs
    /*
     public synchronized void entry(int id, EnumOrderSide side, EnumOrderType orderType, double limitPrice, double triggerPrice, boolean scalein, EnumOrderReason reason, String orderGroup, int size) {
     if (side == EnumOrderSide.BUY) {
     BeanPosition pd = getPosition().get(id);
     double expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
     int symbolPosition = pd.getPosition() + size;
     double positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * size + pd.getPrice() * pd.getPosition()) / (symbolPosition));
     pd.setPosition(symbolPosition);
     pd.setPositionInitDate(TradingUtil.getAlgoDate());
     pd.setPrice(positionPrice);
     getPosition().put(id, pd);
     } else {
     BeanPosition pd = getPosition().get(id);
     double expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
     int symbolPosition = pd.getPosition() - size;
     double positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * size + pd.getPrice() * pd.getPosition()) / (symbolPosition));
     pd.setPosition(symbolPosition);
     pd.setPositionInitDate(TradingUtil.getAlgoDate());
     pd.setPrice(positionPrice);
     getPosition().put(id, pd);
     }
     int internalorderid = getInternalOrderID();
     this.internalOpenOrders.put(id, internalorderid);
     getTrades().put(new OrderLink(internalorderid, 0, "Order"), new Trade(id, id, EnumOrderReason.REGULARENTRY, side, Parameters.symbol.get(id).getLastPrice(), size, internalorderid, 0, getTimeZone(), "Order"));
     logger.log(Level.INFO, "310,EntryOrder,{0},", new Object[]{getStrategy() + delimiter + internalorderid + delimiter + position.get(id).getPosition() + delimiter + position.get(id).getPrice()});
     if (MainAlgorithm.isUseForTrading()) {
     oms.tes.fireOrderEvent(internalorderid, internalorderid, Parameters.symbol.get(id), side, reason, orderType, size, limitPrice, triggerPrice, getStrategy(), maxOrderDuration, EnumOrderStage.INIT, dynamicOrderDuration, maxSlippageExit,true,"DAY", scalein, orderGroup,"",null);
     }
     }
     */
    //used by adr
    public synchronized void entry(int id, EnumOrderSide side, int size, EnumOrderType orderType, double limitPrice, double triggerPrice, EnumOrderReason reason, EnumOrderStage stage, int duration, int dynamicDuration, double slippage, String orderGroup, String validity, String effectiveTime, boolean scalein, boolean transmit) {
        if (id >= 0) {
            size = size == 0 && getNumberOfContracts() == 0 ? (int) (getExposure() / limitPrice) : getNumberOfContracts() * Parameters.symbol.get(id).getMinsize();
            if (side == EnumOrderSide.BUY) {
                BeanPosition pd = getPosition().get(id);
                double expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
                int symbolPosition = pd.getPosition() + size;
                double positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * size + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                pd.setPosition(symbolPosition);
                pd.setPositionInitDate(TradingUtil.getAlgoDate());
                pd.setPrice(positionPrice);
                pd.setStrategy(strategy);
                getPosition().put(id, pd);
            } else {
                BeanPosition pd = getPosition().get(id);
                double expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
                int symbolPosition = pd.getPosition() - size;
                double positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * size + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                pd.setPosition(symbolPosition);
                pd.setPositionInitDate(TradingUtil.getAlgoDate());
                pd.setPrice(positionPrice);
                pd.setStrategy(strategy);
                getPosition().put(id, pd);
            }
            int internalorderid = getInternalOrderID();
            this.internalOpenOrders.put(id, internalorderid);
            new Trade(db, id, id, EnumOrderReason.REGULARENTRY, side, Parameters.symbol.get(id).getLastPrice(), size, internalorderid, 0, internalorderid, getTimeZone(), "Order", this.getStrategy(), "opentrades","TOBECOMPLETED");
            logger.log(Level.INFO, "401,EntryOrder,{0},", new Object[]{getStrategy() + delimiter + internalorderid + delimiter + position.get(id).getPosition() + delimiter + position.get(id).getPrice()});
            if (MainAlgorithm.isUseForTrading()) {
                oms.tes.fireOrderEvent(internalorderid, internalorderid, Parameters.symbol.get(id), side, reason, orderType, size, limitPrice, triggerPrice, getStrategy(), getMaxOrderDuration(), EnumOrderStage.INIT, dynamicOrderDuration, maxSlippageExit, transmit, validity, scalein, orderGroup, effectiveTime, null,"TOBECOMPLETED");
            }
        }
    }

    public int entry(HashMap<String, Object> order) {
        int id = Integer.valueOf(order.get("id").toString());
        int size = Utilities.getInt(order.get("size"), 0);
        order.put("orderref", this.getStrategy());
        double limitPrice = Utilities.getDouble(order.get("limitprice").toString(), 0);
        EnumOrderSide side = EnumOrderSide.valueOf(order.get("side") != null ? order.get("side").toString() : "UNDEFINED");
        if (id >= 0) {
            size = size == 0 && getNumberOfContracts() == 0 ? (int) (getExposure() / limitPrice) : size==0?getNumberOfContracts() * Parameters.symbol.get(id).getMinsize():size;
            order.put("size", size);
            if (side == EnumOrderSide.BUY) {
                BeanPosition pd = getPosition().get(id);
                double expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
                int symbolPosition = pd.getPosition() + size;
                double positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * size + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                pd.setPosition(symbolPosition);
                pd.setPositionInitDate(TradingUtil.getAlgoDate());
                pd.setPrice(positionPrice);
                pd.setStrategy(strategy);
                getPosition().put(id, pd);
            } else {
                BeanPosition pd = getPosition().get(id);
                double expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
                int symbolPosition = pd.getPosition() - size;
                double positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * size + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                pd.setPosition(symbolPosition);
                pd.setPositionInitDate(TradingUtil.getAlgoDate());
                pd.setPrice(positionPrice);
                pd.setStrategy(strategy);
                getPosition().put(id, pd);
            }
            int internalorderid = getInternalOrderID();
            order.put("orderidint", internalorderid);
            order.put("entryorderidint", internalorderid);
            this.internalOpenOrders.put(id, internalorderid);
            String log=order.get("log")!=null?order.get("log").toString():"";
            new Trade(db, id, id, EnumOrderReason.REGULARENTRY, side, Parameters.symbol.get(id).getLastPrice(), size, internalorderid, 0, internalorderid, getTimeZone(), "Order", this.getStrategy(), "opentrades",log);
            logger.log(Level.INFO, "401,EntryOrder,{0},", new Object[]{getStrategy() + delimiter + internalorderid + delimiter + position.get(id).getPosition() + delimiter + position.get(id).getPrice()});
            if (MainAlgorithm.isUseForTrading()) {
                oms.tes.fireOrderEvent(order);
                //oms.tes.fireOrderEvent(internalorderid, internalorderid, Parameters.symbol.get(id), side, reason, orderType, size, limitPrice, triggerPrice, getStrategy(), getMaxOrderDuration(), EnumOrderStage.INIT, dynamicOrderDuration, maxSlippageExit, transmit, validity, scalein, orderGroup, effectiveTime, null);
            }
            return internalorderid;
        } else {
            return -1;
        }
    }

    public synchronized void exit(int id, EnumOrderSide side, int size, EnumOrderType orderType, double limitPrice, double triggerPrice, EnumOrderReason reason, EnumOrderStage stage, int duration, int dynamicDuration, double slippage, String orderGroup, String validity, String effectiveTime, boolean scaleout, boolean transmit) {
        if (id >= 0) {
            int internalorderid = getInternalOrderID();
            logger.log(Level.INFO, "401,ExitOrder,{0},", new Object[]{getStrategy() + delimiter + internalorderid + delimiter + position.get(id).getPosition() + delimiter + Parameters.symbol.get(id).getLastPrice()});
            int tradeSize = scaleout == false ? Math.abs(getPosition().get(id).getPosition()) : size;
            double expectedFillPrice = 0;
                    if (side == EnumOrderSide.COVER) {
                        BeanPosition pd = getPosition().get(id);
                        expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
                        int symbolPosition = pd.getPosition() + tradeSize;
                        double positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * tradeSize + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                        pd.setPosition(symbolPosition);
                        pd.setPositionInitDate(TradingUtil.getAlgoDate());
                        pd.setPrice(positionPrice);
                        pd.setStrategy(strategy);
                        getPosition().put(id, pd);
                    } else  if (side == EnumOrderSide.SELL){
                        BeanPosition pd = getPosition().get(id);
                        expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
                        int symbolPosition = pd.getPosition() - tradeSize;
                        double positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * tradeSize + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                        pd.setPosition(symbolPosition);
                        pd.setPositionInitDate(TradingUtil.getAlgoDate());
                        pd.setPrice(positionPrice);
                        pd.setStrategy(strategy);
                        getPosition().put(id, pd);
                    }

            //int tempinternalOrderID = internalOpenOrders.get(id);
            int tempinternalOrderID = getFirstInternalOpenOrder(id, side, "Order");
            String key = this.getStrategy() + ":" + tempinternalOrderID + ":" + "Order";
            boolean entryTrade = Trade.getEntrySize(db, key) > 0 ? true : false;
            if (entryTrade) {
                int exitSize = Trade.getExitSize(db, key);
                double exitPrice = Trade.getExitPrice(db, key);
                int newexitSize = exitSize + tradeSize;
                double newexitPrice = (exitPrice * exitSize + tradeSize * expectedFillPrice) / (newexitSize);
                if (getPosition().get(id).getPosition() == 0) {
                    Trade.updateExit(db, id, EnumOrderReason.REGULAREXIT, side, newexitPrice, newexitSize, internalorderid, 0, internalorderid, tempinternalOrderID, getTimeZone(), "Order", this.getStrategy(), "closedtrades","TOBECOMPLETED");
                } else {
                    Trade.updateExit(db, id, EnumOrderReason.REGULAREXIT, side, newexitPrice, newexitSize, internalorderid, 0, internalorderid, tempinternalOrderID, getTimeZone(), "Order", this.getStrategy(), "opentrades","TOBECOMPLETED");
                }
                logger.log(Level.INFO, "Debugging_Strategy_exit,{0}", new Object[]{exitSize + delimiter + exitPrice + delimiter + size + delimiter + expectedFillPrice + delimiter + newexitSize + delimiter + newexitPrice});
                if (MainAlgorithm.isUseForTrading()) {
                    oms.tes.fireOrderEvent(internalorderid, tempinternalOrderID, Parameters.symbol.get(id), side, reason, orderType, tradeSize, limitPrice, triggerPrice, getStrategy(), getMaxOrderDuration(), EnumOrderStage.INIT, dynamicOrderDuration, maxSlippageExit, transmit, validity, scaleout, orderGroup, effectiveTime, null,"TOBECOMPLETED");
                }
            } else {
                logger.log(Level.INFO, "101,ExitInternalIDNotFound,{0}", new Object[]{id + delimiter + side + tempinternalOrderID});
            }
        }
    }

    public synchronized int exit(HashMap<String, Object> order) {
        int id = Integer.valueOf(order.get("id").toString());
        int size = Utilities.getInt(order.get("size"), 0);
        order.put("orderref", this.getStrategy());
        double limitPrice = Utilities.getDouble(order.get("limitprice"), 0);
        EnumOrderSide side = EnumOrderSide.valueOf(order.get("side") != null ? order.get("side").toString() : "UNDEFINED");
        Boolean scaleout = order.get("scale") != null ? Boolean.valueOf(order.get("scale").toString()) : false;
        EnumOrderReason reason = EnumOrderReason.valueOf(order.get("reason") != null ? order.get("reason").toString() : "UNDEFINED");
        if (id >= 0) {
            int tradeSize = scaleout == false ? Math.abs(getPosition().get(id).getPosition()) : size;
            order.put("size", tradeSize);
            double expectedFillPrice = 0;
            if (side == EnumOrderSide.COVER) {
                BeanPosition pd = getPosition().get(id);
                expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
                int symbolPosition = pd.getPosition() + tradeSize;
                double positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * tradeSize + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                pd.setPosition(symbolPosition);
                pd.setPositionInitDate(TradingUtil.getAlgoDate());
                pd.setPrice(positionPrice);
                pd.setStrategy(strategy);
                getPosition().put(id, pd);
            } else if (side == EnumOrderSide.SELL) {
                BeanPosition pd = getPosition().get(id);
                expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
                int symbolPosition = pd.getPosition() - tradeSize;
                double positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * tradeSize + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                pd.setPosition(symbolPosition);
                pd.setPositionInitDate(TradingUtil.getAlgoDate());
                pd.setPrice(positionPrice);
                pd.setStrategy(strategy);
                getPosition().put(id, pd);
            }
            //int tempinternalOrderID = Utilities.getInt(order.get("entryorderidint"),-1)>0?Utilities.getInt(order.get("entryorderidint"),-1):getFirstInternalOpenOrder(id, side, "Order");
            int tempinternalOrderID = getFirstInternalOpenOrder(id, side, "Order");
            String key = this.getStrategy() + ":" + tempinternalOrderID + ":" + "Order";
            boolean entryTradeExists = Trade.getEntrySize(db, key) > 0 ? true : false;
            if (entryTradeExists) {
                 int internalorderid = getInternalOrderID();
                order.put("orderidint", internalorderid);
               logger.log(Level.INFO, "401,ExitOrder,{0},", new Object[]{getStrategy() + delimiter + internalorderid + delimiter + position.get(id).getPosition() + delimiter + Parameters.symbol.get(id).getLastPrice()});
                int exitSize = Trade.getExitSize(db, key);
                double exitPrice = Trade.getExitPrice(db, key);
                int newexitSize = exitSize + tradeSize;
                double newexitPrice = (exitPrice * exitSize + tradeSize * expectedFillPrice) / (newexitSize);
                order.put("entryorderidint", tempinternalOrderID);
                String log=order.get("log")!=null?order.get("log").toString():"";
                Trade.updateExit(db, id, reason, side, newexitPrice, newexitSize, internalorderid, 0, internalorderid, tempinternalOrderID, getTimeZone(), "Order", this.getStrategy(), "opentrades",log);
                if (getPosition().get(id).getPosition() == 0) {
                    Trade.closeTrade(db, key);
                }
                
                logger.log(Level.INFO, "501,StrategyExit,{0}", new Object[]{getPosition().get(id).getPosition()+delimiter+exitSize + delimiter + exitPrice + delimiter + size + delimiter + expectedFillPrice + delimiter + newexitSize + delimiter + newexitPrice});
                if (MainAlgorithm.isUseForTrading()) {
                    oms.tes.fireOrderEvent(order);
                }
                return tempinternalOrderID;
            } else {
                
                logger.log(Level.INFO, "101,ExitInternalIDNotFound,{0}", new Object[]{id + delimiter + side +delimiter+ tempinternalOrderID+delimiter+key+delimiter+ Trade.getEntrySize(db, key)});
                return -1;
            }
        } else {
            return -1;
        }
    }

    @Override
    public void notificationReceived(NotificationEvent event) {
    }

    /**
     * @return the oms
     */
    public ExecutionManager getOms() {
        synchronized (lockOMS) {
            return oms;
        }
    }

    /**
     * @param oms the oms to set
     */
    public void setOms(ExecutionManager oms) {
        synchronized (lockOMS) {
            this.oms = oms;
        }
    }

    /**
     * @return the longOnly
     */
    public synchronized Boolean getLongOnly() {
        return longOnly.get();
    }

    /**
     * @param longOnly the longOnly to set
     */
    public synchronized void setLongOnly(Boolean l) {
        logger.log(Level.INFO, "LongOnly Set to {0}", new Object[]{l});
        this.longOnly = new AtomicBoolean(l);
    }

    /**
     * @return the shortOnly
     */
    public synchronized Boolean getShortOnly() {
        return shortOnly.get();
    }

    /**
     * @param shortOnly the shortOnly to set
     */
    public synchronized void setShortOnly(Boolean s) {
        logger.log(Level.INFO, "shortOnly Set to {0}", new Object[]{s});
        this.shortOnly = new AtomicBoolean(s);
    }

    /**
     * @return the aggression
     */
    public synchronized Boolean getAggression() {
        return aggression;
    }

    /**
     * @param aggression the aggression to set
     */
    public synchronized void setAggression(Boolean aggression) {
        this.aggression = aggression;
    }

    /**
     * @return the clawProfitTarget
     */
    public synchronized double getClawProfitTarget() {
        return clawProfitTarget;
    }

    /**
     * @param clawProfitTarget the clawProfitTarget to set
     */
    public synchronized void setClawProfitTarget(double clawProfitTarget) {
        this.clawProfitTarget = clawProfitTarget;
    }

    /**
     * @return the dayProfitTarget
     */
    public synchronized double getDayProfitTarget() {
        return dayProfitTarget;
    }

    /**
     * @param dayProfitTarget the dayProfitTarget to set
     */
    public synchronized void setDayProfitTarget(double dayProfitTarget) {
        this.dayProfitTarget = dayProfitTarget;
    }

    /**
     * @return the dayStopLoss
     */
    public synchronized double getDayStopLoss() {
        return dayStopLoss;
    }

    /**
     * @param dayStopLoss the dayStopLoss to set
     */
    public void setDayStopLoss(double dayStopLoss) {
        this.dayStopLoss = dayStopLoss;
    }

    /**
     * @return the plmanager
     */
    public ProfitLossManager getPlmanager() {
        synchronized (lockPLManager) {
            return plmanager;
        }
    }

    /**
     * @param plmanager the plmanager to set
     */
    public synchronized void setPlmanager(ProfitLossManager plmanager) {
        synchronized (lockPLManager) {
            this.plmanager = plmanager;
        }
    }

    /**
     * @return the maxOrderDuration
     */
    public int getMaxOrderDuration() {
        return maxOrderDuration;
    }

    /**
     * @param maxOrderDuration the maxOrderDuration to set
     */
    public void setMaxOrderDuration(int maxOrderDuration) {
        this.maxOrderDuration = maxOrderDuration;
    }

    /**
     * @return the dynamicOrderDuration
     */
    public int getDynamicOrderDuration() {
        return dynamicOrderDuration;
    }

    /**
     * @param dynamicOrderDuration the dynamicOrderDuration to set
     */
    public void setDynamicOrderDuration(int dynamicOrderDuration) {
        this.dynamicOrderDuration = dynamicOrderDuration;
    }

    /**
     * @return the maxSlippageExit
     */
    public double getMaxSlippageExit() {
        return maxSlippageExit;
    }

    /**
     * @param maxSlippageExit the maxSlippageExit to set
     */
    public void setMaxSlippageExit(double maxSlippageExit) {
        this.maxSlippageExit = maxSlippageExit;
    }

    /**
     * @return the strategy
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * @param strategy the strategy to set
     */
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    /**
     * @return the accounts
     */
    public ArrayList<String> getAccounts() {
        return accounts;
    }

    /**
     * @param accounts the accounts to set
     */
    public void setAccounts(ArrayList<String> accounts) {
        this.accounts = accounts;
    }

    /**
     * @return the tickSize
     */
    public double getTickSize() {
        return tickSize;
    }

    /**
     * @param tickSize the tickSize to set
     */
    public void setTickSize(double tickSize) {
        this.tickSize = tickSize;
    }

    /**
     * @return the maxSlippageEntry
     */
    public double getMaxSlippageEntry() {
        return maxSlippageEntry;
    }

    /**
     * @param maxSlippageEntry the maxSlippageEntry to set
     */
    public void setMaxSlippageEntry(double maxSlippageEntry) {
        this.maxSlippageEntry = maxSlippageEntry;
    }

    /**
     * @return the startDate
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * @return the endDate
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate the endDate to set
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * @return the numberOfContracts
     */
    public int getNumberOfContracts() {
        return numberOfContracts;
    }

    /**
     * @param numberOfContracts the numberOfContracts to set
     */
    public void setNumberOfContracts(int numberOfContracts) {
        this.numberOfContracts = numberOfContracts;
    }

    /**
     * @return the exposure
     */
    public double getExposure() {
        return exposure;
    }

    /**
     * @param exposure the exposure to set
     */
    public void setExposure(double exposure) {
        this.exposure = exposure;
    }

    /**
     * @return the pointValue
     */
    public double getPointValue() {
        return pointValue;
    }

    /**
     * @param pointValue the pointValue to set
     */
    public void setPointValue(double pointValue) {
        this.pointValue = pointValue;
    }

    /**
     * @return the maxOpenPositions
     */
    public int getMaxOpenPositions() {
        return maxOpenPositions;
    }

    /**
     * @param maxOpenPositions the maxOpenPositions to set
     */
    public void setMaxOpenPositions(int maxOpenPositions) {
        this.maxOpenPositions = maxOpenPositions;
    }

    /**
     * @return the futBrokerageFile
     */
    public String getFutBrokerageFile() {
        return futBrokerageFile;
    }

    /**
     * @param futBrokerageFile the futBrokerageFile to set
     */
    public void setFutBrokerageFile(String futBrokerageFile) {
        this.futBrokerageFile = futBrokerageFile;
    }

    /**
     * @return the brokerageRate
     */
    public ArrayList<BrokerageRate> getBrokerageRate() {
        return brokerageRate;
    }

    /**
     * @param brokerageRate the brokerageRate to set
     */
    public void setBrokerageRate(ArrayList<BrokerageRate> brokerageRate) {
        this.brokerageRate = brokerageRate;
    }

    /**
     * @return the tradeFile
     */
    public String getTradeFile() {
        return tradeFile;
    }

    /**
     * @param tradeFile the tradeFile to set
     */
    public void setTradeFile(String tradeFile) {
        this.tradeFile = tradeFile;
    }

    /**
     * @return the orderFile
     */
    public String getOrderFile() {
        return orderFile;
    }

    /**
     * @param orderFile the orderFile to set
     */
    public void setOrderFile(String orderFile) {
        this.orderFile = orderFile;
    }

    /**
     * @return the timeZone
     */
    public String getTimeZone() {
        return timeZone;
    }

    /**
     * @param timeZone the timeZone to set
     */
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * @return the startingCapital
     */
    public double getStartingCapital() {
        return startingCapital;
    }

    /**
     * @param startingCapital the startingCapital to set
     */
    public void setStartingCapital(double startingCapital) {
        this.startingCapital = startingCapital;
    }

    /**
     * @return the strategySymbols
     */
    public List<Integer> getStrategySymbols() {
        return strategySymbols;
    }

    /**
     * @param strategySymbols the strategySymbols to set
     */
    public void setStrategySymbols(List<Integer> strategySymbols) {
        this.strategySymbols = strategySymbols;
    }

    /**
     * @return the position
     */
    public HashMap<Integer, BeanPosition> getPosition() {
        synchronized (lockPL) {
            return position;
        }
    }

    /**
     * @param position the position to set
     */
    public void setPosition(HashMap<Integer, BeanPosition> position) {
        synchronized (lockPL) {
            this.position = position;
        }
    }

    /**
     * @return the internalOrderID
     */
    public synchronized int getInternalOrderID() {
        return Algorithm.orderidint.addAndGet(1);
    }

    /**
     * @return the strategyLog
     */
    public boolean isStrategyLog() {
        return strategyLog;
    }

    /**
     * @param strategyLog the strategyLog to set
     */
    public void setStrategyLog(boolean strategyLog) {
        this.strategyLog = strategyLog;
    }

    /**
     * @return the stopOrders
     */
    public boolean isStopOrders() {
        synchronized (lockPL) {
            return stopOrders;
        }
    }

    /**
     * @param stopOrders the stopOrders to set
     */
    public void setStopOrders(boolean stopOrders) {
        synchronized (lockPL) {
            logger.log(Level.INFO, "StopOrders Set to {0}", new Object[]{stopOrders});
            this.stopOrders = stopOrders;
        }
    }
    
        /**
     * @return the combosAdded
     */
    public static synchronized HashMap<String, String> getCombosAdded() {
        return combosAdded;
    }

    /**
     * @param aCombosAdded the combosAdded to set
     */
    public static synchronized void setCombosAdded(HashMap<String, String> aCombosAdded) {
        combosAdded = aCombosAdded;
    }
}
