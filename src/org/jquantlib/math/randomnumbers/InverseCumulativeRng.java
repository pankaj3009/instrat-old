/*
 Copyright (C) 2007 Richard Gomes

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

 /*
 Copyright (C) 2003, 2004 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */
package org.jquantlib.math.randomnumbers;

import org.jquantlib.methods.montecarlo.Sample;

/**
 * Inverse cumulative random number generator
 * <p>
 * It uses a uniform deviate in (0, 1) as the source of cumulative distribution
 * values. Then an inverse cumulative distribution is used to calculate the
 * distribution deviate.
 *
 * The uniform deviate is supplied by RNG.
 *
 * @author Richard Gomes
 */
public class InverseCumulativeRng<RNG extends RandomNumberGenerator, IC extends InverseCumulative> {

    private RNG uniformGenerator_;
    private IC ICND_; // FIXME: not initialized; possibly a static variable used via templates

    public InverseCumulativeRng(final RNG ug) {
        if (System.getProperty("EXPERIMENTAL") == null) {
            throw new UnsupportedOperationException("Work in progress");
        }
        this.uniformGenerator_ = ug;
    }

    /**
     * @return a sample from a Gaussian distribution
     */
    public Sample<Double> getNext() /* @ReadOnly */ {
        if (System.getProperty("EXPERIMENTAL") == null) {
            throw new UnsupportedOperationException("Work in progress");
        }
        Sample<Double> sample = uniformGenerator_.next(); // FIXME: usage of sample_type :: typedef Sample<Real> sample_type;

        return new Sample<Double>(ICND_.op(sample.value()), sample.weight());
    }
}
