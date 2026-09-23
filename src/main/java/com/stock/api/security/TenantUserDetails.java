package com.stock.api.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * UserDetails enrichi de l'entreprise du compte (null = plateforme SUPER_ADMIN).
 * Le filtre JWT alimente ensuite le TenantContext à partir de cette valeur.
 */
@Getter
public class TenantUserDetails extends User {

    private final Long companyId;

    public TenantUserDetails(String username, String password, boolean active,
                             Collection<? extends GrantedAuthority> authorities,
                             Long companyId) {
        super(username, password, active, true, true, true, authorities);
        this.companyId = companyId;
    }
}
