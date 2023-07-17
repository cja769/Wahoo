package com.jay.wahoo.neat;

import java.util.ArrayList;
import java.util.List;

/**
 * assign Fitness to each genome
 * Created by vishnu on 12/1/17.
 *
 */
public interface Environment {

     void evaluateFitness(List<Species> species);

}
