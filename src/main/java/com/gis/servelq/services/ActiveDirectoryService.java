package com.gis.servelq.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import java.util.*;

@Slf4j
@Service
public class ActiveDirectoryService {

    @Value("${ad.ldap.urls}")
    private String ldapUrls;

    @Value("${ad.ldap.bind-user}")
    private String bindUser;

    @Value("${ad.ldap.bind-password}")
    private String bindPassword;

    @Value("${ad.ldap.user-search-base}")
    private String userSearchBase;

    @Value("${ad.ldap.user-search-filter}")
    private String userSearchFilter;

    @Value("${ad.ldap.mail-attribute:mail}")
    private String mailAttribute;

    @Value("${ad.ldap.display-name-attribute:displayName}")
    private String displayNameAttribute;

    @Value("${ad.ldap.staff-id-attribute:employeeID}")
    private String staffIdAttribute;

    @Value("${ad.ldap.department-attribute:department}")
    private String departmentAttribute;

    @Value("${ad.ldap.title-attribute:title}")
    private String titleAttribute;

    private static final int PAGE_SIZE = 500;

    /**
     * Authenticate user against AD
     * Accepts: email (user@msspf.om), username
     */
    public boolean authenticate(String usernameOrEmail, String password) {
        LdapContext ctx = null;
        try {
            List<String> principalsToTry = buildPrincipalCandidates(usernameOrEmail);
            String[] urls = ldapUrls.split(",");

            for (String principal : principalsToTry) {
                for (String url : urls) {
                    try {
                        Hashtable<String, Object> env = new Hashtable<>();
                        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                        env.put(Context.PROVIDER_URL, url.trim());
                        env.put(Context.SECURITY_AUTHENTICATION, "simple");
                        env.put(Context.SECURITY_PRINCIPAL, principal);
                        env.put(Context.SECURITY_CREDENTIALS, password);
                        env.put(Context.REFERRAL, "follow");
                        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
                        env.put("com.sun.jndi.ldap.read.timeout", "5000");

                        ctx = new InitialLdapContext(env, null);
                        log.info("AD authentication successful for: {}", principal);
                        return true;
                    } catch (AuthenticationException e) {
                        log.warn("AD auth failed for principal [{}] on {}: {}", principal, url, e.getMessage());
                    } catch (NamingException e) {
                        log.warn("Failed to connect to {}: {}", url, e.getMessage());
                    }
                }
            }
            return false;
        } finally {
            if (ctx != null) { try { ctx.close(); } catch (Exception ignored) {} }
        }
    }

    private List<String> buildPrincipalCandidates(String usernameOrEmail) {
        List<String> candidates = new ArrayList<>();
        String netbiosDomain = extractNetbiosDomain();

        if (usernameOrEmail.contains("@")) {
            // Email format: user@msspf.om
            candidates.add(usernameOrEmail);
            String localPart = usernameOrEmail.substring(0, usernameOrEmail.indexOf("@"));
            if (netbiosDomain != null) {
                candidates.add(netbiosDomain + "\\" + localPart);
            }
        } else if (usernameOrEmail.contains("\\")) {
            candidates.add(usernameOrEmail);
        } else {
            // Plain username
            if (netbiosDomain != null) {
                candidates.add(netbiosDomain + "\\" + usernameOrEmail);
            }
            candidates.add(usernameOrEmail);
        }
        return candidates;
    }

    private String extractNetbiosDomain() {
        if (bindUser != null && bindUser.contains("\\")) {
            return bindUser.substring(0, bindUser.indexOf("\\"));
        }
        return null;
    }

    /**
     * Get user attributes from AD by email, username, or UPN
     */
    public Map<String, String> getUserAttributes(String usernameOrEmail) {
        Map<String, String> attrs = new HashMap<>();
        LdapContext ctx = null;

        try {
            ctx = createAdminContext();
            String filter = String.format("(&%s(|(%s=%s)(sAMAccountName=%s)(userPrincipalName=%s)))",
                    userSearchFilter, mailAttribute, usernameOrEmail, usernameOrEmail, usernameOrEmail);

            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[]{
                    displayNameAttribute, mailAttribute, "sAMAccountName",
                    departmentAttribute, titleAttribute, staffIdAttribute
            });

            NamingEnumeration<SearchResult> results = ctx.search(userSearchBase, filter, controls);
            if (results.hasMore()) {
                Attributes adAttrs = results.next().getAttributes();
                attrs.put("displayName", getAttr(adAttrs, displayNameAttribute));
                attrs.put("email", getAttr(adAttrs, mailAttribute));
                attrs.put("username", getAttr(adAttrs, "sAMAccountName"));
                attrs.put("department", getAttr(adAttrs, departmentAttribute));
                attrs.put("title", getAttr(adAttrs, titleAttribute));
                attrs.put("staffId", getAttr(adAttrs, staffIdAttribute));
            }
        } catch (Exception e) {
            log.error("AD search failed for {}: {}", usernameOrEmail, e.getMessage());
        } finally {
            if (ctx != null) { try { ctx.close(); } catch (Exception ignored) {} }
        }
        return attrs;
    }

    /**
     * Get ALL active AD users' emails (for sync)
     */
    public Set<String> getAllUserEmails() {
        Set<String> emails = new HashSet<>();
        LdapContext ctx = null;

        try {
            ctx = createAdminContext();
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[]{mailAttribute});

            byte[] cookie = null;
            do {
                ctx.setRequestControls(new Control[]{
                        new PagedResultsControl(PAGE_SIZE, cookie, Control.CRITICAL)
                });

                NamingEnumeration<SearchResult> results = ctx.search(userSearchBase, userSearchFilter, controls);
                while (results.hasMore()) {
                    try {
                        Attributes adAttrs = results.next().getAttributes();
                        String email = getAttr(adAttrs, mailAttribute);
                        if (email != null && !email.isEmpty()) {
                            emails.add(email.toLowerCase());
                        }
                    } catch (PartialResultException e) {
                        break;
                    }
                }
                results.close();
                cookie = getCookie(ctx);
            } while (cookie != null && cookie.length != 0);

            log.info("Fetched {} active users from AD", emails.size());

        } catch (Exception e) {
            log.error("AD get all users failed: {}", e.getMessage());
        } finally {
            if (ctx != null) { try { ctx.close(); } catch (Exception ignored) {} }
        }
        return emails;
    }

    private LdapContext createAdminContext() throws NamingException {
        String[] urls = ldapUrls.split(",");
        for (String url : urls) {
            try {
                Hashtable<String, Object> env = new Hashtable<>();
                env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                env.put(Context.PROVIDER_URL, url.trim());
                env.put(Context.SECURITY_AUTHENTICATION, "simple");
                env.put(Context.SECURITY_PRINCIPAL, bindUser);
                env.put(Context.SECURITY_CREDENTIALS, bindPassword);
                env.put(Context.REFERRAL, "follow");
                env.put("com.sun.jndi.ldap.connect.timeout", "5000");
                env.put("com.sun.jndi.ldap.read.timeout", "5000");
                return new InitialLdapContext(env, null);
            } catch (NamingException e) {
                log.warn("Failed to connect to {}: {}", url, e.getMessage());
            }
        }
        throw new NamingException("Cannot connect to any AD server");
    }

    private String getAttr(Attributes attrs, String name) {
        try {
            Attribute attr = attrs.get(name);
            return (attr != null && attr.get() != null) ? String.valueOf(attr.get()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] getCookie(LdapContext ctx) {
        try {
            Control[] controls = ctx.getResponseControls();
            if (controls != null) {
                for (Control control : controls) {
                    if (control instanceof PagedResultsResponseControl) {
                        return ((PagedResultsResponseControl) control).getCookie();
                    }
                }
            }
        } catch (Exception e) {}
        return null;
    }
}