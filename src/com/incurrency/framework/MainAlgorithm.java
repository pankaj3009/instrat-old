/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.RatesClient.RequestClient;
import com.incurrency.RatesClient.SocketListener;
import com.incurrency.adr.ADRPublisher;
import static com.incurrency.framework.Algorithm.globalProperties;
import com.verhas.licensor.License;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 *
 * @author admin
 */
public class MainAlgorithm extends Algorithm {

    private static HashMap<String, String> input = new HashMap();
    public final static Logger logger = Logger.getLogger(MainAlgorithm.class.getName());
    private static final Object lockInput = new Object();
    private ADRPublisher paramADR;
    public static JFrame ui;
    private Date preopenDate;
    private static Date startDate;
    private static Date closeDate;
    Timer preopen;
    public static Boolean preOpenCompleted = false;
    public static List<String> strategies = new ArrayList();
    private List<Double> maxPNL = new ArrayList();
    private List<Double> minPNL = new ArrayList();
    private String historicalData;
    private String realTimeBars;
    private static boolean collectTicks;
    private boolean tradingAlgoInitialized = false;
    public static SocketListener socketListener;
    public static ArrayList<Boolean> contractIdAvailable = new ArrayList();
    private RequestClient requestClient;
    public static boolean contractDetailsCompleted = false;
    private boolean duplicateAccounts = false;
    public static ArrayList<Strategy> strategyInstances = new ArrayList<>();
    private License lic = null;
    public static ArrayList<String[]> comboList = new ArrayList<>();
    public static TradingEventSupport tes = new TradingEventSupport();
    private String version = "1.03B-20140826";
    public static boolean instantiated = false;
    private static MainAlgorithm instance = null;
    public static String backtestStartDate;
    public static String backtestEndDate;
    public static String backtestCurrentDate;
    public static String backtestCloseReferenceDate;
    public static String backtestBarSize;
    public static ArrayList<BackTestParameter> backtestParameters = new ArrayList<>();
    public static ArrayList<BackTestFileMap> fileMap = new ArrayList<>();
    private static String backtestOrderFile = null;
    private static int backtestFileCount = 1;
    private volatile static Date algoDate = null;
    private static final Object lockUseForTrading = new Object();
    public static int selectedStrategy = 0;
    /*
     * EOD Validation Fixed
     * Deemed cancellations wes a string arraylist. Changed this to <Integer>
     * Stubs were not creating correct side of child orders. Fixed by introducing a new method in TWSConnection - switchSide
     * EOD reporting now includes open positions
     */
    private final String delimiter = "_";

    protected MainAlgorithm(HashMap<String, String> args) throws Exception {
        super(args); //this initializes the connection and symbols
        input = args;
        logStartupData();
        if (useForTrading) {
            connectToTWS();
            getContractInformation();
            subscribeMarketData();
            Timer keepAlive = new Timer("Timer: Maintain IB Connection");
            keepAlive.schedule(keepConnectionAlive, new Date(), 60 * 1000);
        } else if (Boolean.parseBoolean(globalProperties.getProperty("backtest", "false").toString().trim())) {
            runBackTest();
        }else if(Boolean.parseBoolean(globalProperties.getProperty("connectionfileneeded", "false").toString().trim())){
            connectToTWS();
        }
        collectTicks = Boolean.parseBoolean(globalProperties.getProperty("collectticks", "false").toString().trim());
    }

    private void logStartupData() {
        String concatInput = new String();
        for (Map.Entry<String, String> value : input.entrySet()) {
            String temp = value.getKey() + "=" + value.getValue();
            if (concatInput.equals("")) {
                concatInput = temp;
            } else {
                concatInput = concatInput + " " + temp;
            }
        }
        logger.log(Level.INFO, "100,inStratVersion,{0}", new Object[]{version});
        logger.log(Level.INFO, "100,inStratInputParameters,{0}", new Object[]{concatInput});
    }

    private void connectToTWS() {
        for (BeanConnection c : Parameters.connection) {
            c.setWrapper(new TWSConnection(c));
        }
        int connectioncount = 1;
        ArrayList<BeanConnection> notConnected = new ArrayList();
        //TradingUtil.logProperties();
        for (BeanConnection c : Parameters.connection) {
            logger.log(Level.INFO, "100,ConnectionParameters, IP_{0} ", new Object[]{connectioncount + delimiter + Parameters.connection.get(connectioncount - 1).getIp()});
            logger.log(Level.INFO, "100,ConnectionParameters, Port_{0} ", new Object[]{connectioncount + delimiter + Parameters.connection.get(connectioncount - 1).getPort()});
            logger.log(Level.INFO, "100,ConnectionParameters, ClientID_{0} ", new Object[]{connectioncount + delimiter + Parameters.connection.get(connectioncount - 1).getClientID()});
            logger.log(Level.INFO, "100,ConnectionParameters, Strategy_{0} ", new Object[]{connectioncount + delimiter + Parameters.connection.get(connectioncount - 1).getStrategy()});
            logger.log(Level.INFO, "100,ConnectionParameters, Purpose_{0} ", new Object[]{connectioncount + delimiter + Parameters.connection.get(connectioncount - 1).getPurpose()});
            logger.log(Level.INFO, "100,ConnectionParameters, # RealTime Market Data_{0} ", new Object[]{connectioncount + delimiter + Parameters.connection.get(connectioncount - 1).getTickersLimit()});
            logger.log(Level.INFO, "100,ConnectionParameters, Pause in seconds between historical data_{0} ", new Object[]{connectioncount + delimiter + Parameters.connection.get(connectioncount - 1).getHistMessageLimit()});
            logger.log(Level.INFO, "100,ConnectionParameters, Messages per second_{0} ", new Object[]{connectioncount + delimiter + Parameters.connection.get(connectioncount - 1).getRtMessageLimit()});
            logger.log(Level.INFO, "100,ConnectionParameters, Orders Per 2 minutes for triggering system halt_{0} ", new Object[]{connectioncount + delimiter + Parameters.connection.get(connectioncount - 1).getOrdersHaltTrading()});
            logger.log(Level.INFO, "100,ConnectionParameters, Owner Email_{0} ", new Object[]{connectioncount + delimiter + Parameters.connection.get(connectioncount - 1).getOwnerEmail()});
            c.getWrapper().connectToTWS();
            connectioncount = connectioncount + 1;
            //wait for connection
            if (!c.getWrapper().eClientSocket.isConnected()) {
                notConnected.add(c);
            }
        }

        //remove redunant connections
        for (BeanConnection c : notConnected) {
            logger.log(Level.SEVERE, "100, ConnectionRemoved,{0}", new Object[]{c.getIp() + delimiter + c.getPort()});
            Parameters.connection.remove(c);
        }

        //Synchronize clocks
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().eClientSocket.reqCurrentTime();
        }

        //check license
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().getAccountUpdates();
            c.setAccountName(c.getWrapper().getAccountIDSync().take());
            //logger.log(Level.INFO,"101,AccountName,{0}",new Object[]{c.getAccountName()});
        }

        for (BeanConnection c : Parameters.connection) {
            System.out.print("Going to cancel account updated");
            c.getWrapper().cancelAccountUpdates();
            System.out.println("Account updates cancelled");
        }

        //Confirm no account duplicates exist before proceeding further
        for (int i = 0; i < Parameters.connection.size(); i++) {
            String account = Parameters.connection.get(i).getAccountName();
            for (int j = i + 1; j < Parameters.connection.size(); j++) {
                if (Parameters.connection.get(j).getAccountName().equals(account) && !this.duplicateAccounts) {
                    System.out.println("Duplicate account " + account + " specified in connection file. Please ensure that the connection file does not have duplicates and start program again.  ");
                    if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                        JOptionPane.showMessageDialog(null, "Duplicate account " + account + " specified in connection file. Please ensure that the connection file does not have duplicates and start program again.");
                    }
                    this.duplicateAccounts = true;
                }
            }
        }

        if (!TradingUtil.checkLicense()) {
            if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                //Launch.setMessage("No License. If you are only executing on IB paper accounts, please register. If you have a real account setup for trading, please contact support@incurrency.com");
            }
            logger.log(Level.INFO, "100,License Check Failed");
        }
    }

    private void getContractInformation() throws InterruptedException {
        ArrayList<RequestClient> arrRequestClient = new ArrayList<>();
        if (TradingUtil.checkLicense() && !duplicateAccounts) {
            int threadCount = Math.max(1, Parameters.symbol.size() / 100 + 1); //max 100 symbols per thread
            if (globalProperties.getProperty("datasource") != null) {
                for (int i = 0; i < threadCount; i++) {
                    String dataSource = globalProperties.getProperty("datasource").toString().trim();
                    String requestPort = globalProperties.getProperty("requestport", "5555").toString().trim();
                    Thread t = new Thread(requestClient = new RequestClient(dataSource + ":" + requestPort));//5555 is the port where pubsub accepts request
                    arrRequestClient.add(requestClient);
                    t.setName("DataRequester:" + i);
                    t.start();
                }
                int j = 0;
                for (BeanSymbol s : Parameters.symbol) {
                    j = j < threadCount ? j : 0;
                    while (!RequestClient.isAvailableForNewRequest()) {
                        Thread.sleep(100);
                    }
                    arrRequestClient.get(j).sendRequest("contractid", s, null, null, null, false);
                    if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                        //   Launch.setMessage("Retrieving contract infomation for symbol: " + argument);
                    }
                    j = j + 1;

                }
                boolean complete = false;
                while (!complete) {
                    Thread.sleep(1000);
                    Thread.yield();
                    complete = true;
                    for (RequestClient r : arrRequestClient) {
                        complete = complete && RequestClient.isAvailableForNewRequest();
                    }
                }
                //populate contracts with missing contract id - to be deleted
                for (BeanSymbol s : Parameters.symbol) {
                    if (s.getContractID() > 0 || s.getType().equals("IND")) {
                        contractIdAvailable.add(true);
                    } else {
                        contractIdAvailable.add(false);
                    }

                }
            } else { //no datasource. Get info from IB TWS directly
                BeanConnection tempC = Parameters.connection.get(0);
                List<BeanSymbol> optionsRequiringATMStrike = new ArrayList();
                List<BeanSymbol> underlyingRequiringClosePrice = new ArrayList();
                //create a seperate list of symbols that need ATM strike
                for (BeanSymbol s : Parameters.symbol) {
                    if (s.getType().compareTo("OPT") == 0 && s.getOption() == null) {
                        optionsRequiringATMStrike.add(s);
                        int id = TradingUtil.getIDFromSymbol(s.getSymbol(), "STK", "", "", "") >= 0 ? TradingUtil.getIDFromSymbol(s.getSymbol(), "STK", "", "", "") : TradingUtil.getIDFromSymbol(s.getSymbol(), "IND", "", "", "");
                        if (id >= 0) {
                            underlyingRequiringClosePrice.add(Parameters.symbol.get(id));
                            //tempC.getWrapper().requestSingleSnapshot(Parameters.symbol.get(id));
                        } else {
                            logger.log(Level.INFO, "101,NoATMStrike,{0}", new Object[]{s.getSymbol()});
                        }
                    }
                }

                //Request snapshot data for underlying symbols
                if (underlyingRequiringClosePrice.size() > 0) {
                    Thread t = new Thread(new MarketData(Parameters.connection.get(0), underlyingRequiringClosePrice, 0, true, true));
                    //logger.log(Level.INFO, ",,Creator,Requesting one time snapshot");
                    t.setName("onetime snapshot");
                    t.start();
                    t.join();
                }


                for (BeanSymbol s : optionsRequiringATMStrike) {
                    tempC.getWrapper().getContractDetails(s, "");
                    System.out.print("ContractDetails Requested:" + s.getSymbol());
                }

                while (TWSConnection.mTotalATMChecks > 0) {
                    //System.out.println(TWSConnection.mTotalSymbols);
                    //do nothing
                    if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                        //Launch.setMessage("Waiting for contract information to be retrieved for ATM estimation");
                    }
                }

                //update strikes in Parameters.symbols
                for (BeanSymbol s : optionsRequiringATMStrike) {
                    int optionid = s.getSerialno() - 1;
                    int underlyingid = TradingUtil.getIDFromSymbol(s.getSymbol(), "STK", "", "", "") >= 0 ? TradingUtil.getIDFromSymbol(s.getSymbol(), "STK", "", "", "") : TradingUtil.getIDFromSymbol(s.getSymbol(), "IND", "", "", "");
                    Parameters.symbol.get(optionid).setOption(String.valueOf(Parameters.symbol.get(underlyingid).getAtmStrike()));
                }

                for (BeanSymbol s : Parameters.symbol) {
                    tempC.getWrapper().getContractDetails(s, "");
                    System.out.print("ContractDetails Requested:" + s.getSymbol());
                }

                while (TWSConnection.mTotalSymbols > 0) {
                    //System.out.println(TWSConnection.mTotalSymbols);
                    //do nothing
                    if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                        //Launch.setMessage("Waiting for contract information to be retrieved");
                    }
                }


                for (BeanSymbol s : Parameters.symbol) {
                    while (s.isStatus() == null) {
                        Thread.sleep(1000);
                        logger.log(Level.FINE, "307,AwaitingContractDetails,{0}", new Object[]{s.getDisplayname()});
                        if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                            //Launch.setMessage("Waiting for contract details for " + s.getSymbol());
                        }
                    }
                    contractIdAvailable.add(s.isStatus());
                }
            }
            //Add combos
            for (String[] input : comboList) {
                BeanSymbol s = new BeanSymbol(input[1], input[2], input[13]);
                if (!s.isComboSetupFailed()) {
                    contractIdAvailable.add(true);
                    Parameters.symbol.add(s);
                }
            }


            Iterator<BeanSymbol> symbolitr = Parameters.symbol.iterator();
            Iterator<Boolean> contractReceived = contractIdAvailable.iterator();
            int rowcount = 1;
            while (symbolitr.hasNext()) {
                BeanSymbol s = symbolitr.next(); // must be called before you can call i.remove()
                Boolean received = contractReceived.next();
                if (!received && !(s.getType().equals("IND") || s.getType().equals("COMBO")) || (s.getType().equals("COMBO") && s.isComboSetupFailed())) {
                    logger.log(Level.FINE, "103,ContractDetailsNotReceived,{0}", new Object[]{s.getDisplayname()});
                    symbolitr.remove();
                } else {
                    s.setSerialno(rowcount);
                    rowcount = rowcount + 1;
                }
            }
            //logger.log(Level.INFO, ",,Creator,Contract Information Retrieved");
            contractDetailsCompleted = true;
            if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                //Launch.setMessage("Contract Information Retrieved");
            }
        }
    }

    private void subscribeMarketData() {
        if (globalProperties.getProperty("datasource") != null) { //use jeromq connector
            String dataSource = globalProperties.getProperty("datasource").toString().trim();
            String pubsubPort = globalProperties.getProperty("pubsubport", "5556").toString().trim();
            String topic = globalProperties.getProperty("topic", "INR").toString().trim();
            Thread t = new Thread(socketListener = new SocketListener(dataSource, pubsubPort, topic));//5556 is where pubsub posts streaming data
            t.setName("SocketListener");
            t.start();
            //update symbols with calculated connectionid to be used for real time bars
            Collections.sort(Parameters.symbol, new BeanSymbolCompare());
            int symbolcount = 0;
            int connectionid = -1;
            for (BeanConnection c : Parameters.connection) {
                int startingConnectionCount = symbolcount;
                connectionid = connectionid + 1;
                for (int i = symbolcount; i < startingConnectionCount + c.getTickersLimit() && i < Parameters.symbol.size(); i++) {
                    Parameters.symbol.get(i).setConnectionidUsedForMarketData(connectionid);
                    symbolcount = i;
                }
            }
            if (symbolcount < Parameters.symbol.size() - 1) {
                for (int i = symbolcount; i < Parameters.symbol.size(); i++) {
                    Parameters.symbol.get(i).setConnectionidUsedForMarketData(-1);
                }
            }

        }
        //Request Market Data
        Collections.sort(Parameters.symbol, new BeanSymbolCompare()); //sorts symbols in order of preference streaming priority. low priority is higher
        int serialno = 1;
        for (BeanSymbol s : Parameters.symbol) {
            s.setSerialno(serialno);
            serialno = serialno + 1;
        }
        if (TradingUtil.checkLicense() && !duplicateAccounts) {
            if (globalProperties.getProperty("datasource") == null) { //use IB for market data
                int count = Parameters.symbol.size();
                int allocatedCapacity = 0;
                for (BeanConnection c : Parameters.connection) {
                    //if ("Data".equals(c.getPurpose())) {
                    int connectionCapacity = c.getTickersLimit();
                    if (count > 0) {
                        Thread t = new Thread(new MarketData(c, allocatedCapacity, Math.min(count, connectionCapacity), Parameters.symbol, c.getTickersLimit(), false));
                        t.setName("Streaming Market Data");
                        t.start();
                        allocatedCapacity = allocatedCapacity + Math.min(count, connectionCapacity);
                        count = count - Math.min(count, connectionCapacity);
                    }
                }

                boolean getsnapshotfromallconnections = Boolean.parseBoolean(globalProperties.getProperty("getsnapshotfromallconnections", "false"));
                //If there are symbols left, request snapshot. Distribute across tradingAccounts
                if (getsnapshotfromallconnections) {
                    for (BeanConnection c : Parameters.connection) {
                        int snapshotcount = count / Parameters.connection.size();
                        Thread t = new Thread(new MarketData(c, allocatedCapacity, snapshotcount, Parameters.symbol, c.getTickersLimit(), true));
                        t.setName("Continuous Snapshot");
                        t.start();
                    }
                } //Alternatively, we use 1st connection for snapshot. Make sure it has the number of symbols permitted as zero
                else {
                    if (count > 0) {
                        int snapshotcount = count;;
                        Thread t = new Thread(new MarketData(Parameters.connection.get(0), allocatedCapacity, snapshotcount, Parameters.symbol, Parameters.connection.get(0).getTickersLimit(), true));
                        t.setName("Continuous Snapshot");
                        t.start();
                    }
                }
            }
        }
    }
    TimerTask closeAlgorithms = new TimerTask() {
        @Override
        public void run() {
            logger.log(Level.INFO, "100, inStratShutdown,{0}", new Object[]{closeDate});
            System.exit(0);
        }
    };
    TimerTask keepConnectionAlive = new TimerTask() {
        @Override
        public void run() {
            for (BeanConnection c : Parameters.connection) {
                if (!c.getWrapper().isAlive()) {
                    c.getWrapper().connectToTWS();
                }
            }
        }
    };

    private void runBackTest() throws ParseException, InterruptedException {
        String backtestVariationFile = globalProperties.getProperty("backtestvariationfile");
        if (backtestVariationFile != null) {
            backtestVariationFile = backtestVariationFile.toString().trim();
            loadBackTestParameters(backtestVariationFile);
        }
        Collections.sort(Parameters.symbol, new BeanSymbolCompare()); //sorts symbols in order of preference streaming priority. low priority is higher
        int serialno = 1;
        for (BeanSymbol s : Parameters.symbol) {
            s.setSerialno(serialno);
            serialno = serialno + 1;
        }
        //backtesting using historical data
        String dataSource = globalProperties.getProperty("datasource").toString().trim();
        String pubsubPort = globalProperties.getProperty("pubsubport", "5556").toString().trim();
        String topic = globalProperties.getProperty("topic", "INR").toString().trim();
        Thread t = new Thread(socketListener = new SocketListener(dataSource, pubsubPort, topic));
        t.setName("SocketListener");
        t.start();
        //Get Start Date of BackTest
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        ArrayList<RequestClient> requestClientList = new ArrayList<>();
        //Request Historical Data
        if (globalProperties.getProperty("datasource") != null) {
            String requestPort = globalProperties.getProperty("requestport", "5555").toString().trim();
            Thread t1 = new Thread(requestClient = new RequestClient(dataSource + ":" + requestPort));
            //requestClientList.add(requestClient);
            t1.setName("DataRequester");
            t1.start();
            for (BeanSymbol s : Parameters.symbol) {
                String symbol = "";
                String[] expiries = null;
                switch (s.getType()) {
                    case "STK":
                        symbol = s.getDisplayname();
                        break;
                    case "IND":
                        symbol = s.getDisplayname() + "_" + s.getType();
                        break;
                    case "FUT":
                        expiries = s.getExpiry().split(":");
                        symbol = s.getDisplayname() + "_" + s.getType() + "_" + s.getExpiry();
                        break;
                    case "OPT":
                        symbol = s.getDisplayname() + "_" + s.getType() + "_" + s.getExpiry() + "_" + s.getRight() + "_" + s.getOption();
                    default:
                        break;
                }
                while (!RequestClient.isAvailableForNewRequest()) {
                    Thread.sleep(100);
                }

                if (expiries != null) {
                    for (String exp : expiries) {
                        symbol = s.getDisplayname() + "_" + s.getType() + "_" + exp;
                        requestClient.sendRequest("historicaldata", s, new String[]{backtestStartDate, backtestEndDate, backtestCloseReferenceDate, backtestBarSize}, null, null, false);
                        logger.log(Level.INFO, "100,HistoricalRequestSent,{0}", new Object[]{symbol});
                        boolean complete = false;
                        while (!complete) {
                            Thread.sleep(100);
                            Thread.yield();
                            complete = true;
                            complete = complete && RequestClient.isAvailableForNewRequest();

                        }
                    }
                    s.setExpiry(expiries[0]);
                } else {
                    requestClient.sendRequest("historicaldata", s, new String[]{backtestStartDate, backtestEndDate, backtestCloseReferenceDate, backtestBarSize}, null, null, false);
                    logger.log(Level.INFO, "100,HistoricalRequestSent,{0}", new Object[]{symbol});
                    boolean complete = false;
                    while (!complete) {
                        Thread.sleep(100);
                        Thread.yield();
                        complete = true;
                        complete = complete && RequestClient.isAvailableForNewRequest();
                    }
                }
            }
            //send finished argument so that MDS can start publishing
            //Below line needs to be fixed as "finished" string can no longer be sent to Requestclient
//            requestClient.sendRequest("historicaldata", "finished", new String[]{},null,null,false);
        }
    }

    private void loadBackTestParameters(String parameterFile) throws ParseException {
        Properties p = TradingUtil.loadParameters(parameterFile);
        Enumeration em = p.keys();
        while (em.hasMoreElements()) {
            String str = em.nextElement().toString();
            logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{str + delimiter + p.getProperty(str)});
            switch (str) {
                case "TimeZone":
                    timeZone = (p.getProperty(str) == null ? "Asia/Kolkata" : p.getProperty(str));
                    break;
                case "BackTestStartDate":
                    backtestStartDate = p.getProperty(str);
                    break;
                case "BackTestEndDate":
                    backtestEndDate = p.getProperty(str);
                    break;
                case "BackTestCloseReferenceDate":
                    backtestCloseReferenceDate = p.getProperty(str);
                    break;
                case "BackTestBarSize":
                    backtestBarSize = p.getProperty(str);
                    break;
                default:
                    String parameter = str;
                    String startRange = p.getProperty(str).split(",")[0];
                    String endRange = p.getProperty(str).split(",")[1];
                    String increment = p.getProperty(str).split(",")[2];
                    backtestParameters.add(new BackTestParameter(parameter, startRange, endRange, increment));
                    break;
            }
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        startDate = sdf.parse(backtestStartDate);
    }

   private void loadBackTestStrategies(ArrayList<ArrayList<String>> parameterList, Constructor constructor, Properties p, String parameterFile, ArrayList<String> tradingAccounts, int n, ArrayList<String> prefix) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (backtestOrderFile == null) {
            backtestOrderFile = p.getProperty("OrderFile");
        }
        if (n >= backtestParameters.size()) {
            int i = 0;
            for (String o : prefix) {
                p.setProperty(backtestParameters.get(i).parameter, o);
            }
            /*
             p.setProperty("OrderFile", backtestOrderFile.split("\\.")[0] + backtestFileCount + "." + backtestOrderFile.split("\\.")[1]);
             BackTestFileMap temp = new BackTestFileMap(p.getProperty("OrderFile"));
             for (BackTestParameter b : backtestParameters) {
             temp.peturbedParameters.add(new BackTestParameter(b.parameter, p.getProperty(b.parameter)));
             }
             fileMap.add(temp);
             */
            strategyInstances.add((Strategy) constructor.newInstance(this, p, parameterFile, tradingAccounts, backtestFileCount));
            String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
            String strategyName = tempStrategyArray[tempStrategyArray.length - 1] + backtestFileCount;
            strategies.add(strategyName);
            backtestFileCount = backtestFileCount + 1;
            return;
        }
        for (String o : parameterList.get(n)) {
            prefix.add(o);
            //newPrefix[newPrefix.length-1] = o;
            loadBackTestStrategies(parameterList, constructor, p, parameterFile, tradingAccounts, n + 1, prefix);
        }
    }

    public void postInit() {
        if (strategyInstances.isEmpty()) {
            strategies.add("NoStrategy");
        }
        for (int i = 0; i < strategyInstances.size(); i++) {
            minPNL.add(0D);
            maxPNL.add(0D);
        }
        //set close timer after all licensedStrategies have been initialized. This ensures we get the futhest closeDate
        Timer closeProcessing = new Timer("Timer: Close Algorithm");
        if (!(MainAlgorithm.strategies.contains("NoStrategy") || !MainAlgorithm.useForTrading)) {
            closeProcessing.schedule(closeAlgorithms, closeDate);
        }
        if (MainAlgorithm.isUseForTrading()) {
            if (TradingUtil.checkLicense() && !Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
                ui = new com.incurrency.framework.display.DashBoardNew(); //Display main UI
            }
        }
        instantiated = true;
    }
    
    /**
     * Registers a strategy with inStrat.Strategy is the fully qualified classname of a strategy package.
     * @param strategy
     * @param algorithm
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     * @throws ClassNotFoundException
     * @throws InstantiationException 
     */
     public void registerStrategy(String strategy) throws NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClassNotFoundException, InstantiationException {
        HashMap<String, ArrayList<String>> initValues = strategyInitValues(strategy);
        boolean trading=Boolean.parseBoolean(globalProperties.getProperty("trading", "false").toString().trim());
        boolean backtest=Boolean.parseBoolean(globalProperties.getProperty("backtest", "false").toString().trim());
        for (Map.Entry<String, ArrayList<String>> entry : initValues.entrySet()) {
            String parameterFile = entry.getKey();
            ArrayList<String> tradingAccounts = entry.getValue();
            Class[] arg;
            if(backtest==true||trading==true){
                arg = new Class[5];
            arg[0] = MainAlgorithm.class;
            arg[1] = Properties.class;
            arg[2] = String.class;
            arg[3] = ArrayList.class;
            arg[4] = Integer.class;
            }else{
                    arg = new Class[1];
            arg[0] = String.class;
            }
            Constructor constructor = Class.forName(strategy).getConstructor(arg);
            Properties p = TradingUtil.loadParameters(parameterFile);
            if (useForTrading) {
                strategyInstances.add((Strategy) constructor.newInstance(this, p, parameterFile, tradingAccounts, null));
                String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
                String strategyName = tempStrategyArray[tempStrategyArray.length - 1];
                strategies.add(strategyName);
            } else if(Boolean.parseBoolean(globalProperties.getProperty("backtest","false"))){
                ArrayList<ArrayList<String>> parameterList = new ArrayList<>();
                for (BackTestParameter b : backtestParameters) {
                    ArrayList<String> s = new ArrayList<>();
                    for (double i = Double.parseDouble(b.startRange); i <= Double.parseDouble(b.endRange); i = i + Double.parseDouble(b.increment)) {
                        s.add(String.valueOf(i));
                    }
                    parameterList.add(s);
                }
                loadBackTestStrategies(parameterList, constructor, p, parameterFile, tradingAccounts, 0, new ArrayList<String>());
            }else{ //this is a strategy outside trading, like historical data, market data, scanner etc. 
                //trading=false, backtest=false
               constructor.newInstance(parameterFile);             
            }
        }
        this.tradingAlgoInitialized = true;
    }

    /**
     * Returns a hashmap.Key=Parameter File name, Values=Accounts that will use the file
     * @param strategy
     * @return 
     */
    public HashMap<String, ArrayList<String>> strategyInitValues(String strategy) {

        int l = strategy.split("\\.").length;
        strategy = strategy.split("\\.")[l - 1].toLowerCase(); //strategy is named as the second last part of the extended class name.
        HashMap<String, ArrayList<String>> out = new HashMap<>();
        String argValues = input.get(strategy);
        ArrayList<String> allAccountNames = new ArrayList<>();
        ArrayList<String> allocAccountNames = new ArrayList<>();
        if (isUseForTrading()) {
            for (BeanConnection c : Parameters.connection) {
                if (c.getPurpose().equals("Trading") && c.getStrategy().toLowerCase().contains(strategy.toLowerCase())) {
                    allAccountNames.add(c.getAccountName()); //get list of all accounts that will trade this strategy
                }
            }
        } else {
            allAccountNames.add("Test");
        }
        //file to be setup as
        //U72311-DU12345-inradr2.properties,DU24321-inradr1.properties, inradr.properties
        String[] instanceFile = argValues.split(",");
        //here instance file will have length=3
        for (int i = 0; i < instanceFile.length; i++) {
            String[] instanceParameters = instanceFile[i].split("-");
            //here instance parameters will have length=3,2 and 1
            ArrayList<String> subAccountNames = new ArrayList<>();
            if (instanceParameters.length == 1) {
                //add all tradingAccounts that are not used.
                for (String accountName : allAccountNames) {
                    if (!allocAccountNames.contains(accountName.toUpperCase())) {
                        subAccountNames.add(accountName.toUpperCase());
                    }
                }
            } else {
                for (int j = 0; j < instanceParameters.length - 1; j++) {
                    String account = instanceParameters[j].toUpperCase();
                    if (allAccountNames.contains(account)) {
                        subAccountNames.add(instanceParameters[j].toUpperCase());
                        allocAccountNames.add(instanceParameters[j].toUpperCase());
                        logger.log(Level.INFO, "100,StrategyAdded,{0}", new Object[]{strategy + delimiter + instanceParameters[j].toUpperCase()});
                    }
                }
            }
            out.put(instanceFile[i], subAccountNames);
            //[U72311-DU12345-inradr2.properties,<U72311,DU12345>]
        }
        return out;
    }

    public static MainAlgorithm getInstance(HashMap<String, String> args) throws Exception {
        if (instance == null) {
            instance = new MainAlgorithm(args);
        }
        if (MainAlgorithm.instantiated) {
            return instance;
        } else {
            return null;
        }
    }

    /*
     * ***************************************************************************************
     * **************************** GETTER / SETTER ******************************************
     * ***************************************************************************************
     */
    /**
     * @return the preopenDate
     */
    public Date getPreopenDate() {
        return preopenDate;
    }

    /**
     * @return the licensedStrategies
     */
    public List<String> getStrategies() {
        return strategies;
    }

    /**
     * @param licensedStrategies the licensedStrategies to set
     */
    public void setStrategies(List<String> strategies) {
        MainAlgorithm.strategies = strategies;
    }

    /**
     * @return the maxPNL
     */
    public List<Double> getMaxPNL() {
        return maxPNL;
    }

    /**
     * @param maxPNL the maxPNL to set
     */
    public void setMaxPNL(List<Double> maxPNL) {
        this.maxPNL = maxPNL;
    }

    /**
     * @return the minPNL
     */
    public List<Double> getMinPNL() {
        return minPNL;
    }

    /**
     * @param minPNL the minPNL to set
     */
    public void setMinPNL(List<Double> minPNL) {
        this.minPNL = minPNL;
    }

    /**
     * @return the startDate
     */
    public static Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(Date startDate) {
        MainAlgorithm.startDate = startDate;
    }

    /**
     * @return the historicalData
     */
    public String getHistoricalData() {
        return historicalData;
    }

    /**
     * @param historicalData the historicalData to set
     */
    public void setHistoricalData(String historicalData) {
        this.historicalData = historicalData;
    }

    /**
     * @return the realTimeBars
     */
    public String getRealTimeBars() {
        return realTimeBars;
    }

    /**
     * @param realTimeBars the realTimeBars to set
     */
    public void setRealTimeBars(String realTimeBars) {
        this.realTimeBars = realTimeBars;
    }

    /**
     * @return the closeDate
     */
    public Date getCloseDate() {
        return closeDate;
    }

    /**
     * @param date the date to set
     */
    public static void setCloseDate(Date date) {
        if (closeDate == null) {
            closeDate = date;
        } else if (closeDate.compareTo(date) < 0) {
            closeDate = date;
        }
        logger.log(Level.INFO, "100,inStratShutdown,{0}", new Object[]{closeDate.toString()});
    }

    /**
     * @return the collectTicks
     */
    public static boolean getCollectTicks() {
        return collectTicks;
    }

    /**
     * @param aCollectTicks the collectTicks to set
     */
    public static void setCollectTicks(boolean aCollectTicks) {
        collectTicks = aCollectTicks;
    }

    /**
     * @return the tradingAlgoInitialized
     */
    public boolean isTradingAlgoInitialized() {
        return tradingAlgoInitialized;
    }

    /**
     * @param tradingAlgoInitialized the tradingAlgoInitialized to set
     */
    public void setTradingAlgoInitialized(boolean tradingAlgoInitialized) {
        this.tradingAlgoInitialized = tradingAlgoInitialized;
    }

    /**
     * @return the paramADR
     */
    public ADRPublisher getParamADR() {
        return paramADR;
    }

    /**
     * @param paramADR the paramADR to set
     */
    public void setParamADR(ADRPublisher paramADR) {
        this.paramADR = paramADR;
    }

    /**
     * @return the strategyInstances
     */
    public ArrayList<Strategy> getStrategyInstances() {
        return strategyInstances;
    }

    /**
     * @param strategyInstances the strategyInstances to set
     */
    public void setStrategyInstances(ArrayList<Strategy> strategyInstances) {
        this.strategyInstances = strategyInstances;
    }

    public static MainAlgorithm getInstance() {
        return instance;
    }

    /**
     * @return the getAlgoDate
     */
    public static synchronized Date getAlgoDate() {
        return MainAlgorithm.algoDate;
    }

    /**
     * @param getAlgoDate the getAlgoDate to set
     */
    public static synchronized void setAlgoDate(Date algoDate) {
        MainAlgorithm.algoDate = algoDate;
    }

    /**
     * @return the useForTrading
     */
    public static boolean isUseForTrading() {
        synchronized (lockUseForTrading) {
            return Algorithm.useForTrading;
        }
    }

    /**
     * @param aUseForTrading the useForTrading to set
     */
    public static void setUseForTrading(boolean aUseForTrading) {
        synchronized (lockUseForTrading) {
            Algorithm.useForTrading = aUseForTrading;
        }
    }
}