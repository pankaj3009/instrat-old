/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.fundamental;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author admin
 */
public class Fundamental {

    private static final Logger logger = Logger.getLogger(Fundamental.class.getName());

    public String TTMEPSCHG;
    public String QBVPS;
    public String TTMREVPS;
    public String TTMDIVSHR;
    public String DIVGRPCT;
    public String APENORM;
    public String TTMPAYRAT;
    public String TTMREV;
    public String TTMEBT;
    public String QTANBVPS;
    public String TTMCFSHR;
    public String TTMROEPCT;
    public String QTOTD2EQ;
    public String NPRICE;
    public String EPSTRENDGR;
    public String TTMREVCHG;
    public String AEPSNORM;
    public String TTMPRCFPS;
    public String PR2TANBK;
    public String QCURRATIO;
    public String QLTD2EQ;
    public String QQUICKRATI;
    public String TTMNIAC;
    public String TTMNPMGN;
    public String QCSHPS;
    public String REVCHNGYR;
    public String TTMEPSXCLX;
    public String REVTRENDGR;
    public String TTMPR2REV;
    public String TTMGROSMGN;
    public String AEBTNORM;
    public String AFEEPSNTM;
    public String ANIACNORM;
    public String TTMROAPCT;
    public String TTMROIPCT;
    public String CURRENCY;
    public String TTMOPMGN;
    public String EPSCHNGYR;
    public String MKTCAP;
    public String LATESTADATE;
    public String PEEXCLXOR;
    public String APTMGNPCT;
    public String TTMEBITD;
    public String PRICE2BK;
    public String industryName;
    public String industryCode;
    public String splitRatio;
    public String splitDate;
    public String BusinessSummary;
    public String FinancialSummary;
    public String sharesOutstanding;
    public boolean FundamentalRatiosReceived = false;
    public boolean FundamentalSnapshotReceived = false;
    private int snapshotRequestID = 0;
    boolean empty = true;

    public synchronized boolean takeSummary() {
        // Wait until message is
        // available.
        while (empty) {
            try {
                wait();
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
        // Toggle status.
        empty = true;
        // Notify producer that
        // status has changed.

        notifyAll();
        return true;
    }

    public synchronized void putSummary(String message) {
        // Wait until message has
        // been retrieved.
        while (!empty) {
            try {
                wait();
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
        // Toggle status.
        empty = false;
        // Store Summary

        // Notify consumer that status
        // has changed.
        notifyAll();
    }

    public void addIBFundamentalRatios(String s) {
        //this function adds fundamental ratios from IB TickType=258
        String[] arr = s.split(";");
        for (String namevalue : arr) {
            String[] temp = namevalue.split("=");
            switch (temp[0]) {
                case "TTMEPSCHG":
                    TTMEPSCHG = temp[1];
                    break;
                case "QBVPS":
                    QBVPS = temp[1];
                    break;
                case "TTMREVPS":
                    TTMREVPS = temp[1];
                    break;
                case "TTMDIVSHR":
                    TTMDIVSHR = temp[1];
                    break;
                case "DIVGRPCT":
                    DIVGRPCT = temp[1];
                    break;
                case "APENORM":
                    APENORM = temp[1];
                    break;
                case "TTMPAYRAT":
                    TTMPAYRAT = temp[1];
                    break;
                case "TTMREV":
                    TTMREV = temp[1];
                    break;
                case "TTMEBT":
                    TTMEBT = temp[1];
                    break;
                case "QTANBVPS":
                    QTANBVPS = temp[1];
                    break;
                case "TTMCFSHR":
                    TTMCFSHR = temp[1];
                    break;
                case "TTMROEPCT":
                    TTMROEPCT = temp[1];
                    break;
                case "QTOTD2EQ":
                    QTOTD2EQ = temp[1];
                    break;
                case "NPRICE":
                    NPRICE = temp[1];
                    break;
                case "EPSTRENDGR":
                    EPSTRENDGR = temp[1];
                    break;
                case "TTMREVCHG":
                    TTMREVCHG = temp[1];
                    break;
                case "AEPSNORM":
                    AEPSNORM = temp[1];
                    break;
                case "TTMPRCFPS":
                    TTMPRCFPS = temp[1];
                    break;
                case "PR2TANBK":
                    PR2TANBK = temp[1];
                    break;
                case "QCURRATIO":
                    QCURRATIO = temp[1];
                    break;
                case "QLTD2EQ":
                    QLTD2EQ = temp[1];
                    break;
                case "QQUICKRATI":
                    QQUICKRATI = temp[1];
                    break;
                case "TTMNIAC":
                    TTMNIAC = temp[1];
                    break;
                case "TTMNPMGN":
                    TTMNPMGN = temp[1];
                    break;
                case "QCSHPS":
                    QCSHPS = temp[1];
                    break;
                case "REVCHNGYR":
                    REVCHNGYR = temp[1];
                    break;
                case "TTMEPSXCLX":
                    TTMEPSXCLX = temp[1];
                    break;
                case "REVTRENDGR":
                    REVTRENDGR = temp[1];
                    break;
                case "TTMPR2REV":
                    TTMPR2REV = temp[1];
                    break;
                case "TTMGROSMGN":
                    TTMGROSMGN = temp[1];
                    break;
                case "AEBTNORM":
                    AEBTNORM = temp[1];
                    break;
                case "AFEEPSNTM":
                    AFEEPSNTM = temp[1];
                    break;
                case "ANIACNORM":
                    ANIACNORM = temp[1];
                    break;
                case "TTMROAPCT":
                    TTMROAPCT = temp[1];
                    break;
                case "TTMROIPCT":
                    TTMROIPCT = temp[1];
                    break;
                case "CURRENCY":
                    CURRENCY = temp[1];
                    break;
                case "TTMOPMGN":
                    TTMOPMGN = temp[1];
                    break;
                case "EPSCHNGYR":
                    EPSCHNGYR = temp[1];
                    break;
                case "MKTCAP":
                    MKTCAP = temp[1];
                    break;
                case "LATESTADATE":
                    LATESTADATE = temp[1];
                    break;
                case "PEEXCLXOR":
                    TTMROAPCT = temp[1];
                    break;
                case "APTMGNPCT":
                    TTMROIPCT = temp[1];
                    break;
                case "TTMEBITD":
                    TTMEBITD = temp[1];
                    break;
                case "PRICE2BK":
                    PRICE2BK = temp[1];
                    break;
                default:
                    break;
            }
            this.FundamentalRatiosReceived = true;
        }
    }

    /**
     * @return the summaryRequestID
     */
    public int getSnapshotRequestID() {
        return snapshotRequestID;
    }

    /**
     * @param summaryRequestID the summaryRequestID to set
     */
    public void setSnapshotRequestID(int snapshotRequestID) {
        this.snapshotRequestID = snapshotRequestID;
    }
}
