package functions;

import ec.EvolutionState;
import ec.Problem;
import ec.gp.ADFStack;
import ec.gp.GPData;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import main.DoubleData;
import main.VMCreationProblem;

public class CoBalance extends GPNode {
    public String toString() {return "coBalance";}
    public int expectedChildren() { return 0; }

    public void eval(final EvolutionState state,
                     final int thread,
                     final GPData input,
                     final ADFStack stack,
                     final GPIndividual individual,
                     final Problem problem){
        DoubleData rd = (DoubleData)(input);

        VMCreationProblem p = (VMCreationProblem) problem;
        Double cpu = p.normalizedContainerCpu;
        Double mem = p.normalizedContainerMem;
        Double balance = 0.0;
        if(cpu > mem)
            balance = cpu - mem;
        else
            balance = mem - cpu;
        rd.x = balance;
    }
}
