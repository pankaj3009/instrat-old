/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 *
 * @author pankaj
 */
public class Strategy implements NotificationListener {

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
    //--common parameters required for all strategies
    MainAlgorithm m;
    public HashMap<Integer, Integer> internalOpenOrders = new HashMap(); //holds mapping of symbol id to latest initialization internal order
    private HashMap<OrderLink, Trade> trades = new HashMap();
    private HashMap<OrderLink, Trade> omsInitTrades = new HashMap();
    private HashMap<Integer, BeanPosition> position = new HashMap<>();
    private double tickSize;
    private double pointValue = 1;
    private int internalOrderID = 1;
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
    private final String delimiter = "_";
    private boolean strategyLog;

    public Strategy(MainAlgorithm m, String headerStrategy, String type, Properties prop, String parameterFileName, ArrayList<String> accounts, Integer stratCount) {
        try {
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
            String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
            if (stratCount == null) {
                this.strategy = tempStrategyArray[tempStrategyArray.length - 1];
            } else {
                this.strategy = tempStrategyArray[tempStrategyArray.length - 1] + stratCount;
            }
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
                stratVal = Validator.reconcile("", getTradeFile(), getOrderFile(), account, ownerEmail);
                if (!stratVal) {
                    logger.log(Level.INFO, "100,IntegrityCheckFailed,{0}", new Object[]{getStrategy() + delimiter + account});
                }
                validation = validation && stratVal;
            }
            //}
            if (validation) {
                //Initialize open notional orders and positions
                String filename = "logs" + File.separator + getOrderFile();
                ArrayList<Trade> allOrders = new ArrayList<>();
                new Trade().reader(filename, allOrders);
                //remove any child orders
                ArrayList<Integer> childEntryOrders = new ArrayList<>();
                for (Trade tr : allOrders) {
                    if (Parameters.symbol.get(tr.getEntrySymbolID()).getType().equals("COMBO")) {
                        childEntryOrders.add(tr.getEntryID());
                    }
                }
                Iterator iter1 = allOrders.iterator();
                while (iter1.hasNext()) {
                    Trade trchild = (Trade) iter1.next();
                    if (childEntryOrders.contains(Integer.valueOf(trchild.getEntryID())) && !Parameters.symbol.get(trchild.getEntryID()).getType().equals("COMBO")) {
                        iter1.remove();
                    }
                }

                for (Trade or : allOrders) {
                    int tempPosition = 0;
                    double tempPositionPrice = 0D;
                    int id = TradingUtil.getEntryIDFromDisplayName(or, Parameters.symbol);
                    if (id >= 0) {
                        if (or.getAccountName().equals("Order")) {
                            BeanPosition p = position.get(id) == null ? new BeanPosition(id, getStrategy()) : position.get(id);
                            tempPosition = p.getPosition();
                            tempPositionPrice = p.getPrice();
                            switch (or.getEntrySide()) {
                                case BUY:
                                    tempPositionPrice = or.getEntrySize() + tempPosition != 0 ? (tempPosition * tempPositionPrice + or.getEntrySize() * or.getEntryPrice()) / (or.getEntrySize() + tempPosition) : 0D;
                                    tempPosition = tempPosition + or.getEntrySize();
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    position.put(id, p);
                                    break;
                                case SHORT:
                                    tempPositionPrice = or.getEntrySize() + tempPosition != 0 ? (tempPosition * tempPositionPrice - or.getEntrySize() * or.getEntryPrice()) / (-or.getEntrySize() + tempPosition) : 0D;
                                    tempPosition = tempPosition - or.getEntrySize();
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    position.put(id, p);
                                    break;
                                default:
                                    break;
                            }
                            switch (or.getExitSide()) {
                                case COVER:
                                    tempPositionPrice = or.getEntrySize() + tempPosition != 0 ? (tempPosition * tempPositionPrice + or.getEntrySize() * or.getEntryPrice()) / (or.getEntrySize() + tempPosition) : 0D;
                                    tempPosition = tempPosition + or.getEntrySize();
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    position.put(id, p);
                                    break;
                                case SELL:
                                    tempPositionPrice = -or.getEntrySize() + tempPosition != 0 ? (tempPosition * tempPositionPrice - or.getEntrySize() * or.getEntryPrice()) / (-or.getEntrySize() + tempPosition) : 0D;
                                    tempPosition = tempPosition - or.getEntrySize();
                                    p.setPosition(tempPosition);
                                    p.setPrice(tempPositionPrice);
                                    p.setPointValue(pointValue);
                                    position.put(id, p);
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
                //print positions on initialization
                for (int id : getStrategySymbols()) {
                    if (position.get(id).getPosition() != 0) {
                        logger.log(Level.INFO, "301,InitialOrderPosition,{0}", new Object[]{"Order" + delimiter + this.getStrategy() + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + position.get(id).getPosition() + delimiter + position.get(id).getPrice()});
                    }
                }

                Iterator<Trade> iter = allOrders.iterator();
                while (iter.hasNext()) {
                    Trade tr = iter.next();
                    if (tr.getExitPrice() != 0) {
                        internalOrderID = Math.max(internalOrderID, Math.max(tr.getEntryID(), tr.getExitID()));
                        iter.remove();
                    } else {
                        internalOrderID = Math.max(tr.getEntryID(), internalOrderID);
                    }
                }
                //internalOrderID = internalOrderID + 1; //we reconcile at this orderID
                logger.log(Level.INFO, "100, OpeningInternalOrderID,{0}", new Object[]{getStrategy() + delimiter + internalOrderID});

                for (Trade tr : allOrders) {
                    getTrades().put(new OrderLink(tr.getEntryID(), 0, "Order"), tr);
                    int id = TradingUtil.getEntryIDFromDisplayName(tr, Parameters.symbol);
                    if (id >= 0) {//update internal orders if id exists
                        this.internalOpenOrders.put(id, position.get(id).getPosition());
                    }
                }
                //Initialize open trades        
                filename = "logs" + File.separator + getTradeFile();
                ArrayList<Trade> allTrades = new ArrayList<>();
                new Trade().reader(filename, allTrades);
                iter = allTrades.iterator();
                while (iter.hasNext()) {
                    if (iter.next().getExitPrice() != 0) {
                        iter.remove();
                    }
                }
                for (Trade tr : allTrades) {
                    this.omsInitTrades.put(new OrderLink(tr.getEntryID(), tr.getEntryOrderID(), tr.getAccountName()), tr);
                }
                //oms = new ExecutionManager(this, getAggression(), this.getTickSize(), getEndDate(), this.strategy, getPointValue(), getMaxOpenPositions(), getTimeZone(), accounts, omsInitTrades);
                if (MainAlgorithm.isUseForTrading()) {
                    Thread t = new Thread(oms = new ExecutionManager(this, getAggression(), this.getTickSize(), getEndDate(), this.strategy, getPointValue(), getMaxOpenPositions(), getTimeZone(), accounts, omsInitTrades));
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

        // String currDateStr = DateUtil.getFormatedDate("yyyyMMddHHmmss", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
        //Date currDate=DateUtil.parseDate("yyyyMMddHHmmss", currDateStr, TimeZone.getDefault().toString());
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
            m.setCloseDate(DateUtil.addSeconds(getEndDate(), (this.getMaxOrderDuration() + 2) * 60)); //2 minutes after the enddate+max order duaration
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
        setTradeFile(p.getProperty("TradeFile"));
        if (stratCount == null) {
            setOrderFile(p.getProperty("OrderFile"));
        } else {
            setOrderFile(p.getProperty("OrderFile").split("\\.")[0] + stratCount + "." + p.getProperty("OrderFile").split("\\.")[1]);

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
            //if file doesnt exists, then create it
            if (file.exists()) {
                file.delete();
            }
            if (equityFile.exists()) {
                equityFile.delete();
            }

            logger.log(Level.INFO, "312,Debugging_StartedPrintOrders,{0}", new Object[]{s.getStrategy()});
            String orderFileFullName = "logs" + File.separator + prefix + s.getOrderFile();

            profitGrid = TradingUtil.applyBrokerage(s.trades, s.getBrokerageRate(), s.getPointValue(), s.getOrderFile(), s.getTimeZone(), s.getStartingCapital(), "Order", equityFileName);
            TradingUtil.writeToFile(file.getName(), "-----------------Orders:" + s.strategy + " --------------------------------------------------");
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
            SortedMap<OrderLink, Trade> sortedOrders = new TreeMap<>();
            ArrayList<Trade> oldOrders = new ArrayList<>();
            new Trade().reader(orderFileFullName, oldOrders);
            for (Trade order : oldOrders) {//add earlier orders
                sortedOrders.put(new OrderLink(order.getEntryID(), order.getEntryOrderID(), order.getAccountName()), order);
            }
            for (Map.Entry<OrderLink, Trade> neworder : s.trades.entrySet()) {//add today's orders
                sortedOrders.put(neworder.getKey(), neworder.getValue());
            }
            logger.log(Level.INFO, "312,Debugging_Orders_1,{0}", new Object[]{s.getStrategy() + "_" + sortedOrders.size()});

            File f = new File(orderFileFullName);
            if (f.exists() && !f.isDirectory()) { //delete old file
                f.delete();
            }
            //write arraylist to file
            Comparator<OrderLink> comparator = new TradesPrintCompare();
            SortedSet<OrderLink> keys = new TreeSet<>(comparator);
            keys.addAll(sortedOrders.keySet());
            logger.log(Level.INFO, "312,Debugging_Orders_2,{0}", new Object[]{s.getStrategy() + "_" + sortedOrders.size()});
            for (Trade tr : sortedOrders.values()) {//write to new file
                tr.writer(orderFileFullName);
            }
            String tradeFileFullName = "logs" + File.separator + prefix + s.getTradeFile();
            for (BeanConnection c : Parameters.connection) {
                if (s.accounts.contains(c.getAccountName())) {
                    profitGrid = TradingUtil.applyBrokerage(s.oms.getTrades(), s.getBrokerageRate(), s.getPointValue(), s.getTradeFile(), s.getTimeZone(), s.getStartingCapital(), c.getAccountName(), equityFileName);
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
                            + "Gross P&L today: " + df.format(profitGrid[0]) + Strategy.newline
                            + "Brokerage today: " + df.format(profitGrid[1]) + Strategy.newline
                            + "Net P&L today: " + df.format(profitGrid[2]) + Strategy.newline
                            + "MTD P&L: " + df.format(profitGrid[3]) + Strategy.newline
                            + "YTD P&L: " + df.format(profitGrid[4]) + Strategy.newline
                            + "Max Drawdown (%): " + df.format(profitGrid[5]) + Strategy.newline
                            + "Max Drawdown (days): " + df.format(profitGrid[6]) + Strategy.newline
                            + "Avg Drawdown (days): " + df.format(profitGrid[7]) + Strategy.newline
                            + "Sharpe Ratio: " + df.format(profitGrid[8]) + Strategy.newline
                            + "# days in history: " + df.format(profitGrid[9]);

                    //Identify mismatched positions
                    //Validator.updateComboSymbols(prefix,s.getTradeFile(),s,"CSV");
                    logger.log(Level.INFO, "312,Debugging_UpdateComboSymbols,{0}", new Object[]{s.getStrategy()});

                    String openPositions = Validator.openPositions(c.getAccountName(), s);
                    logger.log(Level.INFO, "312,Debugging_CalculatedOpenPositions,{0}", new Object[]{s.getStrategy()});

                    if (openPositions.equals("")) {
                        message = message + newline + "No open trade positions";
                    } else {
                        message = message + newline + openPositions;
                    }
                    logger.log(Level.INFO, "312,Debugging_SendingEmail,{0}", new Object[]{s.getStrategy()});

                    Thread t = new Thread(new Mail(c.getOwnerEmail(), message, "EOD Reporting - " + s.getStrategy()));
                    t.start();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        logger.log(Level.INFO, "101", ex);
                    }
                }
            }
            SortedMap<OrderLink, Trade> sortedTrades = new TreeMap<>();// holds temp trades
            ArrayList<Trade> oldTrades = new ArrayList<>();
            new Trade().reader(tradeFileFullName, oldTrades);
            for (Trade trade : oldTrades) {//add earlier trades
                sortedTrades.put(new OrderLink(trade.getEntryID(), trade.getEntryOrderID(), trade.getAccountName()), trade);
            }
            if (MainAlgorithm.isUseForTrading()) {
                logger.log(Level.INFO, "312,Debugging_Trades_TradeSize,{0}", new Object[]{s.oms.getTrades().size()});
                for (Map.Entry<OrderLink, Trade> newtrade : s.oms.getTrades().entrySet()) { //add today's trades
                    sortedTrades.put(newtrade.getKey(), newtrade.getValue());
                    logger.log(Level.INFO, "312,Debugging_Duplicates_1,{0}", new Object[]{newtrade.getKey().getAccountName() + "_" + newtrade.getKey().getExternalOrderID() + "_" + newtrade.getKey().getInternalOrderID()});
                }
                logger.log(Level.INFO, "312,Debugging_Trades_1,{0}", new Object[]{s.getStrategy() + "_" + sortedTrades.size()});
            }
            f = new File(tradeFileFullName);
            if (f.exists() && !f.isDirectory()) { //delete old file
                f.delete();
            }
            //Comparator<OrderLink> comparator1=new TradesPrintCompare();
            //keys = new TreeSet<>(comparator1);
            //keys.addAll(sortedOrders.keySet());
            logger.log(Level.INFO, "312,Debugging_Trades_2,{0}", new Object[]{s.getStrategy() + "_" + sortedOrders.size()});
            for (Trade tr : sortedTrades.values()) {//write to new file
                tr.writer(tradeFileFullName);
                logger.log(Level.INFO, "312,Debugging_Duplicates_1,{0}", new Object[]{tr.getEntrySymbol() + "_" + tr.getEntryPrice()});

            }
            for (BeanConnection c : Parameters.connection) {
                if (s.accounts.contains(c.getAccountName())) {
                    Validator.reconcile(prefix, s.getTradeFile(), s.getOrderFile(), c.getAccountName(), c.getOwnerEmail());
                }
                //Validator.reconcile(prefix, s.getTradeFile(), s.getOrderFile(), account,c.getAccountName());
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    public int getFirstInternalOpenOrder(int id, EnumOrderSide side, String accountName) {
        ArrayList<Trade> allTrades = new <Trade> ArrayList();
        for (Map.Entry<OrderLink, Trade> entry : getTrades().entrySet()) {
            if (entry.getKey().getAccountName().equals(accountName)) {
                allTrades.add(entry.getValue());
            }
        }
        Collections.sort(allTrades, new TradesCompare());
        String symbol = Parameters.symbol.get(id).getDisplayname();
        EnumOrderSide entrySide = side == EnumOrderSide.SELL ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
        for (Trade tr : allTrades) {
            if (tr.getEntrySymbol().compareTo(symbol) == 0 && tr.getEntrySide() == entrySide) {
                if (tr.getEntrySize() > tr.getExitSize()) {
                    //logger.log(Level.FINE, "{0},{1},Execution Manager,Internal order id being used, Internal Order ID:{2}", new Object[]{accountName, orderReference, tr.getEntryID()});
                    return tr.getEntryID();
                }
            }
        }
        return 0;
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
//  public synchronized void entry(int id, EnumOrderSide side, EnumOrderType orderType, double limitPrice, double triggerPrice, boolean scalein, EnumOrderReason reason, String orderGroup, int size) {
        size = size == 0 && getNumberOfContracts() == 0 ? (int) (getExposure() / limitPrice) : getNumberOfContracts() * Parameters.symbol.get(id).getMinsize();
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
            oms.tes.fireOrderEvent(internalorderid, internalorderid, Parameters.symbol.get(id), side, reason, orderType, size, limitPrice, triggerPrice, getStrategy(), maxOrderDuration, EnumOrderStage.INIT, dynamicOrderDuration, maxSlippageExit, transmit, validity, scalein, orderGroup, effectiveTime, null);
        }
    }

    /*
     public synchronized void exit(int id, EnumOrderSide side, EnumOrderType orderType, double limitPrice, double triggerPrice, String link, boolean transmit, String validity, boolean scaleout, EnumOrderReason notify, String passToOrderObject, int tradeSize) {
     int internalorderid = getInternalOrderID();
     logger.log(Level.INFO, "310,ExitOrder,{0},", new Object[]{getStrategy() + delimiter + internalorderid + delimiter + position.get(id).getPosition() + delimiter + Parameters.symbol.get(id).getLastPrice()});
     switch (notify) {
     case REGULARENTRY:
     case REGULAREXIT:
     if (side == EnumOrderSide.COVER) {
     BeanPosition pd = getPosition().get(id);
     double expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
     int symbolPosition = pd.getPosition() + tradeSize;
     double positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * tradeSize + pd.getPrice() * pd.getPosition()) / (symbolPosition));
     pd.setPosition(symbolPosition);
     pd.setPositionInitDate(TradingUtil.getAlgoDate());
     pd.setPrice(positionPrice);
     getPosition().put(id, pd);
     } else {
     BeanPosition pd = getPosition().get(id);
     double expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
     int symbolPosition = pd.getPosition() - tradeSize;
     double positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * tradeSize + pd.getPrice() * pd.getPosition()) / (symbolPosition));
     pd.setPosition(symbolPosition);
     pd.setPositionInitDate(TradingUtil.getAlgoDate());
     pd.setPrice(positionPrice);
     getPosition().put(id, pd);
     }
     break;
     case SL:

     break;
     case TP:

     break;
     default:
     break;
     }

     int tempinternalOrderID = internalOpenOrders.get(id);
     Trade tempTrade = getTrades().get(new OrderLink(tempinternalOrderID, 0, "Order"));
     //int internalorderid = getInternalOrderID();
     tempTrade.updateExit(id, EnumOrderReason.REGULAREXIT, side, Parameters.symbol.get(id).getLastPrice(), tradeSize, internalorderid, 0, getTimeZone(), "Order");
     getTrades().put(new OrderLink(tempinternalOrderID, 0, "Order"), tempTrade);
     if (MainAlgorithm.isUseForTrading()) {
     oms.tes.fireOrderEvent(internalorderid, tempinternalOrderID, Parameters.symbol.get(id), side, notify, orderType, tradeSize, limitPrice, triggerPrice, getStrategy(), maxOrderDuration, EnumOrderStage.INIT, dynamicOrderDuration, maxSlippageExit, link, transmit, validity, scaleout, passToOrderObject, "", null);
     }
     }
     */
    //used by adr
//    public synchronized void exit (int id, EnumOrderSide side, EnumOrderType orderType, double limitPrice, double triggerPrice, boolean scaleout,boolean transmit, EnumOrderReason notify, String passToOrderObject,String validity, int size,int duration,int dynamicDuration,double slippage,EnumOrderStage stage,String effectiveTime) {
    public synchronized void exit(int id, EnumOrderSide side, int size, EnumOrderType orderType, double limitPrice, double triggerPrice, EnumOrderReason reason, EnumOrderStage stage, int duration, int dynamicDuration, double slippage, String orderGroup, String validity, String effectiveTime, boolean scaleout, boolean transmit) {
        int internalorderid = getInternalOrderID();
        logger.log(Level.INFO, "310,ExitOrder,{0},", new Object[]{getStrategy() + delimiter + internalorderid + delimiter + position.get(id).getPosition() + delimiter + Parameters.symbol.get(id).getLastPrice()});
        int tradeSize = scaleout == false ? Math.abs(getPosition().get(id).getPosition()) : size;
        double expectedFillPrice = 0;
        switch (reason) {
            case REGULARENTRY:
            case REGULAREXIT:
                if (side == EnumOrderSide.COVER) {
                    BeanPosition pd = getPosition().get(id);
                    expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
                    int symbolPosition = pd.getPosition() + tradeSize;
                    double positionPrice = symbolPosition == 0 ? 0D : Math.abs((expectedFillPrice * tradeSize + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                    pd.setPosition(symbolPosition);
                    pd.setPositionInitDate(TradingUtil.getAlgoDate());
                    pd.setPrice(positionPrice);
                    getPosition().put(id, pd);
                } else {
                    BeanPosition pd = getPosition().get(id);
                    expectedFillPrice = limitPrice != 0 ? limitPrice : Parameters.symbol.get(id).getLastPrice();
                    int symbolPosition = pd.getPosition() - tradeSize;
                    double positionPrice = symbolPosition == 0 ? 0D : Math.abs((-expectedFillPrice * tradeSize + pd.getPrice() * pd.getPosition()) / (symbolPosition));
                    pd.setPosition(symbolPosition);
                    pd.setPositionInitDate(TradingUtil.getAlgoDate());
                    pd.setPrice(positionPrice);
                    getPosition().put(id, pd);
                }
                break;
            case SL:

                break;
            case TP:

                break;
            default:
                break;
        }
        //int tempinternalOrderID = internalOpenOrders.get(id);
        int tempinternalOrderID = getFirstInternalOpenOrder(id, side, "Order");
        Trade tempTrade = getTrades().get(new OrderLink(tempinternalOrderID, 0, "Order"));
        if (tempTrade != null) {
            int exitSize = tempTrade.getExitSize();
            double exitPrice = tempTrade.getExitPrice();
            int newexitSize = exitSize + tradeSize;
            double newexitPrice = (exitPrice * exitSize + tradeSize * expectedFillPrice) / (newexitSize);
            tempTrade.updateExit(id, EnumOrderReason.REGULAREXIT, side, newexitPrice, newexitSize, internalorderid, 0, getTimeZone(), "Order");
            getTrades().put(new OrderLink(tempinternalOrderID, 0, "Order"), tempTrade);
            logger.log(Level.INFO,"Debugging_Strategy_exit,{0}",new Object[]{exitSize+delimiter+exitPrice+delimiter+size+delimiter+expectedFillPrice+delimiter+newexitSize+delimiter+newexitPrice});
            if (MainAlgorithm.isUseForTrading()) {
                oms.tes.fireOrderEvent(internalorderid, tempinternalOrderID, Parameters.symbol.get(id), side, reason, orderType, tradeSize, limitPrice, triggerPrice, getStrategy(), maxOrderDuration, EnumOrderStage.INIT, dynamicOrderDuration, maxSlippageExit, transmit, validity, scaleout, orderGroup, effectiveTime, null);
            }
        } else {
            logger.log(Level.INFO, "101,ExitInternalIDNotFound,{0}", new Object[]{id + delimiter + side + tempinternalOrderID});
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
        logger.log(Level.INFO,"LongOnly Set to {0}",new Object[]{l});
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
        logger.log(Level.INFO,"shortOnly Set to {0}",new Object[]{s});
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
     * @return the trades
     */
    public synchronized HashMap<OrderLink, Trade> getTrades() {
        return trades;
    }

    /**
     * @param trades the trades to set
     */
    public synchronized void setTrades(HashMap<OrderLink, Trade> trades) {
        this.trades = trades;
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
        return ++internalOrderID;
    }

    /**
     * @param internalOrderID the internalOrderID to set
     */
    public void setInternalOrderID(int internalOrderID) {
        this.internalOrderID = internalOrderID;
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
}