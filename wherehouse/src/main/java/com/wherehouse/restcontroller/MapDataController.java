package com.wherehouse.restcontroller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wherehouse.rest.redius.service.MapDataService;

import java.util.List;
import java.util.Map;

@RestController
public class MapDataController {

    @Autowired
    private MapDataService mapDataService;

    @GetMapping("/getAllMapData")
    public Map<String, List<Map<String, Double>>> getAllMapData() {
        return mapDataService.getAllMapDataService();
    }
    
    @GetMapping("/getChoiceMapData")
    public Map<String, List<Map<String, Double>>> getChoiceMapData(@RequestParam(name = "guNames") List<String> guNames) {
        
    	System.out.println("MapDataController.getChoiceMapData()!");
    	
        return mapDataService.getChoiceMapDataService(guNames);
    }

}
