package com.gis.servelq.controllers;

import com.gis.servelq.models.TvContent;
import com.gis.servelq.services.TvContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/serveiq/api/tv-content")
@RequiredArgsConstructor
public class TvContentController {

    private final TvContentService service;

    @GetMapping("/{branchId}")
    public List<TvContent> getContent(@PathVariable String branchId) {
        return service.getByBranch(branchId);
    }

    @PostMapping("/url")
    public TvContent addUrl(@RequestBody Map<String, String> req) {
        return service.addUrl(req.get("branchId"), req.get("url"), req.get("name"));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @PostMapping("/video")
    public TvContent addVideo(@RequestBody Map<String, String> req) {
        // only store video NAME — no file upload
        return service.addVideo(req.get("branchId"), req.get("name"));
    }

    @PatchMapping("/activate")
    public TvContent activate(@RequestBody Map<String, String> req) {
        return service.activate(req.get("branchId"), req.get("id"));
    }
}
