/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.rateserver;

/**
 *
 * @author pankaj
 */
public interface TickListener {
     public void tickReceived( TickEvent event );   
}
