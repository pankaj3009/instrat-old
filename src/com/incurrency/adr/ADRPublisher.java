/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.adr;

import com.incurrency.RatesClient.Subscribe;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Strategy;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import com.incurrency.framework.TradingUtil;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Generates ADRPublisher, Tick , TRIN initialise ADRPublisher passing the adrSymbols that need to
 * be tracked. ADRPublisher will immediately initiate polling market data in snapshot
 * mode. Streaming mode is currently not supported output is available via
 * static fields Advances, Declines, Tick Advances, Tick Declines, Advancing
 * Volume, Declining Volume, Tick Advancing Volume, Tick Declining Volume
 *
 * @author pankaj
 */
public class ADRPublisher extends Strategy implements TradeListener, UpdateListener {

    public ADRPublisherEventProcessor mEsperEvtProcessor = null;
    static com.incurrency.framework.rateserver.RateServer adrServer = new com.incurrency.framework.rateserver.RateServer(5557);
    private static final Logger logger = Logger.getLogger(ADRPublisher.class.getName());
    private final String delimiter = "_";
    //----- updated by ADRListener and TickListener, were earlier static methods
    public double adr;
    public double adrTRIN;
    public double tick;
    public double tickTRIN;
    public double adrDayHigh = Double.MIN_VALUE;
    public double adrDayLow = Double.MAX_VALUE;
//----- updated by method updateListener    
    double adrHigh;
    double adrLow;
    public double adrAvg;
    double adrTRINHigh;
    double adrTRINLow;
    public double adrTRINAvg;
    double tickHigh;
    double tickLow;
    double tickAvg;
    double tickTRINHigh;
    double tickTRINLow;
    double tickTRINAvg;
    double indexHigh;
    double indexLow;
    double indexAvg;
    double indexDayHigh = Double.MIN_VALUE;
    double indexDayLow = Double.MAX_VALUE;
    int tradingSide = 0;
    private Boolean trading;
    private String index;
    private String type;
    private String expiry;
    public int threshold;
    private double stopLoss;
    String window;
    private double windowHurdle;
    private double dayHurdle;
    private double takeProfit;
    private boolean scalpingMode;
    private double reentryMinimumMove;
    private double entryPrice;
    private double lastLongExit;
    private double lastShortExit;
    private String adrRuleName;
    HashMap<Integer,Boolean> closePriceReceived=new HashMap<>();
    
    public ADRPublisher(MainAlgorithm m, Properties p,String parameterFile, ArrayList<String> accounts) {
        super(m, "pankaj", "FUT", p,parameterFile, accounts,null);
        loadParameters("adr", parameterFile);
        TradingUtil.writeToFile("ADR.csv", "adr" + "," + "adrTRIN" + "," + "tick" + "," + "tickTRIN" + "," + "price" + "," + "adrHigh" + "," + "adrLow" + "," + "adrAvg" + "," + "adrTRINHigh" + "," + "adrTRINLow" + "," + "adrTRINAvg" + "," + "indexHigh" + "," + "indexLow" + "," + "indexAvg" + "," + "indexDayHigh" + "," + "indexDayLow");
        mEsperEvtProcessor = new ADRPublisherEventProcessor(this);
        mEsperEvtProcessor.ADRStatement.addListener(this);
        String[] tempStrategyArray = parameterFile.split("\\.")[0].split("-");
        getStrategySymbols().clear();
        for (BeanSymbol s : Parameters.symbol) {
           if (Pattern.compile(Pattern.quote(adrRuleName), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                 getStrategySymbols().add(s.getSerialno() - 1);
                 closePriceReceived.put(s.getSerialno()-1,Boolean.FALSE);
            }
        }
         for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addTradeListener(this);
            c.getWrapper().removeOrderStatusListener(getOms()); //is this needed? Why would i receive orderstatus on this ?
        }

            if (Subscribe.tes != null) {
                Subscribe.tes.addTradeListener(this);
            }
        }
    

    private void loadParameters(String strategy, String parameterFile) {
        Properties p = new Properties(System.getProperties());
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(parameterFile);
            try {
                p.load(propFile);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        System.setProperties(p);
        trading = Boolean.valueOf(System.getProperty("Trading"));
        index = System.getProperty("Index");
        type = System.getProperty("Type");
        expiry = System.getProperty("Expiry") == null ? "" : System.getProperty("Expiry");
        threshold = Integer.parseInt(System.getProperty("Threshold"));
        stopLoss = Double.parseDouble(System.getProperty("StopLoss"));
        window = System.getProperty("Window");
        windowHurdle = Double.parseDouble(System.getProperty("WindowHurdle"));
        dayHurdle = Double.parseDouble(System.getProperty("DayHurdle"));
        takeProfit = Double.parseDouble(System.getProperty("TakeProfit"));
        scalpingMode = System.getProperty("ScalpingMode") == null ? false : Boolean.parseBoolean(System.getProperty("ScalpingMode"));
        reentryMinimumMove = System.getProperty("ReentryMinimumMove") == null ? 0D : Double.parseDouble(System.getProperty("ReentryMinimumMove"));
        adrRuleName = System.getProperty("ADRSymbolTag") == null ? "": System.getProperty("ADRSymbolTag");
        String concatAccountNames = "";
        for (String account : getAccounts()) {
            concatAccountNames = ":" + account;
        }

        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Threshold" + delimiter + threshold});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "TakeProfit" + delimiter + takeProfit});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "Window" + delimiter + window});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ScalpingMode" + delimiter + scalpingMode});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ReentryMinimumMove" + delimiter + reentryMinimumMove});
        logger.log(Level.INFO, "100,StrategyParameters,{0}", new Object[]{getStrategy() + delimiter + "ADRRule" + delimiter + adrRuleName});
        
    }

    @Override
    public void tradeReceived(TradeEvent event) {
       
        if(getStrategySymbols().contains(event.getSymbolID())){
           this.processTradeReceived(event);
            //new Thread(new ADRPublisherTradeReceived(this,event)).start();
        }
    }

    void processTradeReceived(TradeEvent event){
        try{
                int id = event.getSymbolID(); //zero based id
        if (getStrategySymbols().contains(id) && Parameters.symbol.get(id).getType().compareTo("STK") == 0) {
            switch (event.getTickType()) {
                case com.ib.client.TickType.LAST_SIZE:
                    //System.out.println("LASTSIZE, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getLastSize()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new ADRPublisherTickPriceEvent(id, com.ib.client.TickType.LAST_SIZE, Parameters.symbol.get(id).getLastSize()));
                    //mEsperEvtProcessor.debugFireTickQuery();
                    break;
                case com.ib.client.TickType.VOLUME:
                    //System.out.println("VOLUME, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getVolume()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new ADRPublisherTickPriceEvent(id, com.ib.client.TickType.VOLUME, Parameters.symbol.get(id).getVolume()));
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                case com.ib.client.TickType.LAST:
                    //System.out.println("LAST, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getLastPrice()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new ADRPublisherTickPriceEvent(id, com.ib.client.TickType.LAST, Parameters.symbol.get(id).getLastPrice()));
                    if(Parameters.symbol.get(id).getClosePrice()>0 && !this.closePriceReceived.get(id)){
                    mEsperEvtProcessor.sendEvent(new ADRPublisherTickPriceEvent(id, com.ib.client.TickType.CLOSE, Parameters.symbol.get(id).getClosePrice()));                        
                    this.closePriceReceived.put(id, Boolean.TRUE);
                    }
                    //mEsperEvtProcessor.debugFireTickQuery();
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                case com.ib.client.TickType.CLOSE:
                    //System.out.println("CLOSE, Symbol:"+Parameters.symbol.get(id).getSymbol()+" Value: "+Parameters.symbol.get(id).getClosePrice()+" tickerID: "+id);
                    mEsperEvtProcessor.sendEvent(new ADRPublisherTickPriceEvent(id, com.ib.client.TickType.CLOSE, Parameters.symbol.get(id).getClosePrice()));
                    //mEsperEvtProcessor.debugFireADRQuery();
                    break;
                default:
                    break;
            }
        }
        String symbolexpiry = Parameters.symbol.get(id).getExpiry() == null ? "" : Parameters.symbol.get(id).getExpiry();
        if (Parameters.symbol.get(id).getSymbol().equals(index) && Parameters.symbol.get(id).getType().equals(type) && symbolexpiry.equals(expiry) && event.getTickType() == com.ib.client.TickType.LAST) {
            double price = Parameters.symbol.get(id).getLastPrice();
            TradingUtil.writeToFile("ADR.csv", adr + "," + adrTRIN + "," + tick + "," + tickTRIN + "," + price + "," + adrHigh + "," + adrLow + "," + adrAvg + "," + adrTRINHigh + "," + adrTRINLow + "," + adrTRINAvg + "," + indexHigh + "," + indexLow + "," + indexAvg + "," + indexDayHigh + "," + indexDayLow);
        }
    }catch(Exception e){
     logger.log(Level.INFO,"101",e);
    }
    }
    
    @Override
    public void update(EventBean[] newEvents, EventBean[] oldEvents) {
        double high = newEvents[0].get("high") == null ? Double.MIN_VALUE : (Double) newEvents[0].get("high");
        double low = newEvents[0].get("low") == null ? Double.MAX_VALUE : (Double) newEvents[0].get("low");
        double average = newEvents[0].get("average") == null ? adrAvg : (Double) newEvents[0].get("average");
        if (adr > 0) {
            switch ((Integer) newEvents[0].get("field")) {
                case ADRPublisherTickType.D_ADR:
                    adrHigh = high;
                    adrLow = low;
                    adrAvg = average;
                    break;
                case ADRPublisherTickType.D_TRIN:
                    adrTRINHigh = high;
                    adrTRINLow = low;
                    adrTRINAvg = average;
                    break;
                case ADRPublisherTickType.T_TICK:
                    tickHigh = high;
                    tickLow = low;
                    tickAvg = average;
                    break;
                case ADRPublisherTickType.T_TRIN:
                    tickTRINHigh = high;
                    tickTRINLow = low;
                    tickTRINAvg = average;
                    break;
                case ADRPublisherTickType.INDEX:
                    indexHigh = high;
                    indexLow = low;
                    indexAvg = average;
                    break;
                default:
                    break;
            }
        }
    }

    boolean atLeastTwo(boolean a, boolean b, boolean c) {
        return a && (b || c) || (b && c);
    }
}
