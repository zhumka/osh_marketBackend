package com.oshmarket.config;

import com.oshmarket.entity.*;
import com.oshmarket.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Наполняет БД тестовыми данными для проверки API.
 * Идемпотентен: если маркерный арендатель уже существует — ничего не делает.
 * Отключается флагом app.seed.enabled=false (например, в проде).
 *
 * Пароль всех тестовых арендаторов: Tenant@123456
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
@Order(100)
public class DataSeeder implements ApplicationRunner {

    private static final String MARKER_INN = "11111111111111";
    private static final String TENANT_PASSWORD = "Tenant@123456";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PlaceRepository placeRepository;
    private final RentContractRepository contractRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (tenantRepository.existsByInnAndDeletedFalse(MARKER_INN)) {
            log.info("Тестовые данные уже присутствуют — сидинг пропущен.");
            return;
        }

        log.info("Заполняю БД тестовыми данными...");

        seedExtraPaymentMethod();
        PaymentMethod qrMethod = paymentMethodRepository
                .findAllByActiveTrueOrderBySortOrderAscNameAsc().stream().findFirst().orElse(null);

        // Арендатор 1 — должник, с историей платежей разных статусов
        Tenant t1 = createTenant(MARKER_INN, "Асанов Асан Асанович", "+996700111111", "asanov@example.com");
        Place p1 = createPlace("A-01", "Проход 1", "Овощи и фрукты", "5000", true);
        RentContract c1 = createContract(t1, p1, LocalDate.now().minusMonths(4), "5000");
        createPayment(c1, "3000", LocalDate.now().minusMonths(1), PaymentStatus.APPROVED,
                "Оплата за прошлый месяц", null, null);
        createPayment(c1, "5000", LocalDate.now().minusDays(1), PaymentStatus.PENDING,
                "Онлайн-оплата (" + (qrMethod != null ? qrMethod.getName() : "QR") + ")", null, qrMethod);
        createPayment(c1, "5000", LocalDate.now().minusDays(10), PaymentStatus.REJECTED,
                "Онлайн-оплата (" + (qrMethod != null ? qrMethod.getName() : "QR") + ")", "Чек нечитаемый, сумма не совпадает", qrMethod);
        notify(t1, NotificationType.PAYMENT_REMINDER, "Напоминание: оплата аренды до " +
                LocalDate.now().plusDays(3) + ". Сумма: 5 000 сом.");
        notify(t1, NotificationType.PAYMENT_PENDING, "Ваш платёж на сумму 5 000 сом находится на проверке.");

        // Арендатор 2 — всё оплачено
        Tenant t2 = createTenant("22222222222222", "Бектурова Бакыт Бекеновна", "+996700222222", "bekturova@example.com");
        Place p2 = createPlace("B-02", "Проход 2", "Одежда", "8000", true);
        RentContract c2 = createContract(t2, p2, LocalDate.now().minusMonths(2), "0");
        createPayment(c2, "8000", LocalDate.now().minusDays(5), PaymentStatus.APPROVED,
                "Оплата за текущий месяц", null, null);
        notify(t2, NotificationType.PAYMENT_SUCCESS, "Ваш платёж за текущий месяц принят. Сумма: 8 000 сом.");

        // Арендатор 3 — крупный должник, без платежей
        Tenant t3 = createTenant("33333333333333", "Чойбеков Чойбек Чойбекович", "+996700333333", "choibekov@example.com");
        Place p3 = createPlace("C-03", "Проход 1", "Мясо", "6000", true);
        createContract(t3, p3, LocalDate.now().minusMonths(3), "12000");
        notify(t3, NotificationType.PAYMENT_REMINDER, "У вас задолженность 12 000 сом. Просьба погасить.");

        // Свободные места — для тестирования бронирования
        createPlace("D-04", "Проход 3", "Бытовая техника", "7000", false);
        createPlace("E-05", "Проход 3", "Канцтовары", "4500", false);

        log.info("Тестовые данные созданы. Логин арендаторов: ИНН 11111111111111 / 22222222222222 / 33333333333333, пароль: {}", TENANT_PASSWORD);
    }

    private void seedExtraPaymentMethod() {
        PaymentMethod m = new PaymentMethod();
        m.setName("Оплата по QR (Оптима)");
        m.setRequisites("Отсканируйте QR в приложении Оптима Банк и переведите точную сумму. Затем загрузите чек.");
        m.setActive(true);
        m.setSortOrder(2);
        paymentMethodRepository.save(m);
    }

    private Tenant createTenant(String inn, String fullName, String phone, String email) {
        User user = new User();
        user.setInn(inn);
        user.setPasswordHash(passwordEncoder.encode(TENANT_PASSWORD));
        user.setRole(UserRole.TENANT);
        user.setPhone(phone);
        user.setEmail(email);
        userRepository.save(user);

        Tenant tenant = new Tenant();
        tenant.setUser(user);
        tenant.setInn(inn);
        tenant.setFullName(fullName);
        tenant.setPhone(phone);
        tenant.setEmail(email);
        tenant.setPassportSeries("AN");
        tenant.setPassportNumber("12" + inn.substring(0, 5));
        tenant.setPassportIssuedDate(LocalDate.of(2018, 5, 20));
        tenant.setPassportIssuedBy("МКК 50-01");
        return tenantRepository.save(tenant);
    }

    private Place createPlace(String number, String aisle, String department, String rent, boolean occupied) {
        Place place = new Place();
        place.setPlaceNumber(number);
        place.setAisle(aisle);
        place.setDepartment(department);
        place.setMonthlyRent(new BigDecimal(rent));
        place.setOccupied(occupied);
        return placeRepository.save(place);
    }

    private RentContract createContract(Tenant tenant, Place place, LocalDate startDate, String debt) {
        RentContract contract = new RentContract();
        contract.setTenant(tenant);
        contract.setPlace(place);
        contract.setStartDate(startDate);
        contract.setDebt(new BigDecimal(debt));
        return contractRepository.save(contract);
    }

    private void createPayment(RentContract contract, String amount, LocalDate date, PaymentStatus status,
                               String description, String rejectReason, PaymentMethod method) {
        Payment payment = new Payment();
        payment.setContract(contract);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentDate(date);
        payment.setDescription(description);
        payment.setStatus(status);
        payment.setPaymentMethod(method);
        if (status == PaymentStatus.PENDING) {
            payment.setSubmittedAt(LocalDateTime.now());
        }
        if (status == PaymentStatus.REJECTED) {
            payment.setRejectReason(rejectReason);
            payment.setReviewedAt(LocalDateTime.now());
        }
        paymentRepository.save(payment);
    }

    private void notify(Tenant tenant, NotificationType type, String message) {
        Notification n = new Notification();
        n.setTenant(tenant);
        n.setType(type);
        n.setMessage(message);
        notificationRepository.save(n);
    }
}
