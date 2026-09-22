package com.stock.api.audit;

import com.stock.api.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Trace automatiquement les actions métier via AOP.
 * Intercepte les méthodes d'écriture des services métier (create/update/delete/
 * validate/cancel) et enregistre une entrée d'audit après succès.
 *
 * Les détails (entité concernée, quantités...) sont dérivés des arguments
 * et du résultat de la méthode.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditService auditService;

    @Around("execution(* com.stock.api.service.ProductService.create(..)) || "
          + "execution(* com.stock.api.service.ProductService.update(..)) || "
          + "execution(* com.stock.api.service.ProductService.delete(..)) || "
          + "execution(* com.stock.api.service.CategoryService.create(..)) || "
          + "execution(* com.stock.api.service.CategoryService.update(..)) || "
          + "execution(* com.stock.api.service.CategoryService.delete(..)) || "
          + "execution(* com.stock.api.service.StockMovementService.create(..)) || "
          + "execution(* com.stock.api.service.OrderService.create(..)) || "
          + "execution(* com.stock.api.service.OrderService.validate(..)) || "
          + "execution(* com.stock.api.service.OrderService.cancel(..)) || "
          + "execution(* com.stock.api.service.SaleService.create(..)) || "
          + "execution(* com.stock.api.service.AdminUserService.create(..)) || "
          + "execution(* com.stock.api.service.AdminUserService.update(..)) || "
          + "execution(* com.stock.api.service.AdminUserService.delete(..))")
    public Object auditWrite(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();

        try {
            String method = pjp.getSignature().getName();
            String service = pjp.getSignature().getDeclaringType().getSimpleName();
            recordAudit(service, method, pjp.getArgs(), result);
        } catch (Exception e) {
            log.error("Erreur aspect audit : {}", e.getMessage());
        }

        return result;
    }

    private void recordAudit(String service, String method, Object[] args, Object result) {
        String action = switch (method) {
            case "create" -> "CREATE";
            case "update" -> "UPDATE";
            case "delete" -> "DELETE";
            case "validate" -> "VALIDATE";
            case "cancel" -> "CANCEL";
            default -> method.toUpperCase();
        };

        // Arguments typiques : (request, userEmail) ou (id, request)
        Object request = extractRequest(args);
        Long entityId = extractId(args, result);

        switch (service) {
            case "ProductService" -> auditService.record(action, "Product", entityId,
                    productName(request, result), detailsOf(request, "produit"));
            case "CategoryService" -> auditService.record(action, "Category", entityId,
                    categoryName(request, result), detailsOf(request, "catégorie"));
            case "StockMovementService" -> auditService.record(action, "StockMovement", entityId,
                    productName(request, result), movementDetails(request));
            case "OrderService" -> auditService.record(action, "Order", entityId,
                    orderReference(result), detailsOf(request, "commande"));
            case "SaleService" -> auditService.record(action, "Sale", entityId,
                    saleReference(result), saleDetails(request, result));
            case "AdminUserService", "AuthService" -> auditService.record(action, "User", entityId,
                    userEmail(request, result), "Compte utilisateur");
            default -> auditService.record(action, service, entityId, null, null);
        }
    }

    private String extractEmail(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String s && s.contains("@")) {
                return s;
            }
        }
        return null;
    }

    private Object extractRequest(Object[] args) {
        for (Object arg : args) {
            if (!(arg instanceof String) && arg != null) {
                return arg;
            }
        }
        return null;
    }

    private Long extractId(Object[] args, Object result) {
        for (Object arg : args) {
            if (arg instanceof Long l) {
                return l;
            }
        }
        return extractLongFrom(result, "getId");
    }

    private Long extractLongFrom(Object obj, String getter) {
        if (obj == null) return null;
        try {
            Object value = obj.getClass().getMethod(getter).invoke(obj);
            return value instanceof Long l ? l : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String stringFrom(Object obj, String getter) {
        if (obj == null) return null;
        try {
            Object value = obj.getClass().getMethod(getter).invoke(obj);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String productName(Object request, Object result) {
        String fromResult = stringFrom(result, "getName");
        return fromResult != null ? fromResult : stringFrom(request, "getName");
    }

    private String categoryName(Object request, Object result) {
        return productName(request, result);
    }

    private String orderReference(Object result) {
        return stringFrom(result, "getReference");
    }

    private String saleReference(Object result) {
        return stringFrom(result, "getReference");
    }

    private String userEmail(Object request, Object result) {
        String fromResult = stringFrom(result, "getEmail");
        return fromResult != null ? fromResult : stringFrom(request, "getEmail");
    }

    private String detailsOf(Object request, String label) {
        if (request == null) return label;
        String name = stringFrom(request, "getName");
        return name != null ? label + " : " + name : label;
    }

    private String movementDetails(Object request) {
        if (request == null) return null;
        String type = stringFrom(request, "getType");
        String quantity = stringFrom(request, "getQuantity");
        String reason = stringFrom(request, "getReason");
        return String.format("%s qty=%s %s", type, quantity,
                reason != null ? "motif : " + reason : "");
    }

    private String saleDetails(Object request, Object result) {
        String total = stringFrom(result, "getTotalAmount");
        Integer items = null;
        try {
            if (result != null) {
                Object v = result.getClass().getMethod("getTotalItems").invoke(result);
                items = v instanceof Integer i ? i : null;
            }
        } catch (Exception ignored) {
        }
        return String.format("total=%s €, articles=%s", total, items);
    }
}
