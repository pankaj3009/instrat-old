/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author admin
 */
public class Parameters {

    static public List<BeanConnection> connection = Collections.synchronizedList(new ArrayList<BeanConnection>());
    static public ArrayList<BeanSymbol> symbol = new ArrayList<>();
    private static ArrayList _listeners = new ArrayList();
//control variables
//--for realtime bars
    static int barCount;  
    
   
}
