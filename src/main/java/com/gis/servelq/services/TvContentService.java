package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.TvContentResponseDTO;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.AppType;
import com.gis.servelq.models.TvContent;
import com.gis.servelq.repository.TvContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TvContentService {

    private static final List<String> ALLOWED_VIDEO_TYPES = List.of(
            "video/mp4", "video/mp4v-es", "video/x-m4v", "application/mp4",
            "video/webm", "video/x-webm",
            "video/quicktime", "video/x-quicktime", "video/mov",
            "video/x-msvideo", "video/avi",
            "video/x-matroska", "video/matroska", "video/mkv", "video/x-mkv",
            "video/mpeg", "video/mp2t",
            "video/3gpp", "video/3gpp2",
            "application/octet-stream"
    );

    private static final long MAX_VIDEO_SIZE = 10L * 1024 * 1024 * 1024; // 10GB

    private final TvContentRepository tvContentRepository;
    private final TokenEventPublisher tokenEventPublisher;
    private final SocketService socketService;

    @Value("${app.base-url:http://localhost:8085}")
    private String baseUrl;

    @Value("${image.upload.dir}")
    private String uploadDir;

    @Value("${video.upload.dir:${image.upload.dir}/videos}")
    private String videoUploadDir;

    @Value("${video.chunk.dir:${image.upload.dir}/chunks}")
    private String chunkDir;

    @Value("${video.hls.dir:${image.upload.dir}/hls}")
    private String hlsDir;

    @Value("${video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${video.archive.dir:${image.upload.dir}/archived/videos}")
    private String videoArchiveDir;

    @Value("${video.hls-archive.dir:${image.upload.dir}/archived/hls}")
    private String hlsArchiveDir;

    @Value("${image.archive.dir:${image.upload.dir}/archived/images}")
    private String imageArchiveDir;

    private final Map<String, Object> uploadLocks = new ConcurrentHashMap<>();

    // ==================== QUERY METHODS ====================

    public List<TvContentResponseDTO> getContentByBranch(String branchId) {
        List<String> allowedTypes = List.of("URL", "VIDEO");
        List<TvContent> content = tvContentRepository.findByBranchIdAndTypeInAndArchivedFalse(branchId, allowedTypes);
        return content.stream().map(this::toDTO).toList();
    }

    public List<TvContentResponseDTO> getArchivedContent(String branchId) {
        List<TvContent> content = tvContentRepository.findByBranchIdAndArchivedTrue(branchId);
        return content.stream().map(this::toDTO).toList();
    }

    public List<TvContentResponseDTO> getActiveContent(String branchId) {
        List<TvContent> content = tvContentRepository.findByBranchIdAndArchivedFalse(branchId);
        return content.stream().map(this::toDTO).toList();
    }

    public List<TvContentResponseDTO> getAllImages(String branchId) {
        List<TvContent> images = tvContentRepository.findByBranchIdAndType(branchId, "IMAGE");
        return images.stream().map(this::toDTO).toList();
    }

    public List<TvContentResponseDTO> getActiveImages(String branchId) {
        List<TvContent> images = tvContentRepository.findByBranchIdAndTypeAndActive(branchId, "IMAGE", true);
        return images.stream().map(this::toDTO).toList();
    }

    // ==================== DTO CONVERSION ====================

    private TvContentResponseDTO toDTO(TvContent content) {
        TvContentResponseDTO dto = new TvContentResponseDTO();
        dto.setId(content.getId());
        dto.setBranchId(content.getBranchId());
        dto.setName(content.getName());
        dto.setUrl(resolveFullUrl(content.getUrl()));
        dto.setType(content.getType());
        dto.setActive(content.getActive());
        dto.setSize(content.getSize());
        dto.setArchived(content.getArchived());
        dto.setHlsUrl(resolveFullUrl(content.getHlsUrl()));
        dto.setHlsProcessed(content.getHlsProcessed());
        dto.setCreatedAt(content.getCreatedAt());
        dto.setUpdatedAt(content.getUpdatedAt());
        return dto;
    }

    private String resolveFullUrl(String url) {
        if (url == null) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return baseUrl + url;
    }

    // ==================== URL METHODS ====================

    public TvContent addUrl(String branchId, String url, String name) {
        TvContent t = new TvContent();
        t.setBranchId(branchId);
        t.setUrl(url);
        t.setName(name);
        t.setType("URL");
        t.setActive(false);
        t.setArchived(false);
        return tvContentRepository.save(t);
    }

    // ==================== DELETE METHODS ====================

    public void delete(String id) {
        TvContent content = tvContentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found"));

        deleteFilesForContent(content);
        tvContentRepository.deleteById(id);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TV_MEDIA_CHANGED,
                content.getBranchId(), null, null, null, Instant.now()
        ));
    }

    private void deleteFilesForContent(TvContent content) {
        try {
            if ("VIDEO".equals(content.getType())) {
                deleteVideoFiles(content);
            } else if ("IMAGE".equals(content.getType())) {
                deleteImageFiles(content);
            }
            deleteHlsFiles(content);
        } catch (Exception e) {
            log.warn("Failed to delete files for content {}: {}", content.getId(), e.getMessage());
        }
    }

    private void deleteVideoFiles(TvContent content) {
        try {
            String fileName = extractFileName(content.getUrl());
            Files.deleteIfExists(Paths.get(videoUploadDir, fileName).toAbsolutePath().normalize());
            Files.deleteIfExists(Paths.get(videoArchiveDir, fileName).toAbsolutePath().normalize());
        } catch (IOException e) {
            log.warn("Failed to delete video file: {}", e.getMessage());
        }
    }

    private void deleteImageFiles(TvContent content) {
        try {
            String fileName = extractFileName(content.getUrl());
            Files.deleteIfExists(Paths.get(uploadDir, fileName).toAbsolutePath().normalize());
            Files.deleteIfExists(Paths.get(imageArchiveDir, fileName).toAbsolutePath().normalize());
        } catch (IOException e) {
            log.warn("Failed to delete image file: {}", e.getMessage());
        }
    }

    private void deleteHlsFiles(TvContent content) {
        deleteDirectory(Paths.get(hlsDir, content.getId()));
        deleteDirectory(Paths.get(hlsArchiveDir, content.getId()));
    }

    private String extractFileName(String url) {
        if (url == null) return "";
        String fileName = url;
        if (fileName.startsWith("http")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        return Paths.get(fileName).getFileName().toString();
    }

    private void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try { Files.deleteIfExists(path); }
                            catch (IOException e) { log.warn("Failed to delete: {}", e.getMessage()); }
                        });
            }
        } catch (IOException e) {
            log.warn("Failed to delete directory: {}", e.getMessage());
        }
    }

    // ==================== ACTIVATE METHOD ====================

    @Transactional
    public TvContent activateVideo(String branchId, String id) {
        List<String> allowedTypes = List.of("URL", "VIDEO");

        List<TvContent> all = tvContentRepository.findByBranchIdAndTypeInAndArchivedFalse(branchId, allowedTypes);
        all.forEach(c -> c.setActive(false));
        tvContentRepository.saveAll(all);

        TvContent selected = tvContentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found"));

        selected.setActive(true);
        TvContent saved = tvContentRepository.save(selected);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TV_MEDIA_CHANGED,
                branchId, null, null, null, Instant.now()
        ));

        return saved;
    }

    // ==================== ARCHIVE/UNARCHIVE ====================

    @Transactional
    public TvContent archiveContent(String id) {
        TvContent content = tvContentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found"));

        log.info("Archiving content: {} (type: {})", content.getId(), content.getType());

        content.setArchived(true);
        content.setActive(false);
        TvContent saved = tvContentRepository.save(content);

        archiveFilesAsync(content);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TV_MEDIA_CHANGED,
                content.getBranchId(), null, null, null, Instant.now()
        ));

        return saved;
    }

    @Transactional
    public TvContent unarchiveContent(String id) {
        TvContent content = tvContentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found"));

        log.info("Unarchiving content: {} (type: {})", content.getId(), content.getType());

        content.setArchived(false);
        TvContent saved = tvContentRepository.save(content);

        unarchiveFilesAsync(content);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TV_MEDIA_CHANGED,
                content.getBranchId(), null, null, null, Instant.now()
        ));

        return saved;
    }

    @Async
    public void archiveFilesAsync(TvContent content) {
        try {
            switch (content.getType()) {
                case "VIDEO" -> archiveVideoFiles(content);
                case "IMAGE" -> archiveImageFile(content);
                case "URL" -> log.info("URL has no files to archive");
                default -> log.warn("Unknown content type: {}", content.getType());
            }
            log.info("✅ Content {} archived successfully", content.getId());
        } catch (Exception e) {
            log.error("Failed to archive content {}: {}", content.getId(), e.getMessage(), e);
            revertArchive(content, false);
        }
    }

    @Async
    public void unarchiveFilesAsync(TvContent content) {
        try {
            switch (content.getType()) {
                case "VIDEO" -> unarchiveVideoFiles(content);
                case "IMAGE" -> unarchiveImageFile(content);
                case "URL" -> log.info("URL has no files to unarchive");
                default -> log.warn("Unknown content type: {}", content.getType());
            }
            log.info("✅ Content {} unarchived successfully", content.getId());
        } catch (Exception e) {
            log.error("Failed to unarchive content {}: {}", content.getId(), e.getMessage(), e);
            revertArchive(content, true);
        }
    }

    private void revertArchive(TvContent content, boolean wasArchived) {
        tvContentRepository.findById(content.getId()).ifPresent(video -> {
            video.setArchived(wasArchived);
            video.setActive(!wasArchived);
            tvContentRepository.save(video);
        });
    }

    private void archiveVideoFiles(TvContent content) throws IOException {
        String fileName = extractFileName(content.getUrl());
        Path activeFilePath = Paths.get(videoUploadDir, fileName).toAbsolutePath().normalize();
        Path archiveDirPath = Paths.get(videoArchiveDir).toAbsolutePath().normalize();
        Files.createDirectories(archiveDirPath);
        Path archivedFilePath = archiveDirPath.resolve(fileName).normalize();

        if (Files.exists(activeFilePath)) {
            moveFile(activeFilePath, archivedFilePath);
            content.setUrl("/archived/videos/" + fileName);
            log.info("Moved video: {} → {}", activeFilePath, archivedFilePath);
        }

        Path activeHlsDir = Paths.get(hlsDir, content.getId()).toAbsolutePath().normalize();
        if (Files.exists(activeHlsDir)) {
            Path archiveHlsDir = Paths.get(hlsArchiveDir, content.getId()).toAbsolutePath().normalize();
            Files.createDirectories(Paths.get(hlsArchiveDir).toAbsolutePath().normalize());
            moveDirectory(activeHlsDir, archiveHlsDir);
            content.setHlsUrl("/serveiq/api/tv-content/hls/archived/" + content.getId() + "/master.m3u8");
            log.info("Moved HLS: {} → {}", activeHlsDir, archiveHlsDir);
        }

        tvContentRepository.save(content);
    }

    private void unarchiveVideoFiles(TvContent content) throws IOException {
        String fileName = extractFileName(content.getUrl());
        Path archivedFilePath = Paths.get(videoArchiveDir, fileName).toAbsolutePath().normalize();
        Path activeDirPath = Paths.get(videoUploadDir).toAbsolutePath().normalize();
        Files.createDirectories(activeDirPath);
        Path activeFilePath = activeDirPath.resolve(fileName).normalize();

        if (Files.exists(archivedFilePath)) {
            moveFile(archivedFilePath, activeFilePath);
            content.setUrl("/videos/" + fileName);
            log.info("Restored video: {} → {}", archivedFilePath, activeFilePath);
        }

        Path archiveHlsDir = Paths.get(hlsArchiveDir, content.getId()).toAbsolutePath().normalize();
        if (Files.exists(archiveHlsDir)) {
            Path activeHlsDir = Paths.get(hlsDir, content.getId()).toAbsolutePath().normalize();
            Files.createDirectories(Paths.get(hlsDir).toAbsolutePath().normalize());
            moveDirectory(archiveHlsDir, activeHlsDir);
            content.setHlsUrl("/serveiq/api/tv-content/hls/" + content.getId() + "/master.m3u8");
            log.info("Restored HLS: {} → {}", archiveHlsDir, activeHlsDir);
        }

        tvContentRepository.save(content);
    }

    private void archiveImageFile(TvContent content) throws IOException {
        String fileName = extractFileName(content.getUrl());
        Path activeFilePath = Paths.get(uploadDir, fileName).toAbsolutePath().normalize();
        Path archiveDirPath = Paths.get(imageArchiveDir).toAbsolutePath().normalize();
        Files.createDirectories(archiveDirPath);
        Path archivedFilePath = archiveDirPath.resolve(fileName).normalize();

        if (Files.exists(activeFilePath)) {
            moveFile(activeFilePath, archivedFilePath);
            content.setUrl("/archived/images/" + fileName);
            log.info("Moved image: {} → {}", activeFilePath, archivedFilePath);
        }

        tvContentRepository.save(content);
    }

    private void unarchiveImageFile(TvContent content) throws IOException {
        String fileName = extractFileName(content.getUrl());
        Path archivedFilePath = Paths.get(imageArchiveDir, fileName).toAbsolutePath().normalize();
        Path activeDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(activeDirPath);
        Path activeFilePath = activeDirPath.resolve(fileName).normalize();

        if (Files.exists(archivedFilePath)) {
            moveFile(archivedFilePath, activeFilePath);
            content.setUrl("/images/" + fileName);
            log.info("Restored image: {} → {}", archivedFilePath, activeFilePath);
        }

        tvContentRepository.save(content);
    }

    private void moveFile(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            log.info("Atomic move: {} → {}", source, target);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Regular move: {} → {}", source, target);
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.createDirectories(target);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path file : stream) {
                    Files.move(file, target.resolve(file.getFileName()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.deleteIfExists(source);
        }
    }

    // ==================== CHUNKED VIDEO UPLOAD ====================

    public TvContent initiateVideoUpload(String branchId, String fileName,
                                         String contentType, Long totalSize) {
        validateVideoMetadata(fileName, contentType, totalSize);

        try {
            Path videoDir = Paths.get(videoUploadDir).toAbsolutePath().normalize();
            Path chunkPath = Paths.get(chunkDir, fileName).toAbsolutePath().normalize();
            Files.createDirectories(videoDir);
            Files.createDirectories(chunkPath);

            TvContent content = new TvContent();
            content.setBranchId(branchId);
            content.setName(fileName);
            content.setType("VIDEO");
            content.setActive(false);
            content.setArchived(false);
            content.setSize(String.valueOf(totalSize));
            content.setUrl("/videos/" + fileName);

            TvContent saved = tvContentRepository.save(content);
            log.info("Initiated video upload: {} ({} bytes)", fileName, totalSize);
            return saved;

        } catch (IOException e) {
            log.error("Failed to initiate video upload: {}", e.getMessage());
            throw new BusinessException("Failed to initiate upload: " + e.getMessage());
        }
    }

    public TvContent uploadVideoChunk(String contentId, String fileName,
                                      Integer chunkNumber, MultipartFile chunk) {
        Object lock = uploadLocks.computeIfAbsent(contentId, k -> new Object());

        synchronized (lock) {
            try {
                Path chunkPath = Paths.get(chunkDir, fileName)
                        .resolve(String.format("chunk_%05d.tmp", chunkNumber))
                        .toAbsolutePath().normalize();

                try (InputStream inputStream = new BufferedInputStream(chunk.getInputStream(), 65536);
                     OutputStream outputStream = new BufferedOutputStream(
                             Files.newOutputStream(chunkPath,
                                     StandardOpenOption.CREATE,
                                     StandardOpenOption.WRITE,
                                     StandardOpenOption.TRUNCATE_EXISTING), 65536)) {

                    byte[] buffer = new byte[65536];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                }

                TvContent content = tvContentRepository.findById(contentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Content not found"));

                log.info("Uploaded chunk {} for video {} ({} bytes)",
                        chunkNumber, fileName, chunk.getSize());

                return content;

            } catch (IOException e) {
                log.error("Failed to upload chunk: {}", e.getMessage());
                throw new BusinessException("Failed to upload chunk: " + e.getMessage());
            }
        }
    }

    @Transactional
    public TvContent completeVideoUpload(String contentId, String fileName, Integer totalChunks) {
        Object lock = uploadLocks.computeIfAbsent(contentId, k -> new Object());

        synchronized (lock) {
            try {
                TvContent content = tvContentRepository.findById(contentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Content not found"));

                Path chunkDirectory = Paths.get(chunkDir, fileName).toAbsolutePath().normalize();
                Path finalPath = Paths.get(videoUploadDir, fileName).toAbsolutePath().normalize();

                log.info("Merging {} chunks for video {}", totalChunks, fileName);

                try (FileChannel outputChannel = FileChannel.open(finalPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {

                    for (int i = 0; i < totalChunks; i++) {
                        Path chunkPath = chunkDirectory.resolve(String.format("chunk_%05d.tmp", i));
                        if (!Files.exists(chunkPath)) {
                            throw new IOException("Missing chunk " + i);
                        }
                        try (FileChannel inputChannel = FileChannel.open(chunkPath, StandardOpenOption.READ)) {
                            long chunkSize = inputChannel.size();
                            long transferred = 0;
                            while (transferred < chunkSize) {
                                transferred += inputChannel.transferTo(transferred,
                                        chunkSize - transferred, outputChannel);
                            }
                        }
                        Files.deleteIfExists(chunkPath);
                    }
                    outputChannel.force(true);
                }

                long actualSize = Files.size(finalPath);
                content.setUrl("/videos/" + fileName);
                TvContent saved = tvContentRepository.save(content);

                deleteDirectory(chunkDirectory);
                uploadLocks.remove(contentId);

                log.info("✅ Video {} merged. Size: {} bytes", fileName, actualSize);

                processToHlsAsync(contentId, finalPath.toString());

                return saved;

            } catch (IOException e) {
                log.error("Failed to merge video: {}", e.getMessage());
                throw new BusinessException("Failed to merge video: " + e.getMessage());
            }
        }
    }

    public void cancelVideoUpload(String contentId, String fileName) {
        tvContentRepository.deleteById(contentId);
        try {
            deleteDirectory(Paths.get(chunkDir, fileName));
        } catch (Exception e) {
            log.warn("Failed to cleanup chunks: {}", e.getMessage());
        }
        uploadLocks.remove(contentId);
    }

    private void validateVideoMetadata(String fileName, String contentType, Long totalSize) {
        if (fileName == null || fileName.isEmpty()) throw new BusinessException("File name is required");
        if (contentType == null || !ALLOWED_VIDEO_TYPES.contains(contentType.toLowerCase()))
            throw new BusinessException("Unsupported video type: " + contentType);
        if (totalSize == null || totalSize <= 0) throw new BusinessException("Invalid file size");
        if (totalSize > MAX_VIDEO_SIZE) throw new BusinessException("Video larger than 10GB");
    }

    // ==================== HLS PROCESSING ====================

    @Async
    public void processToHlsAsync(String contentId, String originalFilePath) {
        try {
            processToHls(contentId, originalFilePath);
        } catch (Exception e) {
            log.error("HLS processing failed for {}: {}", contentId, e.getMessage());
            tvContentRepository.findById(contentId).ifPresent(v -> {
                v.setHlsProcessed(false);
                tvContentRepository.save(v);
            });
        }
    }

    private void processToHls(String contentId, String originalFilePath) throws Exception {
        TvContent content = tvContentRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found"));

        Path hlsOutputDir = Paths.get(hlsDir, contentId).toAbsolutePath().normalize();
        deleteDirectory(hlsOutputDir);
        Files.createDirectories(hlsOutputDir);

        String masterPlaylistPath = hlsOutputDir.resolve("master.m3u8").toString();

        List<String> copyCommand = List.of(
                ffmpegPath, "-i", originalFilePath,
                "-c:v", "copy", "-c:a", "copy",
                "-hls_time", "10", "-hls_list_size", "0",
                "-hls_segment_filename", hlsOutputDir.resolve("segment_%05d.ts").toString(),
                "-f", "hls", masterPlaylistPath
        );

        int exitCode = runFfmpeg(copyCommand, 30);

        if (exitCode != 0) {
            log.warn("Stream copy failed. Falling back to re-encode...");
            List<String> fallbackCommand = List.of(
                    ffmpegPath, "-i", originalFilePath,
                    "-codec:v", "libx264", "-preset", "ultrafast", "-crf", "23",
                    "-codec:a", "aac", "-b:a", "128k",
                    "-hls_time", "10", "-hls_list_size", "0",
                    "-hls_segment_filename", hlsOutputDir.resolve("segment_%05d.ts").toString(),
                    "-f", "hls", masterPlaylistPath
            );
            exitCode = runFfmpeg(fallbackCommand, 120);
        }

        if (exitCode != 0) {
            throw new BusinessException("FFmpeg failed with exit code: " + exitCode);
        }

        content.setHlsProcessed(true);
        content.setHlsUrl("/serveiq/api/tv-content/hls/" + contentId + "/master.m3u8");
        tvContentRepository.save(content);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TV_MEDIA_CHANGED,
                content.getBranchId(), null, null, null, Instant.now()
        ));

        log.info("✅ HLS processing completed for {}: {}", contentId, content.getHlsUrl());
        socketService.broadcastAppDashboard(AppType.TV_DISPLAY);
    }

    private int runFfmpeg(List<String> command, int timeoutMinutes) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            } catch (IOException e) {
                log.error("Failed to read FFmpeg output: {}", e.getMessage());
            }
        });

        boolean completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            throw new BusinessException("FFmpeg timed out");
        }

        reader.get(1, TimeUnit.MINUTES);
        return process.exitValue();
    }

    // ==================== HLS STREAMING ====================

    public Path getHlsPlaylistPath(String contentId, String fileName) {
        Path hlsRoot = Paths.get(hlsDir, contentId).toAbsolutePath().normalize();
        Path filePath = hlsRoot.resolve(fileName).normalize();
        if (!filePath.startsWith(hlsRoot)) throw new BusinessException("Invalid HLS path");
        return filePath;
    }

    public Path getHlsSegmentPath(String contentId, String fileName) {
        Path hlsRoot = Paths.get(hlsDir, contentId).toAbsolutePath().normalize();
        Path filePath = hlsRoot.resolve(fileName).normalize();
        if (!filePath.startsWith(hlsRoot)) throw new BusinessException("Invalid HLS path");
        return filePath;
    }

    public Path getArchivedHlsPath(String contentId, String fileName) {
        Path hlsRoot = Paths.get(hlsArchiveDir, contentId).toAbsolutePath().normalize();
        Path filePath = hlsRoot.resolve(fileName).normalize();
        if (!filePath.startsWith(hlsRoot)) throw new BusinessException("Invalid HLS path");
        return filePath;
    }

    public Path getVideoFilePath(String contentId) {
        TvContent content = tvContentRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found"));

        String fileName = extractFileName(content.getUrl());
        Path path;
        if (Boolean.TRUE.equals(content.getArchived())) {
            path = Paths.get(videoArchiveDir, fileName);
        } else {
            path = Paths.get(videoUploadDir, fileName);
        }
        return path.toAbsolutePath().normalize();
    }

    // ==================== IMAGE METHODS ====================

    public TvContent uploadImage(String branchId, MultipartFile file) throws IOException {
        Files.createDirectories(Paths.get(uploadDir));
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(uploadDir, filename);
        Files.write(filePath, file.getBytes());

        TvContent image = new TvContent();
        image.setBranchId(branchId);
        image.setName(file.getOriginalFilename());
        image.setUrl("/images/" + filename);
        image.setType("IMAGE");
        image.setActive(false);
        image.setArchived(false);
        return tvContentRepository.save(image);
    }

    @Transactional
    public TvContent toggleImageStatus(String branchId, String id) {
        TvContent image = tvContentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        image.setActive(!image.getActive());
        TvContent savedImage = tvContentRepository.save(image);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.COUNTER_DISPLAY_IMAGE_CHANGED,
                branchId, null, null, null, Instant.now()
        ));
        return savedImage;
    }

    public void deleteImage(String id) throws IOException {
        TvContent image = tvContentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));
        deleteImageFiles(image);
        tvContentRepository.deleteById(id);
    }
}