package main;

import com.opencsv.CSVReader;
import com.sun.javafx.collections.ImmutableObservableList;
import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.gp.GPProblem;
import ec.gp.koza.KozaFitness;
import ec.simple.SimpleProblemForm;
import ec.util.Parameter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

public class VMCreationProblem extends GPProblem implements SimpleProblemForm{
    public static final String P_DATA = "data";
    public static double PMCPU;
    public static double PMMEM;
    public static double PMENERGY;

    private String testCasePath;
    private String osPath;
    private String vmConfigPath;
    private String osProPath;
    private String initEnvPath;


    // A list of containers
    private ArrayList<ArrayList<Double[]>> inputX = new ArrayList<>();

    // A list of candidate VMs, each vm has an array which includes its CPU and Mem capacity
    private ArrayList<Double[]> vmTypeList = new ArrayList<>();

    // An array of OS probability
    private ArrayList<Double> OSPro = new ArrayList<>();

    // An array of benchmark accumulated energy
//    private ArrayList<Double> benchmark = new ArrayList<>();


    // Initialization data
    ArrayList <ArrayList> initPm;
    ArrayList <ArrayList> initVm;
    ArrayList <ArrayList> initOs;
    ArrayList <ArrayList> initContainer;


    public static double vmCpuOverheadRate;
    public static double vmMemOverhead;

    public double containerCpu;
    public double containerMem;
    public int containerOs;
    public double containerOsPro;


    public double normalizedContainerCpu;
    public double normalizedContainerMem;
    public double normalizedVmCpuCapacity;
    public double normalizedVmMemCapacity;


    public double currentPmCpuRemain;
    public double currentPmMemRemain;

    public double currentVmCpuCapacity;
    public double currentVmMemCapacity;

    public double normalizedVmCpuOverhead;
    public double normalizedVmMemOverhead;

    public double normalizedGlobalCpuWaste;
    public double normalizedGlobalMemWaste;

    public boolean newVmFlag = false;


    private double k;

    private int start;
    private int end;

    ArrayList<Double> benchmarkResult;
    SubJustFit_FF benchmark;

    public void setup(final EvolutionState state, final Parameter base){

        // very important
        super.setup(state, base);
        if (!(input instanceof DoubleData)){
            state.output.fatal("GPData class must subclasses from " + DoubleData.class,
                    base.push(P_DATA), null);
        }



        Parameter pmCPUP = new Parameter("PMCPU");
        Parameter pmMemP = new Parameter("PMMEM");
        Parameter pmEnergyP = new Parameter("PMENERGY");
        Parameter vmCPUOverheadRateP = new Parameter("VMCPUOverheadRate");
        Parameter vmMemOverheadP = new Parameter("VMMemOverhead");
        Parameter kP = new Parameter("k");
        Parameter readFileStartFromP = new Parameter("readFileStartFrom");
        Parameter readFileEndP = new Parameter("readFileEnd");
        Parameter testCasePathP = new Parameter("testCasePath");
        Parameter osPathP = new Parameter("osPath");
        Parameter vmConfigPathP = new Parameter("vmConfigPath");
        Parameter osProP = new Parameter("osProPath");
        Parameter envPath = new Parameter("initEnvPath");


        PMCPU = state.parameters.getDouble(pmCPUP, null);
        PMMEM = state.parameters.getDouble(pmMemP, null);
        PMENERGY = state.parameters.getDouble(pmEnergyP, null);
        vmCpuOverheadRate = state.parameters.getDouble(vmCPUOverheadRateP, null);
        vmMemOverhead = state.parameters.getDouble(vmMemOverheadP, null);

        k = state.parameters.getDouble(kP, null);
        start = state.parameters.getInt(readFileStartFromP, null);
        end = state.parameters.getInt(readFileEndP, null);

        testCasePath = state.parameters.getString(testCasePathP, null);
        osPath = state.parameters.getString(osPathP, null);
        vmConfigPath = state.parameters.getString(vmConfigPathP, null);
        osProPath = state.parameters.getString(osProP, null);
        initEnvPath =  state.parameters.getString(envPath, null);


        readEnvData();
        readFromFiles(start, end - 1);
        readVMConfig();
        readOSPro();

        benchmark = new SubJustFit_FF(
                PMCPU,
                PMMEM,
                PMENERGY,
                k,
                vmCpuOverheadRate,
                vmMemOverhead,
                inputX,
                initVm,
                initContainer,
                initOs,
                initPm,
                vmTypeList
        );
        benchmarkResult = new ArrayList<>();
        for(int i = 0; i < inputX.size(); i++){
            benchmarkResult.add(benchmark.allocate(i));
        }
    }

    public void evaluate(final EvolutionState state,
                         final Individual ind,
                         final int subpopulation,
                         final int threadnum){
        if(!ind.evaluated){ // Don't bother re-evaluating

            // initialize the resource lists
            ArrayList<Double> resultList = new ArrayList<>();
            ArrayList<Double> comparedResultList = new ArrayList<>();
            int testCase = state.generation;

            // Loop through the testCases
//            for (int testCase = 0; testCase <= end - start - 1; ++testCase) {


            double globalCPUWaste = 0;
            double globalMEMWaste = 0;
            ArrayList<Double[]> pmResourceList = new ArrayList<>();
            ArrayList<Double[]> pmActualUsageList = new ArrayList<>();
            ArrayList<Double[]> vmResourceList = new ArrayList<>();
            HashMap<Integer, Integer> VMPMMapping = new HashMap<>();
            HashMap<Integer, Integer> vmIndexTypeMapping = new HashMap<>();

            // Initialize data center
            initializeDataCenter(testCase,
                    pmResourceList,
                    pmActualUsageList,
                    vmResourceList,
                    VMPMMapping,
                    vmIndexTypeMapping);

            // the total energy
            Double Energy = energyCalculation(pmActualUsageList);

            // No data center initialization
//                Double Energy = 0.0;
            // Get all the containers
            ArrayList<Double[]> containers = inputX.get(testCase);



            // Start simulation
            for (Double[] container:containers) {

                containerCpu = container[0];
                containerMem = container[1];
                containerOs = container[2].intValue();


                Integer chosenVM;
                Integer currentVmNum = vmResourceList.size();


                // select or create a VM
                chosenVM = VMSelectionCreation(
                        state,
                        ind,
                        threadnum,
                        vmResourceList,
                        pmResourceList,
                        pmActualUsageList,
                        vmIndexTypeMapping,
                        containerCpu,
                        containerMem,
                        containerOs,
                        globalCPUWaste,
                        globalMEMWaste);
//                    System.out.println("chosenVM = " + chosenVM);

                // check if the VM exists, if chosenVM < currentVmNum is true, it means
                // the chosenVM exists, we just need to update its resources
                if (chosenVM < currentVmNum) {
                    // update the VM resources, allocating this container into this VM
                    vmResourceList.set(chosenVM, new Double[]{
                            vmResourceList.get(chosenVM)[0] - containerCpu,
                            vmResourceList.get(chosenVM)[1] - containerMem,
                            new Double(containerOs)
                    });

                    // Find the pmIndex in the mapping
                    int pmIndex = VMPMMapping.get(chosenVM);

                    // update the PM actual resources
                    pmActualUsageList.set(pmIndex, new Double[]{
                            pmActualUsageList.get(pmIndex)[0] - containerCpu,
                            pmActualUsageList.get(pmIndex)[1] - containerMem
                    });

                    // Else, we need to create this new VM
                } else {

                    // Retrieve the type of select VM
                    int vmType = chosenVM - currentVmNum;

                    // create this new VM
                    vmResourceList.add(new Double[]{
                            vmTypeList.get(vmType)[0] - containerCpu - vmTypeList.get(vmType)[0] * vmCpuOverheadRate,
                            vmTypeList.get(vmType)[1] - containerMem - vmMemOverhead,
                            new Double(containerOs)
                    });

                    // Whenever we create a new VM, map its index in the VMResourceList to its type for future purpose
                    vmIndexTypeMapping.put(vmResourceList.size() - 1, vmType);

                    // After creating a VM, we will choose a PM to allocate
                    // We only care about the size of VM
                    Integer chosenPM = VMAllocation(pmResourceList,
                            vmTypeList.get(vmType)[0],
                            vmTypeList.get(vmType)[1]);

                    // If we cannot choose a PM
                    if (chosenPM == null) {

                        // Add the VM to the newly created PM
                        // We don't need to consider the overhead here.
                        pmResourceList.add(new Double[]{
                                PMCPU - vmTypeList.get(vmType)[0],
                                PMMEM - vmTypeList.get(vmType)[1]
                        });

                        // Add the Actual usage to the PM
                        // Here, we must consider the overhead
                        pmActualUsageList.add(new Double[]{
                                PMCPU - containerCpu - vmTypeList.get(vmType)[0] * vmCpuOverheadRate,
                                PMMEM - containerMem - vmMemOverhead
                        });

                        // Map the VM to the PM
                        VMPMMapping.put(vmResourceList.size() - 1, pmResourceList.size() - 1);


                        // If there is an existing PM, we allocate it to an existing PM
                    } else {

                        currentPmCpuRemain = pmResourceList.get(chosenPM)[0] - vmTypeList.get(vmType)[0];
                        currentPmMemRemain = pmResourceList.get(chosenPM)[1] - vmTypeList.get(vmType)[1];


                        // update the PM resources
                        // pm resources - vm size
                        pmResourceList.set(chosenPM, new Double[]{
                                currentPmCpuRemain,
                                currentPmMemRemain
                        });

                        // update the actual resources
                        // Actual usage - container required - vm overhead
                        pmActualUsageList.set(chosenPM, new Double[]{
                                pmActualUsageList.get(chosenPM)[0] - containerCpu - vmTypeList.get(vmType)[0] * vmCpuOverheadRate,
                                pmActualUsageList.get(chosenPM)[1] - containerMem - vmMemOverhead
                        });

                        // Map the VM to the PM
                        VMPMMapping.put(vmResourceList.size() - 1, chosenPM);

                    } // End of allocating a VM to an existing PM

                } // End of creating a new VM


                // After each allocation of container, we evaluate the energy in the data-center
                // And add up the energy consumption to calculate the area under the curve
//                double increment = energyCalculation(pmActualUsageList);
//                Energy += increment;

                // calculate the average increase of allocating a container for a taskCase,
                // We want to minimize this value
                resultList.add(energyCalculation(pmActualUsageList));
            } // End of all test cases

            // For the fitness value, we temporarily use the average energy as the fitness value
            KozaFitness f = (KozaFitness) ind.fitness;

            double aveFit = 0;
            for(int i = 0; i < resultList.size(); ++i){
                aveFit += resultList.get(i);
            }
            aveFit /= benchmarkResult.get(state.generation);
            aveFit /= resultList.size();

            f.setStandardizedFitness(state, aveFit);
//
//             set the evaluation state to true
            ind.evaluated = true;
        }
    } // end of evaluation

    private void readEnvData(){
        ReadConfigures readEnvConfig = new ReadConfigures();

        initPm = readEnvConfig.testCases(initEnvPath, "pm", start, end);
        initVm = readEnvConfig.testCases(initEnvPath, "vm", start, end);
        initOs = readEnvConfig.testCases(initEnvPath, "os", start, end);
        initContainer = readEnvConfig.testCases(initEnvPath, "container", start, end);


    }

    private Double EvolveSelectionMethod(
            final EvolutionState state,
            final Individual ind,
            final int threadnum,
            double vmCpuRemain,
            double vmMemRemain,
            double vmCpuCapacity,
            double vmMemCapacity,
            ArrayList<Double[]> pmResourceList,
            ArrayList<Double[]> actualPmResourceList){


        // allocated flag indicates whether the existing PM can host a newly created VM
        // true means YES, otherwise we must create new PM to host the VM
        boolean allocated = false;

        DoubleData input = (DoubleData) (this.input);

        // The resource is normalized by the PM's capacity.
        normalizedVmCpuCapacity = vmCpuRemain / PMCPU;
        normalizedVmMemCapacity = vmMemRemain / PMMEM;
        normalizedContainerCpu = containerCpu / PMCPU;
        normalizedContainerMem = containerMem / PMMEM;
        // we only consider the overhead of new VM
        if(newVmFlag) {
            normalizedVmCpuOverhead = vmCpuCapacity * vmCpuOverheadRate / PMCPU;
            normalizedVmMemOverhead = vmMemOverhead / PMMEM;
        } else {
            normalizedVmCpuOverhead = 0;
            normalizedVmMemOverhead = 0;
        }


        // Evaluate the GP rule
        ((GPIndividual) ind).trees[0].child.eval(
                state, threadnum, input, stack, (GPIndividual) ind, this);


        return input.x;
    }


    /**
     *
     * @return
     */
    private Integer VMSelectionCreation(final EvolutionState state,
                                        final Individual ind,
                                        final int threadnum,
                                        ArrayList<Double[]> vmResourceList,
                                        ArrayList<Double[]> pmResourceList,
                                        ArrayList<Double[]> actualPmResourceList,
                                        HashMap<Integer, Integer> vmIndexTypeMapping,
                                        Double containerCpu,
                                        Double containerMem,
                                        int containerOS,
                                        double globalCpuWaste,
                                        double globalMemWaste
                                        ){
        Integer chosenVM = null;
        Double BestScore = null;
        int vmNum = vmResourceList.size();
        int vmCount = 0;

        // make a copy of vmResourceList
        ArrayList<Double[]> tempVMResourceList = (ArrayList<Double[]>) vmResourceList.clone();
        for(Double[] vm:vmTypeList){
            // add this new VM into the tempList
            tempVMResourceList.add(new Double[]{
                    vm[0] - vm[0] * vmCpuOverheadRate,
                    vm[1] - vmMemOverhead,
                    new Double(containerOS)
            });
        }


        // Loop through the tempResourceList
        for(Double[] vm:tempVMResourceList){

            // Check if the vm exists
            newVmFlag = vmCount >= vmNum;

            // Get the remaining VM resources and OS
            double vmCpuRemain = vm[0];
            double vmMemRemain = vm[1];
            int vmOS = vm[2].intValue();
            int vmType;
            if(vmCount < vmNum)
                vmType = vmIndexTypeMapping.get(vmCount);
            else
                vmType = vmCount - vmNum;

            // If the remaining resource is enough for the container
            // And the OS is compatible
            if (vmCpuRemain >= containerCpu &&
                    vmMemRemain >= containerMem &&
                    vmOS == containerOS) {

                Double vmScore = EvolveSelectionMethod(
                        state,
                        ind,
                        threadnum,
                        vmCpuRemain,
                        vmMemRemain,
                        vmTypeList.get(vmType)[0],
                        vmTypeList.get(vmType)[1],
//                        globalCpuWaste,globalMemWaste,
                        pmResourceList,
                        actualPmResourceList);

                // Core of BestFit, score the bigger the better
                if (chosenVM == null || vmScore > BestScore) {
                    chosenVM = vmCount;
                    BestScore = vmScore;
                }

            } // End if

            // Increment the VM counter
            vmCount += 1;
        }

        newVmFlag = false;
        return chosenVM;

    }


//    /**
//     *
//     * @param vmResourceList A list of vm remain resources
//     * @param containerCPU The container required CPU
//     * @param containerMem The container required Memory
//     * @return The index of the VM in the vmReseourceList
//     */
//    private Integer VMSelection(
//                            final EvolutionState state,
//                            final Individual ind,
//                            final int  threadnum,
//                            ArrayList<Double[]> vmResourceList,
//                            Double containerCPU,
//                            Double containerMem,
//                            int containerOS){
//        Integer choosedVM = null;
//        Double BestScore = null;
//
//        // Loop through the VMs in the existing VM list
//        for (int vmCount = 0; vmCount < vmResourceList.size(); ++vmCount) {
//
//            // Get the remaining VM resources and OS
//            double vmCPUremain = vmResourceList.get(vmCount)[0];
//            double vmMemremain = vmResourceList.get(vmCount)[1];
//            int vmOS = vmResourceList.get(vmCount)[2].intValue();
//
//
//            // If the remaining resource is enough for the container
//            // And the OS is compatiable
//            if (vmCPUremain > containerCPU && vmMemremain > containerMem && vmOS == containerOS) {


                // Currently we hard-code BestFit inside the VM selection method
                // Notice that, the selection of VM does not take consideration of OS. We have filtered them out.
                // @Warning we might change it to GPHH later
//                Double vmScore = BestFit(
//                                        vmCPUremain,
//                                        vmMemremain,
//                                        containerCPU,
//                                        containerMem,
//                                        vmResourceList.get(vmCount)[0],
//                                        vmResourceList.get(vmCount)[1]);

//                Double vmScore = EvolveSelectionMethod(
//                                                state,
//                                                ind,
//                                                threadnum,
//                                                vmCPUremain,
//                                                vmMemremain,
//                                                vmResourceList.get(vmCount)[0], vmResourceList.get(vmCount)[1]);
                // Penalize if the vmScore is negative
//                if(vmScore < 0) vmScore = 1000.0;

                // Core of BestFit, score the bigger the better
//                if (choosedVM == null || vmScore > BestScore) {
//                    choosedVM = vmCount;
//                    BestScore = vmScore;
//                }
//
//            } // End if
//        } // End for loop through the VMs in the existing VM list
//        return choosedVM;
//
//    }

    private double FirstFit(Double binCPU,
                            Double binMem,
                            Double itemCPU,
                            Double itemMem,
                            double maxCpu,
                            Double maxMem){
        double score;
        // First step, normalize cpu and mem
        // Second step, score = |cpu - mem|

        // We use the (residual resources) / total resource, to normalize
        double normalizedCPU = (binCPU - itemCPU) / maxCpu;
        double normalizedMem = (binMem - itemMem) / maxMem;



        // We want to have a positive value
        if(normalizedCPU > normalizedMem)
            score = normalizedCPU - normalizedMem;
        else
            score = normalizedMem - normalizedCPU;


        return score;

    }

    /**
     * VMAllocation allocates a VM to a PM
     * @param pmResourceList is a list of remaining resources of PM, not the acutal used resources
     * @param vmCpuCapacity the cpu capacity of a VM
     * @param vmMemCapacity the memory capacity of a VM
     * @return an index of a PM
     */
    private Integer VMAllocation(ArrayList<Double[]> pmResourceList, double vmCpuCapacity, double vmMemCapacity){
        Integer chosenPM = null;
//        Double BestScore = null;

        // Loop through the PMs in the existing PM list
        for(int pmCount = 0; pmCount < pmResourceList.size(); ++pmCount){
            double pmCpuRemain = pmResourceList.get(pmCount)[0];
            double pmMemRemain = pmResourceList.get(pmCount)[1];

            // First Fit
            if (pmCpuRemain >= vmCpuCapacity && pmMemRemain >= vmMemCapacity) {
                chosenPM = pmCount;
                break;
            } // End if
        }

        return chosenPM;
    }

    private void initializeDataCenter(int testCase,
                                      ArrayList<Double[]> pmResourceList,
                                      ArrayList<Double[]> pmActualUsageList,
                                      ArrayList<Double[]> vmResourceList,
                                      HashMap<Integer, Integer> VMPMMapping,
                                      HashMap<Integer, Integer> vmIndexTypeMapping){

        ArrayList<Double[]> initPmList = initPm.get(testCase);
        ArrayList<Double[]> initVmList = initVm.get(testCase);
        ArrayList<Double[]> containerList = initContainer.get(testCase);
        ArrayList<Double[]> osList = initOs.get(testCase);


        int globalVmCounter = 0;
        // for each PM, we have an array of VM: vms[]
        for(Double[] vms:initPmList){

            // Create a new PM
            pmResourceList.add(new Double[]{
                    PMCPU,
                    PMMEM });

            pmActualUsageList.add(new Double[]{
                    PMCPU,
                    PMMEM
            });

            // for this each VM
            for(int vmCounter = 0; vmCounter < vms.length; ++vmCounter){

                // Get the type of this VM
                int vmType = vms[vmCounter].intValue() - 1;

                // Get the OS type
                Double[] os = osList.get(vmCounter + globalVmCounter);

                // Create this VM
                vmResourceList.add(new Double[]{
                        vmTypeList.get(vmType)[0] - vmTypeList.get(vmType)[0] * vmCpuOverheadRate,
                        vmTypeList.get(vmType)[1] - vmMemOverhead,
                        new Double(os[0])
                });


                // get the containers allocated on this VM
                Double[] containers = initVmList.get(vmCounter + globalVmCounter);

                // Allocate the VM to this PM,
                // Allocation includes two part, first, pmResourceList indicates the left resource of PM (subtract entire VMs' size)
                // pmIndex denotes the last PM. pmIndex should be at least 0.
                int pmIndex = pmResourceList.size() - 1;

                // update the pm left resources
                pmResourceList.set(pmIndex, new Double[]{
                        pmResourceList.get(pmIndex)[0] - vmTypeList.get(vmType)[0],
                        pmResourceList.get(pmIndex)[1] - vmTypeList.get(vmType)[1]
                });

                // The second part of allocation,
                // We update the actual usage of PM's resources
                pmActualUsageList.set(pmIndex, new Double[]{
                        pmActualUsageList.get(pmIndex)[0] - vmTypeList.get(vmType)[0] * vmCpuOverheadRate,
                        pmActualUsageList.get(pmIndex)[1] - vmMemOverhead
                });

                // Map the VM to the PM
                VMPMMapping.put(vmCounter + globalVmCounter, pmIndex);

                // for each container
                for(int conContainer = containers[0].intValue() - 1;
                    conContainer < containers[containers.length - 1].intValue();
                    ++conContainer){

                    // Get the container's cpu and memory
                    Double[] cpuMem = containerList.get(conContainer);

                    //Create this container
                    // get the left resources of this VM
                    int vmIndex = vmResourceList.size() - 1;
                    Double[] vmCpuMem = vmResourceList.get(vmIndex);

                    // update the vm
                    vmResourceList.set(vmIndex, new Double[] {
                            vmCpuMem[0] - cpuMem[0],
                            vmCpuMem[1] - cpuMem[1],
                            new Double(os[0])
                    });

                    // Whenever we create a new VM, map its index in the VMResourceList to its type for future purpose
                    vmIndexTypeMapping.put(vmResourceList.size() - 1, vmType);

                    // Add the Actual usage to the PM
                    // Here, we must consider the overhead
                    Double[] pmCpuMem = pmActualUsageList.get(pmIndex);

                    // update the pm
                    pmActualUsageList.set(pmIndex, new Double[]{
                            pmCpuMem[0] - cpuMem[0],
                            pmCpuMem[1] - cpuMem[1]
                    });


                } // Finish allocate containers to VMs

            } // End  of each VM
            // we must update the globalVmCounter
            globalVmCounter += vms.length;

        } // End of each PM



        double energy = energyCalculation(pmActualUsageList);
    }




    /**
     * Calculate the energy consumption using the following equation:
     * Energy = k * MaxEnergy + (1 - k)  * MaxEnergy * utilization_of_a_PM
     * @param pmActualUsageList
     * @return the energy consumption
     */
    private Double energyCalculation(ArrayList<Double[]> pmActualUsageList){
        Double energy = 0.0;
        for(Double[] pmActualResource:pmActualUsageList){
            energy += ((PMCPU - pmActualResource[0]) / PMCPU) * PMENERGY * (1 - k) + k * PMENERGY;
        }
        return energy;
    }

    // we read containers from file
    private void readFromFiles(int start, int end){

        for(int i = start; i <= end; ++i){
            String path = testCasePath + i + ".csv";
            String pathOS = osPath + i + ".csv";
            inputX.add(readFromFile(path, pathOS));
        }

    }

    private void readVMConfig(){
        try {
            Reader reader = Files.newBufferedReader(Paths.get(vmConfigPath));
            CSVReader csvReader = new CSVReader(reader);
            String[] nextRecord;
            while((nextRecord = csvReader.readNext()) != null){
                Double[] vm = new Double[2];
                vm[0] = Double.parseDouble(nextRecord[0]);
                vm[1] = Double.parseDouble(nextRecord[1]);
                vmTypeList.add(vm);
            }
        } catch (IOException e1){
            e1.printStackTrace();
        }
    }

    private void readOSPro(){
        try {
            Reader reader = Files.newBufferedReader(Paths.get(osProPath));
            CSVReader csvReader = new CSVReader(reader);
            String[] nextRecord;
            while((nextRecord = csvReader.readNext()) != null){
                Double pro;
                pro = Double.parseDouble(nextRecord[0]);
                OSPro.add(pro);
            }
        } catch (IOException e1){
            e1.printStackTrace();
        }
    }



    // Read two column from the testCase file
    private ArrayList<Double[]> readFromFile(String path, String osPath){
        ArrayList<Double[]> data = new ArrayList<>();
        try {
            Reader reader = Files.newBufferedReader(Paths.get(path));
            Reader readerOS = Files.newBufferedReader(Paths.get(osPath));
            CSVReader csvReader = new CSVReader(reader);
            CSVReader csvReaderOS = new CSVReader(readerOS);
            String[] nextRecord;
            String[] nextRecordOS;
            while((nextRecord = csvReader.readNext()) != null && (nextRecordOS = csvReaderOS.readNext()) != null){
                // [0] is for cpu, [1] is for mem
                Double[] container = new Double[3];
                container[0] = Double.parseDouble(nextRecord[0]);
                container[1] = Double.parseDouble(nextRecord[1]);
                container[2] = Double.parseDouble(nextRecordOS[0]);
                data.add(container);
            }
        } catch (IOException e1){
            e1.printStackTrace();
        }
        return data;
    }

}
// The code is too damn long