/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * This call is used only for daily bars. For intra-day bars, use
 * HistoricalBarsAll
 *
 * @author pankaj
 */
public class HistoricalBars implements Runnable {

    Thread t;
    String strategyFilter;
    String typeFilter;
    EnumSource source;
    String[] timeSeries;
    String metric;
    String startTime;
    String endTime;
    boolean appendAtEnd;
    EnumBarSize barSize;
    private static final Logger logger = Logger.getLogger(HistoricalBars.class.getName());

    public HistoricalBars(String strategyFilter, String typeFilter, EnumSource source,String[] timeSeries,String metric,String startTime, String endTime,EnumBarSize barSize, boolean appendAtEnd) {
        this.strategyFilter = strategyFilter;
        this.typeFilter = typeFilter;
        this.source = source;
        this.timeSeries=timeSeries;
        this.metric=metric;
        this.startTime=startTime;
        this.endTime=endTime;
        this.appendAtEnd=appendAtEnd;
        this.barSize=barSize;
    }

//    public HistoricalBars(String ThreadName){
    //   }
    @Override
    public void run() {
        try {
            switch (source) {
                case IB:
                    int connectionCount = Parameters.connection.size();
                    int i = 0;
                    for (BeanSymbol s : Parameters.symbol) {
                        if (s.getDailyBar().getHistoricalBars().size() == 0 && Pattern.compile(Pattern.quote(strategyFilter), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                            if ("".compareTo(typeFilter) != 0 && s.getType().compareTo(typeFilter) == 0) {
                                while (Parameters.connection.get(i).getHistMessageLimit() == 0) {
                                    i = i + 1;
                                    if (i >= connectionCount) {
                                        i = 0;
                                    }
                                }
                                Parameters.connection.get(i).getWrapper().requestDailyBar(s, "1 Y");
                                i = i + 1;
                                if (i >= connectionCount) {
                                    i = 0; //reset counter
                                    try {
                                        Thread.sleep(Parameters.connection.get(i).getHistMessageLimit() * 1000);
                                    } catch (InterruptedException ex) {
                                        logger.log(Level.SEVERE, null, ex);
                                    }
                                }
                            }
                        }
                    }
                    Thread.sleep(11000);
                    break;
                case CASSANDRA:
                    for (BeanSymbol s : Parameters.symbol) {
                        if (s.getTimeSeriesLength(barSize) == 0 && Pattern.compile(Pattern.quote(strategyFilter), Pattern.CASE_INSENSITIVE).matcher(s.getStrategy()).find()) {
                            if ("".compareTo(typeFilter) != 0 && s.getType().compareTo(typeFilter) == 0) {
                                Utilities.requestHistoricalData(s,timeSeries,metric,startTime,endTime,barSize,appendAtEnd);
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "101", ex);
        }
    }
}
