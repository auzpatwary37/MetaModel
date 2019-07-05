package ust.hk.praisehk.metamodelcalibration.measurements;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

/**
 * A simplified class for holding measurements 
 * Same for both Simulation measurement analytical Measurement and RealCount measurement 
 * TODO: Add linkIds to the reader and writers
 *
 * @author h
 *
 */
public class Measurements {
	
	private final Map<String,Tuple<Double,Double>> timeBean;
	private Map<Id<Measurement>,Measurement> measurements=new HashMap<>();
	
	private double busProfit;
	
	private Measurements(Map<String,Tuple<Double,Double>> timeBean) {
		this.timeBean=timeBean;
	}
	
	public static Measurements createMeasurements(Map<String,Tuple<Double,Double>> timeBean) {
		return new Measurements(timeBean);
	}
	
	public void createAnadAddLinkMeasurement(String measurementId) {
		Measurement m=new LinkMeasurement(measurementId,this.timeBean);
		this.measurements.put(m.getId(), m);
	}
	
	protected void addMeasurement(Measurement m) {
		this.measurements.put(m.getId(), m);
	}

	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return timeBean;
	}

	public Map<Id<Measurement>, Measurement> getMeasurements() {
		return measurements;
	}
	
	/**
	 * Get the volumes for the measurement of linkMeasurement
	 * @param m The Id of the measurement we want
	 * @return
	 */
	public Map<String,Double> getVolumes(Id<Measurement> mId){
		
		Map<String,Double> volumes = ((LinkMeasurement) measurements.get(mId)).getVolumes();
		if(volumes == null) {
			throw new RuntimeException("The volumes is null!");
		}
		return volumes;
	}
	
	/**
	 * It put a volume inside the measurement
	 * @param mId
	 * @param timeBeanId
	 * @param volume
	 */
	public void addVolume(Id<Measurement> mId, String timeBeanId, double volume) {
		((LinkMeasurement) measurements.get(mId)).addVolume(timeBeanId, volume);
	}
	
	public double getBusProfit() {
		return busProfit;
	}
	
	/**
	 * Will deep clone the measurement and provide a new measurement exactly same as the current measurement 
	 * Modifying the current measurement will not affect the new created measurement and vise-versa The attributes are not deep copied
	 */
	@Override
	public Measurements clone() {
		Measurements m=new Measurements(this.timeBean);
		for(Measurement mm: this.measurements.values()) {
			m.addMeasurement(mm.clone());
		}
		m.busProfit = this.busProfit;
		return m;
	}
	
	public void updateMeasurements(Map<String,Map<Id<Link>,Double>> linkVolumes) {
		for(Measurement m:this.measurements.values()) {
			if(m instanceof LinkMeasurement)
				((LinkMeasurement) m).updateLinkVolumes(linkVolumes);
		}
	}
	
	public void updateMeasurements(MeasurementDataContainer mdc) {
		for(Measurement m:this.measurements.values()) {
			if(m instanceof LinkMeasurement)
				((LinkMeasurement) m).updateLinkVolumes(mdc.linkVolumes);
		}
		
		this.busProfit = mdc.profit;
	}
	
	public void setBusProfit(double profit) {
		this.busProfit = profit;
	}
	
	/**
	 * Will return a set containing all the links to count volume for 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Set<Id<Link>> getLinksToCount(){
		Set<Id<Link>>linkSet=new HashSet<>();
		
		for(Measurement m: this.measurements.values()) {
			if(m instanceof LinkMeasurement) {
				LinkMeasurement lm = (LinkMeasurement) m;
				for(Id<Link>lId:(ArrayList<Id<Link>>)lm.getAttribute(lm.linkListAttributeName)) {
					linkSet.add(lId);
				}
			}
		}
		
		return linkSet;
	}
	
	public void writeCSVMeasurements(String fileLoc) {
		try {
			FileWriter fw=new FileWriter(new File(fileLoc),false);
			fw.append("MeasurementId,timeId,PCUCount\n");
			for(Measurement m:this.measurements.values()) {
				if(m instanceof LinkMeasurement) {
					LinkMeasurement lm = (LinkMeasurement) m;
					for(String timeId:lm.getVolumes().keySet())
						fw.append(m.getId().toString()+","+timeId+","+lm.getVolumes().get(timeId)+"\n");
				}
			}
			fw.flush();
			fw.close();
		} catch (IOException e) {
			
			e.printStackTrace();
		}
	}
	
	//TODO: Extend this function to allow other measurement
	public void updateMeasurementsFromFile(String fileLoc) {
		try {
			BufferedReader bf=new BufferedReader(new FileReader(new File(fileLoc)));
			bf.readLine();
			String line;
			while((line=bf.readLine())!=null) {
				String[] part=line.split(",");
				Id<Measurement> measurementId=Id.create(part[0].trim(), Measurement.class);
				if(!this.measurements.containsKey(measurementId)) {
					this.createAnadAddLinkMeasurement(measurementId.toString());
				}
				String timeBeanId=part[1].trim();
				((LinkMeasurement) this.measurements.get(measurementId)).addVolume(timeBeanId, Double.parseDouble(part[2].trim()));
			}
			bf.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
