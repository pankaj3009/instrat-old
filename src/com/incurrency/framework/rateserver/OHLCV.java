/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

/**
 *
 * @author pankaj
 */
public class OHLCV implements Comparable {

    private Long time;
    private String open;
    private String high;
    private String low;
    private String close;
    private String volume;
    private String oi;
    private String symbol;
    private String dayvolume;

    public OHLCV(long time) {
        this.time = time;
    }

    public OHLCV(long time, String close, String symbol) {
        this.time = time;
        this.close = close;
        this.symbol = symbol;
    }

    public OHLCV(long time, String symbol) {
        this.time = time;
        this.symbol = symbol;
    }

    /**
     * @return the time
     */
    public Long getTime() {
        return time;
    }

    /**
     * @param time the time to set
     */
    public void setTime(Long time) {
        this.time = time;
    }

    /**
     * @return the open
     */
    public String getOpen() {
        return open;
    }

    /**
     * @param open the open to set
     */
    public void setOpen(String open) {
        this.open = open;
    }

    /**
     * @return the high
     */
    public String getHigh() {
        return high;
    }

    /**
     * @param high the high to set
     */
    public void setHigh(String high) {
        this.high = high;
    }

    /**
     * @return the low
     */
    public String getLow() {
        return low;
    }

    /**
     * @param low the low to set
     */
    public void setLow(String low) {
        this.low = low;
    }

    /**
     * @return the close
     */
    public String getClose() {
        return close;
    }

    /**
     * @param close the close to set
     */
    public void setClose(String close) {
        this.close = close;
    }

    /**
     * @return the volume
     */
    public String getVolume() {
        return volume;
    }

    /**
     * @param volume the volume to set
     */
    public void setVolume(String volume) {
        this.volume = volume;
    }

    @Override
    public int compareTo(Object o) {
        OHLCV other = (OHLCV) o;
        if (this.symbol.compareTo(other.symbol) < 0) {
            return -1;
        }
        if (this.symbol.compareTo(other.symbol) > 0) {
            return 1;
        }
        return 0;
    }

    /**
     * @return the symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * @param symbol the symbol to set
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /**
     * @return the oi
     */
    public String getOi() {
        return oi;
    }

    /**
     * @param oi the oi to set
     */
    public void setOi(String oi) {
        this.oi = oi;
    }

    /**
     * @return the dayvolume
     */
    public String getDayvolume() {
        return dayvolume;
    }

    /**
     * @param dayvolume the dayvolume to set
     */
    public void setDayvolume(String dayvolume) {
        this.dayvolume = dayvolume;
    }

}
