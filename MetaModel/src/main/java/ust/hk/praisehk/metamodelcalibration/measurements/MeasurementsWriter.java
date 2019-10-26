package ust.hk.praisehk.metamodelcalibration.measurements;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;

import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;

public class MeasurementsWriter extends DefaultHandler{

	private final Measurements m;
	public MeasurementsWriter(Measurements m) {
		this.m=m;
	}

	public void write(String fileLoc) {
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

			Document document = documentBuilder.newDocument();

			Element rootEle = document.createElement("Measurements");

			Element TimeBeans=document.createElement("timeBeans");
			for(Entry<String, Tuple<Double, Double>>timeBean:m.getTimeBean().entrySet()) {
				Element TimeBean=document.createElement("timeBean");
				TimeBean.setAttribute("timeBeanId", timeBean.getKey());
				TimeBean.setAttribute("StartingTime", Double.toString(timeBean.getValue().getFirst()));
				TimeBean.setAttribute("EndingTime", Double.toString(timeBean.getValue().getSecond()));
				TimeBeans.appendChild(TimeBean);
			}
			rootEle.appendChild(TimeBeans);
			
			for(Measurement mm:m.getMeasurements().values()) {
				if(mm instanceof LinkMeasurement) {
					LinkMeasurement lm = (LinkMeasurement) mm;
					Element measurement=document.createElement("Measurement");
					measurement.setAttribute("MeasurementId", mm.getId().toString());
					if(lm.getCoord()!=null) {
						Element Coord=document.createElement("Coord");
						Coord.setAttribute("X", Double.toString(lm.getCoord().getX()));
						Coord.setAttribute("Y", Double.toString(lm.getCoord().getY()));
						measurement.appendChild(Coord);
					}
					Element Volumes=document.createElement("Volumes");
					for(Entry<String,Double>e:lm.getValues().entrySet()) {
						Element volume=document.createElement("Volume");
						volume.setAttribute("TimeBeanId", e.getKey());
						volume.setAttribute("PCUVolume", Double.toString(e.getValue()));
						Volumes.appendChild(volume);
					}
					measurement.appendChild(Volumes);
	
					Element linkIds=document.createElement("LinkIds");
	
					if(lm.getAttribute(lm.linkListAttributeName)==null) {
						Element lId=document.createElement("LinkId");
						lId.setAttribute("Id", mm.getId().toString());
						linkIds.appendChild(lId);
					}else {
						for(Id<Link>linkId:((ArrayList<Id<Link>>)lm.getAttribute(lm.linkListAttributeName))) {
							Element lId=document.createElement("LinkId");
							lId.setAttribute("Id", linkId.toString());
							linkIds.appendChild(lId);
						}
					}
					measurement.appendChild(linkIds);
					
					for(String s:lm.getAttributes().keySet()) {
						measurement.setAttribute(s, lm.getAttribute(s).toString());
					}
					
					rootEle.appendChild(measurement);
				}
				if(mm instanceof FareMeasurement) { 
					FareMeasurement lm = (FareMeasurement) mm;
					Element measurement=document.createElement("Measurement");
					measurement.setAttribute("MeasurementId", mm.getId().toString());
					Element faresCollected=document.createElement("Fares");
					
					for(Entry<String,Double>e : lm.getValues().entrySet()) {
						Element volume=document.createElement("FareCollected");
						volume.setAttribute("TimeBeanId", e.getKey());
						volume.setAttribute("Amount", Double.toString(e.getValue()));
						faresCollected.appendChild(volume);
					}
					measurement.appendChild(faresCollected);
					
					for(String s:lm.getAttributes().keySet()) {
						measurement.setAttribute(s, lm.getAttribute(s).toString());
					}
					
					rootEle.appendChild(measurement);
				}
			}
			
			document.appendChild(rootEle);
			

			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			//tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "measurements.dtd");
			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			tr.transform(new DOMSource(document), new StreamResult(new FileOutputStream(fileLoc)));


		}catch(Exception e) {
			
		}

	}
}
