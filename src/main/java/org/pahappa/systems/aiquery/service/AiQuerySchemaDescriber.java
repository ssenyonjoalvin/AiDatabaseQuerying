package org.pahappa.systems.aiquery.service;

import org.pahappa.systems.aiquery.metadata.AiEntityMetadataService;
import org.pahappa.systems.aiquery.metadata.ResolvedEntity;
import org.pahappa.systems.aiquery.metadata.ResolvedField;
import org.pahappa.systems.aiquery.metadata.ResolvedRelation;
import org.pahappa.systems.aiquery.security.AiDatabaseAuthorizationService;
import org.sers.webutils.model.security.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Describes a user's accessible entities/fields as the plain-text schema block sent to the
 * model, identically for {@link NaturalLanguageQueryTranslator}'s single-shot prompt and the
 * agentic loop's system prompt -- both need the model to see exactly the same authorized names
 * and types, since both feed a plan through the same {@code AiQueryValidator}.
 */
@Service
public class AiQuerySchemaDescriber {

    private final AiEntityMetadataService metadataService;
    private final AiDatabaseAuthorizationService authorizationService;

    @Autowired
    public AiQuerySchemaDescriber(AiEntityMetadataService metadataService,
                                   AiDatabaseAuthorizationService authorizationService) {
        this.metadataService = metadataService;
        this.authorizationService = authorizationService;
    }

    /** Entities the user can see at all, i.e. accessible with at least one accessible field. */
    public List<ResolvedEntity> listAccessibleEntities(User user) {
        List<ResolvedEntity> accessible = new ArrayList<ResolvedEntity>();
        for (ResolvedEntity entity : metadataService.listEntities()) {
            if (!authorizationService.canAccessEntity(user, entity.getEntityName())) {
                continue;
            }
            boolean hasAccessibleField = false;
            for (ResolvedField field : entity.getAllFields()) {
                if (authorizationService.canAccessField(user, entity.getEntityName(), field.getName())) {
                    hasAccessibleField = true;
                    break;
                }
            }
            if (hasAccessibleField) {
                accessible.add(entity);
            }
        }
        return accessible;
    }

    /** "Entity X: field:Type, ...\n..." block for the given entities' accessible own and one-level-relation fields. */
    public String describeSchema(User user, List<ResolvedEntity> entities) {
        StringBuilder schema = new StringBuilder();
        for (ResolvedEntity entity : entities) {
            StringBuilder fieldsLine = new StringBuilder();
            appendFields(user, entity, entity.getAllFields(), null, fieldsLine);
            for (ResolvedRelation relation : entity.getAllRelations()) {
                if (!authorizationService.canAccessField(user, entity.getEntityName(), relation.getName())) {
                    continue;
                }
                ResolvedEntity targetEntity = relation.getTargetEntity();
                appendFields(user, targetEntity, targetEntity.getAllFields(), relation.getName(), fieldsLine);
            }
            if (fieldsLine.length() == 0) {
                continue;
            }
            schema.append("Entity ").append(entity.getEntityName()).append(": ").append(fieldsLine).append("\n");
        }
        return schema.toString();
    }

    /** Appends "name:Type" (or "prefix.name:Type") entries for every field the user can access. */
    private void appendFields(User user, ResolvedEntity fieldOwner, Collection<ResolvedField> fields, String prefix,
                               StringBuilder fieldsLine) {
        for (ResolvedField field : fields) {
            if (!authorizationService.canAccessField(user, fieldOwner.getEntityName(), field.getName())) {
                continue;
            }
            if (fieldsLine.length() > 0) {
                fieldsLine.append(", ");
            }
            String qualifiedName = prefix == null ? field.getName() : prefix + "." + field.getName();
            fieldsLine.append(qualifiedName).append(":").append(field.getJavaType().getSimpleName());
            if (!field.getAllowedValues().isEmpty()) {
                fieldsLine.append("[").append(joinWithPipe(field.getAllowedValues())).append("]");
            }
        }
    }

    private String joinWithPipe(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }
}
