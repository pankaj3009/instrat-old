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
public class Cassandra {

    String value;
    long time;
    String metric;
    String symbol;
    String expiry;
    PrintStream output;
    private static final Logger logger = Logger.getLogger(Cassandra.class.getName());

    public Cassandra(String value, long time, String metric, String symbol, String expiry, PrintStream output) {
        this.value = value;
        this.time = time;
        this.metric = metric;
        this.symbol = symbol;
        this.expiry = expiry;
        this.output = output;

    }
/*
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
  */  
    public void write() {
        try {
            if (expiry.equals("")) {
                //logger.log(Level.INFO,"Symbol:{0}",new Object[]{symbol});
                output.print("put " + metric + " " + time + " " + value + " " + "symbol=" + symbol.split("_")[0].replace("&", "").toLowerCase() + System.getProperty("line.separator"));
                
            } else {
                //logger.log(Level.INFO,"Symbol:{0}",new Object[]{symbol});
                output.print("put " + metric + " " + time + " " + value + " " + "symbol=" + symbol.split("_")[0].replace("&", "").toLowerCase() + " " + "expiry=" + expiry + System.getProperty("line.separator"));
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            //output.close();
        }
    }
}
