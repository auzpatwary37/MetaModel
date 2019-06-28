package ust.hk.praisehk.metamodelcalibration.matamodels;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.calibrator.ParamReader;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.MeasurementsStorage;
import ust.hk.praisehk.metamodelcalibration.matsimIntegration.SimRun;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;


/**
 * TODO: Make this class generic 
 * Now it is CNLSUE specific
 * @author Ashraf
 *
 */
public class SimAndAnalyticalGradientCalculator {
	
	public static final String SPSAMethodName="SPSA";
	public static final String FDMethodName="FD";
	
	
	private MeasurementsStorage storage;
	private Config config;
	private SimRun simRun;
	private final ParamReader pReader;
	private Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>> simGradient=new HashMap<>();
	private Map<Id<Measurement>,Map<String,LinkedHashMap<String,Double>>> anaGradient=new HashMap<>();
	private LinkedHashMap<String,Double> originalGrad=new LinkedHashMap<>();
	private LinkedHashMap<String,Double> currentParam;
	private double c=0.1;
	private int currentIterCounter;
	private final String GradeintCalulationMethod;
	private final boolean runParallel;
	
	/**
	 * This would be the main gradient calculator we are using.
	 * @param config Input config
	 * @param storage Storing the measurements (Real one)
	 * @param simRun For running the simulation
	 * @param c Should be some parameters?
	 * @param gradientCalculationMethod Determine how the gradient are calculated
	 * @param currentIterCounter
	 * @param runParallel Parllel or not
	 * @param pReader For storing the parameters
	 */
	public SimAndAnalyticalGradientCalculator(Config config,MeasurementsStorage storage,SimRun simRun,
			double c,String gradientCalculationMethod,int currentIterCounter,boolean runParallel,ParamReader pReader) {
		this.pReader=pReader;
		this.storage=storage;
		this.simRun=simRun;
		if(c!=0) {
			this.c=c;
		}
		this.currentIterCounter=currentIterCounter;
		this.config=config;
		this.GradeintCalulationMethod=gradientCalculationMethod;
		this.runParallel=runParallel;
	}
	
	/**
	 * This method will invoke run method on the already created gradient calculator
	 * This will give the calibrator option to choose to start the gradient calculation as necessary
	 */
	public void calcGradient(LinkedHashMap<String,Double> param) {
		this.currentParam=param;
		if(this.GradeintCalulationMethod.equals(SPSAMethodName)) {
			this.calcSPSAGradient(runParallel);
		}else if(this.GradeintCalulationMethod.equals(FDMethodName)){
			this.calcFDGradient(runParallel);
		}else {
			throw new IllegalArgumentException("The input gradient is wrong!");
		}
	}
	
	private void calcSPSAGradient(boolean runParallel) {
		//LinkedHashMap<String,Double>gHat=new LinkedHashMap<>();
		double[] delta = generateBernouilliDistribution(this.currentParam.size());
		LinkedHashMap<String,Double> thetaMinus = new LinkedHashMap<>();
		LinkedHashMap<String,Double> thetaPlus = new LinkedHashMap<>();
		int i=0;
		for(String s:this.currentParam.keySet()) {
			thetaMinus.put(s,this.currentParam.get(s)-this.c*delta[i]); //delta in {-1, 1}
			thetaPlus.put(s,this.currentParam.get(s)+c*delta[i]);
			i++;
		}
		Measurements simLinkCountPlus;
		Measurements simLinkCountMinus;
		Measurements anaLinkCountPlus;
		Measurements anaLinkCountMinus;
		
		//Step 1: Calculate the gradient of the simulator
		if(runParallel) {
			Thread[] threads=new Thread[2];
			simandAnaRunRunnable[] functionEvals=new simandAnaRunRunnable[2];
			
			Config configThetaPlus=pReader.SetParamToConfig(this.config, thetaPlus);
			Config configThetaMinus=pReader.SetParamToConfig(this.config, thetaMinus);
			functionEvals[0]=new simandAnaRunRunnable(configThetaPlus, this.simRun, thetaPlus,currentIterCounter+"_thread0",this.storage,this.pReader);
			functionEvals[1]=new simandAnaRunRunnable(configThetaMinus, this.simRun, thetaMinus,currentIterCounter+"_thread1",this.storage,this.pReader);
			
			threads[0]=new Thread(functionEvals[0]);
			threads[1]=new Thread(functionEvals[1]);
			threads[0].start();
			threads[1].start();
			
			try {
				threads[0].join();
				threads[1].join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			simLinkCountPlus=functionEvals[0].getSimCount();
			simLinkCountMinus=functionEvals[1].getSimCount();
			anaLinkCountPlus=functionEvals[0].getAnaCount();
			anaLinkCountMinus=functionEvals[1].getAnaCount();
			
		}else {
			//Run for the first time
			CNLSUEModel sue=new CNLSUEModel(this.storage.getTimeBean());
			sue.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
			Config config=pReader.SetParamToConfig(this.config, thetaPlus); //With little change
			simRun.run(sue, config, thetaPlus, true,currentIterCounter+"_thread0",this.storage);
			simLinkCountPlus=this.storage.getSimMeasurement(thetaPlus); //Key: get back the simulation measurements from the run
			anaLinkCountPlus=simLinkCountPlus.clone();
			anaLinkCountPlus.updateMeasurements(sue.perFormSUE(pReader.ScaleUp(thetaPlus))); //Making the analytical measurements
			
			//Run for the second time.
			sue=new CNLSUEModel(this.storage.getTimeBean());
			sue.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
			config=pReader.SetParamToConfig(this.config, thetaMinus);
			simRun.run(sue, config, thetaMinus, true,currentIterCounter+"_thread1",this.storage);
			simLinkCountMinus=this.storage.getSimMeasurement(thetaMinus);
			anaLinkCountMinus=simLinkCountMinus.clone();
			anaLinkCountMinus.updateMeasurements(sue.perFormSUE(pReader.ScaleUp(thetaMinus)));
		}
		//time
		for(Measurement m:this.storage.getCalibrationMeasurements().getMeasurements().values()) {
			this.simGradient.put(m.getId(),new HashMap<String, LinkedHashMap<String, Double>>());
			this.anaGradient.put(m.getId(), new HashMap<String, LinkedHashMap<String, Double>>());
			//linkId
			for(String timeBeanId:simLinkCountPlus.getVolumes(m.getId()).keySet()) {
				this.simGradient.get(m.getId()).put(timeBeanId, new LinkedHashMap<String,Double>());
				this.anaGradient.get(m.getId()).put(timeBeanId, new LinkedHashMap<String,Double>());
				i=0;
				//params
				for(String paramName:this.currentParam.keySet()) {
					double gHatSim=(simLinkCountPlus.getVolumes(m.getId()).get(timeBeanId)-simLinkCountMinus.getVolumes(m.getId()).get(timeBeanId))/(2*c*delta[i]);
					double gHatAna=(anaLinkCountPlus.getVolumes(m.getId()).get(timeBeanId)-anaLinkCountMinus.getVolumes(m.getId()).get(timeBeanId))/(2*c*delta[i]);
					this.simGradient.get(m.getId()).get(timeBeanId).put(paramName, gHatSim);
					this.anaGradient.get(m.getId()).get(timeBeanId).put(paramName, gHatAna);
					i++;
					if(this.originalGrad.get(paramName)==null) {
						this.originalGrad.put(paramName, gHatSim*(this.storage.getSimMeasurement(this.currentParam).getVolumes(m.getId()).get(timeBeanId)-this.storage.getCalibrationMeasurements().getVolumes(m.getId()).get(timeBeanId)));
					}else {
						this.originalGrad.put(paramName, this.originalGrad.get(paramName)+gHatSim*(this.storage.getSimMeasurement(this.currentParam).getVolumes(m.getId()).get(timeBeanId)-this.storage.getCalibrationMeasurements().getVolumes(m.getId()).get(timeBeanId)));
					}		
				}
			}
		}
	}
	
	
	

	
	public LinkedHashMap<String, Double> getOriginalGrad() {
		return originalGrad;
	}

	private static double[]  generateBernouilliDistribution(int size) {
		double[] delta = new double[size];
		Random rand = new Random();
		for(int i = 0; i<size; i++) {
			delta[i] = (2 * (Math.round(rand.nextDouble())))-1;
		}
		return delta;
	}

	
	
	private void calcFDGradient(boolean runParallel) {
		
		//TODO: calculate original gradient similar to SPSA technique
		 	double h = 0;
		 	Measurements simLinkCountPlus;
			Measurements simLinkCountMinus;
			Measurements anaLinkCountPlus;
			Measurements anaLinkCountMinus;
	        LinkedHashMap<String,Double> p = new LinkedHashMap<>(this.currentParam);
	        int i=0;
	        for (String s:this.currentParam.keySet()) {
	            h = c* Math.abs(this.currentParam.get(s));
	            p.put(s, this.currentParam.get(s)+h);
	            LinkedHashMap<String,Double>pPlus=new LinkedHashMap<>(p);
	            p.put(s, this.currentParam.get(s)-h);
	            LinkedHashMap<String,Double>pMinus=new LinkedHashMap<>(p);
	            if(runParallel!=true) {
	            	AnalyticalModel sue=new CNLSUEModel(this.storage.getTimeBean());
	    			sue.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
	    			Config configPPlus=pReader.SetParamToConfig(this.config, pPlus);
	            	simRun.run(sue, configPPlus, pPlus, true,currentIterCounter+s+"_thread0",storage);
	    			
	    			simLinkCountPlus=this.storage.getSimMeasurement(pPlus);
	    			anaLinkCountPlus=simLinkCountPlus.clone();
	    			anaLinkCountPlus.updateMeasurements(sue.perFormSUE(pReader.ScaleUp(pPlus)));
	    			sue=new CNLSUEModel(this.storage.getTimeBean());
	    			sue.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
	    			
	    			Config configPMinus=pReader.SetParamToConfig(this.config, pMinus);
	    			
	    			simRun.run(sue, configPMinus, pMinus, true,currentIterCounter+s+"_thread0",storage);
	    			simLinkCountMinus=storage.getSimMeasurement(pMinus);
	    			anaLinkCountMinus=simLinkCountMinus.clone();
	    			anaLinkCountMinus.updateMeasurements(sue.perFormSUE(pReader.ScaleUp(pMinus)));
	            }else {
	            	Thread[] threads=new Thread[2];
	    			simandAnaRunRunnable[] functionEvals=new simandAnaRunRunnable[2];
	    			
	    			Config configPPlus=pReader.SetParamToConfig(this.config, pPlus);
	    			Config configPMinus=pReader.SetParamToConfig(this.config, pMinus);
	    			functionEvals[0]=new simandAnaRunRunnable(configPPlus, this.simRun, pPlus,currentIterCounter+s+"_thread0",storage,this.pReader);
	    			functionEvals[1]=new simandAnaRunRunnable(configPMinus, this.simRun, pMinus,currentIterCounter+s+"_thread1",storage,this.pReader);
	    			
	    			threads[0]=new Thread(functionEvals[0]);
	    			threads[1]=new Thread(functionEvals[1]);
	    			threads[0].start();
	    			threads[1].start();
	    			
	    			try {
	    				threads[0].join();
	    				threads[1].join();
	    			} catch (InterruptedException e) {
	    				// TODO Auto-generated catch block
	    				e.printStackTrace();
	    			}
	    			simLinkCountPlus=functionEvals[0].getSimCount();
	    			simLinkCountMinus=functionEvals[1].getSimCount();
	    			anaLinkCountPlus=functionEvals[0].getAnaCount();
	    			anaLinkCountMinus=functionEvals[1].getAnaCount();
	            }
	            p.put(s, currentParam.get(s));
	            
	            for(Measurement m:simLinkCountPlus.getMeasurements().values()) {
	            	if(i==0) {
	            		this.simGradient.put(m.getId(), new HashMap<String, LinkedHashMap<String, Double>>());
	            		this.anaGradient.put(m.getId(), new HashMap<String, LinkedHashMap<String, Double>>());
	            	}
	            	for(String timeBeanId:simLinkCountPlus.getVolumes(m.getId()).keySet()) {
	            		if(i==0) {
		            		this.simGradient.get(m.getId()).put(timeBeanId, new LinkedHashMap<String,Double>());
		            		this.anaGradient.get(m.getId()).put(timeBeanId, new LinkedHashMap<String,Double>());
		            	}
	            		double simGrad=(simLinkCountPlus.getVolumes(m.getId()).get(timeBeanId) - simLinkCountMinus.getVolumes(m.getId()).get(timeBeanId)) / (2.0 * h);
	            		double anaGrad=(anaLinkCountPlus.getVolumes(m.getId()).get(timeBeanId) - anaLinkCountMinus.getVolumes(m.getId()).get(timeBeanId)) / (2.0 * h);
	            		this.simGradient.get(m.getId()).get(timeBeanId).put(s,simGrad);
	            		this.anaGradient.get(m.getId()).get(timeBeanId).put(s, anaGrad);
	            	}
	            }
	            i++;
	        }
	       
	    }

	public Map<Id<Measurement>, Map<String, LinkedHashMap<String, Double>>> getSimGradient() {
		return simGradient;
	}

	public Map<Id<Measurement>, Map<String, LinkedHashMap<String, Double>>> getAnaGradient() {
		return anaGradient;
	}
	
	public void gradientLogger(String fileLoc) {
		FileWriter fw;
		try {
			fw = new FileWriter(new File(fileLoc));
		
		fw.append("current Point,");
		for(String s:this.currentParam.keySet()) {
			fw.append(","+s);
		}
		fw.append("\n");
		for(double d:this.currentParam.values()) {
			fw.append(","+d);
		}
		fw.append("\n");
		fw.append("LinkId,timeBeanId,");
		for(String s:this.currentParam.keySet()) {
			fw.append("AnaGradient"+s+",");
		}
		for(String s:this.currentParam.keySet()) {
			fw.append("SimGradient"+s+",");
		}
		fw.append("\n");
		for(Id<Measurement> mId:this.simGradient.keySet()) {
			for(String timeBeanId:this.simGradient.get(mId).keySet()) {
				fw.append(mId+","+timeBeanId+",");
				for(String ss:this.currentParam.keySet()) {
					fw.append(this.anaGradient.get(mId).get(timeBeanId).get(ss)+",");
				}
				for(String ss:this.currentParam.keySet()) {
					fw.append(this.simGradient.get(mId).get(timeBeanId).get(ss)+",");
				}
				fw.append("\n");
			}
		}
		fw.flush();
		fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
}



class simandAnaRunRunnable implements Runnable{
	private SimRun simRun;
	private Config config;
	private LinkedHashMap<String,Double> atParam;
	private AnalyticalModel sue;
	private Measurements anaCount;
	private Measurements simCount;
	private String threadNo;
	private MeasurementsStorage storage;
	private final ParamReader pReader;
	
	public simandAnaRunRunnable(Config config,SimRun simRun,LinkedHashMap<String,Double>atParam,String threadNo,MeasurementsStorage storage, final ParamReader pReader){
		this.storage=storage;
		this.simRun=simRun;
		this.config=config;
		this.atParam=atParam;
		this.sue=new CNLSUEModel(this.storage.getTimeBean());
		this.sue.setDefaultParameters(pReader.ScaleUp(pReader.getDefaultParam()));
		this.threadNo=threadNo;
		this.pReader=pReader;
	}
	@Override
	public void run() {
		simRun.run(sue, this.config, this.atParam, true,threadNo,storage);
		this.simCount=this.storage.getSimMeasurement(atParam);
		this.anaCount=this.simCount.clone();
		this.anaCount.updateMeasurements(sue.perFormSUE(pReader.ScaleUp(atParam)));
		}
	public Measurements getAnaCount() {
		return anaCount;
	}
	public Measurements getSimCount() {
		return simCount;
	}
	
	
}
