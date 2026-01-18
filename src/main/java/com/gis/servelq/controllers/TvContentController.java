package com.gis.servelq.controllers;

import com.gis.servelq.models.TvContent;
import com.gis.servelq.services.TvContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/serveiq/api/tv-content")
@RequiredArgsConstructor
public class TvContentController {

    private final TvContentService service;

    @GetMapping("/{branchId}")
    public List<TvContent> getContent(@PathVariable String branchId) {
        return service.getContentByBranch(branchId);
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

    @PatchMapping("/activateVideo")
    public TvContent activate(@RequestBody Map<String, String> req) {
        return service.activateVideo(req.get("branchId"), req.get("id"));
    }

    @PostMapping("/image/upload")
    public ResponseEntity<TvContent> upload(@RequestParam String branchId, @RequestParam MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(service.uploadImage(branchId, file));
    }

    @GetMapping("/image")
    public ResponseEntity<List<TvContent>> getAll(
            @RequestParam String branchId
    ) {
        return ResponseEntity.ok(service.getAllImages(branchId));
    }

    @GetMapping("/image/active")
    public ResponseEntity<List<TvContent>> getActiveImages(@RequestParam String branchId) {
        return ResponseEntity.ok(service.getActiveImages(branchId));
    }

    @PatchMapping("/image/toggle/{id}")
    public ResponseEntity<TvContent> toggleImageStatus(@RequestParam String branchId, @PathVariable String id) {
        return ResponseEntity.ok(service.toggleImageStatus(branchId, id));
    }

    @DeleteMapping("/image/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable String id) throws IOException {
        service.deleteImage(id);
        return ResponseEntity.noContent().build();
    }
}
