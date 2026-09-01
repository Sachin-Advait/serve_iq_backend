package com.gis.servelq.controllers;

import com.gis.servelq.dto.ApiResponseDTO;
import com.gis.servelq.dto.NewsItemDTO;
import com.gis.servelq.dto.NewsSourceConfigRequest;
import com.gis.servelq.models.AppType;
import com.gis.servelq.models.BreakingNews;
import com.gis.servelq.models.NewsSourceConfig;
import com.gis.servelq.services.BreakingNewsService;
import com.gis.servelq.services.SocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/serveiq/api/news")
@RequiredArgsConstructor
public class BreakingNewsController {

    private final BreakingNewsService newsService;
    private final SocketService socketService;

    // ==================== PUBLIC ENDPOINTS ====================

    @GetMapping("/breaking-news")
    public ApiResponseDTO<?> getBreakingNews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        return new ApiResponseDTO<>(true, "Breaking news fetched",
                newsService.getBreakingNewsPaginated(page, size, type, search));
    }

    // ==================== ADMIN ENDPOINTS ====================

    @PostMapping("/manual")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<BreakingNews> createManualNews(
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String link,
            @RequestParam(required = false, defaultValue = "Manual") String source,
            @RequestParam(required = false, defaultValue = "Breaking") String category) {
        BreakingNews news = newsService.createManualNews(title, description, link, source, category);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "News added manually", news);
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<BreakingNews> archiveNews(@PathVariable String id) {
        BreakingNews news = newsService.archiveNews(id);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "News archived", news);
    }

    @PutMapping("/{id}/unarchive")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<BreakingNews> unarchiveNews(@PathVariable String id) {
        BreakingNews news = newsService.unarchiveNews(id);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "News unarchived", news);
    }

    @GetMapping("/archived")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<?> getArchivedNews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        return new ApiResponseDTO<>(true, "Archived news fetched",
                newsService.getArchivedNewsPaginated(page, size, type, search));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<BreakingNews> updateNews(@PathVariable String id,
                                                   @RequestBody BreakingNews news) {
        BreakingNews updated = newsService.updateNews(id, news);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "News updated", updated);
    }

    @GetMapping("/fetch")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<List<NewsItemDTO>> fetchNews(
            @RequestParam String url,
            @RequestParam(defaultValue = "20") int maxItems,
            @RequestParam(defaultValue = "Manual Fetch") String sourceName,
            @RequestParam(defaultValue = "Breaking") String category) {
        return new ApiResponseDTO<>(true, "News fetched",
                newsService.fetchNewsFromUrl(url, maxItems, sourceName, category));
    }

    @GetMapping("/fetch-all")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<List<NewsItemDTO>> fetchAllNews() {
        return new ApiResponseDTO<>(true, "News fetched from all sources",
                newsService.fetchNewsFromAllSources());
    }

    @PostMapping("/publish")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<List<BreakingNews>> publishNews(
            @RequestBody List<NewsItemDTO> items) {
        List<BreakingNews> published = newsService.publishNewsItems(items);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "News published", published);
    }

    @PostMapping("/fetch-publish")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<Map<String, Object>> manualFetchAndPublish() {
        Map<String, Object> result = newsService.manualFetchAndPublish();
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "News fetched and published", result);
    }

    @PostMapping("/source-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<NewsSourceConfig> saveSourceConfig(
            @RequestBody NewsSourceConfigRequest request) {
        NewsSourceConfig config = newsService.saveSourceConfig(request);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "Source config saved", config);
    }

    @GetMapping("/source-configs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<List<NewsSourceConfig>> getAllSourceConfigs() {
        return new ApiResponseDTO<>(true, "Source configs fetched",
                newsService.getAllSourceConfigs());
    }

    @DeleteMapping("/source-config/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<Void> deleteSourceConfig(@PathVariable String id) {
        newsService.deleteSourceConfig(id);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "Source config deleted", null);
    }

    @GetMapping("/unpublished")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<List<BreakingNews>> getUnpublishedNews() {
        return new ApiResponseDTO<>(true, "Unpublished news fetched",
                newsService.getUnpublishedNews());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<Void> deleteNews(@PathVariable String id) {
        newsService.deleteNews(id);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "News deleted", null);
    }

    @DeleteMapping("/bulk-delete")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<Map<String, Object>> bulkDeleteNews(
            @RequestBody Map<String, List<String>> request) {
        List<String> ids = request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return new ApiResponseDTO<>(false, "No news IDs provided", null);
        }
        Map<String, Object> result = newsService.bulkDeleteNews(ids);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "Bulk delete completed", result);
    }

    @DeleteMapping("/archived/bulk-delete")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponseDTO<Map<String, Object>> bulkDeleteArchivedNews(
            @RequestBody Map<String, List<String>> request) {
        List<String> ids = request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return new ApiResponseDTO<>(false, "No news IDs provided", null);
        }
        Map<String, Object> result = newsService.bulkDeleteArchivedNews(ids);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "Bulk delete completed", result);
    }

    @PostMapping("/upload-separator")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDTO<Map<String, String>> uploadSeparatorImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        String imageUrl = newsService.uploadSeparatorImage(file, request);
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
        return new ApiResponseDTO<>(true, "Separator image uploaded",
                Map.of("separatorImageUrl", imageUrl));
    }

    @GetMapping("/images/separators/{fileName}")
    public ResponseEntity<Resource> getSeparatorImage(@PathVariable String fileName) {
        try {
            Path filePath = newsService.getSeparatorImagePath(fileName);
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "image/jpeg";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}