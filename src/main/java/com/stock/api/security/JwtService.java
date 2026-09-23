package com.stock.api.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service JWT pour la génération, validation et extraction de tokens.
 */
@Service
public class JwtService {

    /**
     * Secret de signature JWT (Base64) — OBLIGATOIRE, sans valeur par défaut :
     * l'application refuse de démarrer sans la variable d'environnement JWT_SECRET
     * (voir .env.example). Jamais de clé codée en dur dans le code.
     */
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}") // 24h par défaut
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800000}") // 7 jours par défaut
    private long refreshExpiration;

    /**
     * Valide le secret au démarrage : HS256 exige au moins 32 octets (256 bits).
     * Échoue avec un message clair plutôt qu'à la première signature.
     */
    @PostConstruct
    void validateSecret() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret invalide : la clé signée HS256 doit faire au moins 32 octets "
                            + "(générez-en une avec : openssl rand -base64 48)");
        }
    }

    /**
     * Génère un JWT pour un utilisateur donné.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /** Génère un JWT incluant l'entreprise du compte (V2 multi-entreprises). */
    public String generateTokenWithCompany(UserDetails userDetails, Long companyId) {
        HashMap<String, Object> claims = new HashMap<>();
        if (companyId != null) {
            claims.put("companyId", companyId);
        }
        return generateToken(claims, userDetails);
    }

    /** Entreprise du token ; null = compte plateforme (SUPER_ADMIN). */
    public Long extractCompanyId(String token) {
        Object claim = extractAllClaims(token).get("companyId");
        return claim instanceof Number number ? number.longValue() : null;
    }

    /**
     * Génère un JWT avec des claims supplémentaires.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    /**
     * Génère un refresh token.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Extrait l'email (subject) du token.
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extrait la date d'expiration du token.
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extrait un claim spécifique du token.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Vérifie si le token est valide pour un utilisateur donné.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Vérifie si le token est expiré.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extrait toutes les claims du token.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Retourne la clé de signature à partir du secret encodé en Base64.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
