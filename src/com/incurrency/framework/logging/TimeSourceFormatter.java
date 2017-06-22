/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.logging;

import com.incurrency.framework.TradingUtil;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 *
 * @author psharma
 */
public final class TimeSourceFormatter extends SimpleFormatter {

    @Override
    public String format(LogRecord aLogRecord) {
        aLogRecord.setMillis(TradingUtil.getAlgoDate().getTime());
        return super.format(aLogRecord);
    }

}
