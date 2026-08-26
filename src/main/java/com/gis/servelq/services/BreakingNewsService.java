package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.NewsItemDTO;
import com.gis.servelq.dto.NewsSourceConfigRequest;
import com.gis.servelq.models.BreakingNews;
import com.gis.servelq.models.NewsSourceConfig;
import com.gis.servelq.repository.BreakingNewsRepository;
import com.gis.servelq.repository.NewsSourceConfigRepository;
import com.gis.servelq.utils.RssTextCleaner;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BreakingNewsService {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif"
    );
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB

    private final BreakingNewsRepository newsRepository;
    private final NewsSourceConfigRepository sourceConfigRepository;

    @Value("${image.upload.dir}")
    private String uploadDir;

    // Add this to get the base URL from properties or default
    @Value("${app.base-url:http://localhost:8085}")
    private String appBaseUrl;

    // ==================== MANUAL NEWS ====================

    public BreakingNews createManualNews(String title, String description, String link,
                                         String source, String category) {
        BreakingNews news = new BreakingNews();
        news.setTitle(RssTextCleaner.clean(title));
        news.setDescription(RssTextCleaner.clean(description));
        news.setLink(link);
        news.setSource(source != null ? RssTextCleaner.clean(source) : "Manual");
        news.setCategory(category != null ? RssTextCleaner.clean(category) : "Breaking");
        news.setType("MANUAL");
        news.setPublishedDate(Instant.now());
        news.setPublished(true);
        news.setActive(true);
        news.setArchived(false);
        news.setDisplayOrder(0);
        return newsRepository.save(news);
    }

    // ==================== ARCHIVE/UNARCHIVE ====================

    public BreakingNews archiveNews(String id) {
        BreakingNews news = newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found: " + id));
        news.setArchived(true);
        news.setActive(false);
        return newsRepository.save(news);
    }

    public BreakingNews unarchiveNews(String id) {
        BreakingNews news = newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found: " + id));
        news.setArchived(false);
        news.setActive(true);
        return newsRepository.save(news);
    }

    // ==================== QUERY METHODS ====================

    public List<BreakingNews> getBreakingNews(int limit) {
        if (limit < 1) limit = 15;
        return newsRepository.findByPublishedTrueAndActiveTrueAndArchivedFalseOrderByPublishedDateDesc(
                PageRequest.of(0, limit));
    }

    public Map<String, Object> getBreakingNewsPaginated(int page, int size, String type, String search) {
        if (size < 1) size = 15;
        if (page < 0) page = 0;

        // Normalize type to uppercase to match stored values
        if (type != null && !type.isEmpty()) {
            type = type.toUpperCase();
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedDate"));
        Page<BreakingNews> newsPage;

        if (type != null && !type.isEmpty() && search != null && !search.isEmpty()) {
            newsPage = newsRepository.findByPublishedTrueAndActiveTrueAndArchivedFalseAndTypeAndTitleContainingIgnoreCase(
                    pageRequest, type, search);
        } else if (type != null && !type.isEmpty()) {
            newsPage = newsRepository.findByPublishedTrueAndActiveTrueAndArchivedFalseAndType(pageRequest, type);
        } else if (search != null && !search.isEmpty()) {
            newsPage = newsRepository.findByPublishedTrueAndActiveTrueAndArchivedFalseAndTitleContainingIgnoreCase(
                    pageRequest, search);
        } else {
            newsPage = newsRepository.findByPublishedTrueAndActiveTrueAndArchivedFalse(pageRequest);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", newsPage.getContent());
        result.put("totalElements", newsPage.getTotalElements());
        result.put("totalPages", newsPage.getTotalPages());
        result.put("currentPage", newsPage.getNumber());
        result.put("pageSize", newsPage.getSize());
        result.put("hasNext", newsPage.hasNext());
        result.put("hasPrevious", newsPage.hasPrevious());
        return result;
    }

    public Map<String, Object> getArchivedNewsPaginated(int page, int size, String type, String search) {
        if (size < 1) size = 15;
        if (page < 0) page = 0;

        // Normalize type to uppercase
        if (type != null && !type.isEmpty()) {
            type = type.toUpperCase();
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<BreakingNews> newsPage;

        if (type != null && !type.isEmpty() && search != null && !search.isEmpty()) {
            newsPage = newsRepository.findByArchivedTrueAndTypeAndTitleContainingIgnoreCase(pageRequest, type, search);
        } else if (type != null && !type.isEmpty()) {
            newsPage = newsRepository.findByArchivedTrueAndType(pageRequest, type);
        } else if (search != null && !search.isEmpty()) {
            newsPage = newsRepository.findByArchivedTrueAndTitleContainingIgnoreCase(pageRequest, search);
        } else {
            newsPage = newsRepository.findByArchivedTrue(pageRequest);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("content", newsPage.getContent());
        result.put("totalElements", newsPage.getTotalElements());
        result.put("totalPages", newsPage.getTotalPages());
        result.put("currentPage", newsPage.getNumber());
        result.put("pageSize", newsPage.getSize());
        result.put("hasNext", newsPage.hasNext());
        result.put("hasPrevious", newsPage.hasPrevious());
        return result;
    }

    public List<BreakingNews> getUnpublishedNews() {
        return newsRepository.findByPublishedFalse();
    }

    // ==================== RSS FETCH ====================

    public List<NewsItemDTO> fetchNewsFromUrl(String rssUrl, int maxItems, String sourceName, String category) {
        List<NewsItemDTO> items = new ArrayList<>();
        try {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(new URL(rssUrl)));
            List<SyndEntry> entries = feed.getEntries();
            entries = entries.stream().limit(maxItems).collect(Collectors.toList());

            for (SyndEntry entry : entries) {
                NewsItemDTO item = NewsItemDTO.builder()
                        .title(RssTextCleaner.clean(entry.getTitle()))
                        .description(RssTextCleaner.clean(entry.getDescription() != null ? entry.getDescription().getValue() : ""))
                        .link(entry.getLink())
                        .source(RssTextCleaner.clean(sourceName))
                        .category(RssTextCleaner.clean(category))
                        .publishedDate(entry.getPublishedDate() != null ?
                                entry.getPublishedDate().toInstant().toString() : Instant.now().toString())
                        .build();
                items.add(item);
            }
        } catch (Exception e) {
            log.error("Failed to fetch RSS feed from {}: {}", rssUrl, e.getMessage());
            throw new BusinessException("Failed to fetch RSS feed: " + e.getMessage());
        }
        return items;
    }

    public List<NewsItemDTO> fetchNewsFromAllSources() {
        List<NewsSourceConfig> sources = sourceConfigRepository.findByActiveTrue();
        List<NewsItemDTO> allNews = new ArrayList<>();

        for (NewsSourceConfig source : sources) {
            try {
                List<NewsItemDTO> items = fetchNewsFromUrl(
                        source.getRssUrl(),
                        source.getMaxItems() != null ? source.getMaxItems() : 5,
                        source.getSourceName(),
                        source.getCategory());
                allNews.addAll(items);
            } catch (Exception e) {
                log.warn("Failed to fetch from source {}: {}", source.getSourceName(), e.getMessage());
            }
        }
        return allNews;
    }

    // ==================== PUBLISH ====================

    public List<BreakingNews> publishNewsItems(List<NewsItemDTO> items) {
        List<BreakingNews> published = new ArrayList<>();

        String separatorUrl = newsRepository.findTopByOrderByPublishedDateDesc()
                .map(BreakingNews::getSeparatorImageUrl)
                .orElse(null);

        for (NewsItemDTO item : items) {
            BreakingNews news = new BreakingNews();
            news.setTitle(RssTextCleaner.clean(item.getTitle()));
            news.setDescription(RssTextCleaner.clean(item.getDescription()));
            news.setLink(item.getLink());
            news.setSource(RssTextCleaner.clean(item.getSource()));
            news.setCategory(RssTextCleaner.clean(item.getCategory()));
            news.setType("RSS");
            if (separatorUrl != null) {
                news.setSeparatorImageUrl(separatorUrl);
            }

            try {
                news.setPublishedDate(Instant.parse(item.getPublishedDate()));
            } catch (Exception e) {
                news.setPublishedDate(Instant.now());
            }

            news.setPublished(true);
            news.setActive(true);
            news.setArchived(false);
            news.setDisplayOrder(0);
            published.add(newsRepository.save(news));
        }
        return published;
    }

    public Map<String, Object> manualFetchAndPublish() {
        Map<String, Object> result = new HashMap<>();
        log.info("=== [Manual] Fetching and publishing breaking news ===");

        try {
            // Step 1: Fetch new items from RSS sources
            List<NewsItemDTO> items = fetchNewsFromAllSources();

            if (items.isEmpty()) {
                result.put("success", false);
                result.put("error", "No news fetched - keeping existing news");
                return result;
            }

            // Step 2: Archive OLD news BEFORE publishing new ones
            long archived = archiveAllPublishedRssNews();
            log.info("Archived {} RSS news items (manual news kept)", archived);

            // Step 3: Publish new items (these will remain active)
            List<BreakingNews> published = publishNewsItems(items);

            result.put("archived", archived);
            result.put("fetched", items.size());
            result.put("published", published.size());
            result.put("manualNewsKept", true);
            result.put("success", true);
        } catch (Exception e) {
            log.error("Manual fetch and publish failed: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private long archiveAllPublishedRssNews() {
        List<BreakingNews> oldNews = newsRepository.findByPublishedTrueAndArchivedFalse();
        List<BreakingNews> toArchive = new ArrayList<>();
        int manualSkipped = 0;

        for (BreakingNews news : oldNews) {
            if ("MANUAL".equalsIgnoreCase(news.getType())) {
                manualSkipped++;
                log.info("Skipping manual news: {} (protected)", news.getTitle());
                continue;
            }
            news.setArchived(true);
            news.setActive(false);
            toArchive.add(news);
        }

        newsRepository.saveAll(toArchive);
        log.info("Archived {} RSS news items, skipped {} manual news items", toArchive.size(), manualSkipped);
        return toArchive.size();
    }

    // ==================== SCHEDULED TASKS ====================

    @Scheduled(cron = "0 30 7 * * *", zone = "Asia/Muscat")
    public void autoFetchMorningNews() {
        log.info("=== [Scheduler] 7:30 AM - Fetching fresh breaking news ===");
        try {
            List<NewsItemDTO> items = fetchNewsFromAllSources();

            if (items.isEmpty()) {
                log.warn("No news fetched at 7:30 AM - keeping existing news");
                return;
            }

            long archived = archiveAllPublishedRssNews();
            log.info("Archived {} RSS news items (manual news kept)", archived);

            List<BreakingNews> published = publishNewsItems(items);
            log.info("Published {} fresh RSS news items", published.size());

        } catch (Exception e) {
            log.error("Morning news fetch failed - keeping old news: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Muscat")
    public void autoFetchLateMorningNews() {
        log.info("=== [Scheduler] 10:00 AM - Fetching fresh breaking news ===");
        try {
            List<NewsItemDTO> items = fetchNewsFromAllSources();

            if (items.isEmpty()) {
                log.warn("No news fetched at 10:00 AM - keeping existing news");
                return;
            }

            long archived = archiveAllPublishedRssNews();
            log.info("Archived {} RSS news items (manual news kept)", archived);

            List<BreakingNews> published = publishNewsItems(items);
            log.info("Published {} fresh RSS news items", published.size());

        } catch (Exception e) {
            log.error("Late morning news fetch failed - keeping old news: {}", e.getMessage());
        }
    }

    // ==================== SEPARATOR IMAGE ====================

    public String uploadSeparatorImage(MultipartFile file, HttpServletRequest request) {
        validateImage(file);

        try {
            Path newsImageDir = Paths.get(uploadDir, "news", "separators").toAbsolutePath().normalize();
            Files.createDirectories(newsImageDir);

            String extension = getExtension(file);
            String fileName = UUID.randomUUID() + "." + extension;
            Path targetPath = newsImageDir.resolve(fileName).normalize();

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Build the full URL
            String baseUrl = getBaseUrl(request);
            String imageUrl = baseUrl + "/serveiq/api/news/images/separators/" + fileName;

            // Update all news with new separator image
            List<BreakingNews> allNews = newsRepository.findAll();
            allNews.forEach(news -> {
                news.setSeparatorImageUrl(imageUrl);
            });
            newsRepository.saveAll(allNews);

            log.info("Separator image uploaded: {}", imageUrl);
            return imageUrl;

        } catch (IOException e) {
            log.error("Failed to save separator image: {}", e.getMessage());
            throw new BusinessException("Failed to save separator image: " + e.getMessage());
        }
    }

    /**
     * Get the base URL from the request or use the configured property
     */
    private String getBaseUrl(HttpServletRequest request) {
        if (request != null) {
            String scheme = request.getScheme(); // http or https
            String serverName = request.getServerName(); // localhost or domain
            int serverPort = request.getServerPort(); // 8085 or 80 or 443
            String contextPath = request.getContextPath(); // usually empty

            StringBuilder url = new StringBuilder();
            url.append(scheme).append("://").append(serverName);

            // Add port if not default
            if ((scheme.equals("http") && serverPort != 80) ||
                    (scheme.equals("https") && serverPort != 443)) {
                url.append(":").append(serverPort);
            }

            url.append(contextPath);
            return url.toString();
        }

        // Fallback to configured property
        return appBaseUrl;
    }

    public Path getSeparatorImagePath(String fileName) {
        Path imageDir = Paths.get(uploadDir, "news", "separators").toAbsolutePath().normalize();
        Path filePath = imageDir.resolve(fileName).normalize();
        if (!filePath.startsWith(imageDir)) {
            throw new BusinessException("Invalid file path");
        }
        return filePath;
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("File is empty");
        if (file.getSize() > MAX_BYTES) throw new BusinessException("Image is larger than 5MB limit");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.containsKey(contentType.toLowerCase())) {
            throw new BusinessException("Only PNG, JPEG, WebP, and GIF images are allowed");
        }
    }

    private String getExtension(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            String ext = ALLOWED_CONTENT_TYPES.get(contentType.toLowerCase());
            if (ext != null) return ext;
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
        }
        return "jpg";
    }

    // ==================== SOURCE CONFIG ====================

    public NewsSourceConfig saveSourceConfig(NewsSourceConfigRequest request) {
        NewsSourceConfig config = sourceConfigRepository.findBySourceName(request.getSourceName())
                .orElse(new NewsSourceConfig());

        config.setSourceName(RssTextCleaner.clean(request.getSourceName()));
        config.setRssUrl(request.getRssUrl());
        config.setCategory(RssTextCleaner.clean(request.getCategory()));
        config.setActive(request.getActive() != null ? request.getActive() : true);
        config.setMaxItems(request.getMaxItems() != null ? request.getMaxItems() : 5);

        return sourceConfigRepository.save(config);
    }

    public List<NewsSourceConfig> getAllSourceConfigs() {
        return sourceConfigRepository.findAll();
    }

    public void deleteSourceConfig(String id) {
        sourceConfigRepository.deleteById(id);
    }

    // ==================== DELETE ====================

    public void deleteNews(String id) {
        newsRepository.deleteById(id);
    }

    public Map<String, Object> bulkDeleteNews(List<String> ids) {
        Map<String, Object> result = new HashMap<>();
        int deletedCount = 0;
        int failedCount = 0;
        List<String> failedIds = new ArrayList<>();

        for (String id : ids) {
            try {
                newsRepository.deleteById(id);
                deletedCount++;
            } catch (Exception e) {
                failedCount++;
                failedIds.add(id);
                log.warn("Failed to delete news {}: {}", id, e.getMessage());
            }
        }

        result.put("deleted", deletedCount);
        result.put("failed", failedCount);
        result.put("failedIds", failedIds);
        result.put("totalRequested", ids.size());
        result.put("success", failedCount == 0);

        log.info("Bulk delete: {} deleted, {} failed out of {}", deletedCount, failedCount, ids.size());
        return result;
    }

    public Map<String, Object> bulkDeleteArchivedNews(List<String> ids) {
        return bulkDeleteNews(ids);
    }

    // ==================== UPDATE ====================

    public BreakingNews updateNews(String id, BreakingNews updated) {
        BreakingNews existing = newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News not found: " + id));

        if (updated.getTitle() != null) existing.setTitle(RssTextCleaner.clean(updated.getTitle()));
        if (updated.getDescription() != null) existing.setDescription(RssTextCleaner.clean(updated.getDescription()));
        if (updated.getLink() != null) existing.setLink(updated.getLink());
        if (updated.getSource() != null) existing.setSource(RssTextCleaner.clean(updated.getSource()));
        if (updated.getCategory() != null) existing.setCategory(RssTextCleaner.clean(updated.getCategory()));
        if (updated.getType() != null) existing.setType(updated.getType());
        if (updated.getDisplayOrder() != null) existing.setDisplayOrder(updated.getDisplayOrder());
        if (updated.getActive() != null) existing.setActive(updated.getActive());

        return newsRepository.save(existing);
    }
}