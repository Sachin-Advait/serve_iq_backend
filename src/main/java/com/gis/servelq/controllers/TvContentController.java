package com.gis.servelq.controllers;

import com.gis.servelq.dto.TvContentResponseDTO;
import com.gis.servelq.models.AppType;
import com.gis.servelq.models.TvContent;
import com.gis.servelq.services.SocketService;
import com.gis.servelq.services.TvContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/serveiq/api/tv-content")
@RequiredArgsConstructor
public class TvContentController {

    private final TvContentService service;
    private final SocketService socketService;

    // ==================== QUERY ENDPOINTS ====================

    @GetMapping("/{branchId}")
    public List<TvContentResponseDTO> getContent(@PathVariable String branchId) {
        return service.getContentByBranch(branchId);
    }

    @GetMapping("/archived/{branchId}")
    public ResponseEntity<List<TvContentResponseDTO>> getArchivedContent(@PathVariable String branchId) {
        return ResponseEntity.ok(service.getArchivedContent(branchId));
    }

    @GetMapping("/active/{branchId}")
    public ResponseEntity<List<TvContentResponseDTO>> getActiveContent(@PathVariable String branchId) {
        return ResponseEntity.ok(service.getActiveContent(branchId));
    }

    // ==================== URL ENDPOINTS ====================

    @PostMapping("/url")
    public TvContent addUrl(@RequestBody Map<String, String> req) {
        final TvContent tvContent = service.addUrl(req.get("branchId"), req.get("url"), req.get("name"));
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return tvContent;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
    }

    @PatchMapping("/activateVideo")
    public TvContent activate(@RequestBody Map<String, String> req) {
        // FIX: mutate first, then broadcast — the broadcast reads current
        // DB state, so it must fire AFTER activateVideo() persists, not before.
        TvContent result = service.activateVideo(req.get("branchId"), req.get("id"));
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return result;
    }

    // ==================== ARCHIVE/UNARCHIVE ====================

    @PatchMapping("/{id}/archive")
    public ResponseEntity<TvContent> archiveContent(@PathVariable String id) {
        // FIX: same ordering bug as activate() — mutate, then broadcast.
        TvContent result = service.archiveContent(id);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/unarchive")
    public ResponseEntity<TvContent> unarchiveContent(@PathVariable String id) {
        // FIX: same ordering bug — mutate, then broadcast.
        TvContent result = service.unarchiveContent(id);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return ResponseEntity.ok(result);
    }

    // ==================== CHUNKED VIDEO UPLOAD ====================

    @PostMapping("/video/upload/init")
    public ResponseEntity<TvContent> initiateVideoUpload(
            @RequestParam String branchId,
            @RequestParam String fileName,
            @RequestParam String contentType,
            @RequestParam Long totalSize) {
        return ResponseEntity.ok(service.initiateVideoUpload(branchId, fileName, contentType, totalSize));
    }

    @PostMapping("/video/upload/{contentId}/chunk/{chunkNumber}")
    public ResponseEntity<TvContent> uploadVideoChunk(
            @PathVariable String contentId,
            @PathVariable Integer chunkNumber,
            @RequestParam String fileName,
            @RequestParam("chunk") MultipartFile chunk) {
        return ResponseEntity.ok(service.uploadVideoChunk(contentId, fileName, chunkNumber, chunk));
    }

    @PostMapping("/video/upload/{contentId}/complete")
    public ResponseEntity<TvContent> completeVideoUpload(
            @PathVariable String contentId,
            @RequestParam String fileName,
            @RequestParam Integer totalChunks) {
        // NOTE: this endpoint does not broadcast. If hlsProcessed flipping to
        // true here should be visible to connected TV screens without a
        // separate activate() call, add a broadcast after the service call —
        // left as-is since I don't know if activate() always follows this.
        return ResponseEntity.ok(service.completeVideoUpload(contentId, fileName, totalChunks));
    }

    @DeleteMapping("/video/upload/{contentId}/cancel")
    public ResponseEntity<Void> cancelVideoUpload(
            @PathVariable String contentId,
            @RequestParam String fileName) {
        service.cancelVideoUpload(contentId, fileName);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return ResponseEntity.noContent().build();
    }

    // ==================== STREAMING ====================

    @GetMapping("/stream/{contentId}")
    public ResponseEntity<Resource> streamVideo(@PathVariable String contentId) {
        try {
            Path filePath = service.getVideoFilePath(contentId);
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filePath.getFileName() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/hls/{contentId}/{fileName}")
    public ResponseEntity<Resource> getHlsFile(@PathVariable String contentId,
                                               @PathVariable String fileName) {
        try {
            Path filePath = service.getHlsPlaylistPath(contentId, fileName);
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();

            String contentType = fileName.endsWith(".m3u8")
                    ? "application/vnd.apple.mpegurl" : "video/mp2t";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/hls/{contentId}/segments/{fileName}")
    public ResponseEntity<Resource> getHlsSegment(@PathVariable String contentId,
                                                  @PathVariable String fileName) {
        try {
            Path filePath = service.getHlsSegmentPath(contentId, fileName);
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp2t"))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/hls/archived/{contentId}/{fileName}")
    public ResponseEntity<Resource> getArchivedHlsFile(@PathVariable String contentId,
                                                       @PathVariable String fileName) {
        try {
            Path filePath = service.getArchivedHlsPath(contentId, fileName);
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();

            String contentType = fileName.endsWith(".m3u8")
                    ? "application/vnd.apple.mpegurl" : "video/mp2t";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ==================== IMAGE ENDPOINTS ====================

    @PostMapping("/image/upload")
    public ResponseEntity<TvContent> upload(@RequestParam String branchId,
                                            @RequestParam MultipartFile file) throws IOException {
        // FIX: mutate first, then broadcast.
        // NOTE: still broadcasts AppType.FEEDBACK, not TV_DISPLAY — confirm
        // this is the intended topic for image content; unchanged for now.
        TvContent result = service.uploadImage(branchId, file);
        socketService.broadcastAppDashboard(AppType.FEEDBACK);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/image")
    public ResponseEntity<List<TvContentResponseDTO>> getAll(@RequestParam String branchId) {
        return ResponseEntity.ok(service.getAllImages(branchId));
    }

    @GetMapping("/image/active")
    public ResponseEntity<List<TvContentResponseDTO>> getActiveImages(@RequestParam String branchId) {
        return ResponseEntity.ok(service.getActiveImages(branchId));
    }

    @PatchMapping("/image/toggle/{id}")
    public ResponseEntity<TvContent> toggleImageStatus(@RequestParam String branchId,
                                                       @PathVariable String id) {
        // FIX: mutate first, then broadcast.
        TvContent result = service.toggleImageStatus(branchId, id);
        socketService.broadcastAppDashboard(AppType.FEEDBACK);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/image/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable String id) throws IOException {
        service.deleteImage(id);
        socketService.broadcastAppDashboard(AppType.FEEDBACK);
        return ResponseEntity.noContent().build();
    }
}