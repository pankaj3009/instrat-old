/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 *
 * @author pankaj
 */
public class HistoricalDataResponse implements Runnable {

    String symbolName;
    String symbol;
    String metric;
    String[] metricArray;
    String[] timeSeries;
    String expiry;
    String strike;
    String right;
    String type;
    String requestid;
    String periodicity;
    long startTime;
    long endTime;
    static TimeZone timeZone;
    static int tradeCloseHour;
    static int tradeCloseMinute;
    static int tradeCloseSecond;
    static int tradeOpenHour;
    static int tradeOpenMinute;
    static int tradeOpenSecond;
    private static final Logger logger = Logger.getLogger(HistoricalDataResponse.class.getName());
    TreeMultimap<String, OHLCV> nameKey = TreeMultimap.create();
    HashMap<String, String> nameChange = new HashMap<>();
    TreeMap<Long, SplitInformation> splits = new TreeMap<>();

    public HistoricalDataResponse(String requestid, String symbol, String metric, long startDate, long endDate) {
        this.requestid = requestid;
        this.symbol = symbol;
        this.startTime = startDate;
        this.endTime = endDate;
        String[] symbolDetails = symbol.split("_");
        switch (symbolDetails.length) {
            case 1:
                this.symbolName = symbolDetails[0];
                break;
            case 2:
                this.symbolName = symbolDetails[0];
                this.type = symbolDetails[1];
                break;
            case 3:
                this.symbolName = symbolDetails[0];
                this.type = symbolDetails[1];
                this.expiry = symbolDetails[2];
                break;
            case 5:
                this.symbolName = symbolDetails[0];
                this.type = symbolDetails[1];
                this.expiry = symbolDetails[2];
                this.strike = symbolDetails[3];
                this.right = symbolDetails[4];
                break;
            default:
                break;

        }

        if (Algorithm.globalProperties.getProperty("symbolnamechangesfile") != null && new File(Algorithm.globalProperties.getProperty("symbolnamechangesfile").toString().trim()).exists()) {
            namechange(Algorithm.globalProperties.getProperty("symbolnamechangesfile").toString().trim());
        }
        
        if (Algorithm.globalProperties.getProperty("splitfile") != null && new File(Algorithm.globalProperties.getProperty("splitfile").toString().trim()).exists()) {
            getSplitInformation(Algorithm.globalProperties.getProperty("splitfile").toString().trim());
        }
        
        this.metric = metric;
        metricArray = metric.split(",");
        timeSeries = new String[metricArray.length];
        for (int i = 0; i < metricArray.length; i++) {
            timeSeries[i] = metricArray[i].substring(metricArray[i].lastIndexOf(".")+1);
        }
    }

    public void namechange(String fileName) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(fileName));
            String line;
            int i = 0;
            while ((line = br.readLine()) != null) {
                if (i > 0) {//skip the header
                    i = i + 1;
                    String[] input = line.split(",");
                    if (nameChange.get(input[1]) != null) {//old name being changed exists in nameChange
                        String oldNewName = input[1];
                        String oldName = nameChange.get(input[1]);
                        oldName = oldName + oldNewName + ", ";
                        nameChange.remove(input[1]);
                        nameChange.put(input[2], oldName);
                    } else {
                        nameChange.put(input[2], input[1] + ", ");
                    }
                } else {
                    i = i + 1;
                }
            }
            br.close();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    public void getSplitInformation(String fileName) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(fileName));
            String line;
            int i = 0;
            double newShares = 0;
            while ((line = br.readLine()) != null) {
                i = i + 1;
                if (i > 0) {//skip the header
                    String[] input = line.split(",");
                    if (input[1] != null) {//old name being changed exists in nameChange
                        if (Pattern.compile(Pattern.quote(symbol), Pattern.CASE_INSENSITIVE).matcher(input[1]).find()) {
                            SplitInformation si = new SplitInformation();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                            si.splitDate = sdf.parse(input[0]);
                            si.oldShares = Double.parseDouble(input[2]);
                            si.newShares = Double.parseDouble(input[3]);
                            splits.put(si.splitDate.getTime(), si);
                        }
                    }
                }
            }
            br.close();
            double newshares = 0;
            double oldshares = 0;
            for (SplitInformation si : splits.descendingMap().values()) {
                newshares = newshares == 0 ? si.newShares : newshares * si.newShares / si.oldShares;
                si.newShares = newshares;
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void run() {
        SimpleDateFormat dateFormat;
        try {
            long startTime = new Date().getTime();
            long memoryNow = Runtime.getRuntime().freeMemory();
            System.gc();
            long memoryLater = Runtime.getRuntime().freeMemory();
            long memoryCleared = memoryNow - memoryLater;
            System.out.println("Memory cleared:" + memoryCleared);
            timeZone = TimeZone.getTimeZone(Algorithm.timeZone);
            dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            dateFormat.setTimeZone(timeZone);
            publishTick(symbolName, metricArray);
            long endTime = new Date().getTime();
            long seconds = (endTime - startTime) / 1000;
            System.out.println("Data Published. Symbol: " + symbolName + " ,Time taken: " + seconds);
        } catch (Exception ex) {
            Logger.getLogger(HistoricalDataResponse.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void publishTick(String symbol, String[] metric) {
        try{
        HttpClient client = new HttpClient("http://192.187.112.162:8085");
        String metricnew = null;
        int startCounter = 0;
        TreeMap<Long, HashMap<String, String>> symbolData = new TreeMap<>();
        String[] nameChanges;
        if (nameChange.get(symbol.toUpperCase()) != null) {//namechange exists
            nameChanges = nameChange.get(symbol.toUpperCase()).split(",");
            nameChanges[nameChanges.length - 1] = symbol;
        } else {
            nameChanges = new String[]{symbol};
        }

        for (String s : nameChanges) {

            for (int i = startCounter; i < metric.length; i++) {
                metricnew = metric[i];
                QueryBuilder builder = QueryBuilder.getInstance();
                builder.setStart(new Date(startTime))
                        .setEnd(new Date(endTime))
                        .addMetric(metric[i])
                        .addTag("symbol", s.replaceAll("[^A-Za-z0-9]", "").trim().toLowerCase());
                builder.getMetrics().get(0).setOrder(QueryMetric.Order.ASCENDING);
                if (expiry != null) {
                    builder.getMetrics().get(0).addTag("expiry", expiry);
                }
                if (right != null) {
                    builder.getMetrics().get(0).addTag("right", right);
                    builder.getMetrics().get(0).addTag("strike", strike);
                }

                long time = new Date().getTime();
                //System.out.println(symbol.fullname);
                QueryResponse response = client.query(builder);
                List<DataPoint> dataPoints = response.getQueries().get(0).getResults().get(0).getDataPoints();
                for (DataPoint dataPoint : dataPoints) {
                    long lastTime = dataPoint.getTimestamp();
                    Object value = dataPoint.getValue();
                    String dataType = metricnew.substring(metricnew.lastIndexOf(".") + 1);
                    if (symbolData.get(lastTime) == null) {
                        HashMap<String, String> h = new HashMap<>();
                        h.put(dataType, value.toString());
                        symbolData.put(lastTime, h);
                    } else {
                        symbolData.get(lastTime).put(dataType, value.toString());
                    }
                }
            }
        }

        //Now publish data to socket.
        for ( Map.Entry<Long,HashMap<String,String>>entry : symbolData.entrySet()) {
            long time=entry.getKey();
            String response="backfill" + ":" + this.symbol + ":::" + requestid + ":" + time ;
            for(String s: timeSeries){
                String value=entry.getValue().get(s);
                response=response+"_"+value;
            }
            ServerResponse.responder.sendMore(response);
            logger.log(Level.FINE, "Published: {0}", new Object[]{new Date(time) + "_" + this.symbol});
        }
        Thread.sleep(10);
        //}
        //String response = requestid + "_" + "completed";
        //ResponseServer.responder.send(response);
        long memoryNow = Runtime.getRuntime().freeMemory();
        System.gc();
        long memoryLater = Runtime.getRuntime().freeMemory();
        long memoryCleared = memoryNow - memoryLater;
        System.out.println("Memory cleared:" + memoryCleared);
        client.shutdown();
        }catch (Exception e){
            logger.log(Level.SEVERE,null,e);
        }
    }

    public OHLCV getSplitShares(OHLCV d) {
        if (splits.size() > 0) {
            Long time = splits.ceilingKey(d.getTime() + 1);
            if (time != null) {
                double newShares = splits.get(time).newShares;
                double oldShares = splits.get(time).oldShares;
                Double open = d.getOpen() != null ? Double.parseDouble(d.getOpen()) * oldShares / newShares : null;
                Double high = d.getHigh() != null ? Double.parseDouble(d.getOpen()) * oldShares / newShares : null;
                Double low = d.getLow() != null ? Double.parseDouble(d.getOpen()) * oldShares / newShares : null;
                Double close = d.getClose() != null ? Double.parseDouble(d.getOpen()) * oldShares / newShares : null;
                Long volume = d.getVolume() != null ? (long) (Long.parseLong(d.getVolume()) * newShares / oldShares) : null;
                Long dayvolume = d.getDayvolume() != null ? (long) (Long.parseLong(d.getDayvolume()) * newShares / oldShares) : null;
                Integer oi = d.getOi() != null ? (int) (Integer.parseInt(d.getOi()) * newShares / oldShares) : null;

                d.setOpen(open != null ? String.valueOf(open) : null);
                d.setHigh(high != null ? String.valueOf(high) : null);
                d.setLow(low != null ? String.valueOf(low) : null);
                d.setClose(close != null ? String.valueOf(close) : null);
                d.setVolume(volume != null ? String.valueOf(volume) : null);
                d.setDayvolume(dayvolume != null ? String.valueOf(dayvolume) : null);
                d.setOi(oi != null ? String.valueOf(oi) : null);

            }

        }
        return d;
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
