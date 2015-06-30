/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pankaj
 */
public class Cassandra implements Runnable {

    String value;
    long time;
    String metric;
    String symbol;
    String expiry;
    PrintStream output;

    public Cassandra(String value, long time, String metric, String symbol, String expiry, PrintStream output) {
        this.value = value;
        this.time = time;
        this.metric = metric;
        this.symbol = symbol;
        this.expiry = expiry;
        this.output = output;

    }

    @Override
    public void run() {
        try {
            if (expiry == null) {
                output.print("put " + metric + " " + time + " " + value + " " + "symbol=" + symbol.replace("&", "").toLowerCase() + System.getProperty("line.separator"));
            } else {
                output.print("put " + metric + " " + time + " " + value + " " + "symbol=" + symbol.replace("&", "").toLowerCase() + " " + "expiry=" + expiry + System.getProperty("line.separator"));
            }
        } catch (Exception ex) {
            Logger.getLogger(Cassandra.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            //output.close();
        }
    }
}
