/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.RatesClient;

import static com.incurrency.RatesClient.Subscribe.tes;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.TradingUtil;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import static com.incurrency.framework.Algorithm.*;

/**
 *
 * @author pankaj
 */
public class Task implements Runnable {

    String input;
    private static final Logger logger = Logger.getLogger(Task.class.getName());

    public Task(String input) {
        this.input = input;
    }

    @Override
    public void run() {
        try {
            String string = input;
            String[] data = string.split(",");
            if (data.length == 4) {
                //logger.log(Level.INFO,"String:{0}",new Object[]{string});
                //logger.log(Level.FINEST,"Take.Time:{0},Type:{1},Value:{2} ",new Object[]{new Date().getTime(),string.split(",")[0],string.split(",")[2]});
                int type = Integer.parseInt(data[0]);
                long date = Long.parseLong(data[1]);
                String value = data[2];
                String symbol = data[3];
                String[] symbolArray = symbol.split("_");
                int id;
                if (value != null) {
                    switch (symbolArray.length) {
                        case 1:
                            id = TradingUtil.getIDFromSymbol(symbolArray[0], "STK", "", "", "");
                            if (id == -1) {
                                id = TradingUtil.getIDFromDisplayName(symbolArray[0], "STK", "", "", "");
                            }
                            break;
                        case 2:
                            id = TradingUtil.getIDFromSymbol(symbolArray[0], symbolArray[1], "", "", "");
                            if (id == -1) {
                                id = TradingUtil.getIDFromDisplayName(symbolArray[0], symbolArray[1], "", "", "");
                            }
                            break;
                        case 3:
                            id = TradingUtil.getIDFromSymbol(symbolArray[0], symbolArray[1], symbolArray[2], "", "");
                            if (id == -1) {
                                id = TradingUtil.getIDFromDisplayName(symbolArray[0], symbolArray[1], symbolArray[2], "", "");
                            }
                            break;
                        case 5:
                            id = TradingUtil.getIDFromSymbol(symbolArray[0], symbolArray[1], symbolArray[2], symbolArray[3], symbolArray[4]);
                            if (id == -1) {
                                id = TradingUtil.getIDFromDisplayName(symbolArray[0], symbolArray[1], symbolArray[2], symbolArray[3], symbolArray[4]);
                            }
                            break;
                        default:
                            id = -1;
                            break;
                    }
                    if (id >= 0) {
                        switch (type) {
                            case 0: //bidsize
                                Parameters.symbol.get(id).setBidSize((int) Double.parseDouble(value));
                                if (MainAlgorithm.getCollectTicks()) {
                                    TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "BidSize," + value);
                                }
                                break;
                            case 1: //bidprice
                                Parameters.symbol.get(id).setBidPrice(Double.parseDouble(value));
                                tes.fireBidAskChange(id);
                                if (MainAlgorithm.getCollectTicks()) {
                                    TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "Bid," + value);
                                }
                                break;
                            case 2://askprice
                                Parameters.symbol.get(id).setAskPrice(Double.parseDouble(value));
                                tes.fireBidAskChange(id);
                                if (MainAlgorithm.getCollectTicks()) {
                                    TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "Bid," + value);
                                }
                                break;
                            case 3: //ask size
                                Parameters.symbol.get(id).setAskSize((int) Double.parseDouble(value));
                                if (MainAlgorithm.getCollectTicks()) {
                                    TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "AskSize," + value);
                                }
                                break;
                            case 4: //last price
                                double price = Double.parseDouble(value);
                                double prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice() == 0 ? price : Parameters.symbol.get(id).getPrevLastPrice();
                                MainAlgorithm.setAlgoDate(new Date(date));
                                Parameters.symbol.get(id).setPrevLastPrice(prevLastPrice);
                                Parameters.symbol.get(id).setLastPrice(price);
                                Parameters.symbol.get(id).setLastPriceTime(date);
                                Parameters.symbol.get(id).getTradedPrices().add(price);
                                Parameters.symbol.get(id).getTradedDateTime().add(date);
                                // Parameters.symbol.get(id).getDailyBar().setOHLCFromTick(date, price, price, price, price, date);
                                tes.fireTradeEvent(id, com.ib.client.TickType.LAST);
//                            logger.log(Level.INFO,"DEBUG: Symbol_LastPrice,{0}",new Object[]{id+"_"+price});
                                //logger.log(Level.FINER,"Task Data Received, Symbol:{1},Time:{0},Price:{2}",new Object[]{new Date(date),Parameters.symbol.get(id).getDisplayname(),price});

                                if (MainAlgorithm.getCollectTicks()) {
                                    TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "LastPrice," + value);
                                }
                                if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                                    Parameters.symbol.get(id).getIntraDayBarsFromTick().setOHLCFromTick(TradingUtil.getAlgoDate().getTime(), com.ib.client.TickType.LAST, String.valueOf(price));
                                }
                                break;
                            case 5: //last size
                                int size1 = (int) Double.parseDouble(value);
                                /*
                                if (MainAlgorithm.isUseForTrading() ||(!MainAlgorithm.isUseForTrading() && MainAlgorithm.getInput().get("backtest").equals("tick"))) {
                                    Parameters.symbol.get(id).setLastSize(size1);
                                    tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                                    if (Parameters.symbol.get(id).getOneMinuteBarsFromTick() != null) {
                                    Parameters.symbol.get(id).getOneMinuteBarsFromTick().setOHLCFromTick(TradingUtil.getAlgoDate().getTime(), com.ib.client.TickType.VOLUME, String.valueOf(size1));
                                }

                                } else {
                                    //historical bar runs
                                  */
                                if(!MainAlgorithm.isUseForTrading() && !globalProperties.getProperty("backtestprices", "tick").toString().trim().equals("tick")){
                                  prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice();
                                    double lastPrice = Parameters.symbol.get(id).getLastPrice();
                                    int calculatedLastSize;
                                    /*
                                    if (prevLastPrice != lastPrice) {
                                        Parameters.symbol.get(id).setPrevLastPrice(lastPrice);
                                        calculatedLastSize = size1;
                                    } else {
                                        calculatedLastSize = size1 + Parameters.symbol.get(id).getLastSize();
                                    }
                                    */
                                    
                                    Parameters.symbol.get(id).setLastSize(size1);
                                    int volume = Parameters.symbol.get(id).getVolume() + size1;
                                    Parameters.symbol.get(id).setVolume(volume);
                                    tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                                    tes.fireTradeEvent(id, com.ib.client.TickType.VOLUME);
                                }
                                if (MainAlgorithm.getCollectTicks()) {
                                    TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "LastSize," + value);
                                }
                                break;
                            case 6:
                                break;
                            case 7:
                                break;
                            case 8: //volume
                                int size = (int) Double.parseDouble(value);
                                int calculatedLastSize=size-Parameters.symbol.get(id).getVolume();
                                if(calculatedLastSize>0){
                                    Parameters.symbol.get(id).setLastSize(calculatedLastSize);
                                    tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                                }
                                Parameters.symbol.get(id).setVolume(size);
                                tes.fireTradeEvent(id, com.ib.client.TickType.VOLUME);
                                if (MainAlgorithm.getCollectTicks()) {
                                    TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "Volume," + size);
                                }

                                break;
                            case 9: //close
                                Parameters.symbol.get(id).setClosePrice(Double.parseDouble(value));
                                Parameters.symbol.get(id).setLastPriceTime(date);
                                tes.fireTradeEvent(id, com.ib.client.TickType.CLOSE);
                                if (MainAlgorithm.getCollectTicks()) {
                                    TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "Close," + value);
                                }
                                break;
                            case 99:
                                Parameters.symbol.get(id).setClosePrice(Double.parseDouble(value));
                                tes.fireTradeEvent(id, 99);
                                break;                                
                            default:
                                break;
                        }

                    }else{
                        //System.out.println("No id found for symbol:"+Arrays.toString(symbolArray));
                    }
                } else {
                    logger.log(Level.INFO, "Null Value received from dataserver");
                }
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "101", ex);
        }

    }
}