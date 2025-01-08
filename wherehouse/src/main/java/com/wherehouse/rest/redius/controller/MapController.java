package com.wherehouse.rest.redius.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.wherehouse.rest.redius.service.MapService;

@RestController
public class MapController {

    @Autowired
    private MapService mapService;

    @PostMapping("/loadGuCoordinates")
    public Map<String, List<Map<String, Double>>> getAllCoordinates(@RequestBody List<Integer> guIds) {
        System.out.println("MapController.getAllCoordinates()!");
        return mapService.getLocations(guIds);
    }
}
