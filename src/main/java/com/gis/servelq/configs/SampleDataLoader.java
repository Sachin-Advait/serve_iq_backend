//package com.gis.servelq.configs;
//
//import com.gis.servelq.models.*;
//import com.gis.servelq.repository.*;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDateTime;
//import java.util.Arrays;
//import java.util.List;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class SampleDataLoader implements CommandLineRunner {
//
//    private final BranchRepository branchRepository;
//    private final ServiceRepository serviceRepository;
//    private final CounterRepository counterRepository;
//    private final VisitorRepository visitorRepository;
//    private final TokenRepository tokenRepository;
//    private final UserRepository userRepository;
//
//    @Override
//    public void run(String... args) throws Exception {
//        // Check if data already exists to avoid duplicates
//        if (branchRepository.count() == 0) {
//            log.info("Loading sample data...");
//            loadSampleData();
//            log.info("Sample data loaded successfully!");
//        } else {
//            log.info("Data already exists, skipping sample data loading");
//        }
//    }
//
//    private void loadSampleData() {
//        // 1. Create Main Branch
//        Branch mainBranch = createBranch("HQ", "Headquarters - Muscat", "Asia/Muscat");
//
//        // 2. Create Main Services (Top Level)
//        ServiceModel svcReg = createService(mainBranch, "REG", "Registration & Enrollment", null, 900);
//        ServiceModel svcContr = createService(mainBranch, "CONTR", "Contributions", null, 1200);
//        ServiceModel svcClaim = createService(mainBranch, "CLAIM", "Claims & Benefits", null, 1800);
//        ServiceModel svcCert = createService(mainBranch, "CERT", "Certificates & Letters", null, 600);
//        ServiceModel svcUpd = createService(mainBranch, "UPDATE", "Profile/Employer Updates", null, 900);
//        ServiceModel svcHelp = createService(mainBranch, "CSR", "Customer Service / General Inquiries", null, 600);
//
//        // 3. Create Sub-services
//        // Registration Sub-services
//        ServiceModel regEmp = createService(mainBranch, "REG-EMP", "Employer Registration", svcReg.getId(), 900);
//        ServiceModel regWork = createService(mainBranch, "REG-WORK", "Worker Registration", svcReg.getId(), 1200);
//
//        // Contributions Sub-services
//        ServiceModel contrPay = createService(mainBranch, "CONTR-PAY", "Contribution Payment / Reconciliation", svcContr.getId(), 1800);
//        ServiceModel contrAdj = createService(mainBranch, "CONTR-ADJ", "Contribution Adjustments (Back-pay/Corrections)", svcContr.getId(), 2400);
//
//        // Claims Sub-services
//        ServiceModel claimRet = createService(mainBranch, "CLAIM-RET", "Retirement Pension Claim", svcClaim.getId(), 1800);
//        ServiceModel claimDis = createService(mainBranch, "CLAIM-DIS", "Disability Pension Claim", svcClaim.getId(), 2400);
//        ServiceModel claimSurv = createService(mainBranch, "CLAIM-SURV", "Survivor/Heir Benefits", svcClaim.getId(), 2400);
//        ServiceModel claimUnemp = createService(mainBranch, "CLAIM-UNEMP", "Unemployment Support", svcClaim.getId(), 1200);
//
//        // Certificates Sub-services
//        ServiceModel certEmp = createService(mainBranch, "CERT-EMP", "Employment/Contribution Certificate", svcCert.getId(), 600);
//        ServiceModel certPen = createService(mainBranch, "CERT-PEN", "Pension Entitlement Letter", svcCert.getId(), 900);
//
//        // Update Sub-services
//        ServiceModel updEmp = createService(mainBranch, "UPD-EMP", "Employer Data Update", svcUpd.getId(), 900);
//        ServiceModel updBank = createService(mainBranch, "UPD-BANK", "Bank/Contact Update", svcUpd.getId(), 600);
//
//        // CSR Sub-services
//        ServiceModel csrGen = createService(mainBranch, "CSR-GEN", "General Inquiry / Guidance", svcHelp.getId(), 600);
//
//        // 4. Create Counters
//        Counter counter1 = createCounter(mainBranch, "C01", "Counter 1", Arrays.asList(svcReg, regEmp, regWork));
//        Counter counter2 = createCounter(mainBranch, "C02", "Counter 2", Arrays.asList(svcContr, contrPay, contrAdj));
//        Counter counter3 = createCounter(mainBranch, "C03", "Counter 3", Arrays.asList(svcClaim, claimRet, claimDis, claimSurv, claimUnemp));
//        Counter counter4 = createCounter(mainBranch, "C04", "Counter 4", Arrays.asList(svcCert, certEmp, certPen));
//        Counter counter5 = createCounter(mainBranch, "C05", "Counter 5", Arrays.asList(svcUpd, svcHelp, updEmp, updBank, csrGen));
//
//        // 5. Create Sample Visitors
//        Visitor visitor1 = createVisitor("Ahmed Al-Maskari", "+96891234567", "123456789012", "Pension Registration");
//        Visitor visitor2 = createVisitor("Fatma Al-Siyabi", "+96892345678", "234567890123", "Contribution Inquiry");
//        Visitor visitor3 = createVisitor("Khalid Al-Hinai", "+96893456789", "345678901234", "Claim Submission");
//        Visitor visitor4 = createVisitor("Mariam Al-Balushi", "+96894567890", "456789012345", "Certificate Request");
//        Visitor visitor5 = createVisitor("Salem Al-Abri", "+96895678901", "567890123456", "Data Update");
//
//        // 6. Create Sample Tokens
//        createToken(mainBranch, regEmp, visitor1, "REG-EMP-001", TokenStatus.WAITING, null);
//        createToken(mainBranch, regWork, visitor2, "REG-WORK-001", TokenStatus.WAITING, null);
//        createToken(mainBranch, contrPay, visitor3, "CONTR-PAY-001", TokenStatus.CALLING, counter2);
//        createToken(mainBranch, claimRet, visitor4, "CLAIM-RET-001", TokenStatus.SERVING, counter3);
//        createToken(mainBranch, certEmp, visitor5, "CERT-EMP-001", TokenStatus.DONE, counter4);
//        createToken(mainBranch, updBank, visitor1, "UPD-BANK-001", TokenStatus.WAITING, null);
//        createToken(mainBranch, csrGen, visitor2, "CSR-GEN-001", TokenStatus.WAITING, null);
//        createToken(mainBranch, contrAdj, visitor3, "CONTR-ADJ-001", TokenStatus.WAITING, null);
//
//        // 7. Create Users
//        createUser("admin", "admin123", UserRole.ADMIN, mainBranch);
//        createUser("manager", "manager123", UserRole.MANAGER, mainBranch);
//        createUser("teller1", "teller123", UserRole.TELLER, mainBranch);
//        createUser("teller2", "teller123", UserRole.TELLER, mainBranch);
//        createUser("reception", "reception123", UserRole.RECEPTION, mainBranch);
//
//        log.info("Sample data loaded: 1 Branch, 19 Services, 5 Counters, 5 Visitors, 8 Tokens, 5 Users");
//    }
//
//    private Branch createBranch(String code, String name, String timezone) {
//        Branch branch = new Branch();
//        branch.setCode(code);
//        branch.setName(name);
//        branch.setTimezone(timezone);
//        branch.setEnabled(true);
//        return branchRepository.save(branch);
//    }
//
//    private ServiceModel createService(Branch branch, String code, String name, String parentId, Integer slaSec) {
//        ServiceModel service = new ServiceModel();
//        service.setCode(code);
//        service.setName(name);
//        service.setParentId(parentId);
//        service.setBranchId(branch.getId());
//        service.setSlaSec(slaSec);
//        service.setEnabled(true);
//        return serviceRepository.save(service);
//    }
//
//    private Counter createCounter(Branch branch, String code, String name, List<ServiceModel> services) {
//        Counter counter = new Counter();
//        counter.setCode(code);
//        counter.setName(name);
//        counter.setBranchId(branch.getId());
//        counter.setEnabled(true);
//        counter.setPaused(false);
//        counter.setStatus("IDLE");
//        counter.setServices(services);
//        return counterRepository.save(counter);
//    }
//
//    private Visitor createVisitor(String name, String phone, String civilId, String purpose) {
//        Visitor visitor = new Visitor();
//        visitor.setName(name);
//        visitor.setPhone(phone);
//        visitor.setCivilId(civilId);
//        visitor.setPurpose(purpose);
//        visitor.setConsentVer(1);
//        visitor.setConsentAt(LocalDateTime.now());
//        return visitorRepository.save(visitor);
//    }
//
//    private Token createToken(Branch branch, ServiceModel service, Visitor visitor, String tokenNumber,
//                              TokenStatus status, Counter counter) {
//        Token token = new Token();
//        token.setToken(tokenNumber);
//        token.setBranchId(branch.getId());
//        token.setServiceId(service.getId());
//        token.setPriority(100);
//        token.setStatus(status);
//        token.setVisitorId(visitor.getId());
//
//        if (counter != null) {
//            token.setCounterId(counter.getId());
//        }
//
//        token.setCreatedAt(LocalDateTime.now().minusMinutes(30));
//
//        if (status == TokenStatus.CALLING || status == TokenStatus.SERVING || status == TokenStatus.DONE) {
//            token.setCalledAt(LocalDateTime.now().minusMinutes(10));
//        }
//
//        if (status == TokenStatus.SERVING || status == TokenStatus.DONE) {
//            token.setStartAt(LocalDateTime.now().minusMinutes(5));
//        }
//
//        if (status == TokenStatus.DONE) {
//            token.setEndAt(LocalDateTime.now().minusMinutes(2));
//        }
//
//        return tokenRepository.save(token);
//    }
//
//    private User createUser(String username, String password, UserRole role, Branch branch) {
//        User user = new User();
//        user.setUsername(username);
//        user.setPassword(password); // In production, encrypt this!
//        user.setRole(role);
//        user.setBranchId(branch.getId());
//        return userRepository.save(user);
//    }
//}