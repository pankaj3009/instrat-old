/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.ib.client.*;
import com.incurrency.rateserver.Cassandra;
import com.incurrency.rateserver.Rates;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author admin
 */
public class TWSConnectionServer extends TWSConnection {

    String cassandraIP = "192.187.112.162";
    int cassandraPort = 4242;
    Socket cassandraConnection;
    PrintStream output;
    boolean useRTVolume = false;
    String topic;
    public boolean saveToCassandra;
    public String tickEquityMetric;
    public String tickFutureMetric;
    public String tickOptionMetric;
    public String rtEquityMetric;
    public String rtFutureMetric;
    public String rtOptionMetric;
    public static String[][] marketData;
    private static final Logger logger = Logger.getLogger(TWSConnectionServer.class.getName());


    public TWSConnectionServer(BeanConnection c) {
        super(c);
        try {
            cassandraConnection = new Socket(cassandraIP, cassandraPort);
            output = new PrintStream(cassandraConnection.getOutputStream());
        } catch (Exception e) {
            logger.log(Level.SEVERE,null,e);
        }


    }
   
    //<editor-fold defaultstate="collapsed" desc="EWrapper Overrides">
    /**
     * tickPrice will ALWAYS send the bid, ask to rateserver. rtvolume=true:
     * lastprice,close,open will be sent for snapshot
     * rtvolume=false,lastprice,close,open will be sent for all If snapshot is
     * true, lastprice will be sent.If snapshot is false,
     *
     * @param tickerId
     * @param field
     * @param price
     * @param canAutoExecute
     */
    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        int serialno = getRequestDetails().get(tickerId) != null ? (int) getRequestDetails().get(tickerId).symbol.getSerialno() : 0;
        int id = serialno - 1;
        boolean snapshot = false;
        if (getRequestDetails().get(tickerId) != null) {
            snapshot = getRequestDetails().get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
        } else {
            logger.log(Level.SEVERE, "RequestID: {0} was not found", new Object[]{tickerId});
        }

        String type = Parameters.symbol.get(id).getType();
        String header = topic + ":" + type + ":" + "ALL";
        String symbol = "";
        switch (type) {
            case "STK":
                symbol = Parameters.symbol.get(id).getSymbol();
                break;
            case "IND":
                symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType();
                break;
            case "FUT":
                symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType() + "_" + Parameters.symbol.get(id).getExpiry();
                break;
            case "OPT":
                symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType() + "_" + Parameters.symbol.get(id).getExpiry() + "_" + Parameters.symbol.get(id).getRight() + "_" + Parameters.symbol.get(id).getOption();
                break;
            default:
                symbol = "";
                break;
        }
        if (field == com.ib.client.TickType.CLOSE) {
            Parameters.symbol.get(id).setClosePrice(price);
        } else if (field == com.ib.client.TickType.OPEN) {
            Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + price + "," + symbol);
            Parameters.symbol.get(id).setOpenPrice(price);
        } else if (field == com.ib.client.TickType.HIGH) {
            Parameters.symbol.get(id).setHighPrice(price);
            Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + price + "," + symbol);
        } else if (field == com.ib.client.TickType.LOW) {
            Parameters.symbol.get(id).setLowPrice(price);
            Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + price + "," + symbol);
        } else if (field == com.ib.client.TickType.BID || field == com.ib.client.TickType.ASK) {
            Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + price + "," + symbol);
        }
        if (field == com.ib.client.TickType.LAST) {
            if (useRTVolume && snapshot || !useRTVolume) {
                double lastPrice = Parameters.symbol.get(id).getLastPrice();
                Parameters.symbol.get(id).setPrevLastPrice(lastPrice);
                Parameters.symbol.get(id).setLastPrice(price);
                Rates.rateServer.send(header, com.ib.client.TickType.LAST + "," + new Date().getTime() + "," + price + "," + symbol);
                Rates.rateServer.send(header, com.ib.client.TickType.CLOSE + "," + new Date().getTime() + "," + Parameters.symbol.get(id).getClosePrice() + "," + symbol);
                Rates.rateServer.send(header, com.ib.client.TickType.OPEN + "," + new Date().getTime() + "," + Parameters.symbol.get(id).getOpenPrice() + "," + symbol);
                Rates.rateServer.send(header, com.ib.client.TickType.HIGH + "," + new Date().getTime() + "," + Parameters.symbol.get(id).getHighPrice() + "," + symbol);
                Rates.rateServer.send(header, com.ib.client.TickType.LOW + "," + new Date().getTime() + "," + Parameters.symbol.get(id).getLowPrice() + "," + symbol);

                if (saveToCassandra) {
                    if (type.equals("STK") || type.equals("IND")) {
                        new Thread(new Cassandra(String.valueOf(price), new Date().getTime(), tickEquityMetric + ".close", Parameters.symbol.get(id).getDisplayname(), null, output)).start();
                    } else if (type.equals("FUT")) {
                        new Thread(new Cassandra(String.valueOf(price), new Date().getTime(), tickFutureMetric + ".close", Parameters.symbol.get(id).getDisplayname(), Parameters.symbol.get(id).getExpiry(), output)).start();
                    }
                }
            } else {
                if (saveToCassandra) {
                    if (type.equals("STK") || type.equals("IND")) {
                        new Thread(new Cassandra(String.valueOf(price), new Date().getTime(), tickEquityMetric + ".close", Parameters.symbol.get(id).getDisplayname(), null, output)).start();
                    } else if (type.equals("FUT")) {
                        new Thread(new Cassandra(String.valueOf(price), new Date().getTime(), tickFutureMetric + ".close", Parameters.symbol.get(id).getDisplayname(), Parameters.symbol.get(id).getExpiry(), output)).start();
                    }
                }
            }
        }
    }

    /**
     * bidsize, asksize will be sent for all rtvolume=true: lastsize,volume will
     * be sent for snapshot rtvolume=false,lastsize and volume will be sent for
     * all
     *
     * @param tickerId
     * @param field
     * @param size
     */
    @Override
    public void tickSize(int tickerId, int field, int size) {
        int serialno = getRequestDetails().get(tickerId) != null ? (int) getRequestDetails().get(tickerId).symbol.getSerialno() : 0;
        int id = serialno - 1;
        
        boolean snapshot = false;
        if (getRequestDetails().get(tickerId) != null) {
            snapshot = getRequestDetails().get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
        } else {
            logger.log(Level.SEVERE, "RequestID: {0} was not found", new Object[]{tickerId});
        }
        
        String type = Parameters.symbol.get(id).getType();
        String header = Rates.country + ":" + type + ":" + "ALL";
        String symbol = "";
        switch (type) {
            case "STK":
                symbol = Parameters.symbol.get(id).getSymbol();
                break;
            case "IND":
                symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType();
                break;
            case "FUT":
                symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType() + "_" + Parameters.symbol.get(id).getExpiry();
                break;
            case "OPT":
                symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType() + "_" + Parameters.symbol.get(id).getExpiry() + "_" + Parameters.symbol.get(id).getRight() + "_" + Parameters.symbol.get(id).getOption();
                break;
            default:
                symbol = "";
                break;
        }
        if (field == com.ib.client.TickType.BID_SIZE || field == com.ib.client.TickType.ASK_SIZE) {
            Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + size + "," + symbol);
        }
        if (field == com.ib.client.TickType.VOLUME) {
            long localTime = new Date().getTime();
            int lastSize = size - Parameters.symbol.get(id).getVolume();
            if ((useRTVolume && snapshot) || !useRTVolume) {
                //Rates.rateServer.send(header, com.ib.client.TickType.LAST_SIZE + "," + new Date().getTime() + "," + lastSize + "," + symbol);
                Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + size + "," + symbol);
                Parameters.symbol.get(id).setVolume(size);
                if (saveToCassandra) {
                    if (type.equals("STK") || type.equals("IND")) {
                        new Thread(new Cassandra(String.valueOf(size), localTime, tickEquityMetric + ".dayvolume", Parameters.symbol.get(id).getDisplayname(), null, output)).start();
                        //new Thread(new Cassandra(String.valueOf(lastSize), localTime, "india.nse.equity.s1.tick.volume", Parameters.symbol.get(id).getServicename(), null, output)).start();
                    } else if (type.equals("FUT")) {
                        new Thread(new Cassandra(String.valueOf(size), localTime, tickFutureMetric + ".dayvolume", Parameters.symbol.get(id).getDisplayname(), Parameters.symbol.get(id).getExpiry(), output)).start();
                        //new Thread(new Cassandra(String.valueOf(lastSize), localTime, "india.nse.future.s1.tick.volume", Parameters.symbol.get(id).getServicename(), Parameters.symbol.get(id).getExpiry(), output)).start();
                    }
                }

            } else {
                if (saveToCassandra) {
                    switch (type) {
                        case "STK":
                        case "IND":
                            new Thread(new Cassandra(String.valueOf(size), localTime, tickEquityMetric + ".dayvolume", Parameters.symbol.get(id).getDisplayname(), null, output)).start();
                            //new Thread(new Cassandra(String.valueOf(lastSize), localTime, "india.nse.equity.s1.tick.volume", Parameters.symbol.get(id).getServicename(), null, output)).start();
                            break;
                        case "FUT":
                            new Thread(new Cassandra(String.valueOf(size), localTime, tickFutureMetric + ".dayvolume", Parameters.symbol.get(id).getDisplayname(), Parameters.symbol.get(id).getExpiry(), output)).start();
                            //new Thread(new Cassandra(String.valueOf(lastSize), localTime, "india.nse.future.s1.tick.volume", Parameters.symbol.get(id).getServicename(), Parameters.symbol.get(id).getExpiry(), output)).start();
                            break;
                    }
                }
            }
        } else if (field == com.ib.client.TickType.LAST_SIZE) {
            long localTime = new Date().getTime();
            if ((useRTVolume && snapshot) || !useRTVolume) {
                Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + size + "," + symbol);
                if (saveToCassandra) {
                    switch (type) {
                        case "STK":
                        case "IND":
                            new Thread(new Cassandra(String.valueOf(size), localTime, tickEquityMetric + ".volume", Parameters.symbol.get(id).getDisplayname(), null, output)).start();
                            break;
                        case "FUT":
                            new Thread(new Cassandra(String.valueOf(size), localTime, tickFutureMetric + ".volume", Parameters.symbol.get(id).getDisplayname(), Parameters.symbol.get(id).getExpiry(), output)).start();
                            break;
                    }
                }
            } else {
                if (saveToCassandra) {
                    switch (type) {
                        case "STK":
                        case "IND":
                            new Thread(new Cassandra(String.valueOf(size), localTime, tickEquityMetric + ".volume", Parameters.symbol.get(id).getDisplayname(), null, output)).start();
                            break;
                        case "FUT":
                            new Thread(new Cassandra(String.valueOf(size), localTime, tickFutureMetric + ".volume", Parameters.symbol.get(id).getDisplayname(), Parameters.symbol.get(id).getExpiry(), output)).start();
                            break;
                    }
                }
            }
        }
    }

    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        int serialno = getRequestDetails().get(tickerId) != null ? (int) getRequestDetails().get(tickerId).symbol.getSerialno() : 0;
        int id = serialno - 1;
        String type = Parameters.symbol.get(id).getType();
        String header = Rates.country + ":" + type + ":" + "ALL";
        String symbol = "";
        switch (type) {
            case "STK":
                symbol = Parameters.symbol.get(id).getSymbol();
                break;
            case "IND":
                symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType();
                break;
            case "FUT":
                symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType() + "_" + Parameters.symbol.get(id).getExpiry();
                break;
            case "OPT":
                symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType() + "_" + Parameters.symbol.get(id).getExpiry() + "_" + Parameters.symbol.get(id).getRight() + "_" + Parameters.symbol.get(id).getOption();
                break;
            default:
                symbol = "";
                break;
        }


        //Rates.rateServer.send(header, field+","+new Date().getTime()+","+impliedVol+","+symbol);

        //Rates.rateServer.tickEventSupport.fireTickEvent(header, field+","+new Date().getTime()+","+impliedVol+","+symbol);
        //getPrices().add(new Price(header,field+","+new Date().getTime()+","+impliedVol+","+symbol));
        //10= BidVol, 11=AskVol 12=LastVol            
    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        //System.out.println(methodName);
    }

    /**
     * rtvolume=true: lastprice,close,open,lastsize,volume will be sent for
     * snapshot rtvolume=false,lastsize and volume will be sent for all
     *
     * @param tickerId
     * @param tickType
     * @param value
     */
    @Override
    public void tickString(int tickerId, int tickType, String value) {
        /* removed as RTVolume is throwing wrong prices
         if (tickType == 48) {
         long localTime = new Date().getTime();
         int serialno = c.getmReqID().get(tickerId) != null ? (int) c.getmReqID().get(tickerId) : (int) c.getmSnapShotReqID().get(tickerId);
         int id = serialno - 1;
         String type = Parameters.symbol.get(id).getType();
         String header = Rates.country + ":" + type + ":" + "ALL";
         String symbol = "";
         switch (type) {
         case "STK":
         symbol = Parameters.symbol.get(id).getSymbol();
         break;
         case "IND":
         symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType();
         break;
         case "FUT":
         symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType() + "_" + Parameters.symbol.get(id).getExpiry();
         break;
         case "OPT":
         symbol = Parameters.symbol.get(id).getSymbol() + "_" + Parameters.symbol.get(id).getType() + "_" + Parameters.symbol.get(id).getExpiry() + "_" + Parameters.symbol.get(id).getRight() + "_" + Parameters.symbol.get(id).getOption();
         break;
         default:
         symbol = "";
         break;
         }
         String[] values = value.split(";");
         if (values.length >= 6 && !values[0].isEmpty()) {
         try {
         Double price = Double.parseDouble(values[0]);
         int size = Integer.parseInt(values[1]);
         long time = Long.parseLong(values[2]);
         int volume = Integer.parseInt(values[3]);
         Parameters.symbol.get(id).setLastPrice(price);
         Parameters.symbol.get(id).setLastSize(size);
         Parameters.symbol.get(id).setVolume(volume);
         Parameters.symbol.get(id).setLastPriceTime(time);
         if(useRTVolume){
         Rates.rateServer.send(header, com.ib.client.TickType.LAST + "," + localTime + "," + price + "," + symbol);
         Rates.rateServer.send(header, com.ib.client.TickType.LAST_SIZE + "," + localTime + "," + size + "," + symbol);
         Rates.rateServer.send(header, com.ib.client.TickType.VOLUME + "," + localTime + "," + volume + "," + symbol);
         Rates.rateServer.send(header, com.ib.client.TickType.CLOSE + "," + new Date().getTime() + "," + Parameters.symbol.get(id).getClosePrice() + "," + symbol);
         Rates.rateServer.send(header, com.ib.client.TickType.OPEN + "," + new Date().getTime() + "," + Parameters.symbol.get(id).getOpenPrice() + "," + symbol);
         }
         if (saveToCassandra) {
         if (type.equals("STK") || type.equals("IND")) {
         new Thread(new Cassandra(String.valueOf(price), time, rtEquityMetric + ".close", Parameters.symbol.get(id).getServicename(), null, output)).start();
         new Thread(new Cassandra(String.valueOf(volume), time, rtEquityMetric + ".dayvolume", Parameters.symbol.get(id).getServicename(), null, output)).start();
         new Thread(new Cassandra(String.valueOf(size), time, rtEquityMetric + ".volume", Parameters.symbol.get(id).getServicename(), null, output)).start();
         } else if (type.equals("FUT")) {
         new Thread(new Cassandra(String.valueOf(price), time, rtFutureMetric + ".close", Parameters.symbol.get(id).getServicename(), Parameters.symbol.get(id).getExpiry(), output)).start();
         new Thread(new Cassandra(String.valueOf(volume), time, rtFutureMetric + ".dayvolume", Parameters.symbol.get(id).getServicename(), Parameters.symbol.get(id).getExpiry(), output)).start();
         new Thread(new Cassandra(String.valueOf(size), time, rtFutureMetric + ".volume", Parameters.symbol.get(id).getServicename(), Parameters.symbol.get(id).getExpiry(), output)).start();
         }
         }
         } catch (Exception e) {
         logger.log(Level.INFO, null, e);
         }
         }
         }
         */
    }
   
  }
