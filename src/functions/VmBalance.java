package functions;

import ec.EvolutionState;
import ec.Problem;
import ec.gp.ADFStack;
import ec.gp.GPData;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import main.DoubleData;
import main.VMCreationProblem;

public class VmBalance extends GPNode {
    public String toString() {return "vmBalance";}
    public int expectedChildren() { return 0; }

    public void eval(final EvolutionState state,
                     final int thread,
                     final GPData input,
                     final ADFStack stack,
                     final GPIndividual individual,
                     final Problem problem){
        DoubleData rd = (DoubleData)(input);

        VMCreationProblem p = (VMCreationProblem) problem;
        Double cpu = p.normalizedVmCpuCapacity;
        Double mem = p.normalizedVmMemCapacity;
        Double balance = 0.0;
        if(cpu > mem)
            balance = cpu - mem;
        else
            balance = mem - cpu;
        rd.x = balance;
    }
}
