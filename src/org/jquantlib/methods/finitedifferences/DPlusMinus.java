/*
 Copyright (C) 2008 Srinivas Hasti

 This source code is release under the BSD License.
 
 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 
 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.methods.finitedifferences;

/**
 * @author Srinivas Hasti
 *
 */
// ! \f$ D_{+}D_{-} \f$ matricial representation
/*
 * ! The differential operator \f$ D_{+}D_{-} \f$ discretizes the second derivative with the second-order formula \f[
 * \frac{\partial^2 u_{i}}{\partial x^2} \approx \frac{u_{i+1}-2u_{i}+u_{i-1}}{h^2} = D_{+}D_{-} u_{i} \f]
 * 
 * \ingroup findiff
 * 
 * \test the correctness of the returned values is tested by checking them against numerical calculations.
 */
public class DPlusMinus extends TridiagonalOperator {

    public DPlusMinus(int gridPoints, /*Real*/ double h) {
        super(gridPoints);
        setFirstRow(0.0, 0.0); // linear extrapolation
        setMidRows(1 / (h * h), -2 / (h * h), 1 / (h * h));
        setLastRow(0.0, 0.0); // linear extrapolation
    }

}
