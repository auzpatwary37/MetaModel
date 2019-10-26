package ust.hk.praisehk.metamodelcalibration.measurements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;

public class LinkMeasurement extends Measurement{
	
	/**
	 * Some attributes name are kept as public and final string
	 */
	public static final String linkListAttributeName="LINK_LIST";
	
	private static final Logger logger=Logger.getLogger(LinkMeasurement.class);
	private Coord coord=null;
	
	public Coord getCoord() {
		return coord;
	}

	public void setCoord(Coord coord) {
		this.coord = coord;
	}

	protected LinkMeasurement(String id, Map<String, Tuple<Double, Double>> timeBean) {
		super(id, timeBean);
		this.attributes.put(linkListAttributeName, new ArrayList<Id<Link>>());
	}

	
	public LinkMeasurement clone() {
		LinkMeasurement m=new LinkMeasurement(this.id.toString(),new HashMap<>(timeBean));
		for(String s:this.values.keySet()) {
			m.setValue(s, this.getValues().get(s));
		}
		for(String s:this.attributes.keySet()) {
			m.setAttribute(s, this.attributes.get(s));
		}
		m.coord = this.coord;
		return m;
	}

	/**
	 * Default implementation of updater.
	 * Should be overridden if necessary.
	 * @param linkVolumes
	 * is a Map<timeBeanId,Map<Id<Link>,Double>> containing link traffic volumes of all the time beans
	 */
	@SuppressWarnings("unchecked")
	public void updateLinkVolumes(Map<String,Map<Id<Link>,Double>> linkVolumes) {
		if(((ArrayList<Id<Link>>)this.attributes.get(linkListAttributeName)).isEmpty()) {
			logger.warn("MeasurementId: "+this.getId().toString()+" LinkList is empty!!! creating linkId from measurement ID");
			((ArrayList<Id<Link>>)this.attributes.get(linkListAttributeName)).add(Id.createLinkId(this.getId().toString()));
		}
		if(this.values.size()==0) {
			logger.warn("MeasurementId: "+this.getId().toString()+" Volume is empty!!! Updating volume for all time beans");
			for(String s: this.timeBean.keySet()) {
				if(linkVolumes.containsKey(s)) {
					this.values.put(s, 0.);
				}
			}
		}
		for(String s:values.keySet()) {
			double volume=0;
			for(Id<Link>linkId:((ArrayList<Id<Link>>)this.attributes.get(linkListAttributeName))) {
				try {
					if(linkVolumes.get(s)==null) {
						throw new IllegalArgumentException("linkVolumes does not contain volume information");
					}
					if(linkVolumes.get(s).get(linkId)==null) {
						Id<Link> newLinkId = Id.createLinkId(linkId.toString().replaceAll(" ", ""));
						if(linkVolumes.get(s).get(newLinkId)!=null) {
							volume+=linkVolumes.get(s).get(newLinkId);
							continue;
						}
						throw new IllegalArgumentException("linkVolumes does not contain volume information");
					}
					volume+=linkVolumes.get(s).get(linkId);
				}catch(Exception e) {
					logger.error("Illegal Argument Excepton. Could not update measurements. Volumes are missing for measurement Id: "+this.getId()+" timeBeanId: "
							+s+" linkId: "+linkId);
				}
				
			}
			this.values.put(s, volume);
		}
	}

	@Override
	public Set<String> getValidTimeBeans() {
		return this.values.keySet();
	}
}
