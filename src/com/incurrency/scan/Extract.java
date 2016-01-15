/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scan;

import java.util.logging.Logger;

/**
 *
 * @author admin
 */
public class Extract implements Runnable {

    private static final Logger logger = Logger.getLogger(Extract.class.getName());

    @Override
    public void run() {
        while (!Scanner.dateProcessing.empty()) {
            String header = null;
            String content = null;

            switch (Scanner.type) {                         
                default:
                    break;
            }
            //print extendedhash map
            //Scanner.syncDates.take();
        }
    }
}
