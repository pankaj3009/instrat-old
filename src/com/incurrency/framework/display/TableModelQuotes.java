/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.display;

import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author pankaj
 */
public class TableModelQuotes extends AbstractTableModel{

//    private String[] headers={"Symbol","Position","PositionPrice","P&L","HH","LL","Market","CumVol","Slope","20PerThreshold","Volume","MA","Strategy"};
    private String[] headers={"Symbol","LastPrice","Bid","Ask","Close","Volume"};
 private static final Logger logger = Logger.getLogger(TableModelQuotes.class.getName());
    
    int delay = 1000; //milliseconds
    MainAlgorithm m;
    int display=0;
    NumberFormat df = DecimalFormat.getInstance();
    
     ActionListener taskPerformer = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            fireTableDataChanged();
        }
    }; 

    public TableModelQuotes() {
        new Timer(delay, taskPerformer).start();
        this.m=MainAlgorithm.getInstance();
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
     return Parameters.symbol.size();
               }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
    
       switch (columnIndex) {
            case 0:
              return Parameters.symbol.get(rowIndex).getDisplayname();
            case 1:
                return Parameters.symbol.get(rowIndex).getLastPrice();

            case 2:
                return Parameters.symbol.get(rowIndex).getBidPrice();
         case 3:
             return Parameters.symbol.get(rowIndex).getAskPrice();
            case 4:
                return Parameters.symbol.get(rowIndex).getClosePrice();
            case 5:
                return Parameters.symbol.get(rowIndex).getVolume();
        default:
                logger.log(Level.SEVERE," Column no: {0}",new Object[]{columnIndex});
                throw new IndexOutOfBoundsException(); 
                
    }
        
        
        
    }
    
    
}