package ust.hk.praisehk.metamodelcalibration.measurements;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;

public class FareMeasurement extends Measurement {
	
	public static final String busFareName = "busFare";
	public static final String mtrFareName = "trainFare";

	protected FareMeasurement(String id, String mode, Map<String, Tuple<Double, Double>> timeBean) {
		super(id, timeBean);
		this.attributes.put("mode", mode);
	}

	@Override
	public Measurement clone() {
		FareMeasurement m=new FareMeasurement(this.id.toString(), (String) this.attributes.get("mode"), new HashMap<>(timeBean));
		for(String s:this.values.keySet()) {
			m.setValue(s, this.getValues().get(s));
		}
		return m;
	}

	@Override
	/**
	 * It should be valid for all key sets.
	 */
	public Set<String> getValidTimeBeans() {
		return this.timeBean.keySet();
	}

}
