package ust.hk.praisehk.metamodelcalibration.Utils;

import java.util.Map;
import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;

public final class TimeBeanUtils {
	
	public static String findTimeBean(Map<String, Tuple<Double, Double>> timeBeans, double time) {
		time = (time==0)?1:time;
		time = (time > 27*3600)? time = 27 * 3600:time;
		for(String s:timeBeans.keySet()) {
			if(time>timeBeans.get(s).getFirst() && time<=timeBeans.get(s).getSecond()) {
				return s;
			}
		}
		throw new IllegalArgumentException("The time is not correct, or the time bean is wrong!");
	}
}
