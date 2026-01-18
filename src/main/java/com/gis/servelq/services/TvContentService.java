package com.gis.servelq.services;

import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.events.TokenEventPublisher;
import com.gis.servelq.events.TokenEventType;
import com.gis.servelq.models.TvContent;
import com.gis.servelq.repository.TvContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TvContentService {

    private final TvContentRepository tvContentRepository;
    private final TokenEventPublisher tokenEventPublisher;

    @Value("${image.upload.dir}")
    private String uploadDir;

    public List<TvContent> getContentByBranch(String branchId) {
        List<String> allowedTypes = List.of("URL", "VIDEO");
        return tvContentRepository.findByBranchIdAndTypeIn(branchId, allowedTypes);
    }

    public TvContent addUrl(String branchId, String url, String name) {
        TvContent t = new TvContent();
        t.setBranchId(branchId);
        t.setUrl(url);
        t.setName(name);
        t.setType("URL");
        t.setActive(false);
        return tvContentRepository.save(t);
    }

    public TvContent addVideo(String branchId, String name) {
        TvContent content = new TvContent();
        content.setBranchId(branchId);
        content.setName(name);
        content.setType("VIDEO");
        content.setActive(false);
        return tvContentRepository.save(content);
    }

    public void delete(String id) {
        tvContentRepository.deleteById(id);
    }

    @Transactional
    public TvContent activateVideo(String branchId, String id) {
        List<String> allowedTypes = List.of("URL", "VIDEO");

        List<TvContent> all = tvContentRepository.findByBranchIdAndTypeIn(branchId, allowedTypes);
        all.forEach(c -> c.setActive(false));
        tvContentRepository.saveAll(all);

        TvContent selected = tvContentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found"));

        selected.setActive(true);
        TvContent saved = tvContentRepository.save(selected);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.TV_MEDIA_CHANGED,
                branchId,
                null,
                null,
                null,
                Instant.now()
        ));

        return saved;
    }

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

        return tvContentRepository.save(image);
    }

    public List<TvContent> getAllImages(String branchId) {
        return tvContentRepository.findByBranchIdAndType(branchId, "IMAGE");
    }

    public List<TvContent> getActiveImages(String branchId) {
        return tvContentRepository.findByBranchIdAndTypeAndActive(branchId, "IMAGE", true);
    }

    @Transactional
    public TvContent toggleImageStatus(String branchId, String id) {
        TvContent image = tvContentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        image.setActive(!image.getActive());
        TvContent savedImage = tvContentRepository.save(image);

        tokenEventPublisher.publish(new TokenEvent(
                TokenEventType.COUNTER_DISPLAY_IMAGE_CHANGED,
                branchId,
                null,
                null,
                null,
                Instant.now()
        ));

        return savedImage;
    }

    public void deleteImage(String id) throws IOException {
        TvContent image = tvContentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        String fileName = Paths.get(image.getUrl()).getFileName().toString();
        Path filePath = Paths.get(uploadDir, fileName);

        Files.deleteIfExists(filePath);
        tvContentRepository.deleteById(id);
    }
}
