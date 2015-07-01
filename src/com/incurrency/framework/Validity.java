/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author admin
 */
public class Validity {
    String account;
    String product;
    String expiry;
    String type;

    public Validity(String type,String account, String product, String expiry) {
        this.type=type;
        this.account = account;
        this.product = product;
        this.expiry = expiry;
    }
    
    
    
}

