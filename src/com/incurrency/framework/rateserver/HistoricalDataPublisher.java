/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.DataPoint;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.builder.QueryMetric;
import org.kairosdb.client.response.QueryResponse;
import com.google.common.collect.TreeMultimap;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.Utilities;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.SortedSet;

/**
 *
 * @author pankaj
 */
public class HistoricalDataPublisher implements Runnable {

    ArrayList<HistoricalDataParameters> symbols = new ArrayList<>();
    //String startTime;
    //String endTime;
    static TimeZone timeZone;
    static int tradeCloseHour;
    static int tradeCloseMinute;
    static int tradeCloseSecond;
    static int tradeOpenHour;
    static int tradeOpenMinute;
    static int tradeOpenSecond;
    private static final Logger logger = Logger.getLogger(HistoricalDataPublisher.class.getName());
    TreeMultimap<String, OHLCV> nameKey = TreeMultimap.create();

    public HistoricalDataPublisher(ArrayList<HistoricalDataParameters> symbols) {
        this.symbols = symbols;
    }

    @Override
    public void run() {
        SimpleDateFormat dateFormat;
        try {
            long memoryNow = Runtime.getRuntime().freeMemory();
            System.gc();
            long memoryLater = Runtime.getRuntime().freeMemory();
            long memoryCleared = memoryNow - memoryLater;
            System.out.println("Memory cleared:" + memoryCleared);
            tradeCloseHour = Algorithm.closeHour;
            tradeCloseMinute = Algorithm.closeMinute;
            tradeCloseSecond = 0;
            tradeOpenHour = Algorithm.openHour;
            tradeOpenMinute = Algorithm.openMinute;
            tradeOpenSecond = 0;
            timeZone = TimeZone.getTimeZone(Algorithm.timeZone);
            dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            dateFormat.setTimeZone(timeZone);
            String periodicity = symbols.get(0).periodicity;
            Date startDate = null;
            Date endDate = null;
            Date closeReferenceDate = null;
            startDate = dateFormat.parse(symbols.get(0).startDate);
            endDate = dateFormat.parse(symbols.get(0).endDate);
            closeReferenceDate = dateFormat.parse(symbols.get(0).closeReferenceDate);
            String metric = null;
            int increment = 0;

            //first publish the close of last trading day
            //getClose(symbols,closeReferenceDate, "india.nse",periodicity);
            //now publish historical data
            for (Date d = startDate; d.before(endDate); d = addSeconds(d, 28800, true)) {
                logger.log(Level.INFO, "Retrieve and Publish,{0}", new Object[]{d + "_" + addSeconds(d, 28800, true)});
                Calendar now = new GregorianCalendar();
                now.setTime(d);
                Calendar priorStartDate = new GregorianCalendar();
                priorStartDate.setTime(addSeconds(d, -43200, false));//get date 12 hours ago
                if (priorStartDate.get(Calendar.DAY_OF_MONTH) < now.get(Calendar.DAY_OF_MONTH) || priorStartDate.get(Calendar.MONTH) < now.get(Calendar.MONTH)) {
                    //need to retrieve close data
                    getClose(symbols, d);
                }

                publishTick(symbols, periodicity, d, addSeconds(addSeconds(d, 28800, true), -1, false));
                //If EOD reached send 99 ticktype
                Calendar nextStartDate = new GregorianCalendar();
                nextStartDate.setTime(addSeconds(d, 28800, true));
                if (nextStartDate.get(Calendar.DAY_OF_MONTH) > now.get(Calendar.DAY_OF_MONTH) || nextStartDate.get(Calendar.MONTH) > now.get(Calendar.MONTH)) {
                    Thread.sleep(20000);//wait for 10 seconds to ensure all tick for today has been sent
                    for (HistoricalDataParameters h : symbols) {
                        String close = 99 + "," + 1 + "," + 1 + "," + h.displayName;
                        String lastsize = 99 + "," + 1 + "," + 1 + "," + h.displayName;
                        Rates.rateServer.send(symbols.get(0).topic, close);
                        Rates.rateServer.send(symbols.get(0).topic, lastsize);
                        logger.log(Level.FINE, "Published: {0}", new Object[]{h.displayName + "_" + close});
                    }
                }

            }

        } catch (Exception ex) {
            Logger.getLogger(HistoricalDataPublisher.class.getName()).log(Level.SEVERE, null, ex);
        }


    }

    private void publishTick(ArrayList<HistoricalDataParameters> symbols, String periodicity, Date startDate, Date endDate) throws URISyntaxException, IOException, InterruptedException {
        HttpClient client = new HttpClient("http://"+Algorithm.cassandraIP+":8085");
        String metricnew = null;
        int startCounter = 0;
        TreeMultimap<Long, OHLCV> timeKey = TreeMultimap.create();

        for (HistoricalDataParameters symbol : symbols) {
            logger.log(Level.FINE, "Requesting Historical Data: {0}", new Object[]{symbol.displayName});
            long start = new Date().getTime();
            HashMap<Long, OHLCV> symbolData = new HashMap<>();
            for (int i = startCounter; i < 2; i++) {
                metricnew=symbol.metric;
                switch (i) {
                    case 1:
                        metricnew = metricnew + ".volume";
                        break;
                    case 0:
                        metricnew = metricnew + ".close";
                        break;
                    default:
                        break;
                }

                QueryBuilder builder = QueryBuilder.getInstance();
                builder.setStart(startDate)
                        .setEnd(endDate)
                        .addMetric(metricnew)
                        .addTag("symbol", symbol.name.toLowerCase());
                builder.getMetrics().get(0).setOrder(QueryMetric.Order.ASCENDING);
                if (!symbol.expiry.equals("")) {
                    builder.getMetrics().get(0).addTag("expiry", symbol.expiry);
                }
                if (!symbol.right.equals("")) {
                    builder.getMetrics().get(0).addTag("right", symbol.right);
                    builder.getMetrics().get(0).addTag("strike", symbol.strikePrice);
                }

                long time = new Date().getTime();
                //System.out.println(symbol.fullname);
                QueryResponse response = client.query(builder);
                List<DataPoint> dataPoints = response.getQueries().get(0).getResults().get(0).getDataPoints();
                for (DataPoint dataPoint : dataPoints) {
                    long lastTime = dataPoint.getTimestamp();
                    Object value = dataPoint.getValue();
                    //System.out.println("Date:" + new Date(lastTime) + ",Value:" + value.toString());
                    switch (i) {
                        case 0:
                            symbolData.put(lastTime, new OHLCV(lastTime, value.toString(), symbol.displayName));
                            //data.setClose(value.toString());
                            break;
                        case 1:
                            try {
                                symbolData.get(lastTime).setVolume(value.toString());
                            } catch (Exception e) {
                                symbolData.put(lastTime, new OHLCV(lastTime, symbol.displayName));

                            }
                            break;

                        default:
                            break;
                    }
                }
                //update treemultimap
                long t = 0;
                for (OHLCV d : symbolData.values()) {
                    timeKey.put(d.getTime(), d);
                    t = d.getTime();
                }
                long timetaken = (new Date().getTime() - start) / 1000;
                if (symbolData.size() > 0) {
                    logger.log(Level.FINE, "HD:{0}", new Object[]{symbol.displayName + "_" + new Date(t)});
//                logger.log(Level.INFO,"Finished historical data for {0}",new Object[]{symbol.fullname+"_"+timetaken});
                } else {
                    break;
                }

            }
            //publish data to socket
            //todo
            //fix symbol in code below to refer to symbol_type_expiry_right_strike
            //pull in data for a day and then publish sequentially
        }

        //Now publish data to socket.
        for (Long key : timeKey.keySet()) {
            SortedSet<OHLCV> s = timeKey.get(key);
            for (OHLCV d : s) {
                double c=Utilities.getDouble(d.getClose(),0);
                double dbid=c-0.1;
                double dask=c+0.1;
                String close = com.ib.client.TickType.LAST + "," + d.getTime() + "," + d.getClose() + "," + d.getSymbol();
                String bid = com.ib.client.TickType.BID + "," + d.getTime() + "," + dbid + "," + d.getSymbol();
                String ask = com.ib.client.TickType.ASK + "," + d.getTime() + "," + dask + "," + d.getSymbol();
                String lastsize = com.ib.client.TickType.LAST_SIZE + "," + d.getTime() + "," + d.getVolume() + "," + d.getSymbol();
                Rates.rateServer.send(symbols.get(0).topic, close);
                Rates.rateServer.send(symbols.get(0).topic, bid);
                Rates.rateServer.send(symbols.get(0).topic, ask);                
                Rates.rateServer.send(symbols.get(0).topic, lastsize);
                logger.log(Level.FINE, "Published: {0}", new Object[]{new Date(d.getTime()) + "_" + d.getSymbol()});
            }
            Thread.sleep(10);
        }
        long memoryNow = Runtime.getRuntime().freeMemory();
        System.gc();
        long memoryLater = Runtime.getRuntime().freeMemory();
        long memoryCleared = memoryNow - memoryLater;
        System.out.println("Memory cleared:" + memoryCleared);
        client.shutdown();

    }

    private static void getClose(ArrayList<HistoricalDataParameters> symbols, Date closeDate) throws URISyntaxException, IOException {
        HttpClient client = new HttpClient("http://"+Algorithm.cassandraIP+":8085");
        String metricnew = null;
        String topic=symbols.get(0).topic;
        int startCounter = 0;
        TreeMultimap<Long, OHLCV> timeKey = TreeMultimap.create();
        for (HistoricalDataParameters symbol : symbols) {
            logger.log(Level.FINE, "Requesting Close Data: {0}", new Object[]{symbol.displayName});
            long start = new Date().getTime();
            HashMap<Long, OHLCV> symbolData = new HashMap<>();
            for (int i = startCounter; i < 1; i++) {
                metricnew=symbol.metric;
                switch (i) {
                    case 0:
                        metricnew = metricnew + ".close";
                        break;
                    default:
                        break;
                }
                Date startDate = closeDate;
                Date endDate = addDays(closeDate, 1);
                QueryBuilder builder = QueryBuilder.getInstance();
                builder.setStart(new Date(0))
                        .setEnd(addSeconds(closeDate, -1, false))
                        .addMetric(metricnew)
                        .addTag("symbol", symbol.name.toLowerCase().replace("&", ""));
                if (!symbol.expiry.equals("")) {
                    builder.getMetrics().get(0).addTag("expiry", symbol.expiry);
                }
                if (!symbol.right.equals("")) {
                    builder.getMetrics().get(0).addTag("right", symbol.right);
                    builder.getMetrics().get(0).addTag("strike", symbol.strikePrice);
                }

                builder.getMetrics().get(0).setLimit(1);
                builder.getMetrics().get(0).setOrder(QueryMetric.Order.DESCENDING);
                long time = new Date().getTime();
                QueryResponse response = client.query(builder);

                List<DataPoint> dataPoints = response.getQueries().get(0).getResults().get(0).getDataPoints();
                for (DataPoint dataPoint : dataPoints) {
                    long lastTime = dataPoint.getTimestamp();
                    Object value = dataPoint.getValue();
                    //System.out.println("Date:" + new Date(lastTime) + ",Value:" + value.toString());
                    switch (i) {
                        case 0:
                            symbolData.put(lastTime, new OHLCV(lastTime, value.toString(), symbol.displayName));
                            //data.setClose(value.toString());
                            break;
                        default:
                            break;
                    }
                }
                //update treemultimap
                long t = 0;
                for (OHLCV d : symbolData.values()) {
                    timeKey.put(d.getTime(), d);
                    t = d.getTime();
                }
                long timetaken = (new Date().getTime() - start) / 1000;
                if (symbolData.size() > 0) {
                    logger.log(Level.FINE, "HD:{0}", new Object[]{symbol.displayName + "_" + new Date(t)});
//                logger.log(Level.INFO,"Finished historical data for {0}",new Object[]{symbol.fullname+"_"+timetaken});
                } else {
                    break;
                }

            }
        }
        //Now publish data to socket.
        for (Long key : timeKey.keySet()) {
            SortedSet<OHLCV> s = timeKey.get(key);
            for (OHLCV d : s) {
                String close = com.ib.client.TickType.CLOSE + "," + d.getTime() + "," + d.getClose() + "," + d.getSymbol();
                Rates.rateServer.send(topic, close);
                logger.log(Level.FINE, "PublishedClose: {0}", new Object[]{d.getSymbol() + "_" + close});
            }
//            timeKey.removeAll(s);
        }
        long memoryNow = Runtime.getRuntime().freeMemory();
        System.gc();
        long memoryLater = Runtime.getRuntime().freeMemory();
        long memoryCleared = memoryNow - memoryLater;
        System.out.println("Memory cleared:" + memoryCleared);

        client.shutdown();
    }

    static Date addSeconds(Date date, int seconds, boolean adjustGBD) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, seconds); //minus number would decrement the days
        if (adjustGBD) {
            return nextGoodDay(cal.getTime(), 0);
        } else {
            return cal.getTime();
        }

    }

    public static Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days); //minus number would decrement the days
        return nextGoodDay(cal.getTime(), 0);

    }

    public static Date nextGoodDay(Date startDate, int minuteAdjust) {
        Calendar entryCal = Calendar.getInstance(timeZone);
        entryCal.setTime(startDate);
        int entryMinute = entryCal.get(Calendar.MINUTE);
        int entryHour = entryCal.get(Calendar.HOUR_OF_DAY);
        int entryDayOfWeek = entryCal.get(Calendar.DAY_OF_WEEK);
        //round down entryMinute
        if (entryCal.get(Calendar.MILLISECOND) > 0) {
//            entryMinute=30;
            //          entryCal.set(Calendar.MINUTE, 30);
            //        entryCal.set(Calendar.SECOND, 0);
            entryCal.set(Calendar.MILLISECOND, 0);
        }

        Calendar exitCal = (Calendar) entryCal.clone();
        exitCal.setTimeZone(timeZone);
        exitCal.add(Calendar.MINUTE, minuteAdjust);
        //if(minuteAdjust==0){
        //    exitCal.add(Calendar.SECOND, 1);
        //}
        int exitMinute = exitCal.get(Calendar.MINUTE);
        int exitHour = exitCal.get(Calendar.HOUR_OF_DAY);
        int exitDayOfWeek = exitCal.get(Calendar.DAY_OF_WEEK);

        boolean adjust = true;

        if (exitHour > tradeCloseHour || (exitHour == tradeCloseHour && exitMinute >= tradeCloseMinute)) {
            //1.get minutes from close
            int minutesFromClose = (tradeCloseHour - entryHour) > 0 ? (tradeCloseHour - entryHour) * 60 : 0 + tradeCloseMinute - entryMinute;
            int minutesCarriedForward = minuteAdjust - minutesFromClose > 0 ? minuteAdjust - minutesFromClose : 0;
            exitCal.add(Calendar.DATE, 1);
            exitCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            exitCal.set(Calendar.MINUTE, tradeOpenMinute);
            exitCal.set(Calendar.MILLISECOND, 0);
            exitCal.add(Calendar.MINUTE, minutesCarriedForward);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String exitCalString = sdf.format(exitCal.getTime());
        while (exitCal.get(Calendar.DAY_OF_WEEK) == 7 || exitCal.get(Calendar.DAY_OF_WEEK) == 1) {
            exitCal.add(Calendar.DATE, 1);
            exitCal.set(Calendar.MINUTE, tradeOpenMinute);
            exitCalString = sdf.format(exitCal.getTime());
        }
        if (exitHour < tradeOpenHour || (exitHour == tradeOpenHour && exitMinute < tradeOpenMinute)) {
            exitCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            exitCal.set(Calendar.MINUTE, tradeOpenMinute);
            exitCal.set(Calendar.SECOND, 0);
            exitCal.set(Calendar.MILLISECOND, 0);
        }
        return exitCal.getTime();

    }
}
