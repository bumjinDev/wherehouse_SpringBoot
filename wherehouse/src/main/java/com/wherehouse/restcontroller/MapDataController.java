package com.wherehouse.restcontroller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wherehouse.rest.redius.service.MapDataService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class MapDataController {

    @Autowired
    private MapDataService mapDataService;

    @GetMapping("/getAllMapData")
    public  Map<String, List<Map<String, Double>>> getAllMapData() {
        return mapDataService.getAllMapData();
    }
}
