package com.gis.servelq.services;

import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdSyncService {

    private final ActiveDirectoryService adService;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    @Scheduled(cron = "0 0 3 * * *")  // Daily at 3 AM
    public void syncUsersFromAd() {
        log.info("=== Starting daily AD user sync ===");

        try {
            Set<String> adEmails = adService.getAllUserEmails();
            log.info("Fetched {} users from AD", adEmails.size());

            if (adEmails.isEmpty()) {
                log.warn("No users fetched from AD - skipping sync");
                return;
            }

            String defaultBranchId = branchRepository.findFirstByOrderByCreatedAtAsc()
                    .map(branch -> branch.getId())
                    .orElse(null);

            int newUsers = 0;
            int updatedUsers = 0;

            for (String email : adEmails) {
                User existingUser = userRepository.findByEmail(email).orElse(null);

                if (existingUser == null) {
                    // Create new user
                    Map<String, String> adAttrs = adService.getUserAttributes(email);
                    User newUser = new User();
                    newUser.setName(adAttrs.get("displayName"));
                    newUser.setEmail(email);
                    newUser.setRole(UserRole.USER);
                    newUser.setActive(true);
                    newUser.setPassword("AD_AUTH");
                    newUser.setBranchId(defaultBranchId);
                    userRepository.save(newUser);
                    newUsers++;
                } else {
                    // Update existing user
                    Map<String, String> adAttrs = adService.getUserAttributes(email);
                    boolean changed = false;

                    String adName = adAttrs.get("displayName");
                    if (adName != null && !adName.equals(existingUser.getName())) {
                        existingUser.setName(adName);
                        changed = true;
                    }

                    if (existingUser.getBranchId() == null && defaultBranchId != null) {
                        existingUser.setBranchId(defaultBranchId);
                        changed = true;
                    }

                    if (changed) {
                        userRepository.save(existingUser);
                        updatedUsers++;
                    }
                }
            }

            int deletedUsers = deleteUsersNotInAd(adEmails);

            log.info("Sync complete: {} new, {} updated, {} deleted", newUsers, updatedUsers, deletedUsers);

        } catch (Exception e) {
            log.error("AD sync failed - keeping existing users: {}", e.getMessage(), e);
        }
    }

    public Map<String, Integer> manualSync() {
        Map<String, Integer> result = new HashMap<>();

        try {
            Set<String> adEmails = adService.getAllUserEmails();

            if (adEmails.isEmpty()) {
                result.put("error", -1);
                return result;
            }

            String defaultBranchId = branchRepository.findFirstByOrderByCreatedAtAsc()
                    .map(branch -> branch.getId())
                    .orElse(null);

            int newUsers = 0;
            int updatedUsers = 0;

            for (String email : adEmails) {
                User existingUser = userRepository.findByEmail(email).orElse(null);

                if (existingUser == null) {
                    Map<String, String> adAttrs = adService.getUserAttributes(email);
                    User newUser = new User();
                    newUser.setName(adAttrs.get("displayName"));
                    newUser.setEmail(email);
                    newUser.setRole(UserRole.USER);
                    newUser.setActive(true);
                    newUser.setPassword("AD_AUTH");
                    newUser.setBranchId(defaultBranchId);
                    userRepository.save(newUser);
                    newUsers++;
                } else {
                    Map<String, String> adAttrs = adService.getUserAttributes(email);
                    boolean changed = false;

                    String adName = adAttrs.get("displayName");
                    if (adName != null && !adName.equals(existingUser.getName())) {
                        existingUser.setName(adName);
                        changed = true;
                    }

                    if (existingUser.getBranchId() == null && defaultBranchId != null) {
                        existingUser.setBranchId(defaultBranchId);
                        changed = true;
                    }

                    if (changed) {
                        userRepository.save(existingUser);
                        updatedUsers++;
                    }
                }
            }

            int deletedUsers = deleteUsersNotInAd(adEmails);

            result.put("newUsers", newUsers);
            result.put("updatedUsers", updatedUsers);
            result.put("deletedUsers", deletedUsers);
            result.put("totalInAd", adEmails.size());

        } catch (Exception e) {
            log.error("Manual AD sync failed: {}", e.getMessage(), e);
            result.put("error", -1);
        }

        return result;
    }

    private int deleteUsersNotInAd(Set<String> adEmails) {
        int deletedCount = 0;
        List<User> dbUsers = userRepository.findAll();

        for (User user : dbUsers) {
            if (user.getEmail() == null || user.getEmail().isEmpty()) {
                continue;
            }

            // Skip protected roles
            if (isProtectedRole(user.getRole())) {
                continue;
            }

            // Skip users with local passwords (not AD users)
            if (!"AD_AUTH".equals(user.getPassword())) {
                continue;
            }

            // Delete if email not in AD
            if (!adEmails.contains(user.getEmail().toLowerCase())) {
                userRepository.deleteById(user.getId());
                deletedCount++;
                log.info("Deleted user: {} ({}) - not in AD", user.getName(), user.getEmail());
            }
        }

        return deletedCount;
    }

    private boolean isProtectedRole(UserRole role) {
        if (role == null) return true;
        return role == UserRole.DISPLAY || role == UserRole.KIOSK;
    }
}