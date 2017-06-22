/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanConnection;
import com.incurrency.framework.DateUtil;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TWSConnection;
import com.incurrency.framework.Utilities;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates Rates, Tick , TRIN initialise Rates passing the adrSymbols that
 * need to be tracked. Rates will immediately initiate polling market data in
 * snapshot mode. Streaming mode is currently not supported output is available
 * via static fields Advances, Declines, Tick Advances, Tick Declines, Advancing
 * Volume, Declining Volume, Tick Advancing Volume, Tick Declining Volume
 *
 * @author pankaj
 */
public class Rates {

    private static int publishport;
    private static int responseport;
    public static com.incurrency.framework.rateserver.RedisPublisher rateServer;
    private static final Logger logger = Logger.getLogger(Rates.class.getName());
    Date endDate;
    public static String country;
    public static boolean useRTVolume = false;
    //--common parameters required for all strategies
    Properties properties;
    public static String tickFutureMetric;
    public static String tickEquityMetric;
    public static String tickOptionMetric;
    public static String rtFutureMetric;
    public static String rtEquityMetric;
    public static String rtOptionMetric;
    public static boolean pushToCassandra = false;

    public Rates(String parameterFile) {
        loadParameters(parameterFile);
        rateServer = new com.incurrency.framework.rateserver.RedisPublisher(publishport);
        TWSConnection.serverInitialized.set(true);

    }

    private void loadParameters(String ParameterFile) {
        properties = Utilities.loadParameters(ParameterFile);
        String currDateStr = DateUtil.getFormattedDate("yyyyMMdd", new Date().getTime());
        String endDateStr;
        if (Boolean.getBoolean(properties.getProperty("historicaldata", "false"))) {
            endDateStr = DateUtil.getFormattedDate("yyyyMMdd", new Date().getTime() + 24 * 60 * 60 * 1000);
        } else {
            endDateStr = currDateStr + " " + properties.getProperty("endtime");
        }
        if (Utilities.isDate(endDateStr, new SimpleDateFormat("yyyyMMdd HH:mm:ss"))) {
            endDate = DateUtil.parseDate("yyyyMMdd HH:mm:ss", endDateStr, Algorithm.timeZone);
            if (new Date().compareTo(endDate) > 0) {
                //increase enddate by one calendar day
                endDate = DateUtil.addDays(endDate, 1);
            }
        }
        if (endDate != null || "".equals(endDate)) {
            MainAlgorithm.setCloseDate(endDate);
        }
        responseport = Integer.parseInt(properties.getProperty("responseport", "5555"));
        publishport = Integer.parseInt(properties.getProperty("publishport", "5556"));
        String cassandraIP = properties.getProperty("cassandraip", "127.0.0.1");
        int cassandraPort = Integer.valueOf(properties.getProperty("cassandraport", "4242"));
        String topic = properties.getProperty("topic", "INR");
        useRTVolume = Boolean.parseBoolean(properties.getProperty("usertvolume", "false").toString().trim());
        country = properties.getProperty("countrycode", "INR");
        tickFutureMetric = properties.getProperty("tickfuturemetric");
        tickEquityMetric = properties.getProperty("tickequitymetric");
        tickOptionMetric = properties.getProperty("tickoptionmetric");
        rtFutureMetric = properties.getProperty("rtfuturemetric");
        rtEquityMetric = properties.getProperty("rtequitymetric");
        rtOptionMetric = properties.getProperty("rtoptionmetric");
        if (useRTVolume) {
           // ZMQPubSub.equityMetric = rtEquityMetric;
           // ZMQPubSub.futureMetric = rtFutureMetric;
           // ZMQPubSub.optionMetric = rtOptionMetric;
        } else {
           // ZMQPubSub.equityMetric = tickEquityMetric;
           // ZMQPubSub.futureMetric = tickFutureMetric;
           // ZMQPubSub.optionMetric = tickOptionMetric;
        }
        boolean realtime = Boolean.parseBoolean(properties.getProperty("realtime", "false"));
        pushToCassandra = Boolean.parseBoolean(properties.getProperty("savetocassandra", "false"));
        boolean savetocassandra = Boolean.parseBoolean(properties.getProperty("savetocassandra", "false"));
        if (Parameters.connection.size() > 0) {
            for (BeanConnection c : Parameters.connection) {
                c.getWrapper().getCassandraDetails().setCassandraIP(cassandraIP);
                c.getWrapper().getCassandraDetails().setCassandraPort(cassandraPort);
                c.getWrapper().getCassandraDetails().setTopic(topic);
                c.getWrapper().getCassandraDetails().setSaveToCassandra(pushToCassandra);
                c.getWrapper().getCassandraDetails().setTickEquityMetric(tickEquityMetric); 
                c.getWrapper().getCassandraDetails().setTickFutureMetric(tickFutureMetric);
                c.getWrapper().getCassandraDetails().setTickOptionMetric(tickOptionMetric);
                c.getWrapper().getCassandraDetails().setRtEquityMetric(rtEquityMetric);
                c.getWrapper().getCassandraDetails().setRtFutureMetric(rtFutureMetric);
                c.getWrapper().getCassandraDetails().setRtOptionMetric(rtOptionMetric);
                c.getWrapper().getCassandraDetails().setRealtime(realtime);
                c.getWrapper().getCassandraDetails().setSaveToCassandra(savetocassandra);
                if (savetocassandra) {
                    try {
                      //  ZMQPubSub.cassandraConnection = new Socket(cassandraIP, cassandraPort);
                      //  ZMQPubSub.output = new PrintStream(ZMQPubSub.cassandraConnection.getOutputStream());
                      //  ZMQPubSub.saveToCassandra = savetocassandra;
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                    }
                }
            }
        }
        logger.log(Level.INFO, "-----RateServer Parameters----");
        logger.log(Level.INFO, "end Time: {0}", endDate);
        logger.log(Level.INFO, "Country code: {0}", country);
        logger.log(Level.INFO, "Historical Mode: {0}", Boolean.parseBoolean(properties.getProperty("historical").toString()));
        logger.log(Level.INFO, "Use RT Volume: {0}", useRTVolume);
        logger.log(Level.INFO, "Save Data To Cassandra: {0}", pushToCassandra);
        logger.log(Level.INFO, "Tick Equity Metric: {0}", tickEquityMetric);
        logger.log(Level.INFO, "Tick Future Metric: {0}", tickFutureMetric);
        logger.log(Level.INFO, "Tick Option Metric {0}", tickOptionMetric);
        logger.log(Level.INFO, "RT Equity Metric: {0}", rtEquityMetric);
        logger.log(Level.INFO, "RT Future Metric: {0}", rtFutureMetric);
        logger.log(Level.INFO, "RT Option Metric {0}", rtOptionMetric);
    }
}
