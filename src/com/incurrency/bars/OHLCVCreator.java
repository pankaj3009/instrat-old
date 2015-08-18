/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.bars;

import com.incurrency.RatesClient.Subscribe;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.ReservedValues;
import com.incurrency.framework.TradeEvent;
import com.incurrency.framework.TradeListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * @author Pankaj
 */
public class OHLCVCreator implements TradeListener {

    List<Integer> symbolids = new ArrayList<>();
    EnumBarSize barSize;
    boolean useSettle;
    Date startDate = new Date();
    static HashMap<Integer, String> barsCreated = new HashMap<>();

    public OHLCVCreator(int symbolid, EnumBarSize barSize, boolean useSettle, Date startDate) {
        if (barsCreated.get(symbolid) == null || (barsCreated.get(symbolid) != null && !barsCreated.get(symbolid).contains(barSize.toString()))) {
            this.symbolids.add(symbolid);
            this.barSize = barSize;
            this.useSettle = useSettle;
            this.startDate = startDate;
            if (Subscribe.tes != null) {
                Subscribe.tes.addTradeListener(this);
            }
            int durationInMinutes = 1;
            switch (barSize) {
                case ONEMINUTE:
                    durationInMinutes = 1;
                    break;
                case FIVEMINUTE:
                    durationInMinutes = 5;
                    break;
                default:
                    break;
            }
            Timer barProcessing = new Timer("Timer: Generate Zero Volume Bars");
            barProcessing.scheduleAtFixedRate(barProcessingTask, startDate, durationInMinutes * 60 * 1000);
        }
    }

    public OHLCVCreator(List<Integer> symbolids, EnumBarSize barSize, boolean useSettle, Date startDate) {
        for (int id : symbolids) {
            if (barsCreated.get(id) == null || (barsCreated.get(id) != null && !barsCreated.get(id).contains(barSize.toString()))) {
                this.symbolids.add(id);
            }
        }
        if (symbolids.size() > 0) {
            this.barSize = barSize;
            this.useSettle = useSettle;
            this.startDate = startDate;
            if (Subscribe.tes != null) {
                Subscribe.tes.addTradeListener(this);
            }

            if (Subscribe.tes != null) {
                Subscribe.tes.addTradeListener(this);
            }
            int durationInMinutes = 1;
            switch (barSize) {
                case ONEMINUTE:
                    durationInMinutes = 1;
                    break;
                case FIVEMINUTE:
                    durationInMinutes = 5;
                    break;
                default:
                    break;
            }
            Timer barProcessing = new Timer("Timer: Generate Zero Volume Bars");
            barProcessing.schedule(barProcessingTask, startDate, durationInMinutes * 60 * 1000);
        }
    }
    TimerTask barProcessingTask = new TimerTask() {
        @Override
        public void run() {
            for (int id : symbolids) {
                BeanSymbol s = Parameters.symbol.get(id);
                long time = s.getLastBarEndTime(barSize);
                if (time < (new Date()).getTime()) {
                    createZeroVolumeBar(id, time);
                }
                Subscribe.tes.fireBars(id, time, barSize);
            }

        }
    };

    @Override
    public void tradeReceived(TradeEvent event) {
        if ((event.getTickType() == com.ib.client.TickType.LAST || event.getTickType() == com.ib.client.TickType.LAST_SIZE)
                && symbolids.contains(event.getSymbolID())) {
            addOHLCV(event);
        }
    }

    /**
     * Adds a trade event to OHLC bars
     *
     * @param event
     */
    public void addOHLCV(TradeEvent event) {
        BeanSymbol s = Parameters.symbol.get(event.getSymbolID());
        long startTime = s.getLastBarStartTime(barSize);
        if (event.getTickType() == com.ib.client.TickType.LAST) {
            String closeString = "close";
            if (useSettle) {
                closeString = "settle";
            }
            if (s.getTimeSeries(barSize, closeString)==null){
                createZeroVolumeBar(event.getSymbolID(),startTime);
            }
            Double open = s.getBarData(barSize, "open", 0);
            Double high = s.getBarData(barSize, "high", 0);
            Double low = s.getBarData(barSize, "low", 0);
            if (open == ReservedValues.EMPTY || open == 0) {
                s.setTimeSeries(barSize, startTime, "open", s.getLastPrice());
                s.setTimeSeries(barSize, startTime, "high", s.getLastPrice());
                s.setTimeSeries(barSize, startTime, "low", s.getLastPrice());
                s.setTimeSeries(barSize, startTime, closeString, s.getLastPrice());
            } else if (s.getLastPrice() > high) {
                s.setTimeSeries(barSize, startTime, "high", s.getLastPrice());
            } else if (s.getLastPrice() < low) {
                s.setTimeSeries(barSize, startTime, "low", s.getLastPrice());
            }
            s.setTimeSeries(barSize, startTime, closeString, s.getLastPrice());

        } else if (event.getTickType() == com.ib.client.TickType.LAST_SIZE) {
            Double volume = s.getBarData(barSize, "volume", 0);
            volume = volume == ReservedValues.EMPTY ? 0 : volume;
            volume = volume + s.getLastSize();
            s.setTimeSeries(barSize, startTime, "volume", volume);
        }
    }

    private long getBarEndTime(int symbolid) {
        BeanSymbol s = Parameters.symbol.get(symbolid);
        return s.getLastBarEndTime(barSize);
    }

    private void createZeroPriceBar(int symbolid, long time) {
        BeanSymbol s = Parameters.symbol.get(symbolid);
        String[] labels = new String[]{"open", "high", "low", "close", "volume"};
        ArrayList<Long> timeList = new ArrayList<>();
        if (useSettle) {
            labels = new String[]{"open", "high", "low", "settle", "volume"};
        }
        timeList.add(time);
        s.initTimeSeries(barSize, labels, timeList);
        double[] values = new double[]{0, 0, 0, 0, 0};
        s.addTimeSeries(barSize, labels, time, values);
    }

    private void createZeroVolumeBar(int symbolid, long time) {
        BeanSymbol s = Parameters.symbol.get(symbolid);
        if(s.getTimeStampIndex(barSize, time)==-1){
        String[] labels = new String[]{"open", "high", "low", "close", "volume"};
        ArrayList<Long> timeList = new ArrayList<>();
        if (useSettle) {
            labels = new String[]{"open", "high", "low", "settle", "volume"};
        }
        timeList.add(time);
        s.initTimeSeries(barSize, labels, timeList);
        long lastBarStartTime = s.getLastBarStartTime(barSize);
        String closeString = "close";
        if (useSettle) {
            closeString = "settle";
        }
        double lastClose = s.getTimeSeriesValue(barSize, lastBarStartTime, closeString);
        double[] values = new double[]{lastClose, lastClose, lastClose, lastClose, 0};
        s.addTimeSeries(barSize, labels, time, values);
        }
    }
}
