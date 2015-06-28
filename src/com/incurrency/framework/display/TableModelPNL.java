/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.display;

import com.incurrency.framework.Index;
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
public class TableModelPNL extends AbstractTableModel {

    private String[] headers = {"Strategy", "PNL", "MaxPNL", "MinPNL"};
    int delay = 1000; //milliseconds
    int display = 0;
    private static final Logger logger = Logger.getLogger(TableModelPNL.class.getName());
    ActionListener taskPerformer = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            fireTableDataChanged();
        }
    };

    public TableModelPNL() {
        new Timer(delay, taskPerformer).start();
    }

    @Override
    public String getColumnName(int column) {
        return headers[column];
    }

    @Override
    public int getRowCount() {
        return !MainAlgorithm.instantiated ? 0 : MainAlgorithm.strategies == null ? 0 : MainAlgorithm.strategies.size();
    }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        this.display = DashBoardNew.comboDisplayGetConnection();
        String strategy = MainAlgorithm.strategies.get(rowIndex);
        Index ind = new Index(strategy, rowIndex);      

        switch (columnIndex) {
            case 0:
                return MainAlgorithm.strategies.get(rowIndex);
            case 1:
                return ((int) Math.round(Parameters.connection.get(display).getPnlByStrategy().get(strategy) * 100)) / 100;

            case 2:
                return ((int) Math.round(Parameters.connection.get(display).getMaxpnlByStrategy().get(strategy) * 100)) / 100;

            case 3:
                return ((int) Math.round(Parameters.connection.get(display).getMinpnlByStrategy().get(strategy) * 100)) / 100;
            default:
                throw new IndexOutOfBoundsException();

        }
    }
}