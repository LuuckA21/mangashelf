package me.luucka.mangashelf.common;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.proxy.HibernateProxy;

/**
 * Base class for entities with a database-generated surrogate key.
 *
 * <p>The {@code equals}/{@code hashCode} pair follows the only contract that
 * stays consistent across an entity's lifecycle. A transient instance has no
 * id yet, so two unsaved entities must never compare equal; once persisted,
 * identity is the id alone. {@code hashCode} returns a constant per type
 * because the id is assigned <em>after</em> the entity may already sit in a
 * {@link java.util.HashSet}, and a hash that changed on flush would make the
 * entity unreachable in that set.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    protected void setId(Long id) {
        this.id = id;
    }

    /**
     * Resolves the real type behind a possible lazy-loading proxy, so that a
     * proxy and its initialized counterpart compare as the same entity.
     */
    private static Class<?> effectiveClass(Object o) {
        return o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || effectiveClass(this) != effectiveClass(o)) return false;
        Long thisId = getId();
        return thisId != null && thisId.equals(((BaseEntity) o).getId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }
}
