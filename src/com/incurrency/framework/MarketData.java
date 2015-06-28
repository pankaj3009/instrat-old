/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import javax.swing.JTable;
import java.util.List;
import java.util.ArrayList;
import com.ib.client.Contract;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Admin This class will populate SymbolBean with market prices.
 */
public class MarketData implements Runnable {

//    Thread t;
    private BeanConnection mIB;
    private int mStartPosition;
    private int mCount;
    private JTable mMktDataTB = null;
    private boolean mStopThread = false;
    Date mEndDt = null;
    Date mCloseDt = null;
    public boolean mIsReqEnd = false;
    private List<BeanSymbol> symb = new ArrayList();
    private boolean snapshot = false;
    private static final Logger logger = Logger.getLogger(MarketData.class.getName());
    private int rtrequets;
    private boolean onetime = false;
    private int connectionid;
    /*
     * Constructor()
     * 
     * ib : IB Listner
     * dataTB:  The symbol table to be updated with market data request
     * pos: Start column where market  data will be updated
     *
     */

    public MarketData(BeanConnection ib, int pos, int count, List<BeanSymbol> s, int rtrequests, boolean snapshot) {

        this.mIB = ib;
        this.mStartPosition = pos;
        this.mCount = count;
        this.symb = s;
        this.rtrequets = rtrequests;
        this.snapshot = snapshot;
        this.connectionid = Parameters.connection.indexOf(mIB);
//        t=new Thread(this,threadName);
//        t.start();
    }

    public MarketData(BeanConnection ib, List<BeanSymbol> s, int rtrequests, boolean onetime, boolean snapshot) {
        this.mIB = ib;
        this.mStartPosition = 0;
        this.mCount = s.size();
        this.symb = s;
        this.rtrequets = rtrequests;
        this.onetime = onetime;
        this.snapshot = snapshot;
        //      t=new Thread(this,threadName);
        //      t.start();


    }

    // Start the thread
    @Override
    public void run() {
        logger.log(Level.FINE, "307,MarketDataStarted");
        int rowCount = mCount;
        /*
        if (rowCount <= mIB.getTickersLimit() && "N".compareTo(symb.get(0).getPreopen()) == 0) //if preopen for the first symbol="Y", then all symbols are assumed to be snapshot
        {
            getMktData(mStartPosition, mStartPosition + mCount, symb, snapshot);
        } else 
        */
        {

            getMktData(mStartPosition, mStartPosition + mCount, symb, snapshot);
        }
    }

    /*
     * startRow:
     * endRow:
     * isSnap:  whether to make snapshot data request
     */
    private void getMktData(int startRow, int endRow, List<BeanSymbol> s, boolean isSnap) {
        //Get market data for each row specified in the symbol table
        do {
            for (int row = startRow; row < endRow; row++) {
                int col = 0;
                /*  if (symb.get(row).getContractID() == 0) {
                 System.out.println("Zilch");
                 continue;
                 }// ignore null contids
                 */
                //int id = symb.get(row).getContractID();
                try {
                    Contract contract = new Contract();
                    /* For some reason contract id is not working. To be investigated. Interim solution below
                     contract.m_conId = id;
                     contract.m_exchange = symb.get(row).getExchange();
                     contract.m_primaryExch=symb.get(row).getPrimaryexchange();
                     contract.m_secType=symb.get(row).getType();
                     */
                    contract.m_strike = symb.get(row).getOption() == null ? 0 : Double.parseDouble(symb.get(row).getOption());
                    contract.m_right = symb.get(row).getRight();
                    contract.m_expiry = symb.get(row).getExpiry();
                    contract.m_symbol = symb.get(row).getSymbol();
                    contract.m_exchange = symb.get(row).getExchange();
                    contract.m_primaryExch = symb.get(row).getPrimaryexchange();
                    contract.m_secType = symb.get(row).getType();
                    contract.m_currency = symb.get(row).getCurrency();
                    int i = 0;
                    while (isSnap && mIB.getWrapper().outstandingSnapshots>= 100 - this.rtrequets && i < 20 && !contract.m_secType.equals("COMBO")) {
                        Thread.sleep(100);
                        i = i + 1;
                        if (i >= 20) { //trim snapshots after 2 seconds. 
                            //this.pruneOutstandingSnapshots();
                            i = 0;
                        }
                    }
                    if(!contract.m_secType.equals("COMBO")){
                              mIB.getWrapper().getMktData(symb.get(row), contract, isSnap);              
                    }

                    if (isSnap) {
                        symb.get(row).setConnectionidUsedForMarketData(-1);
                    } else {
                        symb.get(row).setConnectionidUsedForMarketData(connectionid);
                    }
                    //---Working Logic with a lock object ---
                    /*
                     if (isSnap && mIB.getWrapper().isInitialsnapShotFilled()) {
                     if (Integer.parseInt(mIB.getReqHandle().getSnapShotDataSync().take()) <= 100) {
                     mIB.getWrapper().getMktData(symb.get(row), contract, isSnap);                        }
                     } else {
                     mIB.getWrapper().getMktData(symb.get(row), contract, isSnap);
                     }
                     //---Logic End
                     */
                    //System.out.println("Market Data Requested:" + symb.get(row).getSymbol());
                } catch (Exception e) {
                    System.out.println("### Error while getting market data for symbol " + mMktDataTB.getValueAt(row, 1));
                    logger.log(Level.INFO, "101", e);
                }
                if (mStopThread) {
                    break;
                }
            }
        } while (isSnap && !onetime);
    }

    // Set the flag to stop the thread
    protected void setStopThread(boolean flag) {
        mStopThread = flag;
    }

    public void pruneOutstandingSnapshots() {
        Iterator it = mIB.getWrapper().getRequestDetailsWithSymbolKey().entrySet().iterator();
        ArrayList<Integer> reqID = new ArrayList();
        while (it.hasNext()) {
            Map.Entry<Integer, Request> pairs = (Map.Entry) it.next();
            if (new Date().getTime() > pairs.getValue().requestTime + 10000) { //and request is over 10 seconds old
                int origReqID = pairs.getValue().requestID;
                mIB.getWrapper().eClientSocket.cancelMktData(origReqID);
                //logger.log(Level.FINER, "SnapShot cancelled. Symbol:{0},RequestID:{1}", new Object[]{Parameters.symbol.get(pairs.getKey()).getSymbol(), origReqID});
                //there is no callback to confirm that IB processed the market data cancellation, so we will just remove from queue
                reqID.add(pairs.getValue().requestID);
                it.remove();
                mIB.getWrapper().outstandingSnapshots=mIB.getWrapper().outstandingSnapshots-1;
                //mIB.getmSnapShotReqID().remove(origReqID);
                //c.getReqHandle().getSnapShotDataSync().put(Integer.toString(c.getmSnapShotSymbolID().size()));
                //we dont reattempt just yet to prevent a loop of attempts when IB is not throwing data for the symbol
            }
        }

        for(int i:reqID){//cleanup the main request details too
            mIB.getWrapper().getRequestDetails().remove(i);
        }
        }
    
    /**
     * @return the snapshot
     */
    public boolean isSnapshot() {
        return snapshot;
    }

    /**
     * @param snapshot the snapshot to set
     */
    public void setSnapshot(boolean snapshot) {
        this.snapshot = snapshot;
    }
}