package com.sag.aggregator;

import java.util.Arrays;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RestService {
	final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Autowired
	private MeanAggregator meanAggregator;

    @GetMapping(value = "/mean", produces = MediaType.APPLICATION_JSON_VALUE)
    public String meanAggregate(String groupId, String fragmentType, String[] series, String fromDate, String toDate, long interval) {
		return meanAggregator.meanAggregateMeasurements(groupId, fragmentType, Arrays.asList(series), DateTime.parse(fromDate), DateTime.parse(toDate), interval);
    }
}
