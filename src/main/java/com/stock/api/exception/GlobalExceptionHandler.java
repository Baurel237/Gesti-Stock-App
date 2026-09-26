package com.stock.api.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.stock.api.service.InvalidProductImageException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gestionnaire centralisé des erreurs.
 * Retourne des messages d'erreur clairs et précis pour chaque type d'erreur,
 * avec un code HTTP cohérent :
 *   400 validation / requête illisible — 404 ressource introuvable —
 *   409 conflit (doublon, état invalide) — 401 non authentifié —
 *   403 accès refusé — 500 erreur interne (sans fuite de détails techniques).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error,
                                                      String message, String details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        if (details != null) {
            body.put("details", details);
        }
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(InvalidProductImageException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidProductImage(InvalidProductImageException ex) {
        return build(HttpStatus.BAD_REQUEST, "Image invalide", ex.getMessage(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.BAD_REQUEST, "Image trop volumineuse",
                "L'image ne doit pas dépasser 5 Mo.", null);
    }

    /** RG : règles de gestion métier violées (RG-01, RG-02, RG-05...). */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRuleException(BusinessRuleException ex) {
        log.warn("Règle de gestion violée : {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Erreur de règle de gestion",
                ex.getMessage(), "Corrigez la requête ou l'état des données puis réessayez.");
    }

    /** 400 : Bean Validation sur le corps de la requête. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : error.getObjectName();
            fieldErrors.put(fieldName, error.getDefaultMessage());
        });
        log.warn("Validation échouée : {}", fieldErrors);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Erreur de validation");
        body.put("message", "Les données saisies sont invalides. Vérifiez les champs indiqués.");
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** 400 : Bean Validation sur les paramètres (@Validated + @PathVariable/@RequestParam). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> violations = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                violations.put(v.getPropertyPath().toString(), v.getMessage()));
        log.warn("Paramètres invalides : {}", violations);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Erreur de validation");
        body.put("message", "Les paramètres de la requête sont invalides. Vérifiez les champs indiqués.");
        body.put("errors", violations);
        return ResponseEntity.badRequest().body(body);
    }

    /** 400 : corps JSON illisible ou mal formé. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Corps de requête illisible : {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Requête invalide",
                "Le corps de la requête est absent ou mal formé (JSON attendu).",
                "Vérifiez la structure du JSON et les valeurs des énumérations.");
    }

    /** 400 : paramètre de requête manquant. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Paramètre manquant : {}", ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "Paramètre manquant",
                String.format("Le paramètre « %s » est obligatoire (type attendu : %s).",
                        ex.getParameterName(), ex.getParameterType()),
                "Ajoutez le paramètre à la requête et réessayez.");
    }

    /** 400 : paramètre de chemin avec le mauvais type (ex. /api/products/abc au lieu de /api/products/1). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type de paramètre invalide : {} = {}", ex.getName(), ex.getValue());
        return build(HttpStatus.BAD_REQUEST, "Paramètre invalide",
                String.format("Le paramètre « %s » est invalide : « %s » n'est pas du type attendu (%s).",
                        ex.getName(), ex.getValue(),
                        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "inconnu"),
                "Vérifiez le format du paramètre et réessayez.");
    }

    /** 404 : ressource introuvable (IllegalArgumentException levé par les services). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Ressource introuvable ou argument invalide : {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Ressource non trouvée",
                ex.getMessage() != null ? ex.getMessage() : "La ressource demandée n'existe pas.",
                "Vérifiez l'identifiant et réessayez.");
    }

    /** 409 : conflit d'état (doublon, transition de statut interdite, stock insuffisant...). */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Conflit d'état : {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflit d'état",
                ex.getMessage() != null ? ex.getMessage() : "Cette action n'est pas autorisée dans l'état actuel.",
                "Rechargez les données puis réessayez, ou adaptez votre action à l'état actuel.");
    }

    /** 401 : identifiants incorrects (login) ou jeton invalide. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Échec d'authentification (identifiants incorrects)");
        return build(HttpStatus.UNAUTHORIZED, "Non authentifié",
                "Email ou mot de passe incorrect.",
                "Vérifiez vos identifiants et réessayez.");
    }

    /** 401 : compte désactivé ou verrouillé — message explicite au lieu d'un « identifiants invalides » trompeur. */
    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<Map<String, Object>> handleAccountStatus(AccountStatusException ex) {
        log.warn("Échec d'authentification (statut du compte) : {}", ex.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED, "Compte indisponible",
                "Votre compte est désactivé ou verrouillé. Contactez un administrateur.",
                null);
    }

    /** 401 : autres erreurs d'authentification (jeton expiré, mal formé...). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Erreur d'authentification : {}", ex.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED, "Non authentifié",
                "Authentification requise ou session expirée. Reconnectez-vous.",
                "Fournissez un jeton valide dans l'en-tête « Authorization: Bearer <token> ».");
    }

    /** 403 : rôle insuffisant. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Accès refusé : {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Accès refusé",
                ex.getMessage() != null && !ex.getMessage().isBlank()
                        ? ex.getMessage()
                        : "Vous n'avez pas les droits nécessaires pour effectuer cette action.",
                "Contactez un administrateur si vous pensez que c'est une erreur.");
    }

    /**
     * 500 : erreur inattendue. Le message technique est LOGUÉ côté serveur
     * (jamais renvoyé au client : pas de fuite de détails SQL/stacktrace),
     * le client reçoit un message générique actionnable.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Erreur interne inattendue : {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur",
                "Une erreur inattendue est survenue. Réessayez ou contactez l'administrateur.",
                "L'erreur a été enregistrée côté serveur (code horodaté : "
                        + LocalDateTime.now() + ").");
    }
}
