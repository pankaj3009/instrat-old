/*
 Copyright (C) 2009 Richard Gomes

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
package org.jquantlib.math.functions;

import org.jquantlib.math.Closeness;
import org.jquantlib.math.Ops;

/**
 * Verifies if 2 double numbers are close enough
 *
 * @see Closeness#isCloseEnough(double, double)
 *
 * @author Richard Gomes
 */
public final class CloseEnough implements Ops.BinaryDoublePredicate {

    //
    // implements BinaryDoublePredicate
    //
    @Override
    public boolean op(final double a, final double b) {
        return Closeness.isCloseEnough(a, b);
    }

}
