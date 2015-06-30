/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.rateserver;

/**
 *
 * @author pankaj
 */
public class Price {
    private String header;
    private String message;

    
    public Price(String header, String message){
        this.header=header;
        this.message=message;
    }
    /**
     * @return the header
     */
    public String getHeader() {
        return header;
    }

    /**
     * @param header the header to set
     */
    public void setHeader(String header) {
        this.header = header;
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * @param message the message to set
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
