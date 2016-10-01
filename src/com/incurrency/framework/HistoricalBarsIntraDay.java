/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.RatesClient.RequestClient;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class HistoricalBarsIntraDay implements Runnable {

    public EnumBarSize barSize;
    public EnumSource source;
    public Date startDate;
    public Date endDate;
    public BeanSymbol s;
    public int tradingMinutes;
    public TimeZone timeZone;
    int tradeCloseHour;
    int tradeCloseMinute;
    int tradeOpenHour;
    int tradeOpenMinute;
    List<String> holidays;
    private static final Logger logger = Logger.getLogger(HistoricalBarsIntraDay.class.getName());
    String path;
    
        public HistoricalBarsIntraDay(){
         path = Algorithm.globalProperties.getProperty("path").toString().toLowerCase();        
         this.source=EnumSource.CASSANDRA;
    }
    
    public HistoricalBarsIntraDay(EnumSource source, EnumBarSize barSize, Date startDate, Date endDate, BeanSymbol s) {
        switch (source) {
            case IB:
                this.barSize = barSize;
                this.source = source;
                this.startDate = startDate;
                this.endDate = endDate;
                this.s = s;
                this.holidays = Algorithm.holidays;
                this.timeZone = TimeZone.getTimeZone(Algorithm.globalProperties.getProperty("timezone").toString().trim());
                SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
                this.tradeCloseHour = Integer.valueOf(Algorithm.globalProperties.getProperty("closehour","15").toString().trim());
                this.tradeCloseMinute = Integer.valueOf(Algorithm.globalProperties.getProperty("closeminute","30").toString().trim());
                this.tradeOpenHour = Integer.valueOf(Algorithm.globalProperties.getProperty("openhour","9").toString().trim());
                this.tradeOpenMinute = Integer.valueOf(Algorithm.globalProperties.getProperty("openminute","15").toString().trim());
                this.tradingMinutes = tradeCloseHour*60+tradeCloseMinute-tradeOpenHour*60-tradeOpenMinute;
                System.out.println("Historical Bar Thread started.StartDate:" + startDate.toString() + " EndDate:" + endDate.toString());
                break;
            case CASSANDRA:
                this.source = source;
                this.barSize = barSize;
                this.s = s;
                path = Algorithm.globalProperties.getProperty("path").toString().toLowerCase();  
                break;
            default:
                break;
        }
    }

    public int estimatedTime() {
        int iterations = 0;
        String duration = "";
        int allowedBars = 2000;
        int connections = 0;
        int secondsPerIteration = 0;
        Calendar startCalendar = Calendar.getInstance();
        startCalendar.setTimeInMillis(startDate.getTime());
        Calendar endCalendar = Calendar.getInstance();
        endCalendar.setTimeInMillis(endDate.getTime());
        int days = (int) ((endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24));
        String ibBarSize = null;
        switch (barSize) {
            case ONESECOND:
                iterations = 1 + ((int) ((days * tradingMinutes) / 30.00));
                System.out.println("Iterations:" + iterations);
                duration = "1800 S";
                ibBarSize = "1 secs";
                break;
            case FIVESECOND:
                iterations = (int) (days * tradingMinutes / 7200) + 1;
                duration = "7200 S";
                ibBarSize = "5 sec";
                break;
            case ONEMINUTE:
                iterations = (int) (days * 5) + 1;
                duration = "5 D";
                ibBarSize = "1 min";
                break;
            case DAILY:
                iterations = days + 1;
                duration = "1 Y";
                ibBarSize = "1 day";
                break;
            default:
                break;
        }
        for (BeanConnection c : Parameters.connection) {
            if (c.getHistMessageLimit() > 0) {
                connections++;
                secondsPerIteration = Math.max(secondsPerIteration, c.getHistMessageLimit());
            }
        }
        System.out.println("Connections:" + connections);
        return (int) (iterations * secondsPerIteration / connections);
    }

    @Override
    public void run() {
        switch (source) {
            case IB:
                int iterations = 0;
                String duration = "";
                int allowedBars = 2000;
                Calendar startCalendar = Calendar.getInstance();
                startCalendar.setTimeInMillis(startDate.getTime());
                Calendar endCalendar = Calendar.getInstance();
                endCalendar.setTimeInMillis(endDate.getTime());
                int days = (int) ((endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24));
                String ibBarSize = null;
                switch (barSize) {
                    case ONESECOND:
                        iterations = (int) (days * tradingMinutes / 30 * 60) + 1;
                        duration = "1800 S";
                        ibBarSize = "1 secs";
                        break;
                    case FIVESECOND:
                        iterations = (int) (days * tradingMinutes / 7200) + 1;
                        duration = "7200 S";
                        ibBarSize = "5 sec";
                        break;
                    case ONEMINUTE:
                        iterations = (int) (days * 5) + 1;
                        duration = "5 D";
                        ibBarSize = "1 min";
                        break;
                    case DAILY:
                        iterations = days + 1;
                        duration = "1 Y";
                        ibBarSize = "1 day";
                        break;
                    default:
                        break;
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                sdf.setTimeZone(timeZone);
                System.out.println("Processing:" + s.getDisplayname()+",Progress:"+s.getSerialno()+"/"+Parameters.symbol.size());
                ArrayList<BeanConnection> useConnection = new ArrayList<>();
                int connectionsInUse = 0;
                for (BeanConnection c : Parameters.connection) {
                    if (c.getHistMessageLimit() > 0) {
                        useConnection.add(c);
                        connectionsInUse++;
                    }
                }
                boolean completed = false;
                for (int i = 0; i < iterations && !completed; i++) {
                    switch (ibBarSize) {
                        case "1 secs":
                            startDate = nextGoodDay(startDate, 0);
                            //System.out.println("New Start Date:"+startDate.toString());
                            break;
                        case "5 sec":
                            startDate = nextGoodDay(startDate, 0);
                            break;
                        case "1 min":
                            startDate = nextGoodDay(startDate, 0); //move to next day
                            break;
                        case "1 day":
                            startDate = new Date(0);
                            break;
                        default:
                            break;
                    }
                    Date tempDate = startDate;

                    String tempDateString = sdf.format(tempDate);
                    if (!tempDate.after(endDate)) {
                        int connectionId = i % connectionsInUse;
                        System.out.println("Connection Being Used:" + connectionId);

                        useConnection.get(connectionId).getWrapper().requestHistoricalData(s, tempDateString, duration, ibBarSize);
                        if (connectionId == connectionsInUse - 1) {//finished a loop through connections. Sleep
                            try {
                                Thread.sleep(useConnection.get(connectionId).getHistMessageLimit() * 1000);
                            } catch (InterruptedException ex) {
                                logger.log(Level.SEVERE, null, ex);
                            }
                        }
                    } else {
                        completed = true;
                    }
                    switch (ibBarSize) {
                        case "1 secs":
                            startDate = nextGoodDay(startDate, 30);
                            //System.out.println("New Start Date:"+startDate.toString());
                            break;
                        case "5 sec":
                            startDate = nextGoodDay(startDate, 120);
                            break;
                        case "1 min":
                            startDate = nextGoodDay(startDate, 7200); //move to next day
                            break;
                        case "1 day":
                            startDate = new Date(0);
                            break;
                        default:
                            break;
                    }
                }
                break;
            case CASSANDRA:
             RequestClient rc;
             rc=new RequestClient(path);
           completed=false;
           //requestCassandraBars(s,barSize) ;    
                break;
            default:
                break;
        }


    }
   
    
    public Date nextGoodDay(Date baseDate, int minuteAdjust) {
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
        int exitMinute = exitCal.get(Calendar.MINUTE);
        int exitHour = exitCal.get(Calendar.HOUR_OF_DAY);
        int exitDayOfWeek = exitCal.get(Calendar.DAY_OF_WEEK);

        boolean adjust = true;

        if (exitHour > tradeCloseHour || (exitHour == tradeCloseHour && exitMinute > tradeCloseMinute)) {
            //1.get minutes from close
            int minutesFromClose = (tradeCloseHour - entryHour) > 0 ? (tradeCloseHour - entryHour) * 60 : 0 + this.tradeCloseMinute - entryMinute;
            int minutesCarriedForward = minuteAdjust - minutesFromClose;
            exitCal.add(Calendar.DATE, 1);
            exitCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            exitCal.set(Calendar.MINUTE, tradeOpenMinute);
            exitCal.set(Calendar.MILLISECOND, 0);
            exitCal.add(Calendar.MINUTE, minutesCarriedForward);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String exitCalString = sdf.format(exitCal.getTime());
        while (exitCal.get(Calendar.DAY_OF_WEEK) == 7 || exitCal.get(Calendar.DAY_OF_WEEK) == 1 || holidays.contains(exitCalString)) {
            exitCal.add(Calendar.DATE, 1);
        }
        if (exitHour < tradeOpenHour || (exitHour == tradeCloseHour && exitMinute < tradeOpenMinute)) {
            //1.get minutes from close
            //int minutesFromClose=(tradeCloseHour-entryHour)>0?(tradeCloseHour-entryHour)*60:0+this.tradeCloseMinute-entryMinute;
            //int minutesCarriedForward=minuteAdjust-minutesFromClose;
            exitCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            exitCal.set(Calendar.MINUTE, tradeOpenMinute);
            exitCal.set(Calendar.MILLISECOND, 0);
            exitCal.add(Calendar.MINUTE, minuteAdjust);
        }
        return exitCal.getTime();


    }
}
