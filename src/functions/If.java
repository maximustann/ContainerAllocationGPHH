package functions;

import ec.EvolutionState;
import ec.Problem;
import ec.gp.ADFStack;
import ec.gp.GPData;
import ec.gp.GPIndividual;
import ec.gp.GPNode;
import main.VMCreationProblem;

public class If extends GPNode {

    public If() {
        super();
        children = new GPNode[3];
    }

    public String toString() { return "If"; }

    public int expectedChildren() { return 2; }

    public void eval(final EvolutionState state,
                     final int thread,
                     final GPData input,
                     final ADFStack stack,
                     final GPIndividual individual,
                     final Problem problem) {
        VMCreationProblem p = (VMCreationProblem) problem;
        int osType = p.containerOs;
        double osProb = p.containerOsPro;

//        children[0].eval(state,thread,input,stack,individual,problem);
//        result = rd.x;
        if (osProb > 0.5) {
            children[0].eval(state,thread,input,stack,individual,problem);
        }
        else {
            children[1].eval(state,thread,input,stack,individual,problem);
        }
    }
}
