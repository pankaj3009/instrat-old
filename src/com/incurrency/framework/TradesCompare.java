/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.Comparator;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class TradesCompare implements Comparator<Trade> {

    private final static Logger logger = Logger.getLogger(TradesCompare.class.getName());

    @Override
    public int compare(Trade t1, Trade t2) {
        if (DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", t1.getEntryTime()).compareTo(DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", t2.getEntryTime()))>0) {
            return 1;
        } if (DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", t1.getEntryTime()).compareTo(DateUtil.parseDate("yyyy-MM-dd HH:mm:ss", t2.getEntryTime()))<=0) {
            return -1;
        } else {
            return 0;
        }
    }
}
