/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.display;

import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.Index;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Utilities;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author pankaj
 */
public class TableModelPositions extends AbstractTableModel {

//    private String[] headers={"Symbol","Position","PositionPrice","P&L","HH","LL","Market","CumVol","Slope","20PerThreshold","Volume","MA","Strategy"};
    private String[] headers = {"Symbol", "Position", "EntryPrice", "P&L","MTM","MarketPrice", "Strategy"};
    private static final Logger logger = Logger.getLogger(TableModelPositions.class.getName());
    int delay = 1000; //milliseconds
    int display = 0;
    NumberFormat df = DecimalFormat.getInstance();
    ActionListener taskPerformer = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            fireTableDataChanged();
        }
    };

    public TableModelPositions() {
        new Timer(delay, taskPerformer).start();
        //display=DashBoardNew.comboDisplayGetConnection();
        df.setMinimumFractionDigits(2);
        df.setMaximumFractionDigits(4);
        df.setRoundingMode(RoundingMode.DOWN);
    }

    @Override
    public String getColumnName(int column) {
        return headers[column];
    }

    @Override
    public int getRowCount() {
        display = DashBoardNew.comboDisplayGetConnection();
        return Parameters.connection.get(display).getPositions().size();
//        return Parameters.symbol.size();
    }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        String strategy = DashBoardNew.comboStrategyGetValue();
        Index ind = new Index(strategy, rowIndex);
        display = DashBoardNew.comboDisplayGetConnection();
        int strategyid = 0;

        if (Parameters.connection.get(display).getPositions().size() > 0) {
            Set<Index> keys = Parameters.connection.get(display).getPositions().keySet(); //getPosition() will be null if there are no positions
            //tempArray=new Index[keys.size()];
            Index[] tempArray = keys.toArray(new Index[keys.size()]);
            if (tempArray.length > 0) {
                ind = tempArray[rowIndex]; //ind is set to a position. 
            }
            strategyid = DashBoardNew.comboStrategyid();
        }
        while (MainAlgorithm.getInstance().getMaxPNL().size() <= strategyid) {
            MainAlgorithm.getInstance().getMaxPNL().add(0D);
        }
        while (MainAlgorithm.getInstance().getMinPNL().size() <= strategyid) {
            MainAlgorithm.getInstance().getMinPNL().add(0D);
        }


        switch (columnIndex) {
            case 0:
                if (Parameters.connection.get(display).getPositions().size() > 0) {
                    BeanSymbol s = Parameters.symbol.get(ind.getSymbolID());
                    String symbol = s.getDisplayname();
                    return symbol;
                } else {
                    return "";
                }
            case 1:
                if (Parameters.connection.get(display).getPositions().size() > 0) {
                    return Parameters.connection.get(display) == null || Parameters.connection.get(display).getPositions() == null || Parameters.connection.get(display).getPositions().get(ind) == null ? 0 : Parameters.connection.get(display).getPositions().get(ind).getPosition();
                } else {
                    return "";
                }

            case 2:
                if (Parameters.connection.get(display).getPositions().size() > 0) {
                    return df.format(Parameters.connection.get(display) == null || Parameters.connection.get(display).getPositions() == null || Parameters.connection.get(display).getPositions().get(ind) == null ? 0 : Parameters.connection.get(display).getPositions().get(ind).getPrice());
                } else {
                    return "";
                }

            case 3:
                if (Parameters.connection.get(display).getPositions().size() > 0) {
                    double pnl = 0;
                    //calculate max, min pnl
                    if (!(Parameters.connection.get(display) == null || Parameters.connection.get(display).getPositions() == null || Parameters.connection.get(display).getPositions().get(ind) == null)) {
                        if (Parameters.connection.get(display).getPositions().get(ind).getPosition() > 0) {
                            return (int) Math.round(-Parameters.connection.get(display).getPositions().get(ind).getPosition() * Parameters.connection.get(display).getPositions().get(ind).getPointValue()*(Parameters.connection.get(display).getPositions().get(ind).getPrice() - Parameters.symbol.get(ind.getSymbolID()).getLastPrice()) + Parameters.connection.get(display).getPositions().get(ind).getProfit());
                            //return String.format("%.02f",(-Parameters.connection.get(display).getPositions().get(ind).getPosition()*(Parameters.connection.get(display).getPositions().get(ind).getPrice()-Parameters.symbol.get(ind.getSymbolID()).getLastPrice())+Parameters.connection.get(display).getPositions().get(ind).getProfit())); 

                        } else if (Parameters.connection.get(display).getPositions().get(ind).getPosition() < 0) {
                            return (int) Math.round(-Parameters.connection.get(display).getPositions().get(ind).getPosition() * Parameters.connection.get(display).getPositions().get(ind).getPointValue()*(Parameters.connection.get(display).getPositions().get(ind).getPrice() - Parameters.symbol.get(ind.getSymbolID()).getLastPrice()) + Parameters.connection.get(display).getPositions().get(ind).getProfit());
                            //return String.format("0.02f",(-Parameters.connection.get(display).getPositions().get(ind).getPosition()*(Parameters.connection.get(display).getPositions().get(ind).getPrice()-Parameters.symbol.get(ind.getSymbolID()).getLastPrice())+Parameters.connection.get(display).getPositions().get(ind).getProfit())); 
                        } else {
                            return (int) Math.round(Parameters.connection.get(display).getPositions().get(ind).getProfit());
                        }
                        //else return String.format("0.02f",(Parameters.connection.get(display).getPositions().get(ind).getProfit()));
                    } else {
                        return Parameters.connection.get(display).getPositions().get(ind).getProfit();
                    }
                } else {
                    return 0;
                }
            case 4:
                if (Parameters.connection.get(display).getPositions().size() > 0) {
                    double pnl = 0;
                    double value=0;
                    //calculate max, min pnl
                    if (!(Parameters.connection.get(display) == null || Parameters.connection.get(display).getPositions() == null || Parameters.connection.get(display).getPositions().get(ind) == null)) {
                        if (Parameters.connection.get(display).getPositions().get(ind).getPosition() > 0) {
                            value= -Parameters.connection.get(display).getPositions().get(ind).getPosition() * Parameters.connection.get(display).getPositions().get(ind).getPointValue()*(Parameters.connection.get(display).getPositions().get(ind).getPrice() - Parameters.symbol.get(ind.getSymbolID()).getLastPrice()) + Parameters.connection.get(display).getPositions().get(ind).getProfit();
                            return Utilities.round(value-Parameters.connection.get(display).getMtmBySymbol().get(ind), 0);
                        } else if (Parameters.connection.get(display).getPositions().get(ind).getPosition() < 0) {
                            value=-Parameters.connection.get(display).getPositions().get(ind).getPosition() * Parameters.connection.get(display).getPositions().get(ind).getPointValue()*(Parameters.connection.get(display).getPositions().get(ind).getPrice() - Parameters.symbol.get(ind.getSymbolID()).getLastPrice()) + Parameters.connection.get(display).getPositions().get(ind).getProfit();
                            return Utilities.round(value-Parameters.connection.get(display).getMtmBySymbol().get(ind), 0);
                        } else {
                            value=Parameters.connection.get(display).getPositions().get(ind).getProfit();
                            return Utilities.round(value-Parameters.connection.get(display).getMtmBySymbol().get(ind), 0);
                        }
                    } else {
                        value= Parameters.connection.get(display).getPositions().get(ind).getProfit();
                        return Utilities.round(value-Parameters.connection.get(display).getMtmBySymbol().get(ind), 0);
                    }
                } else {
                    return 0;
                }
                
            case 5:
                if (Parameters.connection.get(display).getPositions().size() > 0) {
                    return Parameters.symbol.get(ind.getSymbolID()).getLastPrice();
                } else {
                    return "";
                }

            case 6:
                if (Parameters.connection.get(display).getPositions().size() > 0) {
                    return ind.getStrategy();

                } else {
                    return "";
                }

            default:
                logger.log(Level.SEVERE, " Column no: {0}", new Object[]{columnIndex});
                throw new IndexOutOfBoundsException();
        }
    }
}
