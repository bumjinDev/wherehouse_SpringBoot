package com.wherehouse.restapi.mapdata.service;

import java.util.List;
import java.util.Map;



public interface IMapService {

	Map<String, List<Map<String, Double>>> getAllMapDataService();
	Map<String, List<Map<String, Double>>> getChoiceMapDataService(List<String> guNames);
}
