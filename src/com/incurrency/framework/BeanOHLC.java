/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.io.Serializable;
import java.util.logging.Logger;

/**
 *
 * @author jaya
 */
public class BeanOHLC implements Serializable {

    private EnumBarSize periodicity;
    private long openTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private long oi;
    private final static Logger logger = Logger.getLogger(BeanOHLC.class.getName());

    public BeanOHLC(BeanOHLC ohlc) {
        this.openTime = ohlc.getOpenTime();
        this.open = ohlc.getOpen();
        this.high = ohlc.getHigh();
        this.low = ohlc.getLow();
        this.close = ohlc.getClose();
        this.volume = ohlc.getVolume();
        this.periodicity = ohlc.periodicity;
    }

    public BeanOHLC(long opentime, double open, double high, double low, double close, long volume, EnumBarSize periodicity) {
        this.openTime = opentime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.periodicity = periodicity;
    }

    public BeanOHLC() {
        
    }

    /**
     * @return the periodicity
     */
    public EnumBarSize getPeriodicity() {
        return periodicity;
    }

    /**
     * @param periodicity the periodicity to set
     */
    public void setPeriodicity(EnumBarSize periodicity) {
        this.periodicity = periodicity;
    }

    public long getOpenTime() {
        return openTime;
    }

    public void setOpenTime(long time) {

        this.openTime = time;
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;

    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
    }

    public long getVolume() {
        return volume;
    }

    public void setVolume(long volume) {
        this.volume = volume;
    }

    /**
     * @return the oi
     */
    public long getOi() {
        return oi;
    }

    /**
     * @param oi the oi to set
     */
    public void setOi(long oi) {
        this.oi = oi;
    }
}
