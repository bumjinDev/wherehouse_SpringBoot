package com.wherehouse.rest.redius.service;

import java.util.List;
import java.util.Map;



public interface IMapService {

	 Map<String, List<Map<String, Double>>> getLocations(List<Integer> guIds);
}
