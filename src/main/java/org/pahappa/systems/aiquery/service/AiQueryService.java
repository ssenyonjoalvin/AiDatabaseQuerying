package org.pahappa.systems.aiquery.service;

import org.pahappa.systems.aiquery.dto.AggregationRequest;
import org.pahappa.systems.aiquery.dto.FilterCriterion;
import org.pahappa.systems.aiquery.dto.FilterGroup;
import org.pahappa.systems.aiquery.dto.QueryResult;
import org.pahappa.systems.aiquery.dto.QueryableEntityDescriptor;
import org.pahappa.systems.aiquery.dto.QueryableEntitySummary;
import org.pahappa.systems.aiquery.dto.SortCriterion;
import org.sers.webutils.model.security.User;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
public interface AiQueryService {

    List<QueryableEntitySummary> listQueryableEntities(User user);

    QueryResult queryFromNaturalLanguage(
            User user,
            String naturalLanguageQuery
    );

    QueryableEntityDescriptor describeQueryableEntity(
            User user,
            String entityName
    );

    QueryResult queryEntity(
            User user,
            String entityName,
            List<String> fields,
            List<FilterCriterion> filters,
            List<SortCriterion> sort,
            Integer page,
            Integer pageSize,
            AggregationRequest aggregation
    );

    QueryResult queryEntity(
            User user,
            String entityName,
            List<String> fields,
            List<FilterCriterion> filters,
            List<FilterGroup> filterGroups,
            List<SortCriterion> sort,
            Integer page,
            Integer pageSize,
            AggregationRequest aggregation
    );
}