/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class Drop {

    // Message sent from producer to consumer.
    private String message;
    // True if consumer should wait
    // for producer to send message,
    // false if producer should wait for
    // consumer to retrieve message.
    private boolean empty = true;
    private final Object mon = new Object();
    private static final Logger logger = Logger.getLogger(Drop.class.getName());

    public String take() {
        // Wait until message is
        // available.
        synchronized (mon) {
            if (empty) {

                try {
                    mon.wait();
                } catch (InterruptedException ex) {
                    logger.log(Level.SEVERE, "101", ex);
                }
            }
            // Toggle status.
            empty = true;
            // Notify producer that
            // status has changed.
            this.notifyAll();
            return message;
        }
    }

    public boolean empty() {
        synchronized (mon) {
            return empty;
        }
    }

    public String value() {
        synchronized (mon) {
            return message;
        }
    }

    public void put(String message) {
        // Wait until message has
        // been retrieved.
        synchronized (mon) {
            if (!empty) {
                try {
                    mon.wait();
                } catch (InterruptedException e) {
                    logger.log(Level.INFO, "101", e);
                }
            }
            // Toggle status.
            empty = false;
            // Store message.
            this.message = message;
            // Notify consumer that status
            // has changed.
            mon.notifyAll();
        }
    }
}
