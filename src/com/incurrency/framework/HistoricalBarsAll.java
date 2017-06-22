/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

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
public class HistoricalBarsAll implements Runnable {

    public static int connectionnumber = -1;
    private static final Logger logger = Logger.getLogger(HistoricalBarsAll.class.getName());

    public String barSize;
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

    public HistoricalBarsAll(String barSize, Date startDate, Date endDate, BeanSymbol s, int tradingMinutes, String openTime, String endTime, TimeZone timeZone, List<String> holidays) {
        this.barSize = barSize;
        this.startDate = startDate;
        this.endDate = endDate;
        this.s = s;
        this.holidays = holidays;
        this.tradingMinutes = tradingMinutes;
        this.timeZone = timeZone;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        this.tradeCloseHour = Integer.valueOf(endTime.substring(0, 2));
        this.tradeCloseMinute = Integer.valueOf(endTime.substring(3, 5));
        this.tradeOpenHour = Integer.valueOf(openTime.substring(0, 2));
        this.tradeOpenMinute = Integer.valueOf(openTime.substring(3, 5));
        //System.out.println("Historical Bar Thread started.StartDate:" + startDate.toString() + " EndDate:" + endDate.toString());
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
            case "1sec":
                iterations = 1 + ((int) ((days * tradingMinutes) / 30.00));
                //System.out.println("Iterations:" + iterations);
                duration = MainAlgorithm.globalProperties.getProperty("1sec", "1800 S").toString();
                ibBarSize = "1 secs";
                break;
            case "5sec":
                iterations = (int) (days * tradingMinutes / 7200) + 1;
                duration = MainAlgorithm.globalProperties.getProperty("5sec", "7200 S").toString();
//                duration = "7200 S";
                ibBarSize = "5 sec";
                break;
            case "1min":
                iterations = (int) (days * 5) + 1;
                duration = MainAlgorithm.globalProperties.getProperty("1min", "5 D").toString();
                //               duration = "5 D";
                ibBarSize = "1 min";
                break;
            case "1day":
                iterations = days + 1;
                duration = MainAlgorithm.globalProperties.getProperty("1day", "1 Y").toString();
//                duration = "1 Y";
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
        //System.out.println("Connections:" + connections);
        return (int) (iterations * secondsPerIteration / connections);
    }

    @Override
    public void run() {
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
            case "1sec":
                iterations = (int) (days * tradingMinutes / 30 * 60) + 1;
                duration = MainAlgorithm.globalProperties.getProperty("1sec", "1800 S").toString();
                //duration = "1800 S";
                ibBarSize = "1 secs";
                break;
            case "5sec":
                iterations = (int) (days * tradingMinutes / 7200) + 1;
                duration = MainAlgorithm.globalProperties.getProperty("5sec", "7200 S").toString();
//              duration = "7200 S";
                ibBarSize = "5 sec";
                break;
            case "1min":
                iterations = (int) (days * 10) + 1;
                duration = MainAlgorithm.globalProperties.getProperty("1min", "5 D").toString();
                //             duration = "10 D";
                ibBarSize = "1 min";
                break;
            case "1day":
                iterations = days + 1;
                duration = MainAlgorithm.globalProperties.getProperty("1day", "1 Y").toString();
//              duration = "1 Y";
                ibBarSize = "1 day";
                break;
            default:
                break;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        sdf.setTimeZone(timeZone);
        System.out.println("Processing:" + s.getDisplayname() + ",Progress:" + s.getSerialno() + "/" + Parameters.symbol.size());
        ArrayList<BeanConnection> useConnection = new ArrayList<>();
        int connectionsInUse = 0;
        for (BeanConnection c : Parameters.connection) {
            if (c.getHistMessageLimit() > 0) {
                useConnection.add(c);
                connectionsInUse++;
            }
        }
        boolean completed = false;
        TWSConnection.skipsymbol = false;
        while (!completed) {
            int i = connectionnumber + 1;
            connectionnumber = i;
            if (i == 0) {

            }
            if (TWSConnection.skipsymbol == true) {
                completed = true;
            }
            if (i > 0 && i % (connectionsInUse) == 0 && !TWSConnection.skipsymbol) {
                try {
                    Thread.sleep(useConnection.get(0).getHistMessageLimit() * 1000);
                } catch (InterruptedException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
            Date oldStartDate = nextGoodDay(startDate, 0);
            int connectionId = i % connectionsInUse;
            if (useConnection.get(connectionId).getWrapper().isHistoricalDataFarmConnected() && !TWSConnection.skipsymbol) {
                switch (ibBarSize) {
                    case "1 secs":
                        startDate = nextGoodDay(startDate, 30);
                        //System.out.println("New Start Date:"+startDate.toString());
                        break;
                    case "5 sec":
                        startDate = nextGoodDay(startDate, 120);
                        break;
                    case "1 min":
                        startDate = nextGoodDay(startDate, 14400); //move to next day
                        break;
                    case "1 day":
                        startDate = new Date(0);
                        break;
                    default:
                        break;
                }

                /*
                 switch (ibBarSize) {
                 case "1 secs":
                 startDate = nextGoodDay(startDate,30);
                 //System.out.println("New Start Date:"+startDate.toString());
                 break;
                 case "5 sec":
                 startDate = nextGoodDay(startDate,120);
                 break;
                 case "1 min":
                 startDate = nextGoodDay(startDate,7200); //move to next day
                 break;
                 case "1 day":
                 startDate = new Date(0);
                 break;
                 default:
                 break;
                 }
                 */
                Date tempDate = startDate.after(endDate) ? endDate : startDate;

                String tempDateString = sdf.format(tempDate);
                if (!oldStartDate.after(endDate)) {
                    //int connectionId = i % connectionsInUse;
                    //System.out.println("Connection Being Used:" + connectionId);
                    useConnection.get(connectionId).getWrapper().requestHistoricalData(s, tempDateString, duration, ibBarSize);
                    /*
                    if (connectionId == connectionsInUse - 1) {//finished a loop through connections. Sleep
                        try {
                            Thread.sleep(useConnection.get(connectionId).getHistMessageLimit() * 1000);
                        } catch (InterruptedException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                     */
                } else {
                    try {
                        //if (i > 0) {
                        connectionnumber = connectionnumber - 1;
                        //Thread.sleep(useConnection.get(0).getHistMessageLimit() * 1000);
                        //}
                    } catch (Exception ex) {
                        Logger.getLogger(HistoricalBarsAll.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    completed = true;
                }
            }
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
        //if(minuteAdjust==0){
        //    exitCal.add(Calendar.SECOND, 1);
        //}
        int exitMinute = exitCal.get(Calendar.MINUTE);
        int exitHour = exitCal.get(Calendar.HOUR_OF_DAY);
        int exitDayOfWeek = exitCal.get(Calendar.DAY_OF_WEEK);

        boolean adjust = true;

        if (exitHour > tradeCloseHour || (exitHour == tradeCloseHour && exitMinute >= tradeCloseMinute)) {
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
        while (exitCal.get(Calendar.DAY_OF_WEEK) == 7 || exitCal.get(Calendar.DAY_OF_WEEK) == 1 || (holidays != null && holidays.contains(exitCalString))) {
            exitCal.add(Calendar.DATE, 1);
            exitCalString = sdf.format(exitCal.getTime());
        }
        if (exitHour < tradeOpenHour || (exitHour == tradeOpenHour && exitMinute < tradeOpenMinute)) {
            //1.get minutes from close
            //int minutesFromClose=(tradeCloseHour-entryHour)>0?(tradeCloseHour-entryHour)*60:0+this.tradeCloseMinute-entryMinute;
            //int minutesCarriedForward=minuteAdjust-minutesFromClose;

            exitCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            exitCal.set(Calendar.MINUTE, tradeOpenMinute);
            exitCal.set(Calendar.MILLISECOND, 0);
//            exitCal.add(Calendar.MINUTE, minuteAdjust);
        }
        return exitCal.getTime();

    }
}
