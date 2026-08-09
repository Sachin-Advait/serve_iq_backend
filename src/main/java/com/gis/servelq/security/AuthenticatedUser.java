package com.gis.servelq.security;

/**
 * What goes in the SecurityContext once a JWT checks out. Controllers pull it
 * out with @AuthenticationPrincipal, mostly to check the caller's own branch
 * against the resource being asked for.
 */
public record AuthenticatedUser(
        String id,
        String email,
        String role,
        String branchId,
        String counterId) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isSameUser(String userId) {
        return id != null && id.equals(userId);
    }

    public boolean belongsToBranch(String otherBranchId) {
        return branchId != null && branchId.equals(otherBranchId);
    }
}
