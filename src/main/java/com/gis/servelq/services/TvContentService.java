package com.gis.servelq.services;

import com.gis.servelq.models.TvContent;
import com.gis.servelq.repository.TvContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TvContentService {

    private final TvContentRepository repo;
    private final SocketService socketService;

    public List<TvContent> getByBranch(String branchId) {
        return repo.findByBranchId(branchId);
    }

    public TvContent addUrl(String branchId, String url, String name) {
        TvContent t = new TvContent();
        t.setBranchId(branchId);
        t.setUrl(url);
        t.setName(name);
        t.setType("URL");
        t.setActive(false);
        return repo.save(t);
    }

    public TvContent addVideo(String branchId, String name) {
        TvContent content = new TvContent();
        content.setBranchId(branchId);
        content.setName(name);
        content.setType("VIDEO");
        content.setActive(false);
        return repo.save(content);
    }

    public void delete(String id) {
        repo.deleteById(id);
    }

    public TvContent activate(String branchId, String id) {
        // Deactivate all for branch
        List<TvContent> all = repo.findByBranchId(branchId);
        all.forEach(c -> c.setActive(false));
        repo.saveAll(all);

        // Activate required one
        TvContent selected = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Content not found"));
        selected.setActive(true);
        socketService.tvSocket(branchId);
        return repo.save(selected);
    }
}
