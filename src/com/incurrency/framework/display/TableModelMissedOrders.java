/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.display;

import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.MainAlgorithm;
import com.incurrency.framework.Parameters;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author pankaj
 */
public class TableModelMissedOrders extends AbstractTableModel{

    private String[] headers={"Strategy","Symbol","OrderID","Side","Size","OrderPrice","Market"};
    int delay = 1000; //milliseconds
    MainAlgorithm m;
    int display=0;
    private boolean comboDisplay;
    private static final Logger logger = Logger.getLogger(TableModelMissedOrders.class.getName());
    
     ActionListener taskPerformer = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {
            fireTableDataChanged();
        }
    }; 

    public TableModelMissedOrders() {
        new Timer(delay, taskPerformer).start();
    }
        
     public String getColumnName(int column) {
         return headers[column];
     }
 
  
    
    @Override
    public int getRowCount() {
        display = DashBoardNew.comboDisplayGetConnection();
       return Parameters.connection.get(display).getOrdersMissed().size();
               }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        
        this.display=DashBoardNew.comboDisplayGetConnection(); 
        int orderid=-100;
        int pid=-100;
        int cid=-100;
        int id= -100;
        if(Parameters.connection.get(display).getOrdersMissed().size()>0){
            orderid=Parameters.connection.get(display).getOrdersMissed().get(rowIndex);
        
        pid=Parameters.connection.get(display).getOrders().get(orderid).getParentSymbolID()-1;
        cid=Parameters.connection.get(display).getOrders().get(orderid).getChildSymbolID()-1;
        id=isComboDisplay()?pid:cid;
        
        }
 //   private String[] headers={"Strategy","PSymbol","CSymbol","OrderID","PSide","Size","POrderPrice","COrderPrice","PMarket","CMarket"};
        switch (columnIndex) {
            case 0:
                return orderid==-100?"": Parameters.connection.get(display).getOrders().get(orderid).getOrderReference();        
            case 1:
                    BeanSymbol s = Parameters.symbol.get(id);
                    String symbol = s.getDisplayname();
                return pid==-100?"":symbol;
            case 2:
                return orderid==-100?"":Parameters.connection.get(display).getOrders().get(orderid).getOrderID();
            case 3:
                return orderid==-100?"":isComboDisplay()?Parameters.connection.get(display).getOrders().get(orderid).getParentOrderSide():Parameters.connection.get(display).getOrders().get(orderid).getChildOrderSide();
            case 4:
                return orderid==-100?"":isComboDisplay()?Parameters.connection.get(display).getOrders().get(orderid).getParentOrderSize():Parameters.connection.get(display).getOrders().get(orderid).getChildOrderSize();   
            case 5: 
                return orderid==-100?"":isComboDisplay()?Parameters.connection.get(display).getOrders().get(orderid).getParentLimitPrice():Parameters.connection.get(display).getOrders().get(orderid).getChildLimitPrice();
            case 6: 
                return pid==-100?"":Parameters.symbol.get(id).getLastPrice();            
            default:
                
                throw new IndexOutOfBoundsException(); 
    }        
    }

    /**
     * @return the comboDisplay
     */
    public boolean isComboDisplay() {
        return comboDisplay;
    }

    /**
     * @param comboDisplay the comboDisplay to set
     */
    public void setComboDisplay(boolean comboDisplay) {
        this.comboDisplay = comboDisplay;
    }
        
}
