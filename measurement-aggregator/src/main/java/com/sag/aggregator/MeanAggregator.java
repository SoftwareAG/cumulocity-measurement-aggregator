package com.sag.aggregator;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.rest.representation.measurement.MeasurementRepresentation;
import com.cumulocity.sdk.client.Param;
import com.cumulocity.sdk.client.QueryParam;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.measurement.MeasurementApi;
import com.cumulocity.sdk.client.measurement.MeasurementFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MeanAggregator {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private InventoryApi inventoryApi;

	@Autowired
	private MeasurementApi measurementApi;

	public String meanAggregateMeasurements(String groupId, String fragmentType,
			List<String> series, DateTime fromDate, DateTime toDate, long interval) {
		Map<DateTime, Map<String, List<BigDecimal>>> unaggregatedMeasurements = new HashMap<DateTime, Map<String, List<BigDecimal>>>();
		Map<DateTime, Map<String, BigDecimal>> measurements = new TreeMap<DateTime, Map<String, BigDecimal>>();

		ObjectMapper mapper = new ObjectMapper();

		Iterable<GId> mors;
		ManagedObjectRepresentation group = inventoryApi.get(new GId(groupId));
		if (group.getProperty("c8y_IsDynamicGroup") != null) {
			String queryParam = group.getProperty("c8y_DeviceQueryString").toString();
			QueryParam query = null;
			try {
				query = new QueryParam(new Param() {

					@Override
					public String getName() {
						return "query";
					}
					
				}, URLEncoder.encode(queryParam.replace("$filter=", ""), "utf-8").replace(" $orderby", "&orderby"));
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			mors = StreamSupport.stream(inventoryApi.getManagedObjects().get(query).allPages().spliterator(), false).map(mor -> {
				logger.info("Found device {}", mor.getName());
				return mor.getId();
			}).collect(Collectors.toList());
		} else {
			mors = StreamSupport.stream(inventoryApi.getManagedObjectApi(group.getId()).getChildAssets().get().allPages().spliterator(), false).map(morr -> morr.getManagedObject().getId()).collect(Collectors.toList());
		}
		mors.forEach(mor -> {
			MeasurementFilter filter = new MeasurementFilter().byDate(fromDate.toDate(), toDate.toDate())
					.bySource(mor).byValueFragmentType(fragmentType);
			Iterator<MeasurementRepresentation> allMeasurements = measurementApi.getMeasurementsByFilter(filter).get()
					.allPages().iterator();
			if (allMeasurements.hasNext()) {
				DateTime[] currentDate = {fromDate};
				MeasurementRepresentation currentMeasurement = allMeasurements.next();
				while (currentDate[0].isBefore(toDate)) {
					final int[] i = {0};
					final Map<String, BigDecimal> values = new HashMap<>();
					series.forEach(s -> values.put(s, new BigDecimal(0)));
					while (currentMeasurement.getDateTime().isBefore(currentDate[0].plus(interval))) {
						try {
							final JsonNode rootNode = mapper.readTree(currentMeasurement.toJSON());
							series.forEach(s -> {
								if (rootNode.get(fragmentType).has(s)) {
									BigDecimal readValue = rootNode.get(fragmentType).get(s).get("value").decimalValue();
									values.put(s, values.get(s).add(readValue));
								}
							});
						} catch (IOException e) {
							e.printStackTrace();
						}
						i[0]++;
						if (allMeasurements.hasNext()) {
							currentMeasurement = allMeasurements.next();
						} else {
							break;
						}
					}
					if (i[0] > 0) {
						series.forEach(s -> {values.put(s, values.get(s).divide(new BigDecimal(i[0]), 2, RoundingMode.HALF_UP));
							if (!unaggregatedMeasurements.containsKey(currentDate[0])) {
								unaggregatedMeasurements.put(currentDate[0], new HashMap<String, List<BigDecimal>>());
							}
							if (!unaggregatedMeasurements.get(currentDate[0]).containsKey(s)) {
								unaggregatedMeasurements.get(currentDate[0]).put(s, new ArrayList<>());
							}
							unaggregatedMeasurements.get(currentDate[0]).get(s).add(values.get(s));
						});
					}
					currentDate[0] = currentDate[0].plus(interval);
				}
			}
		});
		
		unaggregatedMeasurements.forEach((date, map) -> {
			map.forEach((s, list) -> {
				BigDecimal[] value = {new BigDecimal(0)};
				int[] counter = {0};
				list.forEach(m -> {
					value[0] = value[0].add(m);
					counter[0]++;
				});
				if (!measurements.containsKey(date)) {
					measurements.put(date, new HashMap<String, BigDecimal>());
				}
				if (counter[0] > 0) {
					measurements.get(date).put(s, value[0].divide(new BigDecimal(counter[0]), 2, RoundingMode.HALF_UP));
				}
			});
		});
		
		List<String> measures = new ArrayList<String>();
		measurements.forEach((date, map) -> {
			String[] measurement = {"{\"time\":\"" + date.toDateTimeISO() + "\""};
			map.forEach((s, m) -> {
				measurement[0] += " ,\"" + s +"\":" + m.toString();
			});
			measurement[0] +=  "}";
			measures.add(measurement[0]);
		});

		String result = "[" + measures.stream().reduce(null, (sub, m) -> {
			if (sub != null ) {
				return String.join(",", sub, m);
			} else {
				return m;
			}
		}) + "]";
		logger.info("Result of aggregation: {}", result);
		return result;
	}
}
