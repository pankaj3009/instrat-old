/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.io.FileReader;
import java.util.ArrayList;

/**
 *
 * @author admin
 */
public class Parameters {

    static public ArrayList<BeanConnection> connection = new ArrayList<>();
    static public ArrayList<BeanSymbol> symbol = new ArrayList<>();
    private static ArrayList _listeners = new ArrayList();
//control variables
//--for realtime bars
    static int barCount;  
    
   
}
