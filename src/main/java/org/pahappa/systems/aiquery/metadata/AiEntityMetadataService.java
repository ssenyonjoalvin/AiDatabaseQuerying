package org.pahappa.systems.aiquery.metadata;

import org.pahappa.systems.aiquery.annotation.AiNotQueryable;
import org.pahappa.systems.aiquery.typeconversion.AiTypeConversionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The source of truth for what the AI is structurally allowed to query: every JPA-managed
 * entity and every one of its basic (non-relationship) persistent attributes, discovered
 * from the JPA {@link javax.persistence.metamodel.Metamodel} rather than from class
 * scanning or {@code Class.forName}. An attribute only ever appears here if the JPA
 * provider itself considers it a mapped, basic-typed persistent attribute -- static,
 * transient, synthetic, and relationship attributes never enter this catalog -- and only
 * if it is not annotated {@link AiNotQueryable} (on either the field or its accessor) and its
 * name doesn't look credential-shaped (see {@link #SENSITIVE_FIELD_NAME}).
 * Adding a new entity or a new plain field requires no change to this class or to any
 * AI tool; it is picked up automatically the next time the catalog is built.
 */
@Service
public class AiEntityMetadataService {

    /**
     * Credential-shaped fields are excluded from every entity's catalog by name alone, as a
     * defense-in-depth floor beneath {@link AiNotQueryable}: that annotation only helps for
     * entities the host app owns the source of, but relations routinely reach into
     * framework/library types (e.g. {@code org.sers.webutils.model.security.User}) the host
     * cannot annotate. Sending field *names* like "password" or "apiToken" into a third-party
     * LLM's context is unnecessary and worth avoiding even though values are never exposed.
     */
    private static final Pattern SENSITIVE_FIELD_NAME = Pattern.compile(
            "password|passwd|secret|salt|token|apikey|api_key", Pattern.CASE_INSENSITIVE);

    private final EntityManagerFactory entityManagerFactory;
    private final AiTypeConversionService typeConversionService;

    private Map<String, ResolvedEntity> catalog = Collections.emptyMap();
    private Map<String, ResolvedEntity> caseInsensitiveIndex = Collections.emptyMap();

    @Autowired
    public AiEntityMetadataService(EntityManagerFactory entityManagerFactory, AiTypeConversionService typeConversionService) {
        this.entityManagerFactory = entityManagerFactory;
        this.typeConversionService = typeConversionService;
    }

    @PostConstruct
    void buildCatalog() {
        // Pass 1: resolve every entity's own basic fields, with no relations wired up yet --
        // relation resolution (pass 2) needs every entity's field list to already exist so it
        // can describe the *target* side of a relationship.
        Map<String, ResolvedEntity> withoutRelations = new LinkedHashMap<String, ResolvedEntity>();
        Map<Class<?>, String> entityNameByJavaType = new HashMap<Class<?>, String>();
        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            ResolvedEntity resolved = resolveFields(entityType);
            withoutRelations.put(resolved.getEntityName(), resolved);
            entityNameByJavaType.put(entityType.getJavaType(), entityType.getName());
        }

        // Pass 2: resolve each entity's single-valued (@ManyToOne/@OneToOne) relationships,
        // one level deep, now that every target entity's own fields are known.
        Map<String, ResolvedEntity> built = new LinkedHashMap<String, ResolvedEntity>();
        Map<String, ResolvedEntity> insensitive = new LinkedHashMap<String, ResolvedEntity>();
        for (EntityType<?> entityType : entityManagerFactory.getMetamodel().getEntities()) {
            ResolvedEntity base = withoutRelations.get(entityType.getName());
            Map<String, ResolvedRelation> relations = resolveRelations(entityType, withoutRelations, entityNameByJavaType);
            ResolvedEntity complete = new ResolvedEntity(base.getEntityName(), base.getEntityType(), base.getJavaType(),
                    base.getFields(), Collections.unmodifiableMap(relations));
            built.put(complete.getEntityName(), complete);
            insensitive.put(complete.getEntityName().toLowerCase(Locale.ROOT), complete);
        }

        this.catalog = Collections.unmodifiableMap(built);
        this.caseInsensitiveIndex = Collections.unmodifiableMap(insensitive);
    }

    private ResolvedEntity resolveFields(EntityType<?> entityType) {
        Class<?> javaType = entityType.getJavaType();
        Map<String, ResolvedField> fields = new TreeMap<String, ResolvedField>();

        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            if (!(attribute instanceof SingularAttribute<?, ?>)) {
                continue;
            }
            SingularAttribute<?, ?> singular = (SingularAttribute<?, ?>) attribute;
            if (singular.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC) {
                continue;
            }
            if (isAiNotQueryable(javaType, singular)) {
                continue;
            }
            fields.put(singular.getName(), toResolvedField(singular));
        }

        return new ResolvedEntity(entityType.getName(), entityType, javaType, Collections.unmodifiableMap(fields),
                Collections.<String, ResolvedRelation>emptyMap());
    }

    private Map<String, ResolvedRelation> resolveRelations(EntityType<?> entityType,
                                                             Map<String, ResolvedEntity> resolvedByName,
                                                             Map<Class<?>, String> entityNameByJavaType) {
        Class<?> javaType = entityType.getJavaType();
        Map<String, ResolvedRelation> relations = new TreeMap<String, ResolvedRelation>();

        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            if (!(attribute instanceof SingularAttribute<?, ?>)) {
                continue;
            }
            SingularAttribute<?, ?> singular = (SingularAttribute<?, ?>) attribute;
            Attribute.PersistentAttributeType type = singular.getPersistentAttributeType();
            if (type != Attribute.PersistentAttributeType.MANY_TO_ONE
                    && type != Attribute.PersistentAttributeType.ONE_TO_ONE) {
                continue;
            }
            if (isAiNotQueryable(javaType, singular)) {
                continue;
            }
            String targetEntityName = entityNameByJavaType.get(singular.getJavaType());
            if (targetEntityName == null) {
                continue;
            }
            ResolvedEntity targetEntity = resolvedByName.get(targetEntityName);
            if (targetEntity == null || targetEntity.getAllFields().isEmpty()) {
                continue;
            }
            relations.put(singular.getName(), new ResolvedRelation(singular.getName(), singular, targetEntity));
        }

        return relations;
    }

    private ResolvedField toResolvedField(SingularAttribute<?, ?> attribute) {
        Class<?> javaType = attribute.getJavaType();
        return new ResolvedField(
                attribute.getName(),
                attribute,
                javaType,
                true,
                true,
                typeConversionService.supportedOperatorsFor(javaType),
                typeConversionService.enumConstantNames(javaType));
    }

    private boolean isAiNotQueryable(Class<?> entityClass, Attribute<?, ?> attribute) {
        if (SENSITIVE_FIELD_NAME.matcher(attribute.getName()).find()) {
            return true;
        }
        Member member = attribute.getJavaMember();
        if (member instanceof AnnotatedElement && ((AnnotatedElement) member).isAnnotationPresent(AiNotQueryable.class)) {
            return true;
        }
        Field field = findDeclaredField(entityClass, attribute.getName());
        if (field != null && field.isAnnotationPresent(AiNotQueryable.class)) {
            return true;
        }
        Method getter = findGetter(entityClass, attribute.getName(), attribute.getJavaType());
        return getter != null && getter.isAnnotationPresent(AiNotQueryable.class);
    }

    private Field findDeclaredField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Method findGetter(Class<?> type, String attributeName, Class<?> attributeType) {
        String capitalized = Character.toUpperCase(attributeName.charAt(0)) + attributeName.substring(1);
        String[] candidates = (attributeType == boolean.class || attributeType == Boolean.class)
                ? new String[]{"is" + capitalized, "get" + capitalized}
                : new String[]{"get" + capitalized};

        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (String candidate : candidates) {
                try {
                    return current.getDeclaredMethod(candidate);
                } catch (NoSuchMethodException ignored) {
                    // try next candidate/superclass
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public Collection<ResolvedEntity> listEntities() {
        return catalog.values();
    }

    public Optional<ResolvedEntity> findEntity(String name) {
        if (name == null) {
            return Optional.empty();
        }
        ResolvedEntity exact = catalog.get(name);
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(caseInsensitiveIndex.get(name.toLowerCase(Locale.ROOT)));
    }
}
